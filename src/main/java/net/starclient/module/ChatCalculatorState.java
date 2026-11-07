package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Evaluates live math expressions and Nether/Overworld coordinate conversions
 * typed into the Chat input bar for the "Chat Calculator" UI module.
 */
public final class ChatCalculatorState {

    private static final DecimalFormat GROUPED_FMT = new DecimalFormat("#,##0.####",
            DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat PLAIN_FMT = new DecimalFormat("0.####",
            DecimalFormatSymbols.getInstance(Locale.US));

    private static Module module;

    private ChatCalculatorState() {
    }

    public record CalcResult(String displayAnswer, String extraHint, String replacementText) {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static boolean isTabToInsert() {
        return isEnabled() && getBool("Tab to Insert");
    }

    public static CalcResult evaluate(Minecraft client, String rawInput) {
        if (!isEnabled() || rawInput == null || rawInput.isEmpty()) {
            return null;
        }
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/")) {
            return null;
        }

        // 1. Prefix mode: "= ..."
        if (trimmed.startsWith("=")) {
            String body = trimmed.substring(1).trim();
            if (body.isEmpty()) return null;

            // Coordinate Converter helper: =nether [x z] or =ow / =overworld [x z]
            if (getBool("Coord Converter")) {
                CalcResult coordRes = tryEvaluateCoords(client, body);
                if (coordRes != null) return coordRes;
            }

            if (getBool("Math Expressions")) {
                return tryEvaluateMath(body, "", false);
            }
            return null;
        }

        // 2. Trailing '=' suffix mode inside a chat sentence: e.g. "I need 27*64="
        if (getBool("Math Expressions") && rawInput.endsWith("=") && rawInput.length() >= 2) {
            String withoutEq = rawInput.substring(0, rawInput.length() - 1);
            int exprStart = findTrailingExpressionStart(withoutEq);
            if (exprStart >= 0 && exprStart < withoutEq.length()) {
                String prefix = withoutEq.substring(0, exprStart);
                String expr = withoutEq.substring(exprStart).trim();
                if (!expr.isEmpty()) {
                    return tryEvaluateMath(expr, prefix, true);
                }
            }
        }

        return null;
    }

    private static CalcResult tryEvaluateCoords(Minecraft client, String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        boolean toNether = lower.equals("nether") || lower.startsWith("nether ");
        boolean toOverworld = lower.equals("ow") || lower.startsWith("ow ")
                || lower.equals("overworld") || lower.startsWith("overworld ")
                || lower.equals("coords");
        if (!toNether && !toOverworld) return null;

        String[] parts = body.split("\\s+");
        double x;
        double z;
        boolean autoFlipFromDimension = lower.equals("coords");

        if (parts.length == 1) {
            if (client == null || client.player == null) return null;
            x = client.player.getX();
            z = client.player.getZ();
            if (autoFlipFromDimension && client.level != null) {
                toNether = client.level.dimension() != Level.NETHER;
            }
        } else if (parts.length == 3) {
            try {
                x = Double.parseDouble(parts[1]);
                z = Double.parseDouble(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (parts.length == 4) {
            // Also accept X Y Z (ignoring Y for 8:1 scaling, keeping Y in output)
            try {
                x = Double.parseDouble(parts[1]);
                int y = (int) Math.round(Double.parseDouble(parts[2]));
                z = Double.parseDouble(parts[3]);
                int outX = (int) Math.round(toNether ? x / 8.0 : x * 8.0);
                int outZ = (int) Math.round(toNether ? z / 8.0 : z * 8.0);
                String targetDim = toNether ? "Nether" : "Overworld";
                String coordsText = outX + " " + y + " " + outZ;
                return new CalcResult(targetDim + ": " + outX + ", " + y + ", " + outZ,
                        toNether ? "(÷8 from Overworld)" : "(×8 from Nether)",
                        coordsText);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }

        int outX = (int) Math.round(toNether ? x / 8.0 : x * 8.0);
        int outZ = (int) Math.round(toNether ? z / 8.0 : z * 8.0);
        String targetDim = toNether ? "Nether" : "Overworld";
        String coordsText = outX + " " + outZ;
        return new CalcResult(
                targetDim + ": X " + outX + ", Z " + outZ,
                toNether ? "(÷8)" : "(×8)",
                coordsText
        );
    }

    private static CalcResult tryEvaluateMath(String expr, String prefix, boolean requireComputation) {
        MathParser.ParsedValue parsed = MathParser.parse(expr);
        if (parsed == null) {
            return null;
        }
        if (requireComputation && !parsed.computed()) {
            return null;
        }
        double val = parsed.value();
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return null;
        }
        // Normalize -0.0
        if (Math.abs(val) < 1e-12) {
            val = 0.0;
        }

        String grouped = GROUPED_FMT.format(val);
        String plain = PLAIN_FMT.format(val);

        String extra = "";
        if (getBool("Show Item Stacks")) {
            long rounded = Math.round(val);
            if (Math.abs(val - rounded) < 1e-9 && rounded >= 64 && rounded <= 10_000_000L) {
                long stacks = rounded / 64;
                long rem = rounded % 64;
                StringBuilder sb = new StringBuilder("(");
                sb.append(stacks).append(stacks == 1 ? " stack" : " stacks");
                if (rem > 0) {
                    sb.append(" + ").append(rem);
                }
                if (rounded >= 1728) {
                    double shulkers = rounded / 1728.0;
                    sb.append(String.format(Locale.US, " • %.1f SB", shulkers));
                }
                sb.append(")");
                extra = sb.toString();
            }
        }

        return new CalcResult(grouped, extra, prefix + plain);
    }

    private static int findTrailingExpressionStart(String s) {
        int i = s.length() - 1;
        int parenDepth = 0;
        boolean sawOperatorOrDigit = false;
        while (i >= 0) {
            char c = s.charAt(i);
            if (c == ')') {
                parenDepth++;
                i--;
            } else if (c == '(') {
                if (parenDepth > 0) {
                    parenDepth--;
                    i--;
                } else {
                    break;
                }
            } else if (Character.isDigit(c) || c == '.' || c == '+' || c == '-' || c == '*'
                    || c == '/' || c == '%' || c == '^' || c == ' ') {
                if (Character.isDigit(c)) sawOperatorOrDigit = true;
                i--;
            } else if (Character.isLetter(c)) {
                // Allow suffixes/functions like st, k, m, pi, sqrt only when adjacent to math
                int wordEnd = i + 1;
                while (i >= 0 && Character.isLetter(s.charAt(i))) {
                    i--;
                }
                String word = s.substring(i + 1, wordEnd).toLowerCase(Locale.ROOT);
                if (!isAllowedMathToken(word)) {
                    while (wordEnd < s.length() && s.charAt(wordEnd) == ' ') {
                        wordEnd++;
                    }
                    return wordEnd;
                }
            } else {
                break;
            }
        }
        if (!sawOperatorOrDigit) return -1;
        int start = i + 1;
        while (start < s.length() && s.charAt(start) == ' ') {
            start++;
        }
        return start;
    }

    private static boolean isAllowedMathToken(String token) {
        return switch (token) {
            case "k", "m", "b", "st", "stack", "stacks", "sb",
                 "pi", "e", "sqrt", "sin", "cos", "tan", "abs", "round", "floor", "ceil" -> true;
            default -> false;
        };
    }

    private static boolean getBool(String name) {
        if (module == null) return true;
        Setting<?> s = module.getSetting(name);
        return !(s instanceof BooleanSetting bs) || bs.getValue();
    }

    /**
     * Safe, zero-allocation-heavy recursive descent math parser.
     */
    private static final class MathParser {
        private final String input;
        private int pos = 0;
        private boolean hasOperatorOrFunc = false;

        private MathParser(String input) {
            this.input = input;
        }

        record ParsedValue(double value, boolean computed) {
        }

        static ParsedValue parse(String expr) {
            if (expr == null || expr.length() > 160) return null;
            MathParser p = new MathParser(expr);
            try {
                double result = p.parseExpression();
                p.skipSpaces();
                if (p.pos != p.input.length()) return null;
                return new ParsedValue(result, p.hasOperatorOrFunc);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        private void skipSpaces() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean eat(char expected) {
            skipSpaces();
            if (pos < input.length() && input.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private double parseExpression() {
            double x = parseTerm();
            while (true) {
                if (eat('+')) {
                    hasOperatorOrFunc = true;
                    x += parseTerm();
                } else if (eat('-')) {
                    hasOperatorOrFunc = true;
                    x -= parseTerm();
                } else {
                    return x;
                }
            }
        }

        private double parseTerm() {
            double x = parsePower();
            while (true) {
                if (eat('*')) {
                    hasOperatorOrFunc = true;
                    x *= parsePower();
                } else if (eat('/')) {
                    hasOperatorOrFunc = true;
                    double denom = parsePower();
                    if (Math.abs(denom) < 1e-15) throw new IllegalArgumentException("div0");
                    x /= denom;
                } else if (eat('%')) {
                    hasOperatorOrFunc = true;
                    double denom = parsePower();
                    if (Math.abs(denom) < 1e-15) throw new IllegalArgumentException("mod0");
                    x %= denom;
                } else {
                    return x;
                }
            }
        }

        private double parsePower() {
            double base = parseUnary();
            if (eat('^')) {
                hasOperatorOrFunc = true;
                double exp = parsePower();
                return Math.pow(base, exp);
            }
            return base;
        }

        private double parseUnary() {
            if (eat('+')) return parseUnary();
            if (eat('-')) return -parseUnary();
            return parsePrimary();
        }

        private double parsePrimary() {
            skipSpaces();
            if (pos >= input.length()) throw new IllegalArgumentException("eof");

            if (eat('(')) {
                double val = parseExpression();
                if (!eat(')')) throw new IllegalArgumentException("missing )");
                return applyUnitSuffix(val);
            }

            char c = input.charAt(pos);
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                boolean sawDot = false;
                while (pos < input.length()) {
                    char ch = input.charAt(pos);
                    if (Character.isDigit(ch)) {
                        pos++;
                    } else if (ch == '_' || ch == ',') {
                        pos++;
                    } else if (ch == '.' && !sawDot) {
                        sawDot = true;
                        pos++;
                    } else {
                        break;
                    }
                }
                String numStr = input.substring(start, pos).replace("_", "").replace(",", "");
                double val = Double.parseDouble(numStr);
                return applyUnitSuffix(val);
            }

            if (Character.isLetter(c)) {
                int start = pos;
                while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
                    pos++;
                }
                String ident = input.substring(start, pos).toLowerCase(Locale.ROOT);
                if ("pi".equals(ident)) return Math.PI;
                if ("e".equals(ident)) return Math.E;

                if (!eat('(')) throw new IllegalArgumentException("unknown ident");
                hasOperatorOrFunc = true;
                double arg = parseExpression();
                if (!eat(')')) throw new IllegalArgumentException("missing )");
                return switch (ident) {
                    case "sqrt" -> Math.sqrt(arg);
                    case "sin" -> Math.sin(Math.toRadians(arg));
                    case "cos" -> Math.cos(Math.toRadians(arg));
                    case "tan" -> Math.tan(Math.toRadians(arg));
                    case "abs" -> Math.abs(arg);
                    case "round" -> (double) Math.round(arg);
                    case "floor" -> Math.floor(arg);
                    case "ceil" -> Math.ceil(arg);
                    default -> throw new IllegalArgumentException("bad fn");
                };
            }

            throw new IllegalArgumentException("unexpected char");
        }

        private double applyUnitSuffix(double val) {
            int saved = pos;
            skipSpaces();
            if (pos < input.length() && Character.isLetter(input.charAt(pos))) {
                int start = pos;
                while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
                    pos++;
                }
                String unit = input.substring(start, pos).toLowerCase(Locale.ROOT);
                return switch (unit) {
                    case "k" -> {
                        hasOperatorOrFunc = true;
                        yield val * 1_000.0;
                    }
                    case "m" -> {
                        hasOperatorOrFunc = true;
                        yield val * 1_000_000.0;
                    }
                    case "b" -> {
                        hasOperatorOrFunc = true;
                        yield val * 1_000_000_000.0;
                    }
                    case "st", "stack", "stacks" -> {
                        hasOperatorOrFunc = true;
                        yield val * 64.0;
                    }
                    case "sb" -> {
                        hasOperatorOrFunc = true;
                        yield val * 1728.0;
                    }
                    default -> {
                        pos = saved;
                        yield val;
                    }
                };
            }
            pos = saved;
            return val;
        }
    }
}

package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Redstone Signal & Comparator Inspector ("Redstone Inspector" WORLD module):
 * Renders a crisp glassmorphic HUD badge when aiming at Redstone components
 * (Wire, Repeater, Comparator, Note Block, Observer, Daylight Detector, Target,
 * Piston, Hopper, Dispenser, Dropper, Crafter, Copper Bulb, Torch, Lamp, Lever,
 * Button, Pressure Plate, Sculk Sensor) or comparator-readable blocks
 * (Composter, Cauldron, Cake, Beehive/Bee Nest, Respawn Anchor, Chiseled Bookshelf,
 * End Portal Frame, Jukebox, Lectern).
 */
public final class RedstoneInspectorRenderer {

    private static final String[] NOTE_NAMES = {
            "F#3", "G3", "G#3", "A3", "A#3", "B3",
            "C4", "C#4", "D4", "D#4", "E4", "F4",
            "F#4", "G4", "G#4", "A4", "A#4", "B4",
            "C5", "C#5", "D5", "D#5", "E5", "F5",
            "F#5"
    };

    private static final int[] ANCHOR_COMPARATOR = {0, 3, 7, 11, 15};

    private record Chip(String text, int textColor, int borderColor) {
    }

    private record Inspection(
            ItemStack icon,
            String title,
            int signalStrength, // -1 if no 0-15 signal bar applies
            String signalLabel,
            List<Chip> chips
    ) {
    }

    private static Module module;

    private RedstoneInspectorRenderer() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;
        if (!(client.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        if (state.isAir()) return;

        boolean showSignalBar = getBool(mod, "Show Signal Bar", true);
        boolean showNotePitch = getBool(mod, "Show Note Pitch", true);
        boolean showMisc = getBool(mod, "Show Containers & Misc", true);

        Inspection info = inspect(state, showNotePitch, showMisc);
        if (info == null) return;

        Font font = client.font;
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xE4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xE40F172A;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();

        boolean hasBar = showSignalBar && info.signalStrength() >= 0;

        // Calculate dynamic width from title, chips, and optional 15-segment bar
        int titleRowW = 24 + font.width(info.title()) + 8;
        int chipsRowW = 8;
        for (Chip chip : info.chips()) {
            chipsRowW += font.width(chip.text()) + 12;
        }
        int barRowW = hasBar ? (15 * 7 + 14 + font.width(info.signalLabel()) + 16) : 0;

        int cardW = Math.max(124, Math.max(titleRowW, Math.max(chipsRowW, barRowW)));
        int cardH = 22 + (info.chips().isEmpty() ? 0 : 15) + (hasBar ? 14 : 0) + 4;

        String posMode = getMode(mod, "Position", "Below Crosshair");
        int cardX = (sw - cardW) / 2;
        int cardY = "Top Center".equals(posMode) ? 14 : (sh / 2 + 20);
        cardY = Math.clamp(cardY, 6, Math.max(6, sh - cardH - 6));

        int outlineCol = info.signalStrength() > 0 ? 0xCCEF4444 : ((accent & 0x00FFFFFF) | 0x88000000);
        GuiRenderUtils.drawDropShadow(extractor, cardX, cardY, cardW, cardH, 5, 3, 95);
        GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, cardW, cardH, 5, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, cardW, cardH, 5, outlineCol);

        // Title Row + Block Icon
        if (!info.icon().isEmpty()) {
            extractor.item(info.icon(), cardX + 5, cardY + 3);
        }
        extractor.text(font, info.title(), cardX + 24, cardY + 7, 0xFFF8FAFC, true);

        // Right-aligned live signal pill in header when applicable
        if (info.signalStrength() >= 0) {
            String badge = info.signalStrength() + "/15";
            int bw = font.width(badge) + 8;
            int bx = cardX + cardW - bw - 5;
            int sigCol = signalColor(info.signalStrength());
            GuiRenderUtils.fillRoundedRect(extractor, bx, cardY + 5, bw, 11, 3, (sigCol & 0x00FFFFFF) | 0x2E000000);
            GuiRenderUtils.drawRoundedOutline(extractor, bx, cardY + 5, bw, 11, 3, (sigCol & 0x00FFFFFF) | 0x88000000);
            extractor.text(font, badge, bx + 4, cardY + 7, sigCol, false);
        }

        int curY = cardY + 22;

        // 15-Segment Signal Strength Bar
        if (hasBar) {
            int barX = cardX + 7;
            int segW = 5;
            int segGap = 2;
            int sig = Math.clamp(info.signalStrength(), 0, 15);
            for (int i = 1; i <= 15; i++) {
                int sx = barX + (i - 1) * (segW + segGap);
                int col = i <= sig ? segmentColor(i) : 0x441E293B;
                GuiRenderUtils.fillRoundedRect(extractor, sx, curY + 2, segW, 6, 1, col);
            }
            int labelX = barX + 15 * (segW + segGap) + 4;
            extractor.text(font, info.signalLabel(), labelX, curY + 1, sig > 0 ? 0xFFFCA5A5 : 0xFF94A3B8, false);
            curY += 14;
        }

        // State Chips Row
        if (!info.chips().isEmpty()) {
            int cx = cardX + 6;
            for (Chip chip : info.chips()) {
                int cw = font.width(chip.text()) + 8;
                if (cx + cw > cardX + cardW - 4) break;
                GuiRenderUtils.fillRoundedRect(extractor, cx, curY, cw, 12, 3, (chip.borderColor() & 0x00FFFFFF) | 0x24000000);
                GuiRenderUtils.drawRoundedOutline(extractor, cx, curY, cw, 12, 3, (chip.borderColor() & 0x00FFFFFF) | 0x88000000);
                extractor.text(font, chip.text(), cx + 4, curY + 2, chip.textColor(), false);
                cx += cw + 4;
            }
        }
    }

    private static Inspection inspect(BlockState state, boolean showNotePitch, boolean showMisc) {
        Block block = state.getBlock();
        String name = block.getName().getString();
        List<Chip> chips = new ArrayList<>();

        // 1. Redstone Wire
        if (state.is(Blocks.REDSTONE_WIRE) && state.hasProperty(BlockStateProperties.POWER)) {
            int power = state.getValue(BlockStateProperties.POWER);
            chips.add(new Chip(power > 0 ? "⚡ Powered" : "○ Unpowered",
                    power > 0 ? 0xFFFCA5A5 : 0xFF94A3B8,
                    power > 0 ? 0xFFEF4444 : 0xFF64748B));
            return new Inspection(new ItemStack(Items.REDSTONE), "Redstone Dust", power, "Power", chips);
        }

        // 2. Redstone Repeater
        if (state.is(Blocks.REPEATER)) {
            int delay = state.getValue(BlockStateProperties.DELAY);
            boolean powered = state.getValue(BlockStateProperties.POWERED);
            boolean locked = state.getValue(BlockStateProperties.LOCKED);
            chips.add(new Chip(String.format(Locale.ROOT, "Delay: %dt (0.%ds)", delay, delay), 0xFFFDE68A, 0xFFF59E0B));
            chips.add(new Chip(powered ? "⚡ ON" : "○ OFF", powered ? 0xFFFCA5A5 : 0xFF94A3B8, powered ? 0xFFEF4444 : 0xFF64748B));
            if (locked) {
                chips.add(new Chip("🔒 Locked", 0xFFCBD5E1, 0xFF94A3B8));
            }
            return new Inspection(resolveIcon(block, Items.REPEATER), name, powered ? 15 : 0, "Output", chips);
        }

        // 3. Redstone Comparator
        if (state.is(Blocks.COMPARATOR)) {
            ComparatorMode mode = state.getValue(BlockStateProperties.MODE_COMPARATOR);
            boolean powered = state.getValue(BlockStateProperties.POWERED);
            boolean subtract = mode == ComparatorMode.SUBTRACT;
            chips.add(new Chip(subtract ? "Mode: Subtract (−)" : "Mode: Compare",
                    subtract ? 0xFFFCA5A5 : 0xFF7DD3FC,
                    subtract ? 0xFFEF4444 : 0xFF38BDF8));
            chips.add(new Chip(powered ? "⚡ Active" : "○ Idle",
                    powered ? 0xFF6EE7B7 : 0xFF94A3B8,
                    powered ? 0xFF10B981 : 0xFF64748B));
            return new Inspection(resolveIcon(block, Items.COMPARATOR), name, -1, "", chips);
        }

        // 4. Note Block
        if (state.is(Blocks.NOTE_BLOCK) && state.hasProperty(BlockStateProperties.NOTE)) {
            int note = Math.clamp(state.getValue(BlockStateProperties.NOTE), 0, NOTE_NAMES.length - 1);
            NoteBlockInstrument inst = state.getValue(BlockStateProperties.NOTEBLOCK_INSTRUMENT);
            if (showNotePitch) {
                chips.add(new Chip("♪ " + NOTE_NAMES[note] + " (#" + note + ")", 0xFF6EE7B7, 0xFF10B981));
            } else {
                chips.add(new Chip("Note #" + note, 0xFF6EE7B7, 0xFF10B981));
            }
            chips.add(new Chip(formatEnum(inst.name()), 0xFFC4B5FD, 0xFF8B5CF6));
            return new Inspection(resolveIcon(block, Items.NOTE_BLOCK), name, -1, "", chips);
        }

        // 5. Daylight Detector
        if (state.is(Blocks.DAYLIGHT_DETECTOR) && state.hasProperty(BlockStateProperties.POWER)) {
            int power = state.getValue(BlockStateProperties.POWER);
            boolean inv = state.getValue(BlockStateProperties.INVERTED);
            chips.add(new Chip(inv ? "🌙 Night (Inverted)" : "☀ Day Mode",
                    inv ? 0xFF93C5FD : 0xFFFDE68A,
                    inv ? 0xFF3B82F6 : 0xFFF59E0B));
            return new Inspection(resolveIcon(block, Items.DAYLIGHT_DETECTOR), name, power, "Signal", chips);
        }

        // 6. Redstone Block (always 15)
        if (state.is(Blocks.REDSTONE_BLOCK)) {
            chips.add(new Chip("Constant Power Source", 0xFFFCA5A5, 0xFFEF4444));
            return new Inspection(resolveIcon(block, Items.REDSTONE_BLOCK), name, 15, "Power", chips);
        }

        // 7. Redstone Torch / Wall Torch
        if ((state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) && state.hasProperty(BlockStateProperties.LIT)) {
            boolean lit = state.getValue(BlockStateProperties.LIT);
            chips.add(new Chip(lit ? "⚡ Lit" : "○ Unlit (Powered)",
                    lit ? 0xFFFCA5A5 : 0xFF94A3B8,
                    lit ? 0xFFEF4444 : 0xFF64748B));
            return new Inspection(new ItemStack(Items.REDSTONE_TORCH), "Redstone Torch", lit ? 15 : 0, "Output", chips);
        }

        // 8. Observer
        if (state.is(Blocks.OBSERVER)) {
            boolean powered = state.getValue(BlockStateProperties.POWERED);
            Direction facing = state.getValue(BlockStateProperties.FACING);
            chips.add(new Chip("Watches: " + capitalize(facing.getName()), 0xFFCBD5E1, 0xFF64748B));
            chips.add(new Chip(powered ? "⚡ Pulse!" : "○ Ready",
                    powered ? 0xFFFCA5A5 : 0xFF94A3B8,
                    powered ? 0xFFEF4444 : 0xFF64748B));
            return new Inspection(resolveIcon(block, Items.OBSERVER), name, powered ? 15 : 0, "Pulse", chips);
        }

        // 9. Target Block / Weighted Pressure Plates / Sculk Sensors (any remaining block with POWER)
        if (state.hasProperty(BlockStateProperties.POWER)) {
            int power = state.getValue(BlockStateProperties.POWER);
            if (state.hasProperty(BlockStateProperties.SCULK_SENSOR_PHASE)) {
                String phase = formatEnum(state.getValue(BlockStateProperties.SCULK_SENSOR_PHASE).name());
                chips.add(new Chip("Phase: " + phase, 0xFF67E8F9, 0xFF06B6D4));
            } else {
                chips.add(new Chip(power > 0 ? "⚡ Active" : "○ Idle",
                        power > 0 ? 0xFFFCA5A5 : 0xFF94A3B8,
                        power > 0 ? 0xFFEF4444 : 0xFF64748B));
            }
            return new Inspection(resolveIcon(block, Items.REDSTONE), name, power, "Power", chips);
        }

        // 10. Copper Bulb & Redstone Lamp
        if (block instanceof CopperBulbBlock || state.is(Blocks.REDSTONE_LAMP)) {
            boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            chips.add(new Chip(lit ? "💡 Lit" : "○ Dark", lit ? 0xFFFDE68A : 0xFF94A3B8, lit ? 0xFFF59E0B : 0xFF64748B));
            if (block instanceof CopperBulbBlock) {
                chips.add(new Chip(powered ? "⚡ Powered" : "○ Unpowered",
                        powered ? 0xFFFCA5A5 : 0xFF94A3B8,
                        powered ? 0xFFEF4444 : 0xFF64748B));
                return new Inspection(resolveIcon(block, Items.REDSTONE_LAMP), name, lit ? 15 : 0, "Comp", chips);
            }
            return new Inspection(resolveIcon(block, Items.REDSTONE_LAMP), name, -1, "", chips);
        }

        // 11. Piston / Sticky Piston
        if (state.hasProperty(BlockStateProperties.EXTENDED)) {
            boolean ext = state.getValue(BlockStateProperties.EXTENDED);
            chips.add(new Chip(ext ? "▲ Extended" : "▼ Retracted",
                    ext ? 0xFF6EE7B7 : 0xFF94A3B8,
                    ext ? 0xFF10B981 : 0xFF64748B));
            if (state.hasProperty(BlockStateProperties.FACING)) {
                chips.add(new Chip("Facing: " + capitalize(state.getValue(BlockStateProperties.FACING).getName()), 0xFFCBD5E1, 0xFF64748B));
            }
            return new Inspection(resolveIcon(block, Items.PISTON), name, -1, "", chips);
        }

        // 12. Hopper
        if (state.is(Blocks.HOPPER) && state.hasProperty(BlockStateProperties.ENABLED)) {
            boolean enabled = state.getValue(BlockStateProperties.ENABLED);
            Direction dir = state.getValue(BlockStateProperties.FACING_HOPPER);
            chips.add(new Chip(enabled ? "● Active" : "🔒 Locked",
                    enabled ? 0xFF6EE7B7 : 0xFFFCA5A5,
                    enabled ? 0xFF10B981 : 0xFFEF4444));
            chips.add(new Chip("Out: " + capitalize(dir.getName()), 0xFFCBD5E1, 0xFF64748B));
            return new Inspection(resolveIcon(block, Items.HOPPER), name, -1, "", chips);
        }

        // 13. Dispenser / Dropper / Crafter
        if (state.is(Blocks.CRAFTER) || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)) {
            boolean trig = state.hasProperty(BlockStateProperties.TRIGGERED) && state.getValue(BlockStateProperties.TRIGGERED);
            chips.add(new Chip(trig ? "⚡ Triggered" : "○ Ready",
                    trig ? 0xFFFCA5A5 : 0xFF94A3B8,
                    trig ? 0xFFEF4444 : 0xFF64748B));
            if (state.hasProperty(BlockStateProperties.CRAFTING) && state.getValue(BlockStateProperties.CRAFTING)) {
                chips.add(new Chip("Crafting...", 0xFFFDE68A, 0xFFF59E0B));
            }
            return new Inspection(resolveIcon(block, Items.DISPENSER), name, -1, "", chips);
        }

        // 14. Levers, Buttons, Pressure Plates, Tripwire Hooks, Lightning Rods, Powered/Detector Rails
        if ((block instanceof LeverBlock || block instanceof ButtonBlock || block instanceof PressurePlateBlock
                || block instanceof TripWireHookBlock || block instanceof LightningRodBlock
                || block instanceof DetectorRailBlock || block instanceof PoweredRailBlock)
                && state.hasProperty(BlockStateProperties.POWERED)) {
            boolean powered = state.getValue(BlockStateProperties.POWERED);
            chips.add(new Chip(powered ? "⚡ Powered" : "○ Off",
                    powered ? 0xFFFCA5A5 : 0xFF94A3B8,
                    powered ? 0xFFEF4444 : 0xFF64748B));
            return new Inspection(resolveIcon(block, Items.LEVER), name, powered ? 15 : 0, "Output", chips);
        }

        if (!showMisc) return null;

        // 15. Comparator-Readable Misc Blocks
        // Composter (0-8 -> Comparator 0-8)
        if (state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER)) {
            int lvl = state.getValue(BlockStateProperties.LEVEL_COMPOSTER);
            chips.add(new Chip("Fill: " + lvl + " / 8" + (lvl == 8 ? " (Ready!)" : ""),
                    lvl == 8 ? 0xFF6EE7B7 : 0xFFFDE68A,
                    lvl == 8 ? 0xFF10B981 : 0xFFF59E0B));
            return new Inspection(resolveIcon(block, Items.COMPOSTER), name, lvl, "Comp", chips);
        }

        // Cauldrons (Water / Powder Snow 1-3, Lava 3)
        if (state.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) {
            int lvl = state.getValue(BlockStateProperties.LEVEL_CAULDRON);
            chips.add(new Chip("Level: " + lvl + " / 3", 0xFF7DD3FC, 0xFF38BDF8));
            return new Inspection(new ItemStack(Items.CAULDRON), name, lvl, "Comp", chips);
        }
        if (state.is(Blocks.LAVA_CAULDRON)) {
            chips.add(new Chip("Lava Full", 0xFFFDBA74, 0xFFF97316));
            return new Inspection(new ItemStack(Items.CAULDRON), name, 3, "Comp", chips);
        }

        // Cake (bites 0-6 -> slices 7-1 -> comparator 14 down to 2)
        if (state.hasProperty(BlockStateProperties.BITES)) {
            int bites = state.getValue(BlockStateProperties.BITES);
            int slices = Math.max(1, 7 - bites);
            int comp = slices * 2;
            chips.add(new Chip("Slices: " + slices + " / 7", 0xFFFBCFE8, 0xFFEC4899));
            return new Inspection(new ItemStack(Items.CAKE), name, comp, "Comp", chips);
        }

        // Beehive / Bee Nest (honey 0-5 -> comparator 0-5)
        if (state.hasProperty(BlockStateProperties.LEVEL_HONEY)) {
            int honey = state.getValue(BlockStateProperties.LEVEL_HONEY);
            chips.add(new Chip("Honey: " + honey + " / 5" + (honey == 5 ? " 🍯 Full!" : ""),
                    honey == 5 ? 0xFFFDE047 : 0xFFFDBA74,
                    honey == 5 ? 0xFFEAB308 : 0xFFF97316));
            return new Inspection(resolveIcon(block, Items.BEEHIVE), name, honey, "Comp", chips);
        }

        // Respawn Anchor (charges 0-4 -> comparator 0, 3, 7, 11, 15)
        if (state.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)) {
            int charges = Math.clamp(state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES), 0, 4);
            int comp = ANCHOR_COMPARATOR[charges];
            chips.add(new Chip("Charges: " + charges + " / 4", 0xFFC4B5FD, 0xFF8B5CF6));
            return new Inspection(resolveIcon(block, Items.RESPAWN_ANCHOR), name, comp, "Comp", chips);
        }

        // Chiseled Bookshelf (slots 0-5)
        if (state.hasProperty(BlockStateProperties.SLOT_0_OCCUPIED)) {
            int books = 0;
            if (state.getValue(BlockStateProperties.SLOT_0_OCCUPIED)) books++;
            if (state.getValue(BlockStateProperties.SLOT_1_OCCUPIED)) books++;
            if (state.getValue(BlockStateProperties.SLOT_2_OCCUPIED)) books++;
            if (state.getValue(BlockStateProperties.SLOT_3_OCCUPIED)) books++;
            if (state.getValue(BlockStateProperties.SLOT_4_OCCUPIED)) books++;
            if (state.getValue(BlockStateProperties.SLOT_5_OCCUPIED)) books++;
            chips.add(new Chip("Books: " + books + " / 6", 0xFFFDE68A, 0xFFF59E0B));
            return new Inspection(resolveIcon(block, Items.CHISELED_BOOKSHELF), name, -1, "", chips);
        }

        // End Portal Frame (EYE -> comparator 15 or 0)
        if (state.hasProperty(BlockStateProperties.EYE)) {
            boolean hasEye = state.getValue(BlockStateProperties.EYE);
            chips.add(new Chip(hasEye ? "Eye Placed" : "Empty Frame",
                    hasEye ? 0xFF6EE7B7 : 0xFF94A3B8,
                    hasEye ? 0xFF10B981 : 0xFF64748B));
            return new Inspection(resolveIcon(block, Items.END_PORTAL_FRAME), name, hasEye ? 15 : 0, "Comp", chips);
        }

        // Jukebox
        if (state.hasProperty(BlockStateProperties.HAS_RECORD)) {
            boolean rec = state.getValue(BlockStateProperties.HAS_RECORD);
            chips.add(new Chip(rec ? "♫ Disc Inside" : "○ Empty",
                    rec ? 0xFF7DD3FC : 0xFF94A3B8,
                    rec ? 0xFF38BDF8 : 0xFF64748B));
            return new Inspection(resolveIcon(block, Items.JUKEBOX), name, -1, "", chips);
        }

        // Lectern
        if (state.hasProperty(BlockStateProperties.HAS_BOOK)) {
            boolean hasBook = state.getValue(BlockStateProperties.HAS_BOOK);
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            chips.add(new Chip(hasBook ? "📖 Book Placed" : "○ Empty",
                    hasBook ? 0xFFFDE68A : 0xFF94A3B8,
                    hasBook ? 0xFFF59E0B : 0xFF64748B));
            if (powered) {
                chips.add(new Chip("⚡ Pulse", 0xFFFCA5A5, 0xFFEF4444));
            }
            return new Inspection(resolveIcon(block, Items.LECTERN), name, -1, "", chips);
        }

        return null;
    }

    private static ItemStack resolveIcon(Block block, Item fallback) {
        Item item = block.asItem();
        return item != Items.AIR ? new ItemStack(item) : new ItemStack(fallback);
    }

    private static int signalColor(int sig) {
        if (sig <= 0) return 0xFF94A3B8;
        if (sig <= 5) return 0xFFF59E0B;
        if (sig <= 10) return 0xFFF97316;
        return 0xFFEF4444;
    }

    private static int segmentColor(int index) {
        if (index <= 5) return 0xFFB91C1C;
        if (index <= 10) return 0xFFDC2626;
        return 0xFFEF4444;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String formatEnum(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(capitalize(parts[i]));
        }
        return sb.toString();
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static String getMode(Module mod, String name, String def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof ModeSetting ms ? ms.getValue() : def;
    }
}

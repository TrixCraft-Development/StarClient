package net.starclient.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.starclient.StarClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class PresetCosmetics {
    private static final Map<String, DynamicTexture> PRESET_TEXTURES = new HashMap<>();

    private PresetCosmetics() {
    }

    public static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    public static Identifier getOrRegisterCape(String id) {
        Identifier fileId = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/capes/" + id + ".png");
        Minecraft client = Minecraft.getInstance();
        client.getResourceManager();
        try {
            if (client.getResourceManager().getResource(fileId).isPresent()) {
                return fileId;
            }
        } catch (Exception ignored) {
        }
        if (PresetCosmetics.class.getResource("/assets/starclient/textures/capes/" + id + ".png") != null) {
            return fileId;
        }

        Identifier textureId = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/capes/preset_" + id);
        if (PRESET_TEXTURES.containsKey("cape_" + id)) {
            return textureId;
        }

        NativeImage img = new NativeImage(64, 32, true);

        switch (id) {
            case "galaxy_nebula" -> generateGalaxyCape(img);
            case "cyber_neon" -> generateCyberNeonCape(img);
            case "crimson_flame" -> generateFlameCape(img);
            case "sakura_blossom" -> generateSakuraCape(img);
            case "void_abyssal" -> generateVoidCape(img);
            case "emerald_matrix" -> generateMatrixCape(img);
            case "animated_chroma" -> generateChromaCape(img, 0.0f);
            case "animated_cosmic" -> generateCosmicCape(img, 0.0f);
            default -> generateStarClientCape(img);
        }

        DynamicTexture tex = new DynamicTexture(() -> "starclient_preset_cape_" + id, img);
        client.getTextureManager().register(textureId, tex);
        PRESET_TEXTURES.put("cape_" + id, tex);
        return textureId;
    }

    public static Identifier getOrRegisterWing(String id) {
        Identifier fileId = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/wings/" + id + ".png");
        Minecraft client = Minecraft.getInstance();
        client.getResourceManager();
        try {
            if (client.getResourceManager().getResource(fileId).isPresent()) {
                return fileId;
            }
        } catch (Exception ignored) {
        }

        Identifier textureId = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/wings/preset_" + id);
        if (PRESET_TEXTURES.containsKey("wing_" + id)) {
            return textureId;
        }

        NativeImage img = new NativeImage(64, 64, true);

        switch (id) {
            case "dragon_void" -> generateDragonWings(img);
            case "demon_flame" -> generateDemonWings(img);
            case "fairy_prismatic" -> generateFairyWings(img);
            default -> generateAngelWings(img);
        }

        DynamicTexture tex = new DynamicTexture(() -> "starclient_preset_wing_" + id, img);
        client.getTextureManager().register(textureId, tex);
        PRESET_TEXTURES.put("wing_" + id, tex);
        return textureId;
    }

    public static Identifier getOrRegisterHeadwear(String id) {
        Identifier fileId = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/headwear/" + id + ".png");
        Minecraft client = Minecraft.getInstance();
        client.getResourceManager();
        try {
            if (client.getResourceManager().getResource(fileId).isPresent()) {
                return fileId;
            }
        } catch (Exception ignored) {
        }

        Identifier textureId = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/headwear/preset_" + id);
        if (PRESET_TEXTURES.containsKey("head_" + id)) {
            return textureId;
        }

        NativeImage img = new NativeImage(32, 32, true);

        switch (id) {
            case "demon_horns" -> generateHornsTexture(img);
            case "ninja_bandana" -> generateBandanaTexture(img);
            case "cat_ears" -> generateCatEarsTexture(img);
            default -> generateHaloTexture(img);
        }

        DynamicTexture tex = new DynamicTexture(() -> "starclient_preset_head_" + id, img);
        client.getTextureManager().register(textureId, tex);
        PRESET_TEXTURES.put("head_" + id, tex);
        return textureId;
    }

    public static void tickAnimations(long ticks) {
        // Animated Chroma Cape (shifts colors smoothly)
        DynamicTexture chromaTex = PRESET_TEXTURES.get("cape_animated_chroma");
        if (chromaTex != null) {
            float phase = (ticks % 100) / 100.0f;
            generateChromaCape(chromaTex.getPixels(), phase);
            chromaTex.upload();
        }

        // Animated Cosmic Cape (pulsing star shimmer)
        DynamicTexture cosmicTex = PRESET_TEXTURES.get("cape_animated_cosmic");
        if (cosmicTex != null) {
            float pulse = (float) (Math.sin(ticks * 0.12) * 0.5 + 0.5);
            generateCosmicCape(cosmicTex.getPixels(), pulse);
            cosmicTex.upload();
        }
    }

    private static void fillBaseCape(NativeImage img, int bgArgb, int trimArgb) {
        int bg = toAbgr(bgArgb);
        int trim = toAbgr(trimArgb);

        // Fill all cape surfaces (0..21, 0..16)
        for (int x = 0; x <= 21; x++) {
            for (int y = 0; y <= 16; y++) {
                img.setPixel(x, y, bg);
            }
        }

        // Outer surface trim (x: 1..10, y: 1..16) - this faces OUTWARD (top)
        for (int x = 1; x <= 10; x++) {
            img.setPixel(x, 1, trim);
            img.setPixel(x, 16, trim);
        }
        for (int y = 1; y <= 16; y++) {
            img.setPixel(1, y, trim);
            img.setPixel(10, y, trim);
        }

        // Inner surface trim (x: 12..21, y: 1..16) - underside facing player back
        for (int x = 12; x <= 21; x++) {
            img.setPixel(x, 1, trim);
            img.setPixel(x, 16, trim);
        }
        for (int y = 1; y <= 16; y++) {
            img.setPixel(12, y, trim);
            img.setPixel(21, y, trim);
        }
    }

    private static void drawStarEmblem(NativeImage img, int cx, int cyan, int white, int darkBlue) {
        img.setPixel(cx, 8, white);
        img.setPixel(cx + 1, 8, white);
        img.setPixel(cx, 8 + 1, white);
        img.setPixel(cx + 1, 8 + 1, white);

        img.setPixel(cx, 8 - 2, cyan);
        img.setPixel(cx + 1, 8 - 2, cyan);
        img.setPixel(cx, 8 - 1, cyan);
        img.setPixel(cx + 1, 8 - 1, cyan);

        img.setPixel(cx, 8 + 2, cyan);
        img.setPixel(cx + 1, 8 + 2, cyan);
        img.setPixel(cx, 8 + 3, cyan);
        img.setPixel(cx + 1, 8 + 3, cyan);

        img.setPixel(cx - 2, 8, cyan);
        img.setPixel(cx - 1, 8, cyan);
        img.setPixel(cx - 2, 8 + 1, cyan);
        img.setPixel(cx - 1, 8 + 1, cyan);

        img.setPixel(cx + 2, 8, cyan);
        img.setPixel(cx + 3, 8, cyan);
        img.setPixel(cx + 2, 8 + 1, cyan);
        img.setPixel(cx + 3, 8 + 1, cyan);

        img.setPixel(cx - 1, 8 - 1, darkBlue);
        img.setPixel(cx + 2, 8 - 1, darkBlue);
        img.setPixel(cx - 1, 8 + 2, darkBlue);
        img.setPixel(cx + 2, 8 + 2, darkBlue);
    }

    private static void generateStarClientCape(NativeImage img) {
        fillBaseCape(img, 0xFF0F172A, 0xFF38BDF8); // Slate 900 background, Sky blue trim

        int cyan = toAbgr(0xFF38BDF8);
        int white = toAbgr(0xFFFFFFFF);
        int darkBlue = toAbgr(0xFF1E293B);

        // Outer surface (cx=5, cy=8) - Top / Outside face
        drawStarEmblem(img, 5, cyan, white, darkBlue);
        // Inner surface (cx=16, cy=8) - Underside face
        drawStarEmblem(img, 16, cyan, white, darkBlue);
    }

    private static void generateGalaxyCape(NativeImage img) {
        fillBaseCape(img, 0xFF1E112A, 0xFFA855F7);
        int star1 = toAbgr(0xFFE9D5FF);
        int star2 = toAbgr(0xFF38BDF8);
        int violet = toAbgr(0xFF7E22CE);
        int pink = toAbgr(0xFFEC4899);

        // Nebula cloud on outer (x: 2..9) & inner (x: 13..20)
        for (int y = 3; y <= 14; y++) {
            for (int x = 2; x <= 9; x++) {
                img.setPixel(x, y, ((x + y) % 3 == 0) ? violet : pink);
            }
            for (int x = 13; x <= 20; x++) {
                img.setPixel(x, y, ((x + y) % 3 == 0) ? violet : pink);
            }
        }
        // Outer stars
        img.setPixel(3, 4, star1);
        img.setPixel(7, 6, star2);
        img.setPixel(4, 10, star1);
        img.setPixel(8, 12, star2);
        img.setPixel(5, 14, star1);

        // Inner stars
        img.setPixel(14, 4, star1);
        img.setPixel(18, 6, star2);
        img.setPixel(15, 10, star1);
        img.setPixel(19, 12, star2);
        img.setPixel(16, 14, star1);
    }

    private static void generateCyberNeonCape(NativeImage img) {
        fillBaseCape(img, 0xFF090D16, 0xFF06B6D4);
        int magenta = toAbgr(0xFFEC4899);
        int cyan = toAbgr(0xFF06B6D4);

        for (int y = 3; y <= 15; y += 3) {
            for (int x = 2; x <= 9; x++) img.setPixel(x, y, magenta);
            for (int x = 13; x <= 20; x++) img.setPixel(x, y, magenta);
        }
        for (int y = 2; y <= 15; y++) {
            img.setPixel(3, y, cyan);
            img.setPixel(6, y, cyan);
            img.setPixel(9, y, cyan);
            img.setPixel(14, y, cyan);
            img.setPixel(17, y, cyan);
            img.setPixel(20, y, cyan);
        }
    }

    private static void generateFlameCape(NativeImage img) {
        fillBaseCape(img, 0xFF1C0A0A, 0xFFEA580C);
        int gold = toAbgr(0xFFFACC15);
        int orange = toAbgr(0xFFF97316);
        int red = toAbgr(0xFFDC2626);

        // Outer surface (x: 2..9)
        for (int x = 2; x <= 9; x++) {
            img.setPixel(x, 15, gold);
            img.setPixel(x, 14, gold);
            img.setPixel(x, 13, orange);
            img.setPixel(x, 12, orange);
            img.setPixel(x, 11, red);
            if (x % 2 == 1) {
                img.setPixel(x, 10, orange);
                img.setPixel(x, 9, red);
                img.setPixel(x, 7, gold);
            }
        }
        // Inner surface (x: 13..20)
        for (int x = 13; x <= 20; x++) {
            img.setPixel(x, 15, gold);
            img.setPixel(x, 14, gold);
            img.setPixel(x, 13, orange);
            img.setPixel(x, 12, orange);
            img.setPixel(x, 11, red);
            if (x % 2 == 0) {
                img.setPixel(x, 10, orange);
                img.setPixel(x, 9, red);
                img.setPixel(x, 7, gold);
            }
        }
    }

    private static void generateSakuraCape(NativeImage img) {
        fillBaseCape(img, 0xFF2A1520, 0xFFF472B6);
        int petal1 = toAbgr(0xFFFBCFE8);
        int petal2 = toAbgr(0xFFF472B6);
        int white = toAbgr(0xFFFFFFFF);

        // Outer blossoms
        img.setPixel(4, 4, petal1);
        img.setPixel(5, 4, white);
        img.setPixel(4, 5, petal2);
        img.setPixel(7, 7, petal1);
        img.setPixel(8, 8, petal2);
        img.setPixel(3, 10, petal2);
        img.setPixel(3, 11, petal1);
        img.setPixel(6, 13, petal1);
        img.setPixel(7, 13, white);
        img.setPixel(6, 14, petal2);

        // Inner blossoms
        img.setPixel(15, 4, petal1);
        img.setPixel(16, 4, white);
        img.setPixel(15, 5, petal2);
        img.setPixel(18, 7, petal1);
        img.setPixel(19, 8, petal2);
        img.setPixel(14, 10, petal2);
        img.setPixel(14, 11, petal1);
        img.setPixel(17, 13, petal1);
        img.setPixel(18, 13, white);
        img.setPixel(17, 14, petal2);
    }

    private static void drawVoidEye(NativeImage img, int cx, int tealGlow, int tealCore, int purple) {
        img.setPixel(cx, 8, purple);
        img.setPixel(cx + 1, 8, purple);
        img.setPixel(cx, 8 + 1, purple);
        img.setPixel(cx + 1, 8 + 1, purple);

        img.setPixel(cx - 1, 8, tealGlow);
        img.setPixel(cx + 2, 8, tealGlow);
        img.setPixel(cx - 1, 8 + 1, tealGlow);
        img.setPixel(cx + 2, 8 + 1, tealGlow);

        img.setPixel(cx, 8 - 1, tealCore);
        img.setPixel(cx + 1, 8 - 1, tealCore);
        img.setPixel(cx, 8 + 2, tealCore);
        img.setPixel(cx + 1, 8 + 2, tealCore);
    }

    private static void generateVoidCape(NativeImage img) {
        fillBaseCape(img, 0xFF050508, 0xFF14B8A6);
        int tealGlow = toAbgr(0xFF2DD4BF);
        int tealCore = toAbgr(0xFF5EEAD4);
        int purple = toAbgr(0xFF6B21A8);

        drawVoidEye(img, 5, tealGlow, tealCore, purple);
        drawVoidEye(img, 16, tealGlow, tealCore, purple);
    }

    private static void generateMatrixCape(NativeImage img) {
        fillBaseCape(img, 0xFF030704, 0xFF22C55E);
        int neonGreen = toAbgr(0xFF4ADE80);
        int darkGreen = toAbgr(0xFF15803D);

        // Outer (x: 2..9)
        for (int x = 2; x <= 9; x += 2) {
            for (int y = 2; y <= 15; y++) {
                img.setPixel(x, y, ((x + y) % 3 == 0) ? neonGreen : darkGreen);
            }
        }
        // Inner (x: 13..20)
        for (int x = 13; x <= 20; x += 2) {
            for (int y = 2; y <= 15; y++) {
                img.setPixel(x, y, ((x + y) % 3 == 0) ? neonGreen : darkGreen);
            }
        }
    }

    private static void generateChromaCape(NativeImage img, float phase) {
        fillBaseCape(img, 0xFF0F172A, 0xFFFFFFFF);
        for (int y = 2; y <= 15; y++) {
            float hue = (phase + (y / 14.0f)) % 1.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f);
            int abgr = toAbgr(0xFF000000 | (rgb & 0x00FFFFFF));
            for (int x = 1; x <= 10; x++) {
                img.setPixel(x, y, abgr);
            }
            for (int x = 12; x <= 21; x++) {
                img.setPixel(x, y, abgr);
            }
        }
    }

    private static void drawCosmicStar(NativeImage img, int cx, int starCol, int cyan) {
        img.setPixel(cx, 8, starCol);
        img.setPixel(cx + 1, 8, starCol);
        img.setPixel(cx, 8 + 1, starCol);
        img.setPixel(cx + 1, 8 + 1, starCol);

        img.setPixel(cx, 8 - 2, cyan);
        img.setPixel(cx + 1, 8 - 2, cyan);
        img.setPixel(cx, 8 + 3, cyan);
        img.setPixel(cx + 1, 8 + 3, cyan);
        img.setPixel(cx - 2, 8, cyan);
        img.setPixel(cx + 3, 8, cyan);
    }

    private static void generateCosmicCape(NativeImage img, float pulse) {
        fillBaseCape(img, 0xFF0B0F19, 0xFF38BDF8);
        int starCol = toAbgr(0xFF000000 | (Math.round(255 * (0.4f + pulse * 0.6f)) << 16) | (Math.round(255 * (0.7f + pulse * 0.3f)) << 8) | 255);
        int cyan = toAbgr(0xFF38BDF8);

        drawCosmicStar(img, 5, starCol, cyan);
        drawCosmicStar(img, 16, starCol, cyan);
    }

    // --- WINGS TEXTURE GENERATORS ---

    private static void generateDragonWings(NativeImage img) {
        int darkPurple = toAbgr(0xFF1E0A3C);
        int deepViolet = toAbgr(0xFF3B0764);
        int neonPurple = toAbgr(0xFFA855F7);
        int boneCol = toAbgr(0xFF11071F);

        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                img.setPixel(x, y, 0); // Transparent base
            }
        }

        for (int y = 4; y < 60; y++) {
            for (int x = 4; x < 60; x++) {
                if (x + y > 30 && x - y < 40) {
                    int col = ((x * y) % 7 == 0) ? neonPurple : (x % 4 == 0 ? deepViolet : darkPurple);
                    img.setPixel(x, y, col);
                }
            }
        }
        for (int i = 4; i < 56; i++) {
            img.setPixel(i, 4 + (i / 4), boneCol);
            img.setPixel(i, 5 + (i / 4), boneCol);
            img.setPixel(20 + (i / 2), i, boneCol);
        }
    }

    private static void generateAngelWings(NativeImage img) {
        int pureWhite = toAbgr(0xFFFFFFFF);
        int softBlue = toAbgr(0xFFBAE6FD);
        int skyCyan = toAbgr(0xFF38BDF8);

        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                if (x + y > 24 && x - y < 45) {
                    int col = (y % 6 < 3) ? pureWhite : ((x + y) % 4 == 0 ? skyCyan : softBlue);
                    img.setPixel(x, y, col);
                } else {
                    img.setPixel(x, y, 0);
                }
            }
        }
    }

    private static void generateDemonWings(NativeImage img) {
        int deepBlack = toAbgr(0xFF18181B);
        int darkRed = toAbgr(0xFF7F1D1D);
        int fieryOrange = toAbgr(0xFFEA580C);
        int moltenYellow = toAbgr(0xFFFACC15);

        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                if (x + y > 28 && x - y < 42) {
                    int col = ((x + y) % 9 == 0) ? moltenYellow : ((x * y) % 5 == 0 ? fieryOrange : (y % 3 == 0 ? darkRed : deepBlack));
                    img.setPixel(x, y, col);
                } else {
                    img.setPixel(x, y, 0);
                }
            }
        }
    }

    private static void generateFairyWings(NativeImage img) {
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                if (x + y > 25 && x - y < 45) {
                    float hue = ((x * 0.03f) + (y * 0.02f)) % 1.0f;
                    int rgb = java.awt.Color.HSBtoRGB(hue, 0.6f, 1.0f);
                    int abgr = (180 << 24) | toAbgr(0xFF000000 | (rgb & 0x00FFFFFF));
                    img.setPixel(x, y, abgr);
                } else {
                    img.setPixel(x, y, 0);
                }
            }
        }
    }

    // --- HEADWEAR TEXTURE GENERATORS ---

    private static void generateHaloTexture(NativeImage img) {
        int gold = toAbgr(0xFFFACC15);
        int lightGold = toAbgr(0xFFFEF08A);
        int amber = toAbgr(0xFFD97706);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int col = (y < 4 || y > 28 || x < 4 || x > 28) ? amber : ((x + y) % 2 == 0 ? gold : lightGold);
                img.setPixel(x, y, col);
            }
        }
    }

    private static void generateHornsTexture(NativeImage img) {
        int darkObsidian = toAbgr(0xFF09090B);
        int crimson = toAbgr(0xFFDC2626);
        int red = toAbgr(0xFF991B1B);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int col = (y < 12) ? crimson : (y < 22 ? red : darkObsidian);
                img.setPixel(x, y, col);
            }
        }
    }

    private static void generateBandanaTexture(NativeImage img) {
        int darkCloth = toAbgr(0xFF0F172A);
        int cyan = toAbgr(0xFF38BDF8);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int col = (y >= 12 && y <= 20) ? ((x >= 12 && x <= 20) ? cyan : darkCloth) : darkCloth;
                img.setPixel(x, y, col);
            }
        }
    }

    private static void generateCatEarsTexture(NativeImage img) {
        int outerFur = toAbgr(0xFF1E293B);
        int innerPink = toAbgr(0xFFF472B6);
        int softWhite = toAbgr(0xFFFFFFFF);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                if (x >= 6 && x <= 26 && y >= 8 && y <= 28) {
                    img.setPixel(x, y, (x >= 10 && x <= 22 && y >= 12 && y <= 24) ? innerPink : softWhite);
                } else {
                    img.setPixel(x, y, outerFur);
                }
            }
        }
    }

    public static void exportDefaultTexturesIfMissing() {
        Path capesDir = Path.of("src/main/resources/assets/starclient/textures/capes");
        Path wingsDir = Path.of("src/main/resources/assets/starclient/textures/wings");
        Path headwearDir = Path.of("src/main/resources/assets/starclient/textures/headwear");
        try {
            if (!Files.exists(Path.of("src/main/resources"))) return;
            Files.createDirectories(capesDir);
            Files.createDirectories(wingsDir);
            Files.createDirectories(headwearDir);

            // Capes (64x32)
            exportCapeIfMissing(capesDir, "starclient_official", PresetCosmetics::generateStarClientCape);
            exportCapeIfMissing(capesDir, "galaxy_nebula", PresetCosmetics::generateGalaxyCape);
            exportCapeIfMissing(capesDir, "cyber_neon", PresetCosmetics::generateCyberNeonCape);
            exportCapeIfMissing(capesDir, "crimson_flame", PresetCosmetics::generateFlameCape);
            exportCapeIfMissing(capesDir, "sakura_blossom", PresetCosmetics::generateSakuraCape);
            exportCapeIfMissing(capesDir, "void_abyssal", PresetCosmetics::generateVoidCape);
            exportCapeIfMissing(capesDir, "emerald_matrix", PresetCosmetics::generateMatrixCape);
            exportCapeIfMissing(capesDir, "animated_chroma", img -> generateChromaCape(img, 0.0f));
            exportCapeIfMissing(capesDir, "animated_cosmic", img -> generateCosmicCape(img, 0.0f));

            // Wings (64x64)
            exportWingIfMissing(wingsDir, "dragon_void", PresetCosmetics::generateDragonWings);
            exportWingIfMissing(wingsDir, "angel_light", PresetCosmetics::generateAngelWings);
            exportWingIfMissing(wingsDir, "demon_flame", PresetCosmetics::generateDemonWings);
            exportWingIfMissing(wingsDir, "fairy_prismatic", PresetCosmetics::generateFairyWings);

            // Headwear (32x32)
            exportHeadwearIfMissing(headwearDir, "angel_halo", PresetCosmetics::generateHaloTexture);
            exportHeadwearIfMissing(headwearDir, "demon_horns", PresetCosmetics::generateHornsTexture);
            exportHeadwearIfMissing(headwearDir, "ninja_bandana", PresetCosmetics::generateBandanaTexture);
            exportHeadwearIfMissing(headwearDir, "cat_ears", PresetCosmetics::generateCatEarsTexture);
        } catch (Exception ignored) {}
    }

    private static void exportCapeIfMissing(Path dir, String id, java.util.function.Consumer<NativeImage> generator) {
        Path target = dir.resolve(id + ".png");
        if (Files.exists(target)) return;
        try (NativeImage img = new NativeImage(64, 32, true)) {
            generator.accept(img);
            img.writeToFile(target);
        } catch (Exception ignored) {}
    }

    private static void exportWingIfMissing(Path dir, String id, java.util.function.Consumer<NativeImage> generator) {
        Path target = dir.resolve(id + ".png");
        if (Files.exists(target)) return;
        try (NativeImage img = new NativeImage(64, 64, true)) {
            generator.accept(img);
            img.writeToFile(target);
        } catch (Exception ignored) {}
    }

    private static void exportHeadwearIfMissing(Path dir, String id, java.util.function.Consumer<NativeImage> generator) {
        Path target = dir.resolve(id + ".png");
        if (Files.exists(target)) return;
        try (NativeImage img = new NativeImage(32, 32, true)) {
            generator.accept(img);
            img.writeToFile(target);
        } catch (Exception ignored) {}
    }
}

# Star Client Cosmetics Guide

This directory contains the custom textures for Star Client cosmetics.

## Texture Directories & Image Specifications

### 1. Capes
- **Directory**: `src/main/resources/assets/starclient/textures/capes/`
- **File Name**: `<cosmetic_id>.png` (e.g., `starclient_official.png`, `fire_dragon.png`)
- **Dimensions**: `64 x 32` pixels (standard Minecraft cape UV mapping)
- **Format**: 32-bit PNG with transparency

### 2. Wings
- **Directory**: `src/main/resources/assets/starclient/textures/wings/`
- **File Name**: `<cosmetic_id>.png` (e.g., `dragon_void.png`, `angel_light.png`)
- **Dimensions**: `64 x 64` pixels
- **Format**: 32-bit PNG with transparency

### 3. Headwear
- **Directory**: `src/main/resources/assets/starclient/textures/headwear/`
- **File Name**: `<cosmetic_id>.png` (e.g., `angel_halo.png`, `demon_horns.png`)
- **Dimensions**: `32 x 32` pixels
- **Format**: 32-bit PNG with transparency

### 4. Trails
- Trail particle previews directly utilize Minecraft's built-in particle textures from `minecraft:textures/particle/<particle>.png` (e.g., `glitter_0`, `flame`, `heart`, `spark_0`, `enchanted_hit`, `generic_0`).

---

## How to Add a New Cosmetic

1. **Place your PNG file** into the appropriate folder above (e.g., `capes/void_walker.png`).
2. **Register the cosmetic** in `src/main/java/net/starclient/cosmetics/CosmeticManager.java`:
   ```java
   registerPreset(new CosmeticItem(
       "void_walker",           // ID (must match filename void_walker.png)
       "Void Walker",           // Display Name in GUI
       CosmeticType.CAPE,       // CAPE, WINGS, HEADWEAR, or TRAIL
       Rank.USER,               // Rank required: Rank.USER, Rank.VIP, Rank.PREMIUM, etc.
       false,                   // Is animated (e.g., true for Rank.PREMIUM exclusive animated capes)
       "Mystic dark rift cape", // Description shown on card
       "👁",                     // Icon
       null, null, true
   ));
   ```
3. **Rebuild the client**:
   Run `.\build-client.ps1` in PowerShell. The new cosmetic will immediately appear in the Cosmetics Studio with live 3D preview!

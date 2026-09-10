package net.modernclient.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.modernclient.ModernClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class CosmeticManager {
    public static final String CAPES_FOLDER = ModernClient.CONFIG_FOLDER + "/capes";
    public static final String SKINS_FOLDER = ModernClient.CONFIG_FOLDER + "/skins";

    private final Path capesFolder;
    private final Path skinsFolder;
    private final List<CapeCosmetic> availableCapes = new ArrayList<>();
    private final List<CapeCosmetic> availableSkins = new ArrayList<>();
    private final Map<Identifier, DynamicTexture> loadedTextures = new LinkedHashMap<>();
    private final Map<Identifier, int[]> previewData = new LinkedHashMap<>();
    private boolean texturesLoaded;
    private String equippedCape;
    private String equippedSkin;

    public CosmeticManager() {
        this.capesFolder = Minecraft.getInstance().gameDirectory.toPath().resolve(CAPES_FOLDER);
        this.skinsFolder = Minecraft.getInstance().gameDirectory.toPath().resolve(SKINS_FOLDER);
        createFolder(capesFolder);
        createFolder(skinsFolder);
    }

    private void createFolder(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                ModernClient.LOGGER.error("Failed to create cosmetics folder {}", dir, e);
            }
        }
    }

    public void scanFolder() {
        availableCapes.clear();
        availableSkins.clear();
        scanInto(availableCapes, capesFolder);
        scanInto(availableSkins, skinsFolder);
        ModernClient.LOGGER.info("Found {} cosmetic cape(s) and {} cosmetic skin(s)",
                availableCapes.size(), availableSkins.size());
    }

    private void scanInto(List<CapeCosmetic> target, Path folder) {
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(path -> registerFile(target, folder, path));
        } catch (IOException e) {
            ModernClient.LOGGER.error("Failed to scan cosmetics folder {}", folder, e);
        }
    }

    private void registerFile(List<CapeCosmetic> target, Path folder, Path file) {
        String fileName = file.getFileName().toString();
        String baseName = fileName.substring(0, fileName.length() - 4);
        String id = sanitize(baseName);
        if (id.isEmpty()) return;
        String kind = folder.equals(capesFolder) ? "capes" : "skins";
        Identifier assetId = Identifier.fromNamespaceAndPath(ModernClient.MOD_ID, kind + "/" + id);
        Identifier textureId = Identifier.fromNamespaceAndPath(ModernClient.MOD_ID, "textures/" + kind + "/" + id);
        target.add(new CapeCosmetic(baseName, id, assetId, textureId));
    }

    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return sb.toString();
    }

    public synchronized void reload() {
        closeTextures();
        scanFolder();
    }

    public synchronized boolean ensureLoaded() {
        if (texturesLoaded) return true;
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.isSameThread()) return false;
        scanFolder();
        loadGroup(client, availableCapes, capesFolder);
        loadGroup(client, availableSkins, skinsFolder);
        texturesLoaded = true;
        return true;
    }

    private void loadGroup(Minecraft client, List<CapeCosmetic> group, Path folder) {
        for (CapeCosmetic cosmetic : group) {
            if (loadedTextures.containsKey(cosmetic.textureId())) continue;
            try (InputStream in = Files.newInputStream(folder.resolve(cosmetic.displayName() + ".png"))) {
                NativeImage image = NativeImage.read(in.readAllBytes());
                int[] content = computeContentBounds(image);
                previewData.put(cosmetic.textureId(),
                        new int[]{image.getWidth(), image.getHeight(), content[0], content[1], content[2], content[3]});
                DynamicTexture texture = new DynamicTexture(
                        () -> ModernClient.MOD_ID + "_" + folder.getFileName() + "_" + cosmetic.id(), image);
                client.getTextureManager().register(cosmetic.textureId(), texture);
                loadedTextures.put(cosmetic.textureId(), texture);
            } catch (Exception e) {
                ModernClient.LOGGER.error("Failed to load cosmetic '{}'", cosmetic.displayName(), e);
            }
        }
    }

    private static int[] computeContentBounds(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getPixel(x, y) >>> 24) != 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0 || minY > maxY) {
            return new int[]{0, 0, width, height};
        }
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    public synchronized int[] getPreviewData(Identifier textureId) {
        return previewData.get(textureId);
    }

    public synchronized CapeCosmetic getEquippedCapeCosmetic() {
        if (equippedCape == null || equippedCape.isEmpty()) return null;
        ensureLoaded();
        for (CapeCosmetic cape : availableCapes) {
            if (cape.id().equals(equippedCape)) return cape;
        }
        return null;
    }

    public synchronized CapeCosmetic getEquippedSkinCosmetic() {
        if (equippedSkin == null || equippedSkin.isEmpty()) return null;
        ensureLoaded();
        for (CapeCosmetic skin : availableSkins) {
            if (skin.id().equals(equippedSkin)) return skin;
        }
        return null;
    }

    public String getEquippedCape() {
        return equippedCape == null || equippedCape.isEmpty() ? null : equippedCape;
    }

    public synchronized void setEquippedCape(String capeId) {
        this.equippedCape = capeId == null || capeId.isEmpty() ? null : capeId;
    }

    public String getEquippedSkin() {
        return equippedSkin == null || equippedSkin.isEmpty() ? null : equippedSkin;
    }

    public synchronized void setEquippedSkin(String skinId) {
        this.equippedSkin = skinId == null || skinId.isEmpty() ? null : skinId;
    }

    public List<CapeCosmetic> getAvailableCapes() {
        return availableCapes;
    }

    public List<CapeCosmetic> getAvailableSkins() {
        return availableSkins;
    }

    public Path getCapesFolder() {
        return capesFolder;
    }

    public Path getSkinsFolder() {
        return skinsFolder;
    }

    public synchronized void closeTextures() {
        for (DynamicTexture texture : loadedTextures.values()) {
            texture.close();
        }
        loadedTextures.clear();
        previewData.clear();
        texturesLoaded = false;
    }
}

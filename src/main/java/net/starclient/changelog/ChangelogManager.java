package net.starclient.changelog;

import net.minecraft.client.gui.Font;
import net.starclient.StarClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChangelogManager {
    private static ChangelogManager instance;

    private List<String> cachedLines = null;
    private List<ChangelogNode> cachedNodes = null;
    private int cachedWidth = -1;
    private int cachedAccent = -1;

    private String lastSeenVersion = "";
    private boolean toastDismissed = false;

    private ChangelogManager() {
    }

    public static synchronized ChangelogManager getInstance() {
        if (instance == null) {
            instance = new ChangelogManager();
        }
        return instance;
    }

    public synchronized boolean isCurrentVersionUnread() {
        return lastSeenVersion == null || !lastSeenVersion.trim().equals(StarClient.MOD_VERSION.trim());
    }

    public synchronized void markCurrentVersionAsRead() {
        this.lastSeenVersion = StarClient.MOD_VERSION;
        this.toastDismissed = true;
        if (StarClient.getInstance() != null && StarClient.getInstance().getConfigManager() != null) {
            StarClient.getInstance().getConfigManager().saveConfig();
        }
    }

    public synchronized String getLastSeenVersion() {
        return lastSeenVersion == null ? "" : lastSeenVersion;
    }

    public synchronized void setLastSeenVersion(String version) {
        this.lastSeenVersion = version == null ? "" : version;
    }

    public synchronized boolean isToastDismissed() {
        return toastDismissed;
    }

    public synchronized void dismissToast() {
        this.toastDismissed = true;
    }

    public synchronized void reload() {
        this.cachedLines = null;
        this.cachedNodes = null;
        this.cachedWidth = -1;
        this.cachedAccent = -1;
    }

    public synchronized List<String> getRawLines() {
        if (this.cachedLines != null) {
            return this.cachedLines;
        }

        List<String> lines = tryReadFromFile();
        if (lines == null || lines.isEmpty()) {
            lines = tryReadFromClasspath();
        }
        if (lines == null || lines.isEmpty()) {
            lines = getDefaultFallbackLines();
        }

        this.cachedLines = lines;
        return lines;
    }

    public synchronized List<ChangelogNode> getNodes(Font font, int contentWidth, int accentColor) {
        if (this.cachedNodes != null && this.cachedWidth == contentWidth && this.cachedAccent == accentColor) {
            return this.cachedNodes;
        }

        List<String> raw = getRawLines();
        this.cachedNodes = MarkdownParser.parse(raw, font, contentWidth, accentColor);
        this.cachedWidth = contentWidth;
        this.cachedAccent = accentColor;
        return this.cachedNodes;
    }

    private List<String> tryReadFromFile() {
        // Search in common local paths
        Path[] candidatePaths = new Path[]{
                Paths.get("CHANGELOG.md"),
                Paths.get("config/starclient/changelog.md"),
                Paths.get("src/main/resources/assets/starclient/changelog.md"),
                Paths.get("../CHANGELOG.md")
        };

        for (Path p : candidatePaths) {
            try {
                if (Files.isRegularFile(p) && Files.isReadable(p)) {
                    List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                    if (!lines.isEmpty()) {
                        return lines;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<String> tryReadFromClasspath() {
        String[] resources = new String[]{
                "/assets/starclient/changelog.md",
                "/changelog.md",
                "assets/starclient/changelog.md",
                "changelog.md"
        };

        for (String res : resources) {
            try (InputStream in = StarClient.class.getResourceAsStream(res)) {
                if (in != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        List<String> lines = new ArrayList<>();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            lines.add(line);
                        }
                        if (!lines.isEmpty()) {
                            return lines;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<String> getDefaultFallbackLines() {
        List<String> lines = new ArrayList<>();
        lines.add("# Star Client v" + StarClient.MOD_VERSION);
        lines.add("");
        lines.add("> Welcome to Star Client **v" + StarClient.MOD_VERSION + "**!");
        lines.add("");
        lines.add("---");
        lines.add("");
        lines.add("## What's New");
        lines.add("");
        lines.add("### Inventory HUD");
        lines.add("- [NEW] Compact HUD widget displaying your entire inventory, hotbar, and armor.");
        lines.add("- [NEW] Built-in Inventory Search bar below container screens.");
        lines.add("- [FIX] Boots visibility fix when Show Armor is enabled.");
        lines.add("");
        lines.add("### Combat & Shields");
        lines.add("- [NEW] Low Shield module to lower or reposition shields in first person.");
        lines.add("- [NEW] Damage Indicators with bouncing physics and crit highlights.");
        lines.add("");
        lines.add("### Visuals & HUD");
        lines.add("- [IMPROVED] Boss Bar dynamic sizing prevents text overlapping.");
        lines.add("- [NEW] Transparent Background toggle for Boss Bar, Scoreboard, and Tab.");
        lines.add("");
        lines.add("---");
        lines.add("");
        lines.add("## Controls");
        lines.add("- Press `Right Shift` to open the ClickGUI.");
        lines.add("- Press `ESC` to close this changelog.");
        return lines;
    }
}

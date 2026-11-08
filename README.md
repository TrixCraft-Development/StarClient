# ⭐ Star Client

![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-34D399?style=for-the-badge&logo=minecraft&logoColor=white)
![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric%200.19.5%2B-38BDF8?style=for-the-badge)
![Java 25](https://img.shields.io/badge/Java-25-F59E0B?style=for-the-badge&logo=openjdk&logoColor=white)
![Version](https://img.shields.io/badge/Version-v0.13-A78BFA?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-64748B?style=for-the-badge)

**Star Client** is a modern, high-performance modular Minecraft client built on **Fabric** for **Minecraft 26.2 (Java 25)** by **TrixCraft Development**. It combines a sleek glassmorphic user interface, **100 customizable modules**, **27 draggable & scalable HUD widgets**, a full **3D Cosmetics Studio**, **Saved Loadout Profiles** with automatic world/server switching, a **Client Friends System**, an **In-Game Account Manager**, and deep **FPS / Render Optimizations** — while remaining fully compatible with standard Fabric mods.

---

## 📑 Table of Contents

1. [Key Highlights](#-key-highlights)
2. [Core Client Systems](#-core-client-systems)
   - [Glassmorphic ClickGUI & HUD Editor](#1-glassmorphic-clickgui--hud-editor)
   - [Custom Saved Profiles & Auto-Linking](#2-custom-saved-profiles--auto-linking)
   - [Cosmetics Studio & 3D Live Avatar](#3-cosmetics-studio--3d-live-avatar)
   - [Client Statistics Dashboard](#4-client-statistics-dashboard)
   - [Account Manager, Custom Main Menu & Mod Browser](#5-account-manager-custom-main-menu--mod-browser)
3. [General Client Settings (`Settings` Tab)](#-general-client-settings-settings-tab)
4. [Complete Modules & Settings Reference (100 Modules)](#-complete-modules--settings-reference-100-modules)
   - [🗡 Combat (7 Modules)](#-combat-7-modules)
   - [🎨 UI & Visuals (30 Modules)](#-ui--visuals-30-modules)
   - [👁 Render (15 Modules)](#-render-15-modules)
   - [🛡 Player (17 Modules)](#-player-17-modules)
   - [🚀 Movement (3 Modules)](#-movement-3-modules)
   - [🌍 World (6 Modules)](#-world-6-modules)
   - [🖥 Server (7 Modules)](#-server-7-modules)
   - [⚔ Cheats (15 Modules)](#-cheats-15-modules)
5. [Default Keybinds](#-default-keybinds)
6. [Installation & Building from Source](#-installation--building-from-source)
7. [Configuration & Storage](#-configuration--storage)

---

## ✨ Key Highlights

- **100 Built-In Modules**: Covering Combat enhancements, HUD widgets, rendering upgrades, performance boosters, quality-of-life tools, server integrations, and world customization.
- **Modern Glassmorphic UI**: Custom title screen, rounded buttons, smooth container pop-in animations, background blur, unified slide-in corner toast notifications, and customizable accent (`10` colors) & surface (`10` dark palettes) themes.
- **27 Draggable & Scalable HUD Widgets**: Free-form HUD Editor with magnetic edge/center snap guides, scroll-wheel per-widget scaling (`0.50x` – `2.00x`), and per-widget background/text colors.
- **90+ Built-In Cosmetics**: Complete collection of **82+ official capes**, custom Star Client & animated capes, 3D wings, headwear, and particle trails with interactive 3D avatar preview and per-account loadouts.
- **Smart Profile System**: Create unlimited profiles, bind hotkeys to switch mid-game, share profiles via `STAR-PROFILE:...` clipboard codes, and auto-activate profiles per Singleplayer world or Multiplayer server IP.
- **FPS & Render Engine Optimizations**: Entity/block-entity distance culling, distant sign text skipping, hologram-safe armor stand culling, particle limiters, dynamic unfocused FPS throttling, and `O(1)` client lookups.

---

## 🧩 Core Client Systems

### 1. Glassmorphic ClickGUI & HUD Editor
- Open the **ClickGUI** anywhere (Main Menu or In-Game) by pressing **`Right Shift`** (configurable in Minecraft Controls).
- **Search & Favorites**: Instantly filter modules by name or pin favorite modules (`★`) to the top.
- **HUD Editor**:
  - Drag and drop any of the **27 HUD widgets** anywhere on screen.
  - **Scroll Wheel Scaling**: Hover any widget in the HUD Editor and scroll the mouse wheel to resize it individually (`0.50x` – `2.00x`).
  - **Magnetic Snap Guides**: Automatic horizontal, vertical, and screen-center alignment guides keep your HUD layout pixel-perfect.
  - **Shared HUD Appearance Settings**: Every HUD widget automatically includes:
    - `Transparent Background` (`Boolean`, default `false`) — Render text/icons cleanly without a card backdrop (also supported on `Tab`, `Scoreboard`, `Boss Bar`, `Chat`, and `Nametags`).
    - `Background Color` (`ColorMode`, default `Dark Blue`) — `Dark Blue`, `Black`, `Charcoal`, `Red`, `Orange`, `Amber`, `Green`, `Teal`, `Blue`, `Indigo`, `Purple`, `Pink`.
    - `Text Color` (`ColorMode`, default `White`) — `White`, `Sky Blue`, `Blue`, `Cyan`, `Green`, `Lime`, `Yellow`, `Orange`, `Red`, `Pink`, `Purple`.

### 2. Custom Saved Profiles & Auto-Linking
Accessible from the **`Profiles`** category or the quick-switcher in the ClickGUI header:
- **Full State Snapshots**: Each profile independently stores enabled modules, all module settings, keybinds, command macros, HUD widget positions & scales, client theme colors, and cosmetic selections.
- **Clipboard Share Codes**: Export any profile to your clipboard as a compressed `STAR-PROFILE:...` string (`📤`) or import a profile in one click (`📥`).
- **Per-Profile Hotkeys**: Bind a dedicated key (`⌨`) to any profile card to swap loadouts instantly in-game.
- **World & Server Auto-Selection**: Link specific Singleplayer worlds or Multiplayer server IPs to a profile so Star Client automatically switches to it when you join.

### 3. Cosmetics Studio & 3D Live Avatar
Accessible from the **`Cosmetics`** category:
- **Interactive 3D Avatar Preview**: Drag-to-rotate live player preview in both the Main Menu and In-Game.
- **Capes (90+ Total)**:
  - **Star Client Originals**: *Celestial Star*, *Galaxy Nebula*, *Cyber Neon*, *Crimson Flame*, *Sakura Blossom*, *Void Abyssal*, *Emerald Matrix*, plus animated *Chroma Spectrum* & *Cosmic Core*.
  - **82 Official Capes Collection**: Minecon (`2011`–`2016`), *Alpha*, *1 Million*, *Mojang Studios*, *OptiFine* variants, *Lunar* variants, *Developer* capes, *Bug Tracker*, *Realms*, *Cobalt*, *Scrolls*, *Prismarine*, *Turtle*, *Copper*, *Villager Rescue*, and community classics.
- **Cape Physics**: Authentic `1:1` rigid vanilla cape physics or **16-segment fluid Wavey Cape simulation** with adjustable **Wave Speed**, **Wave Amplitude**, and **Atmospheric Wind Sway** toggle.
- **3D Wings**: *Dragon Void*, *Archangel Light*, *Demonic Flame*, *Prismatic Fairy*.
- **3D Headwear**: *Golden Halo*, *Infernal Horns*, *Shinobi Bandana*, *Neko Ears*.
- **Particle Trails**: *Stardust Sparkles*, *Blazing Flame*, *Love Aura*, *Void Rift*, *Runic Chroma*, *Winter Snowflake* (with `Always` / movement trail modes).
- **Per-Account Loadouts**: Cosmetic selections and cape physics are saved per Minecraft account UUID.

### 4. Client Statistics Dashboard
Accessible from the **`Stats`** category:
- Tracks lifetime **Singleplayer**, **Multiplayer**, and **Combined** gameplay metrics:
  - Playtime & Menu Time, Client Launches, Unique Active Days, Current & Longest Daily Login Streaks.
  - Player Kills, Mob Kills, Deaths, K/D Ratio, Hearts Lost & Regenerated.
  - Blocks Placed, Blocks Mined, Blocks Traveled, Items Crafted, Items Broken, Food Eaten, Worlds Created/Joined, Servers Joined, and Chat Messages Sent.

### 5. Account Manager, Custom Main Menu & Mod Browser
- **In-Game Account Manager**: Switch seamlessly between **Microsoft** accounts and **Offline** accounts without restarting the game, with cached 3D skin avatars.
- **Custom Star Title Screen**: Modern glassmorphic main menu with quick access to Singleplayer, Multiplayer, Cosmetics, Account Switcher, Changelog viewer, and Settings.
- **Fabric Mod Manager**: Automatically detects and lists all loaded Fabric mods alongside Star Client.

---

## ⚙ General Client Settings (`Settings` Tab)

Global settings that control Star Client's theme, menus, and safeguards:

| Setting | Type | Options / Details | Default | Description |
| :--- | :--- | :--- | :---: | :--- |
| **Client Menu Color** | Color | `Sky Blue`, `Blue`, `Cyan`, `Green`, `Lime`, `Yellow`, `Orange`, `Red`, `Pink`, `Purple` | `Sky Blue` | Primary accent color across all Star Client menus, sliders, and themed HUD elements. |
| **Client Menu Background** | Color | `Slate`, `Midnight`, `Near Black`, `Charcoal`, `Deep Navy`, `Deep Purple`, `Dark Forest`, `Maroon`, `Lead`, `Slate Light` | `Slate` | Surface and panel background palette for client menus. |
| **Main Menu Background** | Mode | `Modern Dark`, `Custom Wallpaper`, `Vanilla Panorama` | `Modern Dark` | Background style rendered on the main title screen. |
| **Modern Buttons** | Boolean | `true` / `false` | `true` | Applies Star Client's rounded glassmorphic styling to standard Minecraft UI buttons. |
| **HUD Text Shadow** | Boolean | `true` / `false` | `true` | Draws a crisp drop shadow behind HUD widget text for readability. |
| **Show Name Icon** | Boolean | `true` / `false` | `true` | Displays the Star Client badge next to your name in the tab list and nametag. |
| **Keybind Notifications** | Boolean | `true` / `false` | `true` | Shows a slide-in corner toast notification when toggling modules via keybinds. |
| **Toast Notifications** | Boolean | `true` / `false` | `true` | Shows slide-in corner toast notifications for profile switches, screenshots, friends, waypoints, durability alerts, and timers. |
| **Remove Splash Text** | Boolean | `true` / `false` | `true` | Hides the yellow rotating splash text on the main menu. |
| **Container Animations** | Boolean | `true` / `false` | `false` | Smooth cubic ease-out pop-in and glide animation when opening inventories and chests. |
| **Container Background Blur** | Boolean | `true` / `false` | `true` | Applies Gaussian background blur behind open inventory and container screens. |
| **Friend List** | Modal List | Opens **Friends Manager** | `[]` | Manage global client friends with face avatars, custom note/role tags, and one-click online player suggestions. |
| **Show Friend Badge** | Boolean | `true` / `false` | `true` | Shows a friend badge next to friends' names in overhead nametags and the multiplayer Tab list. |
| **Friend Badge Style** | Mode | `♥ Heart`, `★ Star`, `🛡 Ally`, `✦ Sparkle`, `[FRIEND]` | `♥ Heart` | Customizable badge icon displayed next to friends' names. |
| **Friend Color** | Color | `Sky Blue`, `Blue`, `Cyan`, `Green`, `Lime`, `Yellow`, `Orange`, `Red`, `Pink`, `Purple` | `Green` | Highlight color used for friend badges, Tab rows, Entity ESP, Hitboxes, and Minimap blips. |
| **Friend Chat Badges** | Boolean | `true` / `false` | `true` | Displays friend badge in front of friends' names in chat messages. |
| **Friend Notifications** | Boolean | `true` / `false` | `true` | Pushes slide-in corner toast notifications whenever a friend joins or leaves your server. |
| **Friend Notification Sound** | Boolean | `true` / `false` | `true` | Plays a subtle chime sound on friend join or leave. |
| **Middle Click Friend** | Boolean | `true` / `false` | `true` | Middle-click any player in-game to quickly add or remove them as a friend. |
| **Protect Friends in Combat** | Boolean | `true` / `false` | `true` | Prevents `Aimbot`, `Kill Aura`, and `Auto Clicker` from targeting your friends. |
| **Highlight Friends in ESP** | Boolean | `true` / `false` | `true` | Highlights friends using your `Friend Color` in `Entity ESP`, `Hitboxes`, and `Minimap`. |
| **Hypixel API Key** | Secret Text | API key from `hypixel.dev` | `""` | API key used by Hypixel server integration modules. |
| **Experimental Features** | Boolean | `true` / `false` | `false` | Unlocks experimental modules currently in active development. |
| **Palette Crash Safeguard** | Boolean | `true` / `false` | `true` | Protects against client crashes caused by malformed server chunk palette IDs. |

---

## 📦 Complete Modules & Settings Reference (93 Modules)

> **Note:** Every HUD widget module (`[HUD]`) also includes **Transparent Background**, **Background Color**, and **Text Color** appearance settings in addition to the module-specific settings listed below.

---

### 🗡 Combat (7 Modules)

#### 1. **Reach Display** `[HUD]`
Displays the exact block distance of your most recent melee hit.
- `Reset Time` (`Number`: `1.0` – `15.0s`, step `1.0`, default `5.0`) — Seconds before resetting back to `0.00`.

#### 2. **Combo Counter** `[HUD]`
Tracks consecutive melee hits landed without taking damage.
- `Reset Time` (`Number`: `1.0` – `10.0s`, step `0.5`, default `4.0`) — Inactivity timeout before resetting the combo.

#### 3. **Hit Color**
Customizes the damage flash tint color and opacity rendered on hurt entities.
- `Use Client Color` (`Boolean`, default `false`)
- `Tint Color` (`ColorMode`, default `Crimson Red`)
- `Opacity` (`Number`: `0.10` – `1.00`, step `0.05`, default `0.60`)

#### 4. **Hit Particles**
Spawns extra customizable particle bursts when attacking entities.
- `Always Crit` (`Boolean`, default `true`)
- `Particle Type` (`Mode`: `Crit`, `Enchanted`, `Both`, `Heart`, `Flame`, default `Crit`)
- `Multiplier` (`Number`: `1` – `10x`, step `1`, default `2`)

#### 5. **Low Fire**
Lowers the first-person burning fire overlay so your screen stays clear in combat.
- `Height Offset` (`Number`: `-0.50` – `0.00`, step `0.05`, default `-0.25`)

#### 6. **Low Shield**
Repositions and scales the first-person shield model for unobstructed visibility.
- `Height Offset` (`Number`: `-0.60` – `0.00`, step `0.05`, default `-0.25`)
- `Side Offset` (`Number`: `-0.30` – `0.30`, step `0.05`, default `0.00`)
- `Scale` (`Number`: `0.50` – `1.20x`, step `0.05`, default `1.00`)
- `Lower When Blocking` (`Boolean`, default `true`) — Keep shield lowered while actively blocking.

#### 7. **1.8 Module**
Authentic Minecraft 1.8.9 mechanics, block-hitting animations, instant swap, red armor damage flash, and classic sounds.
- `Block-Hitting` (`Boolean`, default `true`) — Classic 45° sword blocking pose and authentic block-hit swing chops.
- `Old Swing` (`Boolean`, default `true`) — Snappy 1.8 arm swing trajectory without modern weapon tilt.
- `Old Re-equip` (`Boolean`, default `true`) — Instant item swap without 1.9+ item lowering dip delay.
- `Old Rod & Bow` (`Boolean`, default `true`) — Classic 1.8 upright fishing rod stance and centered bow draw angle.
- `Old Eating & Drinking` (`Boolean`, default `true`) — Bouncy mouth-bobbing eating animation without sideways hand tilt.
- `Red Armor Flash` (`Boolean`, default `true`) — Flashes entire equipped armor bright red when taking damage.
- `Hide Modern Cooldown` (`Boolean`, default `true`) — Hides modern crosshair attack indicator and hotbar weapon cooldown bar.
- `Disable Sweep Effects` (`Boolean`, default `true`) — Disables modern sword sweep attack particle waves and sweep sound.
- `Hide Offhand (1st Person)` (`Boolean`, default `true`) — Hides offhand item in first person for classic single-item view.
- `Instant 1.8 Sneaking` (`Boolean`, default `true`) — Snappy sneak transition without camera glide, snapping eye height instantly to `1.54`.
- `Old Hit Sounds` (`Boolean`, default `true`) — Classic punch impact hit sounds instead of modern swooshes.
- `Bow Ding` (`Boolean`, default `true`) — Plays classic high-pitched chime ding when hitting players with arrows.

---

### 🎨 UI & Visuals (30 Modules)

#### 1. **Keystrokes** `[HUD]` *(Enabled by default)*
Displays a modern real-time WASD, Jump, and Mouse button overlay.
- `Show Mouse` (`Boolean`, default `true`) — Display LMB/RMB buttons with live CPS counters.

#### 2. **FPS Display** `[HUD]` *(Enabled by default)*
Displays real-time frames per second on the HUD.

#### 3. **Ping Display** `[HUD]` *(Enabled by default)*
Displays your current server network latency (`ms`) on the HUD.

#### 4. **Coordinates** `[HUD]` *(Enabled by default)*
Displays your live `X`, `Y`, `Z` world position on the HUD.

#### 5. **Active Modules** `[HUD]` *(Enabled by default)*
Displays a sorted arraylist of currently enabled modules on screen.

#### 6. **Zoom** *(Hold Key: `C`)*
Hold to zoom in smoothly with scroll-wheel magnification and proportional sensitivity scaling.
- `FOV` (`Number`: `1.0` – `100.0`, step `0.5`, default `30.0`) — Target zoomed-in field of view.
- `Smooth Transition` (`Boolean`, default `true`) — Exponentially interpolate FOV when zooming or scrolling.
- `Transition Speed` (`Number`: `4.0` – `30.0`, step `1.0`, default `14.0`) — Glide animation speed.
- `Scroll Step` (`Number`: `1.0` – `15.0`, step `0.5`, default `4.0`) — FOV change per mouse-wheel notch.
- `Mouse Sensitivity` (`Number`: `10.0` – `250.0%`, step `5.0`, default `100.0`) — Camera turn speed multiplier while zoomed.
- `Cinematic Camera` (`Boolean`, default `false`) — Enable vanilla smooth cinematic camera damping while zoomed.
- `Reset on Release` (`Boolean`, default `false`) — Reset zoom back to `30.0` FOV after releasing the zoom key.

#### 7. **Target HUD** `[HUD]`
Displays a combat info card showing your target's 3D head/avatar, health bar, distance, and equipped gear (renders a `♥ FRIEND` pill when targeting friends).
- `Target Mode` (`Mode`: `Crosshair & Attack`, `Attack Only`, `Crosshair Only`, default `Crosshair & Attack`)
- `Players Only` (`Boolean`, default `false`) — Only show Target HUD for players.
- `Show Armor` (`Boolean`, default `true`) — Render the target's equipped armor and held items.
- `Hold Time` (`Number`: `0.5` – `10.0s`, step `0.5`, default `3.0`) — Seconds to keep showing the card after looking away.

#### 8. **Potion Effects** `[HUD]`
Displays active status effects with icons, amplifiers, and live countdown timers on the HUD.
- `Hide Vanilla Icons` (`Boolean`, default `true`) — Hide vanilla top-right potion icons while enabled.

#### 9. **CPS Display** `[HUD]`
Displays your real-time clicks per second.
- `Show Right Click` (`Boolean`, default `true`) — Show both LMB and RMB CPS (`LMB | RMB`).

#### 11. **Speedometer** `[HUD]`
Displays your live horizontal movement speed.
- `Unit` (`Mode`: `m/s`, `km/h`, default `m/s`)

#### 12. **Custom Crosshair**
Replaces the vanilla crosshair with a customizable geometric reticle.
- `Shape` (`Mode`: `Cross`, `Circle`, `Dot`, `Cross + Dot`, default `Cross`)
- `Use Client Color` (`Boolean`, default `true`) — Sync crosshair color with `Client Menu Color`.
- `Color` (`ColorMode`, default `Sky Blue`) — Custom color when `Use Client Color` is disabled.
- `Center Dot` (`Boolean`, default `true`) — Draw a 1px/custom center dot.
- `Outline` (`Boolean`, default `true`) — Draw a dark high-contrast outline around crosshair elements.
- `Dynamic` (`Boolean`, default `true`) — Expand crosshair gap dynamically when moving or attacking.
- `Cooldown Indicator` (`Boolean`, default `true`) — Render 1.9+ weapon attack cooldown bar below the crosshair.
- `Always Show Cooldown` (`Boolean`, default `false`) — Show cooldown bar regardless of vanilla attack indicator setting.
- `Friend Tint` (`Boolean`, default `true`) — Tint crosshair emerald green when aiming at a friend so you immediately know not to attack.
- `Size` (`Number`: `1` – `12px`, step `1`, default `4`) — Arm length or circle radius.
- `Gap` (`Number`: `0` – `10px`, step `1`, default `2`) — Inner gap from center.
- `Thickness` (`Number`: `1` – `4px`, step `1`, default `1`) — Line thickness.

#### 13. **Tab** *(Enabled by default)*
Upgrades the multiplayer player Tab list with glassmorphic styling, 3D head layers, and numeric ping.
- `Transparent Background` (`Boolean`, default `false`)
- `Use Client Theme` (`Boolean`, default `true`)
- `Show Ping Numbers` (`Boolean`, default `true`) — Color-coded numeric latency (`ms`) instead of bars.
- `Show Player Heads` (`Boolean`, default `true`) — Render player face avatars with 3D hat layer.
- `Show Header & Footer` (`Boolean`, default `true`) — Show server header and footer text.
- `Highlight Self` (`Boolean`, default `true`) — Accent-highlight your own row in the tab list.
- `Background Opacity` (`Number`: `10` – `100%`, step `5`, default `75`)
- `Rounded Corners` (`Boolean`, default `true`)
- `Text Shadow` (`Boolean`, default `true`)

#### 14. **Scoreboard** *(Enabled by default)*
Modern glassmorphic sidebar scoreboard with optional score-number hiding and vertical positioning.
- `Transparent Background` (`Boolean`, default `false`)
- `Hide Red Numbers` (`Boolean`, default `true`) — Hide red score index numbers on the right.
- `Use Client Theme` (`Boolean`, default `true`)
- `Show Border` (`Boolean`, default `true`)
- `Background Opacity` (`Number`: `10` – `100%`, step `5`, default `70`)
- `Rounded Corners` (`Boolean`, default `true`)
- `Text Shadow` (`Boolean`, default `true`)
- `Offset Y` (`Number`: `-150` – `150px`, step `5`, default `0`) — Vertical screen offset.

#### 15. **Boss Bar** *(Enabled by default)*
Sleek customizable boss health bar with dynamic width expansion and health percentage readout.
- `Transparent Background` (`Boolean`, default `false`)
- `Show Percentage` (`Boolean`, default `true`) — Display live HP `%` next to the boss name.
- `Show Boss Name` (`Boolean`, default `true`)
- `Use Client Theme` (`Boolean`, default `true`)
- `Color` (`ColorMode`, default `Neon Cyan`) — Custom bar color when `Use Client Theme` is off.
- `Rounded Corners` (`Boolean`, default `true`)
- `Show Border` (`Boolean`, default `true`)
- `Bar Width` (`Number`: `80` – `300px`, step `2`, default `150`)
- `Bar Height` (`Number`: `3` – `14px`, step `1`, default `5`)
- `Background Opacity` (`Number`: `10` – `100%`, step `5`, default `70`)
- `Text Shadow` (`Boolean`, default `true`)

#### 17. **Clock Display** `[HUD]`
Displays real-world local system time on the HUD.
- `Format` (`Mode`: `24-Hour`, `12-Hour`, default `24-Hour`)
- `Show Seconds` (`Boolean`, default `true`)
- `Only Time` (`Boolean`, default `false`) — Minimalist time-only display without icon or prefix.
- `Show Icon` (`Boolean`, default `true`)
- `Show Text` (`Boolean`, default `false`) — Show `"Time:"` label prefix.

#### 18. **Compass Display** `[HUD]`
Displays a scrolling horizontal directional compass ribbon or compact cardinal heading card.
- `Style` (`Mode`: `Ribbon`, `Compact`, default `Ribbon`)
- `Show Degrees` (`Boolean`, default `true`) — Display numeric yaw heading badge (`0°` – `360°`).
- `Show Intercardinals` (`Boolean`, default `true`) — Show `NE`, `SE`, `SW`, `NW` markers on the ribbon.
- `Ribbon Width` (`Number`: `140` – `320px`, step `10`, default `210`)

#### 19. **Server Info** `[HUD]`
Customizable server & session telemetry card.
- `Show IP / World` (`Boolean`, default `true`) — Current server address or Singleplayer world name.
- `Show Server Brand` (`Boolean`, default `true`) — Server software brand (e.g., *Paper*, *Velocity*, *Vanilla*).
- `Show Version` (`Boolean`, default `true`) — Minecraft version reported by the server.
- `Show TPS` (`Boolean`, default `true`) — Live color-coded estimated server ticks per second (`20.0 TPS`).
- `Show Session Time` (`Boolean`, default `true`) — Elapsed play time in the current session.
- `Compact Mode` (`Boolean`, default `false`) — Combine enabled items onto a single horizontal bar.

#### 20. **Memory Display** `[HUD]`
Displays JVM RAM allocation metrics and an optional FPS sparkline graph.
- `Show Percentage` (`Boolean`, default `true`)
- `Show Megabytes` (`Boolean`, default `false`) — Show `Used / Total MB`.
- `Show Usage Bar` (`Boolean`, default `true`) — Visual memory progress bar.
- `Show FPS Graph` (`Boolean`, default `false`) — Live rolling FPS mini sparkline graph.

#### 21. **Minimap** `[HUD]`
Top-down terrain minimap with height shading, entity radar blips, and Waypoint pin integration.
- `Shape` (`Mode`: `Square`, `Circle`, default `Square`)
- `Size` (`Number`: `64` – `160px`, step `4`, default `96`)
- `Zoom` (`Number`: `0.5x` – `4.0x`, step `0.25`, default `1.0`)
- `Rotate with Player` (`Boolean`, default `true`) — Rotate map with camera yaw or lock North-Up.
- `Show Waypoints` (`Boolean`, default `true`) — Render active `Waypoints` on the minimap.
- `Show Players` (`Boolean`, default `true`)
- `Show Hostile Mobs` (`Boolean`, default `true`)
- `Show Passive Mobs` (`Boolean`, default `false`)
- `Show Items` (`Boolean`, default `false`) — Show dropped item stacks on radar.
- `Show Cardinals` (`Boolean`, default `true`) — Show `N`, `E`, `S`, `W` border markers.
- `Show Coordinates` (`Boolean`, default `true`) — Show compact `X, Y, Z` label below the map.

#### 22. **World Info** `[HUD]`
Displays live environment and world metadata.
- `Show Biome` (`Boolean`, default `true`)
- `Show Day` (`Boolean`, default `true`) — In-game world day counter.
- `Show Light Level` (`Boolean`, default `true`) — Block/sky light readout with mob-spawn warning color.
- `Show Chunk` (`Boolean`, default `false`) — Chunk coordinates and intra-chunk position.
- `Show Seed` (`Boolean`, default `false`) — World seed (automatic in Singleplayer; captures `/seed` on Multiplayer).
- `Compact Mode` (`Boolean`, default `false`)

#### 23. **Item Counter** `[HUD]`
Displays live total counts of key PvP & survival items across your entire inventory and offhand.
- `Orientation` (`Mode`: `Horizontal`, `Vertical`, default `Horizontal`)
- `Hide Zero` (`Boolean`, default `true`) — Hide tracked item icons when count is `0`.
- `Totems` (`Boolean`, default `true`)
- `Golden Apples` (`Boolean`, default `true`)
- `Enchanted Gapples` (`Boolean`, default `false`)
- `Ender Pearls` (`Boolean`, default `true`)
- `Arrows` (`Boolean`, default `true`)
- `Healing Potions` (`Boolean`, default `false`)
- `XP Bottles` (`Boolean`, default `false`)
- `End Crystals` (`Boolean`, default `false`)
- `Building Blocks` (`Boolean`, default `false`)

#### 24. **Chat**
Comprehensive chat overhaul with timestamps, anti-spam stacking, click-to-copy, cross-server history, banned-word censoring, and mention highlights.
- `Transparent Background` (`Boolean`, default `false`) — Hide the dark chat backdrop.
- `Timestamps` (`Boolean`, default `true`) — Prepend local time to incoming chat messages.
- `Time Format` (`Mode`: `24h (HH:mm)`, `24h (HH:mm:ss)`, `12h (hh:mm a)`, default `24h (HH:mm)`)
- `Compact Chat` (`Boolean`, default `true`) — Collapse duplicate messages with `[x2]`, `[x3]` counters.
- `Click to Copy` (`Boolean`, default `true`) — Click chat lines to copy their text to the clipboard.
- `Copy Server & Mod Info` (`Boolean`, default `true`) — Also allow click-to-copy on server announcements, system messages, and mod info lines.
- `Save Chat History` (`Boolean`, default `true`) — Preserve chat history when switching servers or worlds, inserting a styled `⭐ Joined <Server / World>` divider between sessions.
- `Extended History` (`Number`: `100` – `5000`, step `100`, default `1000`) — Maximum scrollback message limit.
- `Word Filter` (`Boolean`, default `true`) — Censor configured banned words in chat by replacing each letter with `*`.
- `Banned Words` (`BannedWordListSetting`) — Opens the **Banned Words Manager** modal to add, preview (`word → ****`), or remove censored words/phrases.
- `Mention Highlight` (`Boolean`, default `true`) — Highlight your username inside chat message bodies when mentioned.
- `Mention Color` (`ColorMode`, default `Yellow`) — `Yellow`, `Gold`, `Sky Blue`, `Cyan`, `Green`, `Lime`, `Orange`, `Red`, `Pink`, `Purple`, `White`.
- `Mention Sound` (`Boolean`, default `true`) — Play a subtle ping sound when mentioned.

#### 25. **Scrollable Tooltips**
Scroll tall or wide item tooltips vertically and horizontally so overflowing lore never clips off-screen.
- `Vertical Scroll` (`Boolean`, default `true`) — Mouse Wheel or `Up` / `Down` Arrow keys.
- `Horizontal Scroll` (`Boolean`, default `true`) — `Shift` + Mouse Wheel or `Left` / `Right` Arrow keys.
- `Scroll Speed` (`Number`: `4` – `40px`, step `2`, default `14`)
- `Only When Overflowing` (`Boolean`, default `false`) — Only scroll tooltips that exceed screen height/width.
- `Reset on New Item` (`Boolean`, default `true`) — Reset scroll offset when hovering a different slot.

#### 26. **Block Info** `[HUD]`
WAILA (*"What Am I Looking At"*) HUD widget showing the targeted block or entity's 3D icon, harvestability check, optimal breaking tool & required tier, live mining duration & progress bar, contextual block state details (crop growth `%`, redstone power, honey level, repeater delay, cauldron/composter fill), and **Entity Info** (live health/armor bars, Horse/Camel speed & jump height, Villager profession & tier, pet tame state, and player ping/friend tags).
- `Show Icon` (`Boolean`, default `true`)
- `Show Entities` (`Boolean`, default `true`) — Inspect targeted players, mobs, mounts, and villagers when looking at an entity.
- `Show Entity Health` (`Boolean`, default `true`) — Display live health hearts, armor points, and a bottom health progress bar on living entities.
- `Show Entity Stats` (`Boolean`, default `true`) — Display mount speed (`m/s`) & jump height (`blocks`), Villager profession/tier, pet tame/sitting status, and player ping/friend tags.
- `Show Harvestability` (`Boolean`, default `true`) — `✔ Harvestable` / `✕ Wrong Tool` / `∞ Unbreakable`.
- `Show Optimal Tool` (`Boolean`, default `true`) — Displays optimal tool type & required tier (e.g., `Pickaxe (Iron+)`, `Pickaxe (Diamond+)`, `Shovel`, `Axe`, `Hoe`, `Shears`).
- `Show Break Time` (`Boolean`, default `true`) — Estimated break time (`s` / `Instant`) + live breaking `%` progress bar.
- `Show Block Details` (`Boolean`, default `true`)
- `Show Coordinates` (`Boolean`, default `false`)
- `Include Fluids` (`Boolean`, default `false`)
- `Max Distance` (`Number`: `3.0` – `20.0` blocks, step `0.5`, default `5.5`)

#### 27. **Hunger & Saturation**
AppleSkin-style golden saturation outlines on the hunger bar, pulsing held-food restoration preview, exhaustion progress bar, and food tooltip nutrition/saturation stats.
- `Show Saturation` (`Boolean`, default `true`)
- `Show Food Preview` (`Boolean`, default `true`) — Pulsing preview of restored hunger & saturation when holding edible food.
- `Show Exhaustion Bar` (`Boolean`, default `true`) — Subtle exhaustion progress bar behind the hunger bar.
- `Food Tooltip Info` (`Boolean`, default `true`) — Appends `🍖 +X Hunger • ✦ +Y.Y Saturation` to food tooltips.

#### 28. **Chat Calculator**
Live floating calculator and Nether/Overworld coordinate converter above the chat input bar when typing expressions starting or ending with `=`.
- `Math Expressions` (`Boolean`, default `true`) — Supports `+`, `-`, `*`, `/`, `%`, `^`, parentheses, `st`/`sb`/`k`/`m` suffixes, `pi`, `e`, and math functions (`= 27*64`, `I need 27*64=`).
- `Show Item Stacks` (`Boolean`, default `true`) — Displays `64`-stack and Shulker Box breakdown next to whole results.
- `Coord Converter` (`Boolean`, default `true`) — `=nether [x z]`, `=ow [x z]`, or `=coords` (`÷8` / `×8` conversion).
- `Tab to Insert` (`Boolean`, default `true`) — Press `Tab` to replace the expression in chat with the clean result.

#### 29. **Pinned Notes** `[HUD]`
Draggable HUD checklist and sticky notes card for coordinates, material goals, and reminders.
- `Title` (`Text`, default `"Pinned Notes"`)
- `Notes List` (`NoteListSetting`) — Opens the **Pinned Notes & Checklist** modal to add, check off (`[✓]`), or delete notes.
- `Show Checkboxes` (`Boolean`, default `true`)
- `Show Completed` (`Boolean`, default `true`) — Keep completed notes visible with strikethrough or hide them automatically.
- `Hide When Empty` (`Boolean`, default `false`)

#### 30. **Stopwatch & Timer** `[HUD]`
Draggable HUD stopwatch and countdown timer with start/pause/reset hotkey controls and completion alerts.
- `Mode` (`Mode`: `Stopwatch`, `Countdown`, default `Stopwatch`)
- `Running` (`Boolean`, default `false`) — Start or pause the active timer.
- `Reset Timer` (`Boolean`, default `false`) — Immediately resets the timer back to `00:00.0`.
- `Keybind Action` (`Mode`: `Start / Pause`, `Toggle Module`, default `Start / Pause`)
- `Double-Tap Reset` (`Boolean`, default `true`) — Double-press the hotkey within `350ms` to reset.
- `Countdown Minutes` (`Number`: `0.5` – `120.0` min, step `0.5`, default `5.0`)
- `Show Milliseconds` (`Boolean`, default `true`) — Display tenths of a second (`.0`).
- `Alert Sound` (`Boolean`, default `true`) — Play a chime and corner toast notification when the countdown finishes.

#### 31. **Sound Radar** `[HUD]`
Directional audio compass and circular HUD radar card visualizing 3D in-game sounds (footsteps, explosions, projectiles, chests/blocks, mobs) with distance readouts and optional crosshair directional chevrons.
- `Display Mode` (`Mode`: `Radar Card`, `Crosshair Indicators`, `Both`, default `Radar Card`)
- `Range` (`Number`: `8.0` – `64.0` blocks, step `4.0`, default `32.0`)
- `Fade Time (s)` (`Number`: `1.0` – `5.0s`, step `0.5`, default `2.5`)
- `Track Players` (`Boolean`, default `true`) — Detect footsteps, swimming, pearls, and eating.
- `Track Explosions` (`Boolean`, default `true`) — Detect TNT, Creeper hisses, and explosions.
- `Track Projectiles` (`Boolean`, default `true`) — Detect bows, crossbows, and arrows.
- `Track Chests & Blocks` (`Boolean`, default `true`) — Detect chests, doors, and pistons.
- `Track Mobs` (`Boolean`, default `true`) — Detect hostile mob sounds.
- `Show Distance` (`Boolean`, default `true`) — Display distance in blocks next to sound pings.
- `Show Sound Name` (`Boolean`, default `true`) — Display sound type label.

#### 32. **Potion Alerts**
Audio note block pling and bass notify pings, actionbar alerts, and corner toasts when positive potion buffs are about to expire or when harmful debuffs strike.
- `Expiry Warning` (`Boolean`, default `true`) — Notify when beneficial potion buffs are about to expire.
- `Expiry Threshold (s)` (`Number`: `3.0` – `30.0s`, step `1.0`, default `10.0`)
- `Expiry Sound Ping` (`Boolean`, default `true`) — Play a crisp note block pling when a potion buff is expiring.
- `Debuff Alert` (`Boolean`, default `true`) — Alert when afflicted with harmful debuffs (Poison, Wither, Darkness, Bad Omen, Slowness, Mining Fatigue).
- `Debuff Sound Ping` (`Boolean`, default `true`) — Play a bass alert tone when afflicted with a debuff.
- `Show Cure Indicator` (`Boolean`, default `true`) — Display an on-screen cure reminder badge (`🥛 Drink Milk to Cure!`) while afflicted with a debuff.
- `Actionbar Warning` (`Boolean`, default `true`) — Display overlay warnings on the in-game actionbar.
- `Toast Notifications` (`Boolean`, default `true`) — Push slide-in corner toast notifications for potion alerts.

---

### 👁 Render (15 Modules)

#### 26. **Fullbright** *(Default Key: `B`)*
Grants maximum visibility in dark caves, dimensions, and underwater.
- `Mode` (`Mode`: `Gamma`, `Night Vision`, `Both`, default `Gamma`)

#### 27. **Damage Indicators** *(Enabled by default)*
Spawns RPG-style floating damage numbers and critical-hit callouts above hurt entities.
- `Format` (`Mode`: `Damage & Heart`, `Hearts Only`, `Number Only`, default `Damage & Heart`)
- `Show Crits` (`Boolean`, default `true`) — Highlight critical hits in gold (`✧`).
- `Show Self` (`Boolean`, default `true`) — Show indicators when you take damage.
- `Show Heart Icon` (`Boolean`, default `true`)
- `Scale` (`Number`: `0.5` – `2.0x`, step `0.1`, default `1.0`)
- `Duration` (`Number`: `0.5` – `3.0s`, step `0.1`, default `1.2`)
- `Movement` (`Mode`: `Bounce Up`, `Float Up`, `Static`, default `Bounce Up`)

#### 29. **Block Outline**
Customizes the targeted block wireframe outline color, thickness, and opacity.
- `Use Client Color` (`Boolean`, default `true`)
- `Color` (`ColorMode`, default `Sky Blue`)
- `Thickness` (`Number`: `1.0` – `5.0`, step `0.5`, default `2.0`)
- `Opacity` (`Number`: `0.10` – `1.00`, step `0.05`, default `0.85`)

#### 30. **No Hurt Cam**
Reduces or eliminates camera tilt shake when taking damage.
- `Hurt Shake %` (`Number`: `0` – `100%`, step `5`, default `0%`)

#### 33. **Item Physics**
Dropped items tumble realistically through the air and rest flat on the ground.
- `Rotation Speed` (`Number`: `0.5` – `5.0`, step `0.5`, default `2.0`)

#### 35. **Motion Blur**
Applies a smooth directional camera-rotation motion blur post-processing shader.
- `Strength` (`Number`: `1` – `5`, step `1`, default `2`)

#### 36. **Hand Customizer**
Full 3D viewmodel customization for first-person main-hand and off-hand items.
- `Main Hand X` / `Main Hand Y` / `Main Hand Z` (`Number`: `-1.00` – `1.00`, step `0.05`, default `0.00`)
- `Main Hand Scale` (`Number`: `0.30` – `2.00x`, step `0.05`, default `1.00`)
- `Main Hand Pitch` / `Main Hand Yaw` / `Main Hand Roll` (`Number`: `-180°` – `180°`, step `5°`, default `0°`)
- `Mirror Offhand` (`Boolean`, default `true`) — Mirror main-hand offsets and scale onto the off-hand.
- `Offhand X` / `Offhand Y` / `Offhand Z` (`Number`: `-1.00` – `1.00`, step `0.05`, default `0.00`)
- `Offhand Scale` (`Number`: `0.30` – `2.00x`, step `0.05`, default `1.00`)
- `Swing Speed` (`Number`: `0.2` – `3.0x`, step `0.1`, default `1.0`) — Attack swing animation speed multiplier.

#### 37. **Hitboxes**
Renders clean customizable 3D entity hitboxes without needing vanilla `F3 + B` debug mode.
- `Use Client Color` (`Boolean`, default `false`)
- `Color` (`ColorMode`, default `White`)
- `Show Eye Line` (`Boolean`, default `true`) — Red eye-height ring around living entities.
- `Show Look Vector` (`Boolean`, default `true`) — Blue view-direction ray from entity eyes.
- `Highlight Target` (`Boolean`, default `true`) — Highlight crosshair-targeted entity in gold.
- `Players Only` (`Boolean`, default `false`)
- `Include Projectiles & Items` (`Boolean`, default `true`)
- `Thickness` (`Number`: `1.0` – `4.0px`, step `0.5`, default `1.5`)
- `Opacity` (`Number`: `0.20` – `1.00`, step `0.05`, default `0.90`)
- `Range` (`Number`: `8` – `128` blocks, step `4`, default `48`)

#### 38. **Trajectories**
Predicts and renders real-time 3D flight paths and impact markers for bows, crossbows, tridents, ender pearls, snowballs, eggs, wind charges, potions, and XP bottles.
- `Use Client Color` (`Boolean`, default `true`)
- `Color` (`ColorMode`, default `Sky Blue`)
- `Preview Uncharged Bow` (`Boolean`, default `true`) — Preview full-charge arc while holding a bow/trident.
- `Landing Box` (`Boolean`, default `true`) — Render 3D landing marker at impact point.
- `Highlight Hit Entity` (`Boolean`, default `true`) — Highlight entities predicted to be hit in green.
- `Show Distance` (`Boolean`, default `true`) — Show impact distance readout.
- `Thickness` (`Number`: `1.0` – `4.0px`, step `0.5`, default `2.0`)

#### 39. **Fog Customizer**
Independently disable or tune underwater, lava, status-effect, and atmospheric terrain fog.
- `Disable Water Fog` (`Boolean`, default `true`)
- `Disable Lava Fog` (`Boolean`, default `true`)
- `Disable Blindness Fog` (`Boolean`, default `true`) — Removes Blindness, Darkness, and Powder Snow fog.
- `Disable Terrain Fog` (`Boolean`, default `false`)
- `Fog Distance` (`Number`: `0.2x` – `3.0x`, step `0.1`, default `1.5x`)

#### 40. **Glint Customizer**
Customize enchantment glint animation speed and intensity, force glint onto all items, or disable glint completely.
- `Glint Speed` (`Number`: `0.0` – `5.0x`, step `0.1`, default `1.0`)
- `Glint Strength` (`Number`: `0.00` – `2.00x`, step `0.05`, default `1.00`)
- `Force Glint on All Items` (`Boolean`, default `false`)
- `Disable All Glint` (`Boolean`, default `false`)

#### 41. **Performance**
All-in-one FPS booster with distance culling, hologram-safe lobby optimization, particle filtering, and background FPS throttling.
- **Entity Culling**:
  - `Cull Distant Entities` (`Boolean`, default `true`)
  - `Max Entity Distance` (`Number`: `16` – `128` blocks, step `4`, default `64`)
  - `Cull Distant Players` (`Boolean`, default `false`)
- **Armor Stand & Item Frame Culling**:
  - `Cull Armor Stands` (`Boolean`, default `true`)
  - `Armor Stand Distance` (`Number`: `8` – `64` blocks, step `4`, default `32`)
  - `Keep Named Holograms` (`Boolean`, default `true`) — Preserves custom-named server hologram stands.
  - `Cull Item Frames` (`Boolean`, default `true`)
  - `Item Frame Distance` (`Number`: `8` – `64` blocks, step `4`, default `24`)
- **Dropped Item & XP Orb Culling**:
  - `Cull Dropped Items` (`Boolean`, default `true`)
  - `Dropped Item Distance` (`Number`: `8` – `64` blocks, step `4`, default `32`)
- **Block Entity & Sign Text Culling**:
  - `Cull Block Entities` (`Boolean`, default `true`) — Distant chests, skulls, signs, banners, shulkers.
  - `Max Block Entity Distance` (`Number`: `16` – `96` blocks, step `4`, default `48`)
  - `Hide Distant Sign Text` (`Boolean`, default `true`) — Skips expensive font layout on distant signs.
  - `Sign Text Distance` (`Number`: `8` – `48` blocks, step `2`, default `16`)
- **Particle Limiter & Filters**:
  - `Particle Reduction` (`Number`: `0` – `100%`, step `5`, default `25%`)
  - `Disable Explosion Particles` (`Boolean`, default `false`)
  - `Disable Firework Particles` (`Boolean`, default `false`)
  - `Disable Block Break Particles` (`Boolean`, default `false`)
  - `Disable Potion Particles` (`Boolean`, default `false`)
  - `Disable Weather Particles` (`Boolean`, default `true`)
- **World Detail Toggles**:
  - `Disable Beacon Beams` (`Boolean`, default `false`)
  - `Disable Weather Rendering` (`Boolean`, default `false`)
  - `Disable Clouds` (`Boolean`, default `false`)
- **Dynamic Unfocused FPS**:
  - `Limit Unfocused FPS` (`Boolean`, default `true`) — Saves GPU/CPU power when alt-tabbed.
  - `Unfocused Max FPS` (`Number`: `5` – `60` FPS, step `5`, default `15`)

#### 42. **Nametags**
Upgrades overhead player nametags with health, ping, 3rd-person self-tag, scaling, and transparent backdrop support.
- `Transparent Background` (`Boolean`, default `false`)
- `Own Nametag (3rd Person)` (`Boolean`, default `true`) — Render your own nametag in `F5` third-person view.
- `Show Health` (`Boolean`, default `true`) — Append live heart HP readout (`❤ 20`).
- `Show Ping` (`Boolean`, default `true`) — Append network latency (`[24ms]`).
- `Scale` (`Number`: `0.5` – `2.5x`, step `0.1`, default `1.0`)
- `Distance Scale` (`Boolean`, default `false`) — Scale up distant nametags so they stay legible.

#### 43. **Item ESP**
Highlights dropped items on the ground with 2D/3D boxes, Chams fills, tracer lines, item icons, and stack count labels.
- `Style` (`Mode`: `Box 2D`, `Box 3D`, `Chams`, `Glowing`, default `Box 2D`)
- `Boxes` (`Boolean`, default `true`)
- `Tracers` (`Boolean`, default `true`)
- `Labels` (`Boolean`, default `true`) — Shows item name, stack count (`x64`), and distance (`m`).
- `Show Item Icon` (`Boolean`, default `true`)
- `Use Client Colors` (`Boolean`, default `true`) — Syncs box outlines & tracer lines to `Client Menu Color` and box/label fills to `Client Menu Background`.
- `Line Color` (`ColorMode`, default `Sky Blue`) — Custom box/tracer color when `Use Client Colors` is off.
- `Fill Color` (`ColorMode`, default `Slate`) — Custom box/label fill color when `Use Client Colors` is off.
- `Range` (`Number`: `4` – `100` blocks, step `1`, default `32`)

#### 44. **Low Health Vignette**
Renders a smooth heartbeat-pulsing vignette around the screen edges when your health drops at or below a configurable threshold.
- `Health Threshold` (`Number`: `2.0` – `16.0` HP, step `1.0`, default `8.0`)
- `Max Opacity %` (`Number`: `15` – `90%`, step `5`, default `55%`)
- `Pulse Speed` (`Number`: `0.5` – `5.0x`, step `0.5`, default `2.0`)
- `Use Client Color` (`Boolean`, default `false`)
- `Color` (`ColorMode`, default `Crimson`)

---

### 🛡 Player (17 Modules)

#### 43. **Armor HUD** `[HUD]`
Displays equipped armor pieces, optional held item, durability values, low-durability audio/visual alerts, and optional tool/Elytra break protection.
- `Orientation` (`Mode`: `Horizontal`, `Vertical`, default `Vertical`)
- `Mirror Items` (`Boolean`, default `false`) — Reverse armor display order (`Helmet ↔ Boots`).
- `Show Held Item` (`Boolean`, default `false`)
- `Low Durability Alert` (`Boolean`, default `true`) — Red warning flash when gear drops below `Warning Threshold %`.
- `Warning Threshold %` (`Number`: `5` – `30%`, step `1`, default `15%`)
- `Durability Sound Ping` (`Boolean`, default `true`) — Plays an alert chime and actionbar warning when armor, Elytra, or tools drop below threshold.
- `Prevent Tool Breaking` (`Boolean`, default `false`) — Stops mining, attacking, or right-clicking with valuable tools/weapons/Elytra at `≤ 2` durability so gear never shatters.
- `Durability` (`Mode`: `Show current & max Durability`, `Only current`, `Hidden`, default `Show current & max Durability`)

#### 44. **Inventory HUD** `[HUD]`
Floating real-time inventory preview widget plus an in-container **Inventory Search Bar** that highlights matching items and dims non-matches across item names, IDs, and lore lines.
- `Show HUD Widget` (`Boolean`, default `true`) — Can be disabled to use only the Inventory Search bar.
- `Include Hotbar` (`Boolean`, default `false`)
- `Show Offhand` (`Boolean`, default `true`)
- `Show Armor` (`Boolean`, default `false`)
- `Show Title` (`Boolean`, default `false`)
- `Highlight Selected` (`Boolean`, default `true`)
- `Show Slot Backgrounds` (`Boolean`, default `true`)
- `Hide in Inventory` (`Boolean`, default `true`)
- `Inventory Search` (`Boolean`, default `true`) — Places an instant search bar below container/creative screens.

#### 45. **Auto Tool**
Automatically switches to the fastest hotbar tool when mining a block.
- `Switch Back` (`Boolean`, default `true`) — Return to your previous hotbar slot after mining.

#### 46. **Free Look** *(Hold Key: `G`)*
Hold to orbit a `360°` third-person camera freely around your character without changing your movement heading.

#### 47. **Auto Armor**
Automatically equips higher-protection armor pieces from your inventory.
- `Delay` (`Number`: `5` – `40` ticks, step `5`, default `10`)

#### 48. **Auto Totem**
Automatically swaps a *Totem of Undying* from your inventory into your off-hand whenever your off-hand empties.

#### 49. **Auto Eat**
Automatically eats food when your hunger drops below a configurable threshold.
- `Hunger Threshold` (`Number`: `1` – `19`, step `1`, default `12`)
- `Select Food` (`Boolean`, default `true`) — Automatically select food from your hotbar if needed.

#### 50. **Auto Fish**
Automatically detects bobber bites, reels in catches, and recasts your fishing rod.
- `Auto Recast` (`Boolean`, default `true`)

#### 51. **Auto Chest Loot**
Quickly transfers all items from opened chests into your inventory.
- `Delay` (`Number`: `0` – `10` ticks, step `1`, default `1`)
- `Close After` (`Boolean`, default `true`) — Automatically close the chest once looted.

#### 52. **Nick-Tool**
Client-side name hider and streamer utility to replace your own name and other players' names across chat, nametags, and UI.
- `Your Nick` (`Text`, default `""`) — Custom display name shown for yourself.
- `Nick List` (`NickEntryListSetting`) — Manage real-username → custom-nickname mappings.

#### 53. **Quick Commands**
Bind custom hotkeys to instantly run server commands (`/hub`, `/l`, `/play ...`) or send chat messages.
- `Command Macros` (`MacroListSetting`) — Opens the **Command Macros Manager** modal.
- `Chat Feedback` (`Boolean`, default `false`) — Print a client confirmation in chat when a macro fires.

#### 54. **Auto Respawn**
Automatically respawns you upon dying with an optional delay timer and Death screen countdown banner.
- `Respawn Delay` (`Number`: `0.0` – `10.0s`, step `0.1`, default `0.0`)
- `Show Countdown` (`Boolean`, default `true`)

#### 55. **Auto Reconnect**
Automatically reconnects to your last multiplayer server when disconnected, displaying a live countdown button on the Disconnect screen.
- `Reconnect Delay` (`Number`: `1.0` – `60.0s`, step `0.5`, default `5.0`)

#### 56. **Better Screenshots**
Asynchronous lag-free `F2` screenshot saving with automatic Windows clipboard image copy, interactive chat action buttons, and custom output resolutions (`720p`, `1080p`, `1440p`, `4K`, `8K`, or `Custom`).
- `Resolution` (`Mode`: `Native`, `720p`, `1080p`, `1440p`, `4K`, `8K`, `Custom`, default `Native`) — Output resolution for screenshots (resamples seamlessly from any window or monitor size).
- `Keep Aspect Ratio` (`Boolean`, default `true`) — Preserves your display's perspective ratio (16:9, 16:10, 21:9 ultrawide) so blocks and geometry never stretch or squish.
- `Custom Width` (`Number`: `320` – `7680px`, step `10`, default `1920`) — Custom horizontal resolution when `Resolution` is set to `Custom`.
- `Custom Height` (`Number`: `240` – `4320px`, step `10`, default `1080`) — Custom vertical resolution when `Resolution` is set to `Custom`.
- `Auto Copy to Clipboard` (`Boolean`, default `true`) — Copies the captured PNG image directly to your system clipboard.
- `Chat Action Buttons` (`Boolean`, default `true`) — Appends clickable `[Open]`, `[Folder]`, and `[Copy]` chips in chat.

#### 57. **Container Preview**
Renders an inline glassmorphic preview card anchored cleanly outside the item tooltip when hovering **Shulker Boxes** (`3×9` grid), **Ender Chests** (cached `3×9` contents), or **Filled Maps** (`128×128` live canvas).
- `Display Mode` (`Mode`: `Always`, `Hold Key`, default `Always`)
- `Preview Key` (`Mode`: `Shift`, `Ctrl`, `Alt`, default `Shift`) — Shows a non-overlapping `Hold [Key] to preview` badge below the tooltip until pressed.
- `Shulker Boxes` (`Boolean`, default `true`)
- `Filled Maps` (`Boolean`, default `true`)
- `Ender Chest` (`Boolean`, default `true`)

#### 58. **Death Recap** `[HUD]`
Comprehensive post-death summary card on the Death Screen displaying killer name & 3D skin avatar, weapon used, lost XP levels, coordinates, and dimension, with one-click `[📌 Set Waypoint]` and `[📋 Copy Coords]` buttons. Fully draggable and resizable in the HUD Editor with custom background/text theme colors.
- `Auto Death Waypoint` (`Boolean`, default `true`) — Automatically registers a red death waypoint at your death coordinates.
- `Show Killer Avatar` (`Boolean`, default `true`) — Render the killer's 3D skin face avatar when slain by a player.
- `Auto Open Waypoint Screen` (`Boolean`, default `false`) — Immediately open the Waypoint Manager upon death.

#### 59. **Radial Quick-Swap** *(Hold Key: `R`)*
Glassmorphic 8-slice circular wheel centered on your crosshair while holding a hotkey to instantly equip weapons, tools, water buckets, pearls, and food without scrolling the hotbar.
- `Mode` (`Mode`: `Smart Survival`, `Hotbar Slots (1-8)`, default `Smart Survival`) — `Smart Survival` automatically finds your best Sword, Pickaxe, Axe, Shovel, Water Bucket, Ender Pearl, Food/Gapple, and Blocks/Bow; `Hotbar Slots (1-8)` maps directly to slots 1–8.
- `Radius` (`Number`: `60` – `130px`, step `5`, default `85`) — Circular wheel distance from crosshair.
- `Show Item Count` (`Boolean`, default `true`) — Display item stack counts on wheel slices.
- `Play Sound` (`Boolean`, default `true`) — Plays an equipment click sound when releasing on a slot.

---

### 🚀 Movement (3 Modules)

#### 60. **Toggle Sprint** *(Default Key: `V`)*
Automatically engages sprinting whenever moving forward.

#### 61. **Safe Walk**
Prevents you from accidentally walking off block edges without needing to hold sneak.

#### 62. **Toggle Sneak**
Tap your sneak key once to stay crouched hands-free.
- `Unsneak in Containers` (`Boolean`, default `true`) — Temporarily release sneak when opening inventories/chests.

---

### 🌍 World (6 Modules)

#### 63. **Time Changer** *(Default Key: `T`)*
Overrides the client-side sky time and lighting without affecting the server.
- `Preset` (`Mode`: `Custom`, `Sunrise`, `Morning`, `Noon`, `Afternoon`, `Sunset`, `Night`, `Midnight`, default `Noon`)
- `Time` (`Number`: `0` – `24000` ticks, step `100`, default `6000`) — Used when `Preset` is set to `Custom`.

#### 64. **Weather Changer**
Overrides client-side weather rendering independently of server weather.
- `Weather` (`Mode`: `Clear`, `Rain`, `Thunder`, default `Clear`)

#### 65. **Waypoints**
Per-server and per-world 3D waypoints with vertical beacon beams, distance badges, dimension isolation, optional Nether `8:1` coordinate projection, automatic death markers, and an inline ClickGUI overview list + full Waypoint Manager modal.
- `Show Beam` (`Boolean`, default `true`)
- `Show Distance` (`Boolean`, default `true`)
- `Auto Death Waypoint` (`Boolean`, default `true`) — Automatically records/updates your `Last Death` location.
- `Cross-Dimension Sync (8:1)` (`Boolean`, default `false`) — Project Overworld ↔ Nether coordinates (`÷8` / `×8`).
- `Scale` (`Number`: `0.5` – `2.5x`, step `0.1`, default `1.0`)
- `Max Distance` (`Number`: `100` – `30000` blocks, step `100`, default `5000`)
- `Quick Add Action` (`Mode`: `Open Creator`, `Instant Save`, default `Open Creator`)
- `Waypoints List` (`WaypointListSetting`) — Inline overview list (`[OW]` / `[Nether]` / `[End]` tags, visibility toggle, edit, delete, and `+ Add Waypoint`).

#### 66. **Light Overlay**
Renders a 3D mob-spawn grid on nearby solid surfaces: **Red** markers where hostile mobs can spawn anytime (`skyLight == 0`) and **Yellow** markers where hostile mobs can spawn at night (`skyLight > 0`).
- `Style` (`Mode`: `Cross`, `Square`, `Diamond`, default `Cross`)
- `Range` (`Number`: `6` – `32` blocks, step `2`, default `16`)
- `Show Night Spawns` (`Boolean`, default `true`)
- `Max Safe Block Light` (`Number`: `0` – `7`, step `1`, default `0`)
- `Line Thickness` (`Number`: `1.0` – `3.5px`, step `0.5`, default `1.5`)

#### 67. **Chunk Borders**
Renders clean customizable `16×16` chunk boundary wireframes, `16`-block vertical sub-chunk rings, neighbor chunk corners, and automatic **Slime Chunk** floor highlights when the world seed is known.
- `Use Client Color` (`Boolean`, default `false`)
- `Border Color` (`ColorMode`, default `Yellow`)
- `Show Sub-Chunks` (`Boolean`, default `true`)
- `Show Wall Grid` (`Boolean`, default `true`)
- `Show Neighbor Corners` (`Boolean`, default `true`)
- `Highlight Slime Chunks` (`Boolean`, default `true`)
- `Height Range` (`Number`: `16` – `96` blocks, step `16`, default `32`)
- `Line Thickness` (`Number`: `1.0` – `4.0px`, step `0.5`, default `1.5`)

#### 68. **Spawner Overlay**
Highlights nearby **Mob Spawners**, **Trial Spawners**, and **Vaults** with 3D bounding boxes, mob type & `● Active` / `○ Standby` badges, and a 3D `16`-block player activation radius ring.
- `Highlight Box` (`Boolean`, default `true`)
- `Activation Radius (16m)` (`Boolean`, default `true`) — Renders a 16-block activation ring (Green when inside `≤ 16m`, Red when outside).
- `Show Info Label` (`Boolean`, default `true`)
- `Include Trial & Vaults` (`Boolean`, default `true`)
- `Tracers` (`Boolean`, default `false`)
- `Use Client Color` (`Boolean`, default `false`)
- `Color` (`ColorMode`, default `Gold`)
- `Range` (`Number`: `16` – `96` blocks, step `8`, default `48`)
- `Line Thickness` (`Number`: `1.0` – `4.0px`, step `0.5`, default `1.5`)

---

### 🖥 Server (7 Modules)

#### 69. **Hypixel Level Head**
Displays players' Hypixel Network, Bedwars, or SkyWars level badges above their heads *(requires `Hypixel API Key` in Client Settings)*.
- `Level Type` (`Mode`: `Network`, `Bedwars`, `Skywars`, default `Network`)
- `Level Color` (`ColorMode`, default `Gold`)
- `Show Prefix` (`Boolean`, default `true`)
- `Background` (`Boolean`, default `true`)
- `Show Self` (`Boolean`, default `false`)
- `Hypixel Only` (`Boolean`, default `true`)
- `Range` (`Number`: `1` – `256` blocks, step `1`, default `64`)
- `Y Offset` (`Number`: `-60` – `20`, step `1`, default `-20`)
- `Update Interval` (`Number`: `10` – `600s`, step `10`, default `60`)

#### 70. **Hypixel Player Stats** *(Experimental)*
One-shot action keybind that looks up a player's Bedwars, SkyWars, and Duels statistics via the Hypixel API and prints a formatted summary in chat.
- `Target` (`Mode`: `Crosshair`, `Nearest`, `Self`, default `Crosshair`)
- `Bedwars` (`Boolean`, default `true`)
- `SkyWars` (`Boolean`, default `true`)
- `Duels` (`Boolean`, default `true`)
- `Show Level` (`Boolean`, default `true`)
- `Show Wins/Losses` (`Boolean`, default `true`)
- `Show Kills/Deaths` (`Boolean`, default `true`)
- `Show Extras` (`Boolean`, default `false`) — Finals, beds broken, goals, assists, winstreak.
- `Hypixel Only` (`Boolean`, default `true`)
- `Range` (`Number`: `1` – `256` blocks, step `1`, default `64`)
- `Cache Duration` (`Number`: `30` – `3600s`, step `30`, default `300`)
- `Debug` (`Boolean`, default `true`)

#### 71. **TNT Timer**
Renders a live color-coded countdown timer (`seconds`) above primed TNT entities.

#### 72. **Auto GG**
Automatically sends customizable `GG` and `GLHF` messages on match end/start across major multiplayer servers with anti-spoof chat detection.
- `GG Message` (`Text`, default `"gg"`)
- `GG Delay` (`Number`: `0.0` – `5.0s`, step `0.1`, default `1.0`)
- `Auto GLHF` (`Boolean`, default `false`)
- `GLHF Message` (`Text`, default `"glhf"`)
- `GLHF Delay` (`Number`: `0.0` – `5.0s`, step `0.1`, default `1.0`)
- `Cooldown` (`Number`: `3` – `30s`, step `1`, default `8`)

#### 73. **Bed Defense (Base Defense)**
Scans nearby beds in Bedwars and renders a floating glassmorphic card above each bed displaying its defense layers (`Obsidian`, `End Stone`, `Wood`, `Clay`, `Glass`, `Wool`, `Water`), 3D block icons, layer order, and `⚠ Exposed` warnings when a bed is uncovered or has missing defense blocks.
- `Highlight Bed` (`Boolean`, default `true`) — Renders a glowing 3D highlight bounding box around the bed.
- `Show Block Icons` (`Boolean`, default `true`) — Renders miniature 3D floating block icons for each defense layer.
- `Show Layer Names` (`Boolean`, default `true`) — Displays text badges for each layer material.
- `Show Exposed Alert` (`Boolean`, default `true`) — Shows a red `⚠ EXPOSED` warning badge if the bed is directly accessible without mining defenses.
- `Max Layers` (`Number`: `1` – `4`, step `1`, default `3`) — Number of block defense layers to inspect outward from the bed.
- `Range` (`Number`: `8` – `96` blocks, step `8`, default `48`) — Detection radius in blocks around the player.

#### 74. **Fireball Alert**
Tracks incoming fireballs in Bedwars and mini-games to warn you before they hit or knock you off bridges.
- `Sound Alert` (`Boolean`, default `true`) — Play a warning chime when an incoming fireball is detected.
- `Sound Type` (`Mode`: `Bell`, `Pling`, `Ding`, `Anvil`, `Bass`, default `Bell`)
- `Screen Warning` (`Boolean`, default `true`) — Display a high-visibility pulsing warning banner on screen with distance & ETA.
- `Direction Arrow` (`Boolean`, default `true`) — Display directional pointer (`▲ Ahead`, `◀ Left`, `▶ Right`, `▼ Behind`) indicating where the fireball is coming from.
- `World Box` (`Boolean`, default `true`) — Draw a 3D glowing highlight box around the fireball in the world.
- `Tracer Line` (`Boolean`, default `true`) — Draw a 3D tracer line from screen center towards the fireball.
- `Detection Range` (`Number`: `20.0` – `100.0` blocks, step `5.0`, default `60.0`)
- `Direct Threat Only` (`Boolean`, default `true`) — Only trigger alerts for fireballs trajectory-calculated to land near you (`< 8.5` blocks).
- `Toast Alert` (`Boolean`, default `false`) — Show a slide-in corner toast on initial detection.

#### 75. **Base Alert**
Automatically locks onto your team's bed on Bedwars match start or initial spawn, monitors your base perimeter, and alerts you when enemy players enter your territory so you never get snuck on or lose your bed unexpectedly.
- `Alert Radius` (`Number`: `10.0` – `50.0` blocks, step `1.0`, default `25.0`) — Radius around your bed to trigger intruder alerts.
- `Ignore NPCs` (`Boolean`, default `true`) — Filters out Hypixel Shopkeepers, Upgrades NPCs, and server Citizens bots.
- `Sound Alert` (`Boolean`, default `true`) — Play alarm chimes when an enemy enters the base perimeter (increases pitch when `< 10m`).
- `Sound Type` (`Mode`: `Bell`, `Pling`, `Ding`, `Anvil`, `Bass`, default `Ding`)
- `Sound Cooldown` (`Number`: `1.0` – `10.0s`, step `0.5`, default `3.0`)
- `Screen Threat HUD` (`Boolean`, default `true`) — Render top-screen threat card showing nearest intruder's name, distance to bed, relative direction, and held item (e.g. `⛏ Diamond Pickaxe`, `🧨 TNT`).
- `Show Held Item` (`Boolean`, default `true`) — Displays the enemy's main hand item in the threat alert.
- `Invis Enemy Alert` (`Boolean`, default `true`) — Highlights invisible players with special `👻 INVISIBLE!` warning when they step inside base radius.
- `3D Perimeter Ring` (`Boolean`, default `true`) — Renders a glowing 3D defense boundary circle on the ground around your bed.
- `3D Threat Box` (`Boolean`, default `true`) — Highlights invading enemy players in the world with a glowing 3D box.
- `Tracer Line` (`Boolean`, default `false`) — Renders a tracer line from screen center towards invading enemies.
- `Bed Status Marker` (`Boolean`, default `true`) — Floating 3D status badge directly above your team's bed (`🛏 Your Bed: [SAFE]` / `⚠ BASE ATTACK!`).
- `Toast Notification` (`Boolean`, default `true`) — Fires a corner toast notification on intruder detection.

---

### ⚔ Cheats (15 Modules)

> ⚠️ **Disclaimer:** Modules in the `Cheats` category are intended strictly for **Singleplayer**, **private servers**, or **environments where movement/combat modifications are explicitly permitted**. Always respect server rules.

#### 76. **Fly** *(Default Key: `F`)*
Enables free creative-style flight with adjustable speed.
- `Speed` (`Number`: `1.0` – `10.0x`, step `0.5`, default `1.0`)

#### 77. **Entity ESP** *(Default Key: `Y`)*
Highlights nearby players and mobs through walls with boxes, Chams fills, tracer lines, and labels.
- `Players Only` (`Boolean`, default `true`)
- `Style` (`Mode`: `Glowing`, `Box 2D`, `Box 3D`, `Chams`, default `Glowing`)
- `Boxes` (`Boolean`, default `true`)
- `Tracers` (`Boolean`, default `true`)
- `Labels` (`Boolean`, default `true`)
- `Use Client Colors` (`Boolean`, default `true`) — Syncs box outlines & tracer lines to `Client Menu Color` and box/label fills to `Client Menu Background`.
- `Line Color` (`ColorMode`, default `Sky Blue`) — Custom box/tracer color when `Use Client Colors` is off.
- `Fill Color` (`ColorMode`, default `Slate`) — Custom box/label fill color when `Use Client Colors` is off.
- `Range` (`Number`: `1` – `100` blocks, step `1`, default `32`)

#### 78. **Anti Knockback** *(Default Key: `K`)*
Reduces or cancels incoming velocity knockback packets.
- `Reduction` (`Number`: `0` – `100%`, step `5`, default `100%`)

#### 79. **Walk on Water** *(Default Key: `J`)*
Allows walking across water surfaces as solid ground (hold Sneak to submerge).

#### 80. **Speed** *(Default Key: `N`)*
Multiplies horizontal ground movement velocity.
- `Speed` (`Number`: `1.0` – `10.0x`, step `0.5`, default `2.0`)

#### 81. **Scaffold**
Automatically places blocks from your hotbar beneath your feet while walking.
- `Tower` (`Boolean`, default `true`) — Hold Jump while standing still to build straight upward.

#### 82. **Free Cam** *(Default Key: `U`)*
Detaches the camera so you can fly freely through the world while your player body remains stationary.
- `Speed` (`Number`: `0.1` – `10.0` blocks/tick, step `0.1`, default `1.0`)

#### 83. **Aimbot** *(Default Key: `H`)*
Smoothly rotates toward nearby targets with optional automatic attacking.
- `Targets` (`Mode`: `Players & Mobs`, `Players Only`, `Mobs Only`, default `Players & Mobs`)
- `Range` (`Number`: `1.0` – `10.0` blocks, step `1.0`, default `6.0`)
- `Attack Distance` (`Number`: `0.5` – `5.0` blocks, step `0.1`, default `3.0`)
- `Smooth` (`Number`: `1.0` – `30.0°/tick`, step `1.0`, default `10.0`)
- `Auto Attack` (`Boolean`, default `true`)
- `PVP Mode` (`Mode`: `1.9+`, `1.8`, default `1.9+`)

#### 84. **Kill Aura**
Automatically attacks valid entities within range using configurable target priority and weapon cooldown modes.
- `Range` (`Number`: `1.0` – `8.0` blocks, step `0.5`, default `4.5`)
- `Players Only` (`Boolean`, default `false`)
- `Priority` (`Mode`: `Closest`, `Lowest HP`, `Angle`, default `Closest`)
- `Attack Mode` (`Mode`: `1.9+`, `1.8`, default `1.9+`)

#### 85. **Auto Clicker**
Automatically attacks living entities under your crosshair at a configurable CPS rate.
- `CPS` (`Number`: `1` – `30`, step `1`, default `12`)
- `When Holding` (`Boolean`, default `true`) — Only click while holding Left Mouse Button.

#### 86. **Fast Place**
Removes the vanilla right-click block placement delay.

#### 87. **Fast Break**
Accelerates block mining speed and eliminates block-break cooldown ticks.
- `Speed` (`Number`: `1.0` – `10.0x`, step `0.5`, default `3.0`)

#### 88. **No Fall**
Mitigates fall distance tracking to prevent fall damage.
- `Mode` (`Mode`: `Safe`, `Strict`, default `Safe`)

#### 89. **Chest ESP**
Highlights storage containers (Chests, Trapped Chests, Barrels, Ender Chests, Shulker Boxes) through walls.
- `Range` (`Number`: `8` – `128` blocks, step `8`, default `48`)
- `Boxes` (`Boolean`, default `true`)
- `Tracers` (`Boolean`, default `true`)
- `Labels` (`Boolean`, default `true`)

#### 90. **Ore ESP**
Scans and highlights valuable ore blocks through terrain with per-ore color coding.
- `Show Diamond` (`Boolean`, default `true`)
- `Show Ancient Debris` (`Boolean`, default `true`)
- `Show Iron` (`Boolean`, default `false`)
- `Show Gold` (`Boolean`, default `false`)
- `Show Coal` (`Boolean`, default `false`)
- `Show Redstone` (`Boolean`, default `false`)
- `Show Lapis` (`Boolean`, default `false`)
- `Show Copper` (`Boolean`, default `false`)
- `Show Emerald` (`Boolean`, default `false`)
- `Range` (`Number`: `8` – `64` blocks, step `4`, default `32`)
- `Boxes` (`Boolean`, default `true`)
- `Tracers` (`Boolean`, default `true`)
- `Labels` (`Boolean`, default `true`)

---

## ⌨ Default Keybinds

All module keybinds can be customized or cleared directly inside the **ClickGUI**:

| Key | Action / Module | Mode |
| :---: | :--- | :---: |
| `Right Shift` | **Open Star Client ClickGUI** | Toggle |
| `C` | **Zoom** | Hold |
| `G` | **Free Look** (`360°` Orbit Camera) | Hold |
| `R` | **Radial Quick-Swap** | Hold |
| `V` | **Toggle Sprint** | Toggle |
| `B` | **Fullbright** | Toggle |
| `T` | **Time Changer** | Toggle |
| `F` | **Fly** | Toggle |
| `Y` | **Entity ESP** | Toggle |
| `K` | **Anti Knockback** | Toggle |
| `J` | **Walk on Water** | Toggle |
| `N` | **Speed** | Toggle |
| `U` | **Free Cam** | Toggle |
| `H` | **Aimbot** | Toggle |

---

## 🛠 Installation & Building from Source

### Requirements
- **Minecraft**: `26.2`
- **Fabric Loader**: `0.19.5+` (`>=0.19.3`)
- **Fabric API**: `0.152.2+26.2` (or latest for MC `26.2`)
- **Java Runtime / JDK**: **Java 25** (`JDK 25`)

### Installing the Mod
1. Install **Fabric Loader** for Minecraft `26.2`.
2. Place **Fabric API** and the **Star Client** `.jar` into your `.minecraft/mods` folder.
3. Launch Minecraft using **Java 25** and press **`Right Shift`** to open the Star Client menu.

---

## 💾 Configuration & Storage

All client data is stored cleanly inside `.minecraft/config/starclient/`:
- `config.json` — Active settings, module states, keybinds, profiles, banned words, macros, nicknames, HUD layout & widget scales, and per-account cosmetic loadouts.
- `waypoints.json` — Per-server and per-world saved 3D waypoints.
- `accounts.json` — Saved Microsoft & Offline account entries for the Account Switcher.
- `stats.json` — Lifetime Singleplayer & Multiplayer client statistics and login streaks.

---

## 📄 License

Licensed under the **MIT License** — Developed by **TrixCraft Development**.

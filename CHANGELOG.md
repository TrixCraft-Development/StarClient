# Star Client v0.15

> Welcome to Star Client **v0.15**! A high-performance, modular Fabric client for Minecraft 26.2 featuring glassmorphic UI, custom HUD widgets, and game enhancements.

---

## What's New

### New Modules, Friends System & Client Enhancements
- [NEW] **Anvil Helper (`WORLD`)**: Shows each item's **Anvil Uses / Prior Work Penalty (`2/6`)**, reveals the **real XP level cost** past vanilla's `"Too Expensive!"` (40+) limit, flags conflicting enchantments that would be lost, and alerts you when swapping the left & right inputs saves XP levels (`💡 Swap inputs: Save X lvls!`).
- [NEW] **Color Formatting Bar (`UI`)**: Compact 1-click Minecraft color & style toolbar (`0–9`/`a–f` color swatches, Bold, Italic, Underline, Strike, Obfuscated, Reset, and `&`/`§` prefix toggle) attached to **Sign Editors**, **Book & Quill**, and **Anvil Rename** screens.
- [NEW] **Crafting Calculator (`PLAYER` — Hotkey: `O`)**: Bulk Crafting Material Calculator (press `O` in-game or while hovering any item in an inventory/chest) featuring live unlocked recipes + 80+ curated Minecraft recipes, recursive **Raw Materials** expansion, stack breakdowns, live player inventory comparison (`✓ Ready` / missing counts), and 1-click **Copy Shopping List**.
- [NEW] **Sound Muffler (`PLAYER`)**: Selectively silence (`0%`) or quietly scale down (`5%–50%`) annoying Minecraft sounds with 11 built-in category toggles (**Nether Portals**, **Rain & Thunder**, **Villagers**, **Endermen**, **Minecarts**, **Pistons**, **Fire & Lava**, **Elytra Wind**, **Water & Cave Ambient**, **Farm Animals**, **Beacon & Conduit**) plus a custom filter modal featuring a live **Recently Played Sounds** 1-click mute feed.
- [NEW] **Villager Trades (`WORLD`)**: Enhances the Villager Trading UI by highlighting **Max-Level & Rare Enchanted Books** (and enchanted gear) directly in the trade list, rendering a glassmorphic **Trade Inspector** side panel with vanilla-accurate **`GREAT` / `FAIR` / `PRICY` / `CURSE`** emerald price ratings, and firing celebratory chime + Toast alerts when rolling Librarians.
- [NEW] **Discord Rich Presence (`Settings` Tab)**: Native pure-Java Windows Discord IPC integration displaying your live activity (**Main Menu**, **Server IP**, **Singleplayer World**, **Dimension**, and **Session Playtime**) on Discord with privacy toggles (`Show Server IP`, `Show World Name`, `Show Dimension`, `Show Playtime`), live connection indicator, and optional custom `Discord App ID`.
- [NEW] **Slot Lock (`PLAYER` — Hotkey: `L`)**: Lock any hotbar, inventory, armor, or offhand slot (hover a slot in any inventory/container and press `L`, or press `L` in-game to lock your held hotbar slot) with crisp pixel-art padlock icons on both inventory screens and the hotbar HUD — preventing accidental drops (`Q`/`Ctrl+Q`), offhand swaps (`F`), and chest shift-clicks/swaps, with per-profile persistence.
- [NEW] **In-Game Screenshot Gallery (`Main Menu`, `Pause Menu`, & `ClickGUI`)**: Browse your `.minecraft/screenshots` directly inside Minecraft in a glassmorphic thumbnail grid with async lazy thumbnail decoding, search filtering, date/size/resolution badges (`720p`, `1080p`, `1440p`, `4K`, `8K`), and a fullscreen lightbox viewer supporting 1-click **Copy Image to Clipboard**, **Open File/Folder**, **Rename**, and **Delete**.
- [NEW] **Quick Server Switcher (`Pause Menu` & `ClickGUI`)**: Switch between saved multiplayer servers or Direct Join any IP in **1 click** without disconnecting back to the Main Menu first — complete with live server icons, MOTDs, color-coded ping (`ms`), and player counts.
- [NEW] **Now Playing Media HUD (`UI` — Draggable HUD Widget)**: Displays currently playing music on Windows (Spotify, YouTube Music, Apple Music, VLC, browsers) with `Card` & `Compact` layouts, real-time song duration & progress/remaining bar (`0:37/1:20`), animated audio equalizer bars, smooth marquee scrolling, Client Color sync, and optional `Alt+P` / `Alt+←/→` in-game media control shortcuts.
- [NEW] **Chat Keyword Alerts (`UI`)**: Configure custom chat keywords, phrases, or player names via the in-game modal editor (`+ Quick-Add Chips`, live highlight preview & sound test); matching chat messages are highlighted in your chosen color, trigger a slide-in **Toast Notification**, and play a configurable **Sound Chime** (`Pling`, `Bell`, `Ding`, `Level Up`, `Bass`).
- [NEW] **Prevent Game Crashes (`Settings` Tab)**: New opt-in general client safeguard (with a confirmation warning popup explaining it can cause issues and is not guaranteed to work 100%) that intercepts runtime crashes, saves the full crash report to `crash-reports/`, resets broken render/world state, and returns you safely to the **Main Menu** instead of closing the game.
- [NEW] **Tactical Ping (`WORLD` — Hotkey: `Z`)**: Apex/Shooter-style 3D crosshair ping marker (cast up to `150` blocks away; `Sneak + Key` to clear) that automatically identifies **Enemies**, **Allies / Friends**, **Primed TNT / Fireballs**, **Beds (Exposed/Protected)**, **Loot Chests**, **Resources**, **Spawners**, and **Locations** with glowing 3D markers, off-screen directional edge arrows, optional chat/clipboard coordinate callouts, and audio chimes.
- [NEW] **Bedwars Upgrades (`SERVER` — Draggable HUD Widget)**: Automatically tracks your team's purchased **Bedwars Upgrades** (`Sharpness`, `Protection I–IV`, `Maniac Miner I–II`, `Iron` → `Molten Forge`, `Heal Pool`, `Dragon Buff`) and queued **Traps (`0–3`)** from match chat, flashing urgent audio & banner alerts when an enemy triggers a trap. Supports both Full Card and Compact Pill layouts.
---

## Shortcuts & Controls
- Press `Right Shift` to open the **ClickGUI** menu.
- Use the **HUD Editor** button to drag and position your widgets on screen.
- Press `ESC` or click the close button to exit this changelog.

> Star Client is continuously improving. Have feedback or suggestions? Feel free to share them!

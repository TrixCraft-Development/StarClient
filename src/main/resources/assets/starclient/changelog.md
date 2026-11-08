# Star Client v0.13

> Welcome to Star Client **v0.13**! A high-performance, modular Fabric client for Minecraft 26.2 featuring glassmorphic UI, custom HUD widgets, and game enhancements.

---

## What's New

### New Modules, Friends System & Client Enhancements
- [NEW] **Base Alert (`SERVER`)**: Automatically locks onto your team's bed on Bedwars match start or initial spawn, monitors your base perimeter (`10` – `50` blocks), and sends high-urgency alerts when enemy players approach—featuring an on-screen Threat HUD card (nearest intruder, distance to bed, direction, held item: e.g. `⛏ Diamond Pickaxe`, `🧨 TNT`), invisible player alerts (`👻 INVISIBLE!`), a glowing 3D defense perimeter ring on the ground around your bed, 3D threat boxes, tracer lines, floating bed status markers (`🛏 Your Bed: [SAFE]` / `⚠ BASE ATTACK!`), audio warning chimes, and slide-in corner toast notifications.
- [NEW] **Fireball Alert (`SERVER`)**: Tracks incoming fireballs in Bedwars and mini-games to warn you before they hit or knock you off bridges—featuring pulsing on-screen warning banners with live distance & ETA, directional pointers (`▲ Ahead`, `◀ Left`, `▶ Right`, `▼ Behind`), 3D world bounding boxes, tracer lines, and warning chimes.
- [NEW] **1.8 Module (`COMBAT`)**: Authentic Minecraft 1.8.9 mechanics and combat feel for modern clients—featuring classic **Sword Block-Hitting** & chops, snappy **Old Swing Trajectory**, instant **Old Re-equip** (zero item swap dip delay), classic **Old Rod & Bow Angles**, mouth-bobbing **Old Eating/Drinking**, **Red Armor Damage Flash** on hurt entities, **Hide Modern Cooldown**, **Disable Sweep Particles & Sounds**, **Hide Offhand (1st Person)**, snappy **Instant 1.8 Sneaking** (`1.54` eye height), and classic **Hit Sounds & Bow Ding**.
- [NEW] **Sound Radar (`UI` — HUD Widget)**: Directional audio compass & circular HUD radar card visualizing 3D in-game sounds (footsteps, explosions, projectiles, chests/blocks, mobs) with distance readouts and optional crosshair directional chevrons.
- [NEW] **Death Recap (`PLAYER`)**: Post-death summary card on the Death Screen displaying killer name & 3D skin avatar, weapon, lost XP levels, coordinates, and dimension, with one-click `[📌 Set Waypoint]` and `[📋 Copy Coords]` buttons.
- [NEW] **Radial Quick-Swap Wheel (`PLAYER` — Hold Key `R`)**: Hold hotkey to open an 8-slice glassmorphic circular wheel around your crosshair to instantly equip tools, food/gapples, pearls, or water buckets without scrolling the hotbar.
- [NEW] **Potion Alerts (`UI`)**: Note Block pling sound pings, actionbar warnings, and corner toasts when positive potion buffs are expiring (`≤ 10s`), plus bass warning tones and an on-screen cure indicator badge (`🥛 Drink Milk to Cure!`) when afflicted with harmful debuffs.
- [NEW] **Stopwatch & Timer (`UI` — HUD)**: Draggable HUD stopwatch and countdown timer (`0.5` – `120.0` min) with `RUN` / `PAUSE` / `DONE` status badges, tenths-of-a-second readout, `Start / Pause` hotkey action, and `Double-Tap Reset`.
- [NEW] **Entity Info in Block Info (`UI` — WAILA HUD)**: `Block Info` now inspects targeted entities—displaying live health & armor bars, **Horse / Donkey / Mule / Camel** speed (`m/s`) & jump height (`blocks`), **Villager** profession & trade tier (`Novice` → `Master`), pet tame/sitting status, sheep wool state, and player ping/friend tags.
- [NEW] **Item ESP (`RENDER`) & Upgraded Entity ESP (`CHEATS`)**: Highlights dropped items and entities with `Box 2D`, `Box 3D`, `Chams`, or `Glowing` styles, tracer lines, item icons, and stack labels—synced with **Client Menu Color** & **Client Menu Background** (`Use Client Colors`) or custom `Line Color` & `Fill Color` palettes.
- [NEW] **Spawner Overlay (`WORLD`)**: Highlights **Mob Spawners**, **Trial Spawners**, and **Vaults** with 3D boxes, mob type & `● Active` / `○ Standby` badges, and a 3D **16-block Activation Radius ring**.
- [NEW] **Low Health Vignette (`RENDER`)**: Smooth heartbeat-pulsing screen-edge vignette when your health drops at or below a configurable HP threshold.
- [NEW] **Bed Defense / Base Defense (`SERVER`)**: Scans nearby beds in Bedwars and displays a floating glassmorphic card above each bed showing its defense layers (`Obsidian`, `End Stone`, `Wood`, `Clay`, `Glass`, `Wool`, `Water`) in order, 3D block icons, layer names, 3D world highlight boxes, and instant `⚠ Exposed` alerts when a bed is uncovered.
- [NEW] **Client Friends System Overhaul (`Client Settings`)**:
  - **Player Skin Avatars**: Face avatars with 3D skin hat layers rendered next to each friend in the Friends Manager (resolved via online players or Mojang profile cache).
  - **Per-Friend Notes & Tags**: Add custom notes/tags per friend (e.g., `"2v2 Partner"`, `"SMP Builder"`) with inline editing and sleek HUD/card pill badges.
  - **Join & Leave Toast Notifications**: Automatic Tab-list monitor with corner toasts (`Friend Joined` / `Friend Left`) and subtle chime sounds.
  - **Chat Name Highlighting**: Displays your friend's badge in front of their name in chat messages.
  - **Customizable Badge Styles**: Choose between `♥ Heart`, `★ Star`, `🛡 Ally`, `✦ Sparkle`, or `[FRIEND]`.
  - **Target HUD & Crosshair Indicator**: Displays a `♥ FRIEND` pill on the Target HUD card, and tints the Custom Crosshair emerald green when aiming at friends (`Friend Tint`).
  - **Global Client-Wide Storage**: Friends are persisted in `starclient/friends.json` globally and never reset or wiped when switching profiles.
- [NEW] **Unified Toast Notification System (`Client Settings`)**: Expanded slide-in corner toasts for Profile switches, Friends added/removed, Screenshots saved/copied, Low Durability & Tool Saved alerts, Waypoints, and Timer completion.
- [IMPROVED] **Custom Screenshot Resolutions (`PLAYER — Screenshots`)**: Configure custom output resolutions for F2 screenshots (`Native`, `720p`, `1080p`, `1440p`, `4K`, `8K`, or `Custom Width × Height`) with native STB image resampling and aspect-ratio preservation—capture pristine 4K or 8K screenshots from any monitor, window size, or ultrawide aspect ratio.
- [IMPROVED] **Combat Category (`🗡 Combat`)**: Organized **Reach Display**, **Combo Counter**, **Hit Color**, **Hit Particles**, **Low Fire**, and **Low Shield** into the dedicated `Combat` category.
---

## Shortcuts & Controls
- Press `Right Shift` to open the **ClickGUI** menu.
- Use the **HUD Editor** button to drag and position your widgets on screen.
- Press `ESC` or click the close button to exit this changelog.

> Star Client is continuously improving. Have feedback or suggestions? Feel free to share them!

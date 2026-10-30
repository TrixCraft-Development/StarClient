# Star Client v0.9-dev

> Welcome to Star Client **v0.9-dev**! A high-performance, modular Fabric client for Minecraft 26.2 featuring glassmorphic UI, custom HUD widgets, and game enhancements.

---

## What's New

### Cosmetics Studio & 3D Live Avatar
- [NEW] **Dedicated Cosmetics Studio**: Opened directly from the Module menu with sleek client theme synchronization and optimized card layouts.
- [NEW] **3D Live Avatar Preview**: Interactive 3D character viewport supporting drag-to-rotate, synchronized head alignment, and animated cosmetic previews in **both Main Menu and In-Game**.
- [NEW] **Visual Card Previews**: Miniature visual displays for all cosmetics — high-fidelity cape textures, mirrored 3D wings, genuine Minecraft particle icons for trails, and headwear icons.
- [NEW] **Silent Equipping**: Removed intrusive popup notifications when toggling cosmetics for an instantaneous, seamless equip flow.

### Official Cape Collection (82+ Capes)
- [NEW] **82 Official Capes**: Built-in access to the complete collection of recognized cape designs — including Minecon (2011–2016), Alpha, 1 Million, OptiFine variants, Mojang Studios, Developer capes, Lunar variants, Pride, Villager Rescue, Halloween, and community classics.
- [NEW] **Free for All Players**: All standard official collection capes are unlocked and accessible under `Rank.USER`.
- [NEW] **Animated Premium Capes**: Exclusive animated capes (Chroma Spectrum and Cosmic Core) marked with the dynamic `ANIMATED` badge for Premium supporters.

### Rank-Gated Wavey Capes & 1:1 Vanilla Physics
- [NEW] **Authentic Vanilla Cape Behavior**: Non-premium / User rank players now enjoy Minecraft's genuine rigid `PlayerCapeModel` with 1:1 vanilla physics. Eliminates any stretching or inversion over the head during creative flying or sprinting.
- [NEW] **Fluid Wavey Cape Simulation**: Premium supporters unlock 16-segment aerodynamic wave simulation with real-time wave speed and amplitude sliders.
- [NEW] **Atmospheric Wind Sway Toggle**: Premium users can toggle "Atmospheric Wind Sway" off to effortlessly return to the classic rigid vanilla cape behavior at any time.

### Per-Account Cosmetic Profiles
- [NEW] **Account-Isolated Loadouts**: Cosmetics, equipped capes, wings, headwear, trails, and physics configurations are now saved strictly per Minecraft account (keyed by UUID).
- [NEW] **Instant Account Switching**: Switching accounts in the account manager immediately updates the active cosmetic loadout without cross-account bleed-through.

### Inventory HUD & Search
- [NEW] **Compact HUD Widget**: View all 27 inventory slots, 9 hotbar slots, 4 armor slots, and offhand item without opening your inventory screen.
- [NEW] **Inventory Search Bar**: Built-in instant search box placed directly below container inventory screens.
- [NEW] **Lore & Tag Search**: Filter items in real time by item name, registry identifier, and all tooltip lore lines.
- [NEW] **Dynamic Slot Highlights**: Matching items are highlighted with an accent ring while non-matching items are smoothly dimmed.
- [NEW] **Show HUD Widget Toggle**: Option to completely turn off the floating HUD widget while keeping the Inventory Search bar enabled.
- [FIX] **Boots Visibility**: Fixed boots slot disappearing when *Show Armor* was enabled and *Include Hotbar* was disabled.
- [FIX] **Creative Mode Search Bar**: Relocated the Inventory Search bar below bottom creative tabs in Creative Inventory mode to eliminate tab overlap.

### Combat & Shield
- [NEW] **Low Shield Module**: Lowers or repositions the shield in first person, mirroring the Low Fire visual improvement.
- [NEW] **Damage Indicators**: Clean floating damage numbers, golden crit indicators (`✧ -X.X ❤ ✧`), customizable duration, scale, and bounce gravity physics.
- [FIX] **Server Login Protection**: Prevented entity ID access exceptions when joining servers (`ClientboundLoginPacket`).

### Boss Bar & Visuals
- [IMPROVED] **Variable Boss Bar Sizing**: Boss bar dynamically expands its width when boss names are long to prevent overlapping with the percentage display.
- [IMPROVED] **Centered Boss Name**: Cleanly centered boss name with refined bar dimensions and proportions.
- [NEW] **Transparent Background**: Added transparent background toggle to *Boss Bar*, *Scoreboard*, and *Tab list*.

### Stats & Sharing
- [NEW] **Shareable Stats Card**: Added an interactive button in the Stats menu to copy a formatted summary or generate an exportable card to share with friends.
- [IMPROVED] Real-time tracking for kills, deaths, distance traveled, jumps, food consumed, and playtime.

### In-Game Changelog
- [NEW] **In-Game Version Changelogs**: Click the version number in the bottom-left corner of the Main Menu to view formatted release notes with full markdown styling!

---

## Shortcuts & Controls
- Press `Right Shift` to open the **ClickGUI** menu.
- Use the **HUD Editor** button to drag and position your widgets on screen.
- Press `ESC` or click the close button to exit this changelog.

> Star Client is continuously improving. Have feedback or suggestions? Feel free to share them!

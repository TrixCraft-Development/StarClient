package net.starclient.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.api.HypixelApi;
import net.starclient.gui.EntityEsp;
import net.starclient.gui.HypixelLevelHead;
import net.starclient.gui.HypixelPlayerStats;
import net.starclient.gui.LightOverlayRenderer;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.mixin.FishingHookAccessor;
import net.starclient.mixin.MinecraftAccessor;
import net.starclient.mixin.MultiPlayerGameModeAccessor;
import net.starclient.setting.BannedWordListSetting;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.MacroListSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NickEntryListSetting;
import net.starclient.setting.NoteListSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;
import net.starclient.setting.WaypointListSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> modulesByName = new HashMap<>();
    private List<Module> cachedSortedActiveModules = List.of();
    private static volatile boolean activeModulesDirty = true;

    public static void markActiveModulesDirty() {
        activeModulesDirty = true;
    }

    /** How long a key-triggered Hypixel lookup waits before giving up. */
    private static final long LOOKUP_TIMEOUT_MILLIS = 10_000L;

    public ModuleManager() {
        // Register Default Client Modules
        register(new Module("Fullbright", "Grants maximum vision in the dark.", Category.RENDER, GLFW.GLFW_KEY_B) {
            private double previousGamma = 1.0;

            {
                addSetting(new ModeSetting("Mode", "Fullbright technique", "Gamma", List.of("Gamma", "Night Vision", "Both")));
            }

            @Override
            public void onEnable() {
                previousGamma = mc.options.gamma().get();
                apply(mc);
            }

            @Override
            public void onTick(Minecraft client) {
                apply(client);
            }

            @Override
            public void onDisable() {
                FullbrightState.setGammaActive(false);
                mc.options.gamma().set(previousGamma);
                if (mc.player != null) {
                    mc.player.removeEffect(MobEffects.NIGHT_VISION);
                    mc.player.removeEffect(MobEffects.DARKNESS);
                }
            }

            private void apply(Minecraft client) {
                String mode = getMode();
                boolean useGamma = "Gamma".equals(mode) || "Both".equals(mode);
                boolean useNightVision = "Night Vision".equals(mode) || "Both".equals(mode);
                FullbrightState.setGammaActive(useGamma);
                if (useGamma) {
                    mc.options.gamma().set(1.0);
                }
                if (useNightVision && client.player != null) {
                    client.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
                    client.player.removeEffect(MobEffects.DARKNESS);
                }
            }

            private String getMode() {
                Setting<?> setting = getSetting("Mode");
                return setting == null ? "Both" : (String) setting.getValue();
            }
        });

        register(new Module("Keystrokes", "Displays modern WASD & Mouse keystrokes overlay.", Category.UI) {
            {
                setEnabled(true);
                addSetting(new BooleanSetting("Show Mouse", "Display CPS & Mouse buttons", true));
            }
        });

        register(new Module("Armor HUD", "Displays held items & equipped armor durability with low-durability alerts and tool/Elytra break protection.", Category.PLAYER) {
            {
                DurabilityGuardState.setModule(this);
                setEnabled(false);
                addSetting(new ModeSetting("Orientation", "HUD layout alignment", "Vertical", List.of("Horizontal", "Vertical")));
                addSetting(new BooleanSetting("Mirror Items", "Reverse armor order (Helmet <-> Boots)", false));
                addSetting(new BooleanSetting("Show Held Item", "Also display the main-hand item alongside armor", false));
                addSetting(new BooleanSetting("Low Durability Alert", "Flash a red border and warning when gear drops below Warning Threshold %", true));
                addSetting(new NumberSetting("Warning Threshold %", "Durability percentage threshold for low-durability warnings", 15.0, 5.0, 30.0, 1.0));
                addSetting(new BooleanSetting("Durability Sound Ping", "Play an alert ping and actionbar warning when armor, Elytra, or tools drop below threshold", true));
                addSetting(new BooleanSetting("Prevent Tool Breaking", "Stop mining, attacking, or using valuable tools and Elytra at <= 2 durability so they never shatter", false));
                addSetting(new ModeSetting("Durability", "How armor durability text is displayed",
                        "Show current & max Durability",
                        List.of("Show current & max Durability", "Only current", "Hidden")));
            }

            @Override
            public void onTick(Minecraft client) {
                DurabilityGuardState.onTick(client);
            }

            @Override
            public void onDisable() {
                DurabilityGuardState.reset();
            }
        });

        register(new Module("Inventory HUD", "Displays a compact real-time preview of your inventory on your HUD.", Category.PLAYER) {
            {
                setEnabled(false);
                addSetting(new BooleanSetting("Show HUD Widget", "Render the floating inventory preview widget on your HUD", true));
                addSetting(new BooleanSetting("Include Hotbar", "Include hotbar slots below inventory", false));
                addSetting(new BooleanSetting("Show Offhand", "Display offhand slot on the side", true));
                addSetting(new BooleanSetting("Show Armor", "Display equipped armor column", false));
                addSetting(new BooleanSetting("Show Title", "Show 'Inventory' header title", false));
                addSetting(new BooleanSetting("Highlight Selected", "Highlight active hotbar slot with accent color", true));
                addSetting(new BooleanSetting("Show Slot Backgrounds", "Draw individual slot backdrops", true));
                addSetting(new BooleanSetting("Hide in Inventory", "Hide HUD widget while viewing your inventory or containers", true));
                addSetting(new BooleanSetting("Inventory Search", "Show an item search bar below your inventory to search items and lores", true));
            }
        });

        register(new Module("FPS Display", "Displays real-time FPS.", Category.UI) {
            {
                setEnabled(true);
            }
        });

        register(new Module("Ping Display", "Displays your network latency.", Category.UI) {
            {
                setEnabled(true);
            }
        });

        register(new Module("Coordinates", "Displays your current XYZ position.", Category.UI) {
            {
                setEnabled(true);
            }
        });

        register(new Module("Active Modules", "Displays enabled modules list (Arraylist) on screen.", Category.UI) {
            {
                setEnabled(true);
            }
        });

        register(new Module("Zoom", "Hold to zoom in with smooth FOV interpolation and scroll wheel zoom control.", Category.UI, GLFW.GLFW_KEY_C) {
            private boolean previousSmoothCamera = false;

            private static final double DEFAULT_ZOOM_FOV = 30.0;

            {
                ZoomState.setZoomModule(this);
                addSetting(new NumberSetting("FOV", "Target zoomed-in field of view", DEFAULT_ZOOM_FOV, 1.0, 100.0, 0.5));
                addSetting(new BooleanSetting("Smooth Transition", "Smoothly glide in and out when zooming or scrolling", true));
                addSetting(new NumberSetting("Transition Speed", "Animation glide speed for smooth zoom", 14.0, 4.0, 30.0, 1.0));
                addSetting(new NumberSetting("Scroll Step", "How much each scroll notch changes the zoom FOV", 4.0, 1.0, 15.0, 0.5));
                addSetting(new NumberSetting("Mouse Sensitivity", "Camera turn speed multiplier while zoomed (%)", 100.0, 10.0, 250.0, 5.0));
                addSetting(new BooleanSetting("Cinematic Camera", "Enable slow cinematic mouse damping while zoomed", false));
                addSetting(new BooleanSetting("Reset on Release", "Reset zoom level back to 30 FOV on each new zoom", false));
            }

            @Override
            public boolean isHoldKey() {
                return true;
            }

            @Override
            public void onEnable() {
                previousSmoothCamera = mc.options.smoothCamera;
                if (getSetting("Reset on Release") instanceof BooleanSetting bs && bs.getValue()) {
                    if (getSetting("FOV") instanceof NumberSetting number) {
                        number.setValue(DEFAULT_ZOOM_FOV);
                    }
                }
                applyZoom();
            }

            @Override
            public void onTick(Minecraft client) {
                applyZoom();
            }

            @Override
            public void onDisable() {
                mc.options.smoothCamera = previousSmoothCamera;
            }

            @Override
            public void onScroll(double amount) {
                if (!(getSetting("FOV") instanceof NumberSetting setting)) return;
                double step = getSetting("Scroll Step") instanceof NumberSetting ns ? ns.getValue() : 4.0;
                double current = setting.getValue();
                // Scale step proportionally at tight FOVs so ultra-deep zoom (down to 1.0 FOV) stays smooth
                double curveFactor = Math.clamp(current / 30.0, 0.12, 1.4);
                setting.setValue(Math.clamp(current - amount * step * curveFactor, setting.getMin(), setting.getMax()));
            }

            private void applyZoom() {
                mc.options.smoothCamera = isSmoothCamera();
            }

            private boolean isSmoothCamera() {
                Setting<?> setting = getSetting("Cinematic Camera");
                return setting != null && (boolean) setting.getValue();
            }
        });

        register(new Module("Toggle Sprint", "Automatically holds sprint mode.", Category.MOVEMENT, GLFW.GLFW_KEY_V) {
            @Override
            public void onTick(Minecraft client) {
                if (client.player != null && client.options.keyUp.isDown()) {
                    client.player.setSprinting(true);
                }
            }
        });

        register(new Module("Fly", "Allows free motion flight in creative or survival.", Category.CHEATS, GLFW.GLFW_KEY_F) {
            private static final float BASE_FLY_SPEED = 0.05f;

            {
                addSetting(new NumberSetting("Speed", "Flight speed multiplier (1x = normal creative)", 1.0, 1.0, 10.0, 0.5));
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player != null && isEnabled()) {
                    client.player.getAbilities().flying = true;
                    client.player.getAbilities().setFlyingSpeed(BASE_FLY_SPEED * (float) getSpeed());
                }
            }

            @Override
            public void onDisable() {
                if (mc.player != null) {
                    mc.player.getAbilities().flying = false;
                    mc.player.getAbilities().setFlyingSpeed(BASE_FLY_SPEED);
                }
            }

            private double getSpeed() {
                Setting<?> setting = getSetting("Speed");
                return setting == null ? 1.0 : (double) setting.getValue();
            }
        });

        register(new Module("Entity ESP", "Draws tracer lines & highlights around entities.", Category.CHEATS, GLFW.GLFW_KEY_Y) {
            {
                addSetting(new BooleanSetting("Players Only", "Highlight only players", true));
                addSetting(new ModeSetting("Style", "ESP render style", "Glowing", List.of("Glowing", "Box 2D", "Chams")));
                addSetting(new NumberSetting("Range", "Max ESP range in blocks", 15.0, 1.0, 100.0, 1.0));
            }

            @Override
            public void onEnable() {
                if ("Glowing".equals(getStyle())) {
                    EntityEsp.applyGlow(mc, isPlayersOnly(), getMaxRange(), true);
                }
            }

            @Override
            public void onDisable() {
                EntityEsp.applyGlow(mc, false, 100, false);
            }

            @Override
            public void onTick(Minecraft client) {
                if ("Glowing".equals(getStyle())) {
                    EntityEsp.applyGlow(client, isPlayersOnly(), getMaxRange(), true);
                }
            }

            private boolean isPlayersOnly() {
                Setting<?> setting = getSetting("Players Only");
                return setting == null || (boolean) setting.getValue();
            }

            private String getStyle() {
                Setting<?> setting = getSetting("Style");
                return setting == null ? "Glowing" : (String) setting.getValue();
            }

            private double getMaxRange() {
                Setting<?> setting = getSetting("Range");
                return setting == null ? 100.0 : (double) setting.getValue();
            }
        });

        register(new Module("Auto Tool", "Automatically selects best tool for block breaking.", Category.PLAYER) {
            {
                addSetting(new BooleanSetting("Switch Back", "Returns to previous slot after breaking", true));
            }

            @Override
            public void onEnable() {
                AutoToolState.setActive(true);
                AutoToolState.setSwitchBack(getSwitchBack());
                AutoToolState.reset();
            }

            @Override
            public void onTick(Minecraft client) {
                AutoToolState.setSwitchBack(getSwitchBack());
            }

            @Override
            public void onDisable() {
                if (AutoToolState.isSwitchBack() && AutoToolState.isMining() && mc.player != null) {
                    mc.player.getInventory().setSelectedSlot(AutoToolState.getPreviousSlot());
                }
                AutoToolState.setActive(false);
                AutoToolState.reset();
            }

            private boolean getSwitchBack() {
                Setting<?> setting = getSetting("Switch Back");
                return setting == null || (boolean) setting.getValue();
            }
        });

        register(new Module("Anti Knockback", "Reduces or removes knockback taken.", Category.CHEATS, GLFW.GLFW_KEY_K) {
            {
                addSetting(new NumberSetting("Reduction", "How much knockback is reduced (%)", 100.0, 0.0, 100.0, 5.0));
            }

            @Override
            public void onEnable() {
                AntiKnockbackState.setActive(true);
                AntiKnockbackState.setReduction(getReduction());
            }

            @Override
            public void onTick(Minecraft client) {
                AntiKnockbackState.setReduction(getReduction());
            }

            @Override
            public void onDisable() {
                AntiKnockbackState.setActive(false);
            }

            private double getReduction() {
                Setting<?> setting = getSetting("Reduction");
                return setting == null ? 100.0 : (double) setting.getValue();
            }
        });

        register(new Module("Walk on Water", "Walk across water as if it were solid ground.", Category.CHEATS, GLFW.GLFW_KEY_J) {
            @Override
            public void onTick(Minecraft client) {
                if (client.player == null || client.level == null) return;
                if (client.options.keyShift.isDown()) return;
                Player player = client.player;
                boolean feetInWater = isWaterAt(client, player.getX(), player.getY() - 0.1, player.getZ());
                if (!player.isInWater() && !feetInWater) return;
                Vec3 motion = player.getDeltaMovement();
                if (motion.y < 0) {
                    player.setDeltaMovement(motion.x, 0, motion.z);
                }
                player.setOnGround(true);
            }

            private boolean isWaterAt(Minecraft client, double x, double y, double z) {
                assert client.level != null;
                Fluid fluid = client.level.getFluidState(BlockPos.containing(x, y, z)).getType();
                return fluid.isSame(Fluids.WATER);
            }
        });

        register(new Module("Speed", "Increases your ground movement speed.", Category.CHEATS, GLFW.GLFW_KEY_N) {
            {
                addSetting(new NumberSetting("Speed", "Ground speed multiplier", 2.0, 1.0, 10.0, 0.5));
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null) return;
                Player player = client.player;
                if (!player.onGround() || player.isInWater()) return;
                Vec2 move = client.player.input.getMoveVector();
                if (move.x == 0.0F && move.y == 0.0F) return;
                Vec3 motion = player.getDeltaMovement();
                double horizontal = Math.hypot(motion.x, motion.z);
                if (horizontal < 0.01) return;
                double scale = (0.3 * getSpeed()) / horizontal;
                player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
            }

            private double getSpeed() {
                Setting<?> setting = getSetting("Speed");
                return setting == null ? 2.0 : (double) setting.getValue();
            }
        });

        register(new Module("Scaffold", "Automatically places blocks beneath you while you walk.", Category.CHEATS) {
            private int placeCooldown;
            private int previousSlot = -1;

            {
                addSetting(new BooleanSetting("Tower", "Hold the jump key while standing still to climb up", true));
            }

            @Override
            public void onEnable() {
                previousSlot = -1;
            }

            @Override
            public void onDisable() {
                Minecraft client = Minecraft.getInstance();
                if (previousSlot >= 0 && client.player != null) {
                    client.player.getInventory().setSelectedSlot(previousSlot);
                }
                previousSlot = -1;
                placeCooldown = 0;
            }

            @Override
            public void onTick(Minecraft client) {
                if (placeCooldown > 0) placeCooldown--;
                if (client.player == null || client.level == null || client.gameMode == null) return;
                Player player = client.player;
                if (player.containerMenu != player.inventoryMenu) return;
                if (player.isUsingItem()) return;

                BlockPos target = findTargetBlock(client, player);
                if (target == null) return;
                BlockHitResult hit = findSupport(client, target);
                if (hit == null) return;
                if (placeCooldown > 0) return;

                int blockSlot = findBlockSlot(player);
                if (blockSlot < 0) return;

                Inventory inventory = player.getInventory();
                if (blockSlot != inventory.getSelectedSlot()) {
                    if (previousSlot < 0) previousSlot = inventory.getSelectedSlot();
                    inventory.setSelectedSlot(blockSlot);
                }

                client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                client.player.swing(InteractionHand.MAIN_HAND);
                placeCooldown = 2;
            }

            private BlockPos findTargetBlock(Minecraft client, Player player) {
                Vec3 motion = player.getDeltaMovement();
                boolean still = motion.x * motion.x + motion.z * motion.z < 0.01;
                boolean tower = isTower() && client.options.keyJump.isDown() && still;
                BlockPos target = tower ? player.blockPosition() : player.blockPosition().below();
                assert client.level != null;
                if (!client.level.getBlockState(target).isAir()) return null;
                return target;
            }

            private BlockHitResult findSupport(Minecraft client, BlockPos target) {
                assert client.level != null;
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = target.relative(direction);
                    Direction normal = direction.getOpposite();
                    if (!client.level.getBlockState(neighbor).isFaceSturdy(client.level, neighbor, normal)) continue;
                    Vec3 location = Vec3.atCenterOf(neighbor)
                            .add(normal.getStepX() * 0.5, normal.getStepY() * 0.5, normal.getStepZ() * 0.5);
                    return new BlockHitResult(location, normal, neighbor, false);
                }
                return null;
            }

            private int findBlockSlot(Player player) {
                Inventory inventory = player.getInventory();
                int selected = inventory.getSelectedSlot();
                if (inventory.getItem(selected).getItem() instanceof BlockItem) return selected;
                for (int i = 0; i < 9; i++) {
                    if (inventory.getItem(i).getItem() instanceof BlockItem) return i;
                }
                return -1;
            }

            private boolean isTower() {
                Setting<?> setting = getSetting("Tower");
                return setting == null || (boolean) setting.getValue();
            }
        });

        register(new Module("Free Cam", "Detaches the camera so you can fly freely around your body.", Category.CHEATS, GLFW.GLFW_KEY_U) {
            private Vec3 originalPos = Vec3.ZERO;

            {
                FreeCamState.setFreeCamModule(this);
                addSetting(new NumberSetting("Speed", "Free cam movement speed in blocks per tick", 1.0, 0.1, 10.0, 0.1));
            }

            @Override
            public void onEnable() {
                FreeCamState.setInitialized(false);
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null) return;
                Player player = client.player;

                if (!FreeCamState.isInitialized()) {
                    FreeCamState.setPosition(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
                    FreeCamState.setRotation(player.getYRot(), player.getXRot());
                    FreeCamState.setInitialized(true);
                    originalPos = new Vec3(player.getX(), player.getY(), player.getZ());
                }

                player.setPos(originalPos.x, originalPos.y, originalPos.z);
                player.setDeltaMovement(Vec3.ZERO);

                double forward = 0.0;
                double strafe = 0.0;
                if (client.options.keyUp.isDown()) forward += 1.0;
                if (client.options.keyDown.isDown()) forward -= 1.0;
                if (client.options.keyRight.isDown()) strafe += 1.0;
                if (client.options.keyLeft.isDown()) strafe -= 1.0;

                if (forward != 0.0 || strafe != 0.0) {
                    double yawRad = Math.toRadians(FreeCamState.getYaw());
                    double sin = Math.sin(yawRad);
                    double cos = Math.cos(yawRad);
                    double dx = -sin * forward - cos * strafe;
                    double dz = cos * forward - sin * strafe;
                    double len = Math.hypot(dx, dz);
                    if (len > 1.0E-5) {
                        FreeCamState.addPosition(dx / len * getSpeed(), 0.0, dz / len * getSpeed());
                    }
                }

                if (client.options.keyJump.isDown()) FreeCamState.addPosition(0.0, getSpeed(), 0.0);
                if (client.options.keyShift.isDown()) FreeCamState.addPosition(0.0, -getSpeed(), 0.0);
            }

            @Override
            public void onDisable() {
                FreeCamState.setInitialized(false);
            }

            private double getSpeed() {
                Setting<?> setting = getSetting("Speed");
                return setting == null ? 1.0 : (double) setting.getValue();
            }
        });

        register(new Module("Free Look", "Hold to orbit the camera around your body while it keeps moving.", Category.PLAYER, GLFW.GLFW_KEY_G) {
            {
                FreeLookState.setFreeLookModule(this);
            }

            @Override
            public boolean isHoldKey() {
                return true;
            }

            @Override
            public void onEnable() {
                if (Minecraft.getInstance().player != null) {
                    FreeLookState.setRotation(Minecraft.getInstance().player.getYRot() + 180.0F, 0.0F);
                }
                FreeLookState.setInitialized(false);
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null) return;
                if (!FreeLookState.isInitialized()) {
                    FreeLookState.setRotation(client.player.getYRot() + 180.0F, 0.0F);
                    FreeLookState.setInitialized(true);
                }
            }

            @Override
            public void onDisable() {
                FreeLookState.setInitialized(false);
            }
        });

        register(new Module("Aimbot", "Automatically aims at players & mobs.", Category.CHEATS, GLFW.GLFW_KEY_H) {            private int cooldownTicks;

            {
                addSetting(new ModeSetting("Targets", "Which entities to target", "Players & Mobs", List.of("Players & Mobs", "Players Only", "Mobs Only")));
                addSetting(new NumberSetting("Range", "Max aim range in blocks", 6.0, 1.0, 10.0, 1.0));
                addSetting(new NumberSetting("Attack Distance", "Max distance for aimbot attacks", 3.0, 0.5, 5.0, 0.1));
                addSetting(new NumberSetting("Smooth", "Rotation speed in degrees per tick", 10.0, 1.0, 30.0, 1.0));
                addSetting(new BooleanSetting("Auto Attack", "Automatically attack targets in range", true));
                addSetting(new ModeSetting("PVP Mode", "1.8 spam click or 1.9+ cooldown timing", "1.9+", List.of("1.9+", "1.8")));
            }

            @Override
            public void onTick(Minecraft client) {
                if (cooldownTicks > 0) cooldownTicks--;
                if (client.player == null || client.level == null) return;
                Entity target = findTarget(client);
                if (target == null) return;

                double eyeY = client.player.getY() + client.player.getEyeHeight();
                double dx = target.getX() - client.player.getX();
                double dy = target.getY() + target.getBbHeight() * 0.8 - eyeY;
                double dz = target.getZ() - client.player.getZ();
                double horizontal = Math.sqrt(dx * dx + dz * dz);

                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(horizontal, 0.0001))));

                float maxDelta = (float) getSmooth();
                client.player.setYRot(rotateTowards(client.player.getYRot(), targetYaw, maxDelta));
                client.player.setXRot(rotateTowards(client.player.getXRot(), targetPitch, maxDelta));

                if (isAutoAttack() && target.distanceTo(client.player) <= getAttackDistance()) {
                    if ("1.8".equals(getPvpMode())) {
                        doAttack(client, target, false);
                    } else if (cooldownTicks <= 0) {
                        doAttack(client, target, true);
                        cooldownTicks = getWeaponCooldownTicks(client.player.getMainHandItem());
                    }
                }
            }

            @Override
            public void onDisable() {
                cooldownTicks = 0;
            }

            private static int getWeaponCooldownTicks(ItemStack stack) {
                double speed = Attributes.ATTACK_SPEED.value().getDefaultValue();
                ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (modifiers != null) {
                    speed = modifiers.compute(Attributes.ATTACK_SPEED, speed, EquipmentSlot.MAINHAND);
                }
                return (int) Math.round((1.0 / Math.max(speed, 0.01)) * 20.0);
            }

            private void doAttack(Minecraft client, Entity target, boolean resetCooldown) {
                assert client.player != null;
                assert client.gameMode != null;
                client.gameMode.attack(client.player, target);
                if (resetCooldown) client.player.resetAttackStrengthTicker();
                client.player.swing(InteractionHand.MAIN_HAND);
            }

            private Entity findTarget(Minecraft client) {
                String targets = getTargets();
                double maxRange = getRange();
                Entity best = null;
                double bestDist = Double.MAX_VALUE;
                assert client.level != null;
                assert client.player != null;
                List<Entity> candidates = client.level.getEntities(client.player,
                        client.player.getBoundingBox().inflate(maxRange),
                        entity -> {
                            if (entity == client.player || !entity.isAlive()) return false;
                            boolean isPlayer = entity instanceof Player;
                            boolean isMob = entity instanceof Mob;
                            return switch (targets) {
                                case "Players Only" -> isPlayer;
                                case "Mobs Only" -> isMob;
                                default -> isPlayer || isMob;
                            };
                        });
                for (Entity entity : candidates) {
                    double dist = entity.distanceTo(client.player);
                    if (dist <= maxRange && dist < bestDist) {
                        bestDist = dist;
                        best = entity;
                    }
                }
                return best;
            }

            private static float rotateTowards(float current, float target, float maxDelta) {
                float delta = target - current;
                while (delta > 180) delta -= 360;
                while (delta < -180) delta += 360;
                if (Math.abs(delta) <= maxDelta) return target;
                return current + Math.copySign(maxDelta, delta);
            }

            private String getTargets() {
                Setting<?> setting = getSetting("Targets");
                return setting == null ? "Players & Mobs" : (String) setting.getValue();
            }

            private double getRange() {
                Setting<?> setting = getSetting("Range");
                return setting == null ? 6.0 : (double) setting.getValue();
            }

            private double getAttackDistance() {
                Setting<?> setting = getSetting("Attack Distance");
                return setting == null ? 3.0 : (double) setting.getValue();
            }

            private double getSmooth() {
                Setting<?> setting = getSetting("Smooth");
                return setting == null ? 10.0 : (double) setting.getValue();
            }

            private boolean isAutoAttack() {
                Setting<?> setting = getSetting("Auto Attack");
                return setting == null || (boolean) setting.getValue();
            }

            private String getPvpMode() {
                Setting<?> setting = getSetting("PVP Mode");
                return setting == null ? "1.9+" : (String) setting.getValue();
            }
        });

        register(new Module("Kill Aura", "Automatically attacks nearby entities within range.", Category.CHEATS) {
            private int cooldownTicks;

            {
                addSetting(new NumberSetting("Range", "Attack entities within this range in blocks", 4.5, 1.0, 8.0, 0.5));
                addSetting(new BooleanSetting("Players Only", "Only attack other players", false));
                addSetting(new ModeSetting("Priority", "Target selection priority", "Closest", List.of("Closest", "Lowest HP", "Angle")));
                addSetting(new ModeSetting("Attack Mode", "Cooldown behaviour", "1.9+", List.of("1.9+", "1.8")));
            }

            @Override
            public void onTick(Minecraft client) {
                if (cooldownTicks > 0) cooldownTicks--;
                if (client.player == null || client.level == null || client.gameMode == null) return;
                Entity target = findTarget(client);
                if (target == null) return;

                if ("1.9+".equals(getAttackMode())) {
                    if (client.player.getAttackStrengthScale(0.0F) < 0.9F) return;
                    doAttack(client, target);
                } else {
                    if (cooldownTicks > 0) return;
                    doAttack(client, target);
                    cooldownTicks = 2;
                }
            }

            @Override
            public void onDisable() {
                cooldownTicks = 0;
            }

            private void doAttack(Minecraft client, Entity target) {
                assert client.player != null;
                assert client.gameMode != null;
                client.gameMode.attack(client.player, target);
                client.player.resetAttackStrengthTicker();
                client.player.swing(InteractionHand.MAIN_HAND);
            }

            private Entity findTarget(Minecraft client) {
                boolean playersOnly = isPlayersOnly();
                String priority = getPriority();
                double range = getRange();
                Entity best = null;
                double bestScore = Double.MAX_VALUE;
                assert client.level != null;
                assert client.player != null;
                List<Entity> candidates = client.level.getEntities(client.player,
                        client.player.getBoundingBox().inflate(range),
                        entity -> {
                            if (entity == client.player || !entity.isAlive()) return false;
                            boolean isPlayer = entity instanceof Player;
                            if (playersOnly) return isPlayer;
                            return isPlayer || entity instanceof Mob;
                        });
                for (Entity entity : candidates) {
                    double dist = entity.distanceTo(client.player);
                    if (dist > range) continue;

                    double score;
                    if ("Lowest HP".equals(priority)) {
                        score = ((LivingEntity) entity).getHealth();
                    } else if ("Angle".equals(priority)) {
                        score = angleTo(client.player, entity);
                    } else {
                        score = dist;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = entity;
                    }
                }
                return best;
            }

            private static double angleTo(Player player, Entity target) {
                Vec3 look = player.getLookAngle();
                Vec3 delta = target.position()
                        .add(-player.getX(), -(player.getY() + player.getEyeHeight()), -player.getZ())
                        .normalize();
                double dot = look.x * delta.x + look.y * delta.y + look.z * delta.z;
                return Math.acos(Math.clamp(dot, -1.0, 1.0));
            }

            private boolean isPlayersOnly() {
                Setting<?> setting = getSetting("Players Only");
                return setting != null && (boolean) setting.getValue();
            }

            private String getPriority() {
                Setting<?> setting = getSetting("Priority");
                return setting == null ? "Closest" : (String) setting.getValue();
            }

            private String getAttackMode() {
                Setting<?> setting = getSetting("Attack Mode");
                return setting == null ? "1.9+" : (String) setting.getValue();
            }

            private double getRange() {
                Setting<?> setting = getSetting("Range");
                return setting == null ? 4.5 : (double) setting.getValue();
            }
        });

        register(new Module("Auto Clicker", "Automatically attacks entities under your crosshair at high speed.", Category.CHEATS) {
            private int cooldownTicks;

            {
                addSetting(new NumberSetting("CPS", "Attacks per second", 12.0, 1.0, 30.0, 1.0));
                addSetting(new BooleanSetting("When Holding", "Only click while you are holding left-click", true));
            }

            @Override
            public void onTick(Minecraft client) {
                if (cooldownTicks > 0) cooldownTicks--;
                if (client.player == null || client.level == null || client.gameMode == null) return;
                if (client.gui.screen() != null) return;
                Player player = client.player;
                if (isWhenHolding() && !client.mouseHandler.isLeftPressed()) return;

                HitResult hit = client.hitResult;
                if (!(hit instanceof EntityHitResult entityHit)) return;
                Entity target = entityHit.getEntity();
                if (target == player || !target.isAlive() || !(target instanceof LivingEntity)) return;

                double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                if (player.distanceToSqr(target) > reach * reach) return;
                if (cooldownTicks > 0) return;

                client.gameMode.attack(player, target);
                player.resetAttackStrengthTicker();
                player.swing(InteractionHand.MAIN_HAND);
                cooldownTicks = Math.max(1, (int) Math.round(20.0 / getCps()));
            }

            private boolean isWhenHolding() {
                Setting<?> setting = getSetting("When Holding");
                return setting == null || (boolean) setting.getValue();
            }

            private double getCps() {
                Setting<?> setting = getSetting("CPS");
                return setting == null ? 12.0 : (double) setting.getValue();
            }
        });

        register(new Module("Fast Place", "Removes the right-click place delay for faster block placement.", Category.CHEATS) {
            @Override
            public void onTick(Minecraft client) {
                if (client != null) {
                    ((MinecraftAccessor) client).setRightClickDelay(0);
                }
            }

            @Override
            public void onDisable() {
                Minecraft client = Minecraft.getInstance();
                ((MinecraftAccessor) client).setRightClickDelay(4);
            }
        });

        register(new Module("Fast Break", "Breaks blocks faster and removes the break delay.", Category.CHEATS) {
            {
                addSetting(new NumberSetting("Speed", "Break speed multiplier", 3.0, 1.0, 10.0, 0.5));
            }

            @Override
            public void onEnable() {
                FastBreakState.setActive(true);
                FastBreakState.setSpeed(getSpeed());
            }

            @Override
            public void onTick(Minecraft client) {
                FastBreakState.setSpeed(getSpeed());
                if (client.gameMode != null) {
                    ((MultiPlayerGameModeAccessor) client.gameMode).setDestroyDelay(0);
                }
            }

            @Override
            public void onDisable() {
                FastBreakState.setActive(false);
                Minecraft client = Minecraft.getInstance();
                if (client.gameMode != null) {
                    ((MultiPlayerGameModeAccessor) client.gameMode).setDestroyDelay(5);
                }
            }

            private double getSpeed() {
                Setting<?> setting = getSetting("Speed");
                return setting == null ? 3.0 : (double) setting.getValue();
            }
        });

        register(new Module("Auto Armor", "Automatically equips the best armor found in your inventory.", Category.PLAYER) {
            private int cooldownTicks;

            {
                addSetting(new NumberSetting("Delay", "Ticks between armor checks", 10.0, 5.0, 40.0, 5.0));
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null || client.level == null) return;
                Player player = client.player;
                if (player.containerMenu != player.inventoryMenu) return;
                if (++cooldownTicks < getDelay()) return;
                cooldownTicks = 0;

                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                Inventory inventory = player.getInventory();
                for (EquipmentSlot slot : slots) {
                    ItemStack equipped = player.getItemBySlot(slot);
                    int bestIndex = -1;
                    double bestScore = score(equipped, slot);
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = inventory.getItem(i);
                        if (stack.isEmpty()) continue;
                        if (!fitsSlot(stack, slot)) continue;
                        double candidate = score(stack, slot);
                        if (candidate > bestScore) {
                            bestScore = candidate;
                            bestIndex = i;
                        }
                    }
                    if (bestIndex >= 0) {
                        if (!equipped.isEmpty()) {
                            int armorSlot = armorMenuSlot(slot);
                            if (armorSlot >= 0) {
                                assert client.gameMode != null;
                                client.gameMode.handleContainerInput(InventoryMenu.CONTAINER_ID, armorSlot, 0, ContainerInput.QUICK_MOVE, player);
                            }
                        }
                        int menuSlot = containerToMenuSlot(bestIndex);
                        assert client.gameMode != null;
                        client.gameMode.handleContainerInput(InventoryMenu.CONTAINER_ID, menuSlot, 0, ContainerInput.QUICK_MOVE, player);
                        return;
                    }
                }
            }

            private int getDelay() {
                Setting<?> setting = getSetting("Delay");
                return setting == null ? 10 : (int) (double) setting.getValue();
            }

            private static boolean fitsSlot(ItemStack stack, EquipmentSlot slot) {
                Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                return equippable != null && equippable.slot() == slot;
            }

            private static double score(ItemStack stack, EquipmentSlot slot) {
                if (stack.isEmpty()) return -1.0;
                ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                double value = 0.0;
                if (modifiers != null) {
                    value += modifiers.compute(Attributes.ARMOR, 0.0, slot) * 4.0;
                    value += modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
                }
                if (stack.isDamageableItem()) {
                    double max = stack.getMaxDamage();
                    value += 2.0 * (1.0 - stack.getDamageValue() / max);
                }
                return value;
            }

            private static int armorMenuSlot(EquipmentSlot slot) {
                return switch (slot) {
                    case HEAD -> InventoryMenu.ARMOR_SLOT_START;
                    case CHEST -> InventoryMenu.ARMOR_SLOT_START + 1;
                    case LEGS -> InventoryMenu.ARMOR_SLOT_START + 2;
                    case FEET -> InventoryMenu.ARMOR_SLOT_START + 3;
                    default -> -1;
                };
            }

            private static int containerToMenuSlot(int containerIndex) {
                if (containerIndex < 9) return InventoryMenu.USE_ROW_SLOT_START + containerIndex;
                return InventoryMenu.INV_SLOT_START + (containerIndex - 9);
            }
        });

        register(new Module("Auto Totem", "Automatically puts a Totem of Undying into your off-hand whenever you lose one.", Category.PLAYER) {
            private boolean restocking;

            @Override
            public void onEnable() {
                restocking = false;
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null || client.level == null || client.gameMode == null) return;
                Player player = client.player;
                if (player.containerMenu != player.inventoryMenu) return;

                ItemStack offhand = player.getOffhandItem();
                if (offhand.is(Items.TOTEM_OF_UNDYING)) {
                    restocking = false;
                    return;
                }
                if (restocking) return;

                Inventory inventory = player.getInventory();
                int totemSlot = -1;
                for (int i = 0; i < 36; i++) {
                    if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                        totemSlot = i;
                        break;
                    }
                }
                if (totemSlot < 0) return;

                int menuSlot = totemSlot < 9
                        ? InventoryMenu.USE_ROW_SLOT_START + totemSlot
                        : InventoryMenu.INV_SLOT_START + (totemSlot - 9);
                client.gameMode.handleContainerInput(InventoryMenu.CONTAINER_ID, menuSlot,
                        40, ContainerInput.SWAP, player);
                restocking = true;
            }
        });

        register(new Module("Auto Eat", "Automatically eats food when your hunger gets low.", Category.PLAYER) {
            private boolean holdingUse;

            {
                addSetting(new NumberSetting("Hunger Threshold", "Eat when hunger drops below this value", 12.0, 1.0, 19.0, 1.0));
                addSetting(new BooleanSetting("Select Food", "Search the hotbar for food if your main hand is empty", true));
            }

            @Override
            public void onEnable() {
                holdingUse = false;
            }

            @Override
            public void onDisable() {
                releaseUse();
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null || client.level == null || client.gameMode == null) {
                    releaseUse();
                    return;
                }
                Player player = client.player;
                boolean hungry = player.containerMenu == player.inventoryMenu
                        && player.getFoodData().getFoodLevel() < getHungerThreshold();

                boolean foodReady = false;
                if (hungry) {
                    ItemStack mainHand = player.getMainHandItem();
                    if (isFood(mainHand)) {
                        foodReady = true;
                    } else if (isSelectFood()) {
                        selectFoodSlot(player);
                        foodReady = isFood(player.getMainHandItem());
                    }
                }
                if (!hungry || !foodReady) {
                    releaseUse();
                    return;
                }

                if (player.isUsingItem()) {
                    if (holdingUse || isFood(player.getUseItem())) {
                        holdingUse = true;
                        client.options.keyUse.setDown(true);
                    }
                    return;
                }

                if (client.options.keyUse.isDown()) return;

                holdingUse = true;
                client.options.keyUse.setDown(true);
            }

            private void releaseUse() {
                if (holdingUse) {
                    Minecraft.getInstance().options.keyUse.setDown(false);
                }
                holdingUse = false;
            }

            private static void selectFoodSlot(Player player) {
                Inventory inventory = player.getInventory();
                int selected = inventory.getSelectedSlot();
                for (int i = 0; i < 9; i++) {
                    int slot = (selected + i) % 9;
                    if (isFood(inventory.getItem(slot))) {
                        if (slot != selected) inventory.setSelectedSlot(slot);
                        return;
                    }
                }
            }

            private static boolean isFood(ItemStack stack) {
                return !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
            }

            private boolean isSelectFood() {
                Setting<?> setting = getSetting("Select Food");
                return setting != null && (boolean) setting.getValue();
            }

            private double getHungerThreshold() {
                Setting<?> setting = getSetting("Hunger Threshold");
                return setting == null ? 12.0 : (double) setting.getValue();
            }
        });

        register(new Module("Auto Fish", "Automatically casts, detects bites and reels in fish.", Category.PLAYER) {
            private int cooldownTicks;
            private boolean pendingRelease;

            {
                addSetting(new BooleanSetting("Auto Recast", "Cast the rod again after catching a fish", true));
            }

            @Override
            public void onEnable() {
                cooldownTicks = 0;
                pendingRelease = false;
            }

            @Override
            public void onDisable() {
                if (pendingRelease) {
                    Minecraft.getInstance().options.keyUse.setDown(false);
                    pendingRelease = false;
                }
            }

            @Override
            public void onTick(Minecraft client) {
                if (pendingRelease) {
                    client.options.keyUse.setDown(false);
                    pendingRelease = false;
                }
                if (cooldownTicks > 0) cooldownTicks--;
                if (client.player == null || client.level == null || client.gameMode == null) return;
                Player player = client.player;
                if (player.containerMenu != player.inventoryMenu) return;
                if (!player.getMainHandItem().is(Items.FISHING_ROD)) return;
                if (client.options.keyUse.isDown()) return;
                if (cooldownTicks > 0) return;

                FishingHook bobber = findBobber(client, player);
                if (bobber != null) {
                    if (((FishingHookAccessor) bobber).isBiting()) {
                        pressUse(client);
                    }
                    return;
                }
                if (!isAutoRecast()) return;
                pressUse(client);
            }

            private static FishingHook findBobber(Minecraft client, Player player) {
                assert client.level != null;
                for (Entity entity : client.level.getEntities(player, player.getBoundingBox().inflate(48.0),
                        e -> e instanceof FishingHook hook && hook.getPlayerOwner() == player)) {
                    return (FishingHook) entity;
                }
                return null;
            }

            private void pressUse(Minecraft client) {
                client.options.keyUse.setDown(true);
                pendingRelease = true;
                cooldownTicks = 10;
            }

            private boolean isAutoRecast() {
                Setting<?> setting = getSetting("Auto Recast");
                return setting == null || (boolean) setting.getValue();
            }
        });

        register(new Module("No Fall", "Prevents fall damage.", Category.CHEATS) {
            {
                addSetting(new ModeSetting("Mode", "Prevention technique", "Safe", List.of("Safe", "Strict")));
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null) return;
                if (client.player.isFallFlying()) return;
                if (client.player.isInWater() || client.player.isInLava()) return;
                String mode = getMode();
                if ("Strict".equals(mode)) {
                    if (client.player.fallDistance > 0.05) {
                        client.player.fallDistance = 0.0;
                    }
                } else if (client.player.fallDistance > 3.0) {
                    client.player.fallDistance = 3.0;
                }
            }

            private String getMode() {
                Setting<?> setting = getSetting("Mode");
                return setting == null ? "Safe" : (String) setting.getValue();
            }
        });

        register(new Module("Safe Walk", "Stops you from walking off block edges.", Category.MOVEMENT) {
            @Override
            public void onEnable() {
                SafeWalkState.setActive(true);
            }

            @Override
            public void onDisable() {
                SafeWalkState.setActive(false);
            }
        });

        register(new Module("Chest ESP", "Highlights chests, barrels, ender chests and shulker boxes through walls.", Category.CHEATS) {
            {
                addSetting(new NumberSetting("Range", "Scan and render range in blocks", 48.0, 8.0, 128.0, 8.0));
                addSetting(new BooleanSetting("Boxes", "Draw outlines around storage blocks.", true));
                addSetting(new BooleanSetting("Tracers", "Draw lines from your crosshair to storage blocks.", true));
                addSetting(new BooleanSetting("Labels", "Show names and distances.", true));
            }

            @Override
            public void onEnable() {
                ChestEspState.clear();
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null || client.level == null) return;
                if (client.level.getGameTime() % 10 == 0) {
                    ChestEspState.update(client, getRange());
                }
            }

            @Override
            public void onDisable() {
                ChestEspState.clear();
            }

            private double getRange() {
                Setting<?> setting = getSetting("Range");
                return setting == null ? 48.0 : (double) setting.getValue();
            }
        });

        register(new Module("Ore ESP", "Highlights ore blocks through walls.", Category.CHEATS) {
            private int scanTicks;
            private final boolean[] enabled = new boolean[OreEspState.Ore.values().length];

            {
                for (OreEspState.Ore ore : OreEspState.Ore.values()) {
                    addSetting(new BooleanSetting("Show " + ore.label,
                            "Highlight " + ore.label + " ore",
                            ore == OreEspState.Ore.DIAMOND || ore == OreEspState.Ore.ANCIENT_DEBRIS));
                }
                addSetting(new NumberSetting("Range", "Scan range in blocks", 32.0, 8.0, 64.0, 4.0));
                addSetting(new BooleanSetting("Boxes", "Draw outlines around ore blocks.", true));
                addSetting(new BooleanSetting("Tracers", "Draw lines from your crosshair to ore blocks.", true));
                addSetting(new BooleanSetting("Labels", "Show names and distances.", true));
            }

            @Override
            public void onEnable() {
                OreEspState.clear();
                scanTicks = 0;
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null || client.level == null) return;
                if (++scanTicks < 20) return;
                scanTicks = 0;
                for (OreEspState.Ore ore : OreEspState.Ore.values()) {
                    Setting<?> setting = getSetting("Show " + ore.label);
                    enabled[ore.ordinal()] = setting == null || (boolean) setting.getValue();
                }
                OreEspState.update(client, getRange(), enabled);
            }

            @Override
            public void onDisable() {
                OreEspState.clear();
            }

            private double getRange() {
                Setting<?> setting = getSetting("Range");
                return setting == null ? 32.0 : (double) setting.getValue();
            }
        });

        register(new Module("Time Changer", "Forces the client-side time of day, changing the sky and lighting just for you.", Category.WORLD, GLFW.GLFW_KEY_T) {
            {
                addSetting(new ModeSetting("Preset", "Time of day preset", "Noon", List.of("Custom", "Sunrise", "Morning", "Noon", "Afternoon", "Sunset", "Night", "Midnight")));
                addSetting(new NumberSetting("Time", "Custom time of day in ticks (0-24000)", 6000.0, 0.0, 24000.0, 100.0));
            }

            @Override
            public void onEnable() {
                TimeChangerState.setActive(true);
                TimeChangerState.setDesiredTime(getDesiredTime());
            }

            @Override
            public void onTick(Minecraft client) {
                TimeChangerState.setActive(true);
                TimeChangerState.setDesiredTime(getDesiredTime());
            }

            @Override
            public void onDisable() {
                TimeChangerState.setActive(false);
            }

            private double getDesiredTime() {
                Setting<?> preset = getSetting("Preset");
                String mode = preset == null ? "Noon" : (String) preset.getValue();
                return switch (mode) {
                    case "Sunrise" -> 0.0;
                    case "Morning" -> 1000.0;
                    case "Noon" -> 6000.0;
                    case "Afternoon" -> 10000.0;
                    case "Sunset" -> 12000.0;
                    case "Night" -> 13800.0;
                    case "Midnight" -> 18000.0;
                    default -> getCustomTime();
                };
            }

            private double getCustomTime() {
                Setting<?> setting = getSetting("Time");
                return setting == null ? 6000.0 : (double) setting.getValue();
            }
        });

        register(new Module("Weather Changer", "Forces the client-side weather, overriding the server weather just for you.", Category.WORLD) {
            {
                addSetting(new ModeSetting("Weather", "Weather type", "Clear", List.of("Clear", "Rain", "Thunder")));
            }

            @Override
            public void onEnable() {
                WeatherChangerState.setActive(true);
                apply();
            }

            @Override
            public void onTick(Minecraft client) {
                WeatherChangerState.setActive(true);
                apply();
            }

            @Override
            public void onDisable() {
                WeatherChangerState.setActive(false);
            }

            private void apply() {
                String mode = getMode();
                switch (mode) {
                    case "Rain" -> {
                        WeatherChangerState.setRainLevel(1.0F);
                        WeatherChangerState.setThunderLevel(0.0F);
                    }
                    case "Thunder" -> {
                        WeatherChangerState.setRainLevel(1.0F);
                        WeatherChangerState.setThunderLevel(1.0F);
                    }
                    default -> {
                        WeatherChangerState.setRainLevel(0.0F);
                        WeatherChangerState.setThunderLevel(0.0F);
                    }
                }
            }

            private String getMode() {
                Setting<?> setting = getSetting("Weather");
                return setting == null ? "Clear" : (String) setting.getValue();
            }
        });

        register(new Module("Auto Chest Loot", "Automatically transfers all items from opened chests to your inventory.", Category.PLAYER) {
            private int cooldownTicks;
            private int cursor;

            {
                addSetting(new NumberSetting("Delay", "Ticks between transfers", 1.0, 0.0, 10.0, 1.0));
                addSetting(new BooleanSetting("Close After", "Close the chest once everything has been taken.", true));
            }

            @Override
            public void onTick(Minecraft client) {
                if (client.player == null) return;
                Player player = client.player;
                if (!(player.containerMenu instanceof ChestMenu chest)) return;

                if (++cooldownTicks < getDelay() + 1) return;
                cooldownTicks = 0;

                int size = chest.getContainer().getContainerSize();
                if (size <= 0) return;
                if (cursor >= size) cursor = 0;
                for (int i = 0; i < size; i++) {
                    int slot = (cursor + i) % size;
                    if (chest.getSlot(slot).getItem().isEmpty()) continue;
                    assert client.gameMode != null;
                    client.gameMode.handleContainerInput(chest.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
                    cursor = slot + 1;
                    return;
                }
                cursor = 0;
                if (isCloseAfter()) {
                    Minecraft.getInstance().setScreenAndShow(null);
                    client.player.closeContainer();
                }
            }

            private int getDelay() {
                Setting<?> setting = getSetting("Delay");
                return setting == null ? 1 : (int) (double) setting.getValue();
            }

            private boolean isCloseAfter() {
                Setting<?> setting = getSetting("Close After");
                return setting == null || (boolean) setting.getValue();
            }
        });

        register(new Module("Nick-Tool", "Client-side nick hider: change your own and other players' displayed names.", Category.PLAYER) {
            {
                NickToolState.setModule(this);
                addSetting(new TextSetting("Your Nick", "The display name shown for you", ""));
                addSetting(new NickEntryListSetting("Nick List", "Map real usernames to nicknames"));
            }
        });

        register(new Module("Hypixel Level Head", "Shows Hypixel Network / Bedwars / Skywars levels above players' heads.", Category.SERVER) {
            private int ticks;

            {
                addSetting(new ModeSetting("Level Type", "Which Hypixel level to show above each player's head",
                        "Network", List.of("Network", "Bedwars", "Skywars")));
                addSetting(new ColorModeSetting("Level Color", "Colour of the level label", "Gold", HypixelLevelHead.LEVEL_COLORS));
                addSetting(new BooleanSetting("Show Prefix", "Prefix the number with the level type, e.g. 'Bedwars: 412'", true));
                addSetting(new BooleanSetting("Background", "Draw a dark plate behind the level badge", true));
                addSetting(new BooleanSetting("Show Self", "Also draw your own level above your head", false));
                addSetting(new BooleanSetting("Hypixel Only", "Only work while connected to Hypixel", true));
                addSetting(new NumberSetting("Range", "How far away a player can be and still get a level", 64.0, 1.0, 256.0, 1.0));
                addSetting(new NumberSetting("Y Offset", "Vertical offset of the label relative to the top of the head", -20.0, -60.0, 20.0, 1.0));
                addSetting(new NumberSetting("Update Interval", "Seconds between level refreshes for the same player", 60.0, 10.0, 600.0, 10.0));
            }

            @Override
            public void onEnable() {
                ticks = 0;
                HypixelApi.invalidateIfKeyChanged();
            }

            @Override
            public void onDisable() {
                HypixelApi.clear();
            }

            @Override
            public void onTick(Minecraft client) {
                if (ticks++ % getUpdateInterval() != 0) {
                    return;
                }
                HypixelLevelHead.request(client, getRange(), isHypixelOnly(), getUpdateInterval() * 1000L);
            }

            private boolean isHypixelOnly() {
                Setting<?> setting = getSetting("Hypixel Only");
                return setting == null || (boolean) setting.getValue();
            }

            private double getRange() {
                Setting<?> setting = getSetting("Range");
                return setting == null ? 64.0 : (double) setting.getValue();
            }

            private int getUpdateInterval() {
                Setting<?> setting = getSetting("Update Interval");
                return setting == null ? 60 : Math.max(1, (int) (double) setting.getValue());
            }
        });

        register(new Module("Hypixel Player Stats", "Press your keybind to print a player's Bedwars, SkyWars and Duels stats in chat.", Category.SERVER) {
            private UUID pending;
            private String pendingName;
            private long pendingSince;

            {
                setExperimental(true);
                addSetting(new ModeSetting("Target", "Who to look up: the player on your crosshair, the one closest to it, or yourself",
                        "Crosshair", List.of("Crosshair", "Nearest", "Self")));
                addSetting(new BooleanSetting("Bedwars", "Include Bedwars stats", true));
                addSetting(new BooleanSetting("SkyWars", "Include SkyWars stats", true));
                addSetting(new BooleanSetting("Duels", "Include Duels stats", true));
                addSetting(new BooleanSetting("Show Level", "Show the game level next to each game name", true));
                addSetting(new BooleanSetting("Show Wins/Losses", "Show wins, losses and win rate", true));
                addSetting(new BooleanSetting("Show Kills/Deaths", "Show kills, deaths and the K/D ratio", true));
                addSetting(new BooleanSetting("Show Extras", "Also show finals, beds, goals, assists and winstreak", false));
                addSetting(new BooleanSetting("Hypixel Only", "Only work while connected to Hypixel", true));
                addSetting(new NumberSetting("Range", "How far away a player can be for 'Nearest' to pick them", 64.0, 1.0, 256.0, 1.0));
                addSetting(new NumberSetting("Cache Duration", "Seconds a result is reused before it is fetched again", 300.0, 30.0, 3600.0, 30.0));
                addSetting(new BooleanSetting("Debug", "Print what every lookup is doing in chat, step by step", true));
            }

            @Override
            public boolean isKeyBindToggleable() {
                return false;
            }

            @Override
            public void onEnable() {
                pending = null;
                HypixelApi.invalidateIfKeyChanged();
            }

            @Override
            public void onDisable() {
                HypixelApi.unpin(pending);
                pending = null;
            }

            @Override
            public void onKeyPress(Minecraft client) {
                if (!isEnabled()) {
                    // The keybind is a one-shot action rather than a toggle, so a disabled module
                    // would otherwise swallow the keypress with no feedback at all.
                    chat(client, "[Hypixel] Hypixel Player Stats is disabled. Enable it in the module list first.");
                    return;
                }

                HypixelApi.invalidateIfKeyChanged();
                drainDebug(client);
                debug(client, "keybind pressed; player=" + (client.player == null ? "none" : client.player.getName().getString())
                        + " level=" + (client.level == null ? "none" : "loaded")
                        + " apiKey=" + (HypixelApi.isConfigured() ? "set" : "MISSING")
                        + " hypixel=" + HypixelLevelHead.isHypixel(client));

                if (!HypixelApi.isConfigured()) {
                    chat(client, "[Hypixel] Set your API key in the client menu first.");
                    return;
                }
                if (isHypixelOnly() && !HypixelLevelHead.isHypixel(client)) {
                    chat(client, "[Hypixel] Not on Hypixel.");
                    return;
                }
                if (client.gui.screen() != null) {
                    debug(client, "ignored: a screen is open");
                    return;
                }

                Player target = resolveTarget(client);
                if (target == null) {
                    debug(client, "no target resolved (mode=" + getString() + ")");
                    chat(client, "[Hypixel] No player to look up.");
                    return;
                }

                UUID uuid = target.getUUID();
                String name = target.getName().getString();
                long ttl = getCacheDuration() * 1000L;
                debug(client, "target " + name + " uuid=" + uuid + " ttl=" + (ttl / 1000L) + "s");

                HypixelApi.Stats cached = HypixelApi.getStats(uuid);
                if (cached != null && !cached.isStale(ttl)) {
                    debug(client, "cache hit, " + ((ttl - (System.currentTimeMillis() - cached.fetchedAt())) / 1000L) + "s of TTL left");
                    print(client, name, cached);
                    drainDebug(client);
                    return;
                }
                if (cached != null) {
                    debug(client, "cache entry is stale, refetching");
                }
                if (HypixelApi.isBlocked(uuid)) {
                    debug(client, "blocked, cannot request right now");
                    chat(client, "[Hypixel] API is cooling down, try again shortly.");
                    return;
                }

                // Pinned so the level head's cache pruning cannot delete the result before the
                // tick below reads it.
                HypixelApi.pin(uuid);
                String refusal = HypixelApi.request(uuid, ttl);
                if (refusal != null) {
                    HypixelApi.unpin(uuid);
                    debug(client, "request refused: " + refusal);
                    chat(client, "[Hypixel] " + refusal + ".");
                    return;
                }

                pending = uuid;
                pendingName = name;
                pendingSince = System.currentTimeMillis();
                chat(client, "[Hypixel] Looking up " + name + "...");
                drainDebug(client);
            }

            @Override
            public void onTick(Minecraft client) {
                drainDebug(client);
                if (pending == null) {
                    return;
                }
                HypixelApi.Stats stats = HypixelApi.getStats(pending);
                if (stats != null) {
                    debug(client, "result arrived for " + pendingName + " after "
                            + (System.currentTimeMillis() - pendingSince) + "ms");
                    String name = pendingName;
                    HypixelApi.unpin(pending);
                    pending = null;
                    print(client, name, stats);
                    drainDebug(client);
                    return;
                }
                // The worker fills FAILED_UNTIL when every endpoint fails, and rate limiting backs
                // the whole client off, so either ends the wait instead of polling forever.
                if (HypixelApi.isBlocked(pending) || System.currentTimeMillis() - pendingSince > LOOKUP_TIMEOUT_MILLIS) {
                    debug(client, "giving up: blocked=" + HypixelApi.isBlocked(pending)
                            + " waited=" + (System.currentTimeMillis() - pendingSince) + "ms");
                    chat(client, "[Hypixel] No Hypixel data for " + pendingName + ".");
                    HypixelApi.unpin(pending);
                    pending = null;
                }
            }

            private Player resolveTarget(Minecraft client) {
                if (client.player == null || client.level == null) {
                    return null;
                }

                String mode = getString();
                if ("Self".equalsIgnoreCase(mode)) {
                    return client.player;
                }

                if (client.hitResult instanceof EntityHitResult hit
                        && hit.getEntity() instanceof Player player
                        && player != client.player) {
                    return player;
                }
                if ("Crosshair".equalsIgnoreCase(mode)) {
                    debug(client, "crosshair mode: nothing under the crosshair");
                    return null;
                }

                Vec3 look = client.player.getLookAngle();
                Vec3 eye = client.player.getEyePosition();
                double range = getNumber("Range", 64.0);
                int seen = 0;
                double bestDot = -Double.MAX_VALUE;
                double bestDistance = Double.MAX_VALUE;
                Player nearest = null;
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (!(entity instanceof Player player) || !player.isAlive() || player == client.player) {
                        continue;
                    }
                    double distance = player.distanceTo(client.player);
                    if (distance > range) {
                        continue;
                    }
                    Vec3 delta = player.getEyePosition().subtract(eye);
                    double length = delta.length();
                    if (length < 1.0E-4) {
                        continue;
                    }
                    // Pick the player closest to where the user is looking, falling back to raw
                    // distance so overlapping players still resolve to a single target.
                    double dot = delta.normalize().dot(look);
                    seen++;
                    if (dot > bestDot + 1.0E-6 || (Math.abs(dot - bestDot) <= 1.0E-6 && distance < bestDistance)) {
                        bestDot = dot;
                        bestDistance = distance;
                        nearest = player;
                    }
                }
                debug(client, "nearest mode: " + seen + " player(s) in range, picked "
                        + (nearest == null ? "none" : nearest.getName().getString()
                        + " dot=" + String.format(Locale.ROOT, "%.3f", bestDot)
                        + " dist=" + String.format(Locale.ROOT, "%.1f", bestDistance)));
                return nearest;
            }

            private void print(Minecraft client, String name, HypixelApi.Stats stats) {
                debug(client, "printing: network=" + stats.networkLevel()
                        + " bw=" + (stats.bedwars().isEmpty() ? "none" : stats.bedwars().kills() + "/" + stats.bedwars().deaths())
                        + " sw=" + (stats.skywars().isEmpty() ? "none" : stats.skywars().kills() + "/" + stats.skywars().deaths())
                        + " duels=" + (stats.duels().isEmpty() ? "none" : stats.duels().kills() + "/" + stats.duels().deaths()));
                HypixelPlayerStats.print(client, name, stats, new HypixelPlayerStats.Options(
                        isOn("Bedwars"),
                        isOn("SkyWars"),
                        isOn("Duels"),
                        isOn("Show Level"),
                        isOn("Show Wins/Losses"),
                        isOn("Show Kills/Deaths"),
                        isOn("Show Extras")));
            }

            private boolean isDebug() {
                return isOn("Debug");
            }

            private void debug(Minecraft client, String message) {
                if (isDebug()) {
                    chat(client, "[Hypixel][debug] " + message);
                }
            }

            /**
             * Moves the worker's diagnostics onto the client thread. Chat is not thread safe, so
             * the worker only ever writes to a buffer.
             */
            private void drainDebug(Minecraft client) {
                for (String line : HypixelApi.drainDebug()) {
                    if (isDebug()) {
                        chat(client, "[Hypixel][debug] " + line);
                    }
                }
            }

            private boolean isOn(String name) {
                Setting<?> setting = getSetting(name);
                return setting == null || (boolean) setting.getValue();
            }

            private boolean isHypixelOnly() {
                return isOn("Hypixel Only");
            }

            private String getString() {
                Setting<?> setting = getSetting("Target");
                return setting == null ? "Crosshair" : (String) setting.getValue();
            }

            private double getNumber(String name, double fallback) {
                Setting<?> setting = getSetting(name);
                return setting == null ? fallback : (double) setting.getValue();
            }

            private long getCacheDuration() {
                return (long) getNumber("Cache Duration", 300.0);
            }

            private static void chat(Minecraft client, String message) {
                client.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
            }
        });

        register(new Module("Target HUD", "Displays a card with target player or entity health, distance and armor.", Category.UI) {
            {
                addSetting(new ModeSetting("Target Mode", "When to show Target HUD", "Crosshair & Attack",
                        List.of("Crosshair & Attack", "Attack Only", "Crosshair Only")));
                addSetting(new BooleanSetting("Players Only", "Only display Target HUD for players", false));
                addSetting(new BooleanSetting("Show Armor", "Display target's equipped armor and held items", true));
                addSetting(new NumberSetting("Hold Time", "Seconds to keep showing target after looking away", 3.0, 0.5, 10.0, 0.5));
            }
        });

        register(new Module("Potion Effects", "Displays active potion effects with icons and remaining timers on the HUD.", Category.UI) {
            {
                addSetting(new BooleanSetting("Hide Vanilla Icons", "Hide vanilla top-right effect icons when enabled", true));
            }
        });

        register(new Module("CPS Display", "Displays your left and right clicks per second.", Category.UI) {
            {
                addSetting(new BooleanSetting("Show Right Click", "Display both LMB and RMB CPS", true));
            }
        });

        register(new Module("Reach Display", "Displays the distance of your last melee hit.", Category.UI) {
            {
                addSetting(new NumberSetting("Reset Time", "Seconds before reach resets to 0.00", 5.0, 1.0, 15.0, 1.0));
            }
        });

        register(new Module("Speedometer", "Displays your movement speed in blocks/sec or km/h.", Category.UI) {
            {
                addSetting(new ModeSetting("Unit", "Speed measurement unit", "m/s", List.of("m/s", "km/h")));
            }
        });

        register(new Module("Custom Crosshair", "Replaces the vanilla crosshair with a customizable crosshair.", Category.UI) {
            {
                CustomCrosshairState.setModule(this);
                addSetting(new ModeSetting("Shape", "Crosshair style", "Cross", List.of("Cross", "Circle", "Dot", "Cross + Dot")));
                addSetting(new BooleanSetting("Use Client Color", "Match crosshair color to Client Menu Color", true));
                addSetting(new ColorModeSetting("Color", "Custom crosshair color when Use Client Color is off", "Sky Blue", BlockOutlineState.OUTLINE_COLORS));
                addSetting(new BooleanSetting("Center Dot", "Draw a center dot", true));
                addSetting(new BooleanSetting("Outline", "Draw a dark outline around the crosshair", true));
                addSetting(new BooleanSetting("Dynamic", "Expand gap when moving or attacking", true));
                addSetting(new BooleanSetting("Cooldown Indicator", "Show 1.9+ PVP weapon cooldown attack indicator", true));
                addSetting(new BooleanSetting("Always Show Cooldown", "Show cooldown bar even if Minecraft setting is not set to Crosshair", false));
                addSetting(new NumberSetting("Size", "Arm length or radius in pixels", 4.0, 1.0, 12.0, 1.0));
                addSetting(new NumberSetting("Gap", "Distance from center in pixels", 2.0, 0.0, 10.0, 1.0));
                addSetting(new NumberSetting("Thickness", "Line thickness in pixels", 1.0, 1.0, 4.0, 1.0));
            }
        });

        register(new Module("Tab", "Modern glassmorphic player tab overlay with custom ping and heads.", Category.UI) {
            {
                setEnabled(true);
                addSetting(new BooleanSetting("Use Client Theme", "Match card accents and borders to Client Menu Theme", true));
                addSetting(new BooleanSetting("Show Ping Numbers", "Display numeric latency (ms) with latency color coding", true));
                addSetting(new BooleanSetting("Show Player Heads", "Render player face avatars with 3D skin layer", true));
                addSetting(new BooleanSetting("Show Header & Footer", "Display server header and footer announcements", true));
                addSetting(new BooleanSetting("Highlight Self", "Subtly highlight your own name row with client accent", true));
                addSetting(new NumberSetting("Background Opacity", "Glass background opacity percentage", 75.0, 10.0, 100.0, 5.0));
                addSetting(new BooleanSetting("Rounded Corners", "Smooth rounded card corners instead of sharp boxes", true));
                addSetting(new BooleanSetting("Text Shadow", "Render player names and text with drop shadow", true));
            }
        });

        register(new Module("Scoreboard", "Modern glassmorphic scoreboard with customizable red numbers.", Category.UI) {
            {
                setEnabled(true);
                addSetting(new BooleanSetting("Hide Red Numbers", "Hide red index numbers on the right side", true));
                addSetting(new BooleanSetting("Use Client Theme", "Match card header and accents to Client Menu Theme", true));
                addSetting(new BooleanSetting("Show Border", "Display subtle glass outline and accent header line", true));
                addSetting(new NumberSetting("Background Opacity", "Glass background opacity percentage", 70.0, 10.0, 100.0, 5.0));
                addSetting(new BooleanSetting("Rounded Corners", "Smooth rounded card corners instead of sharp boxes", true));
                addSetting(new BooleanSetting("Text Shadow", "Render scoreboard text with drop shadow", true));
                addSetting(new NumberSetting("Offset Y", "Vertical offset on the screen", 0.0, -150.0, 150.0, 5.0));
            }
        });

        register(new Module("Boss Bar", "Modern sleek boss health bar with percentage readout & custom styling.", Category.UI) {
            {
                setEnabled(true);
                addSetting(new BooleanSetting("Show Percentage", "Display health percentage readout", true));
                addSetting(new BooleanSetting("Show Boss Name", "Display the boss title above the bar", true));
                addSetting(new BooleanSetting("Use Client Theme", "Tint the boss bar using client accent color", true));
                addSetting(new ColorModeSetting("Color", "Custom color for the boss bar", "Neon Cyan", HitColorState.HIT_COLORS));
                addSetting(new BooleanSetting("Rounded Corners", "Use sleek modern rounded health bars", true));
                addSetting(new BooleanSetting("Show Border", "Display subtle glass outline", true));
                addSetting(new NumberSetting("Bar Width", "Width of the boss health bar", 150.0, 80.0, 300.0, 2.0));
                addSetting(new NumberSetting("Bar Height", "Height of the boss health bar", 5.0, 3.0, 14.0, 1.0));
                addSetting(new NumberSetting("Background Opacity", "Glass background opacity percentage", 70.0, 10.0, 100.0, 5.0));
                addSetting(new BooleanSetting("Text Shadow", "Render boss name and text with drop shadow", true));
            }
        });

        register(new Module("Damage Indicators", "Displays RPG-style bouncing damage numbers above hurt entities.", Category.RENDER) {
            {
                setEnabled(true);
                addSetting(new ModeSetting("Format", "Display format", "Damage & Heart", List.of("Damage & Heart", "Hearts Only", "Number Only")));
                addSetting(new BooleanSetting("Show Crits", "Highlight critical hits in gold", true));
                addSetting(new BooleanSetting("Show Self", "Show damage numbers when you take damage", true));
                addSetting(new BooleanSetting("Show Heart Icon", "Display heart symbol alongside damage", true));
                addSetting(new NumberSetting("Scale", "Text scale of floating numbers", 1.0, 0.5, 2.0, 0.1));
                addSetting(new NumberSetting("Duration", "How long indicators remain on screen (seconds)", 1.2, 0.5, 3.0, 0.1));
                addSetting(new ModeSetting("Movement", "Animation movement style", "Bounce Up", List.of("Bounce Up", "Float Up", "Static")));
            }
        });

        register(new Module("Hit Color", "Customizes the damage flash color and opacity on hit entities.", Category.RENDER) {
            {
                addSetting(new BooleanSetting("Use Client Color", "Match hit tint to Client Menu Color", false));
                addSetting(new ColorModeSetting("Tint Color", "Damage overlay color", "Crimson Red", HitColorState.HIT_COLORS));
                addSetting(new NumberSetting("Opacity", "Damage overlay opacity", 0.6, 0.1, 1.0, 0.05));
            }

            @Override
            public void onEnable() {
                HitColorState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                HitColorState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                HitColorState.setActive(false);
            }

            private void sync() {
                Setting<?> useClient = getSetting("Use Client Color");
                boolean clientCol = useClient != null && (boolean) useClient.getValue();
                int rgb = 0xFFEF4444;
                if (clientCol && StarClient.getInstance() != null) {
                    rgb = StarClient.getInstance().getClientSettings().getMenuAccentColor();
                } else if (getSetting("Tint Color") instanceof ColorModeSetting cms) {
                    rgb = cms.getColor();
                }
                double alpha = getSetting("Opacity") instanceof NumberSetting ns ? ns.getValue() : 0.6;
                HitColorState.update(rgb, alpha);
            }
        });

        register(new Module("Block Outline", "Customizes the block selection outline color and thickness.", Category.RENDER) {
            {
                BlockOutlineState.setModule(this);
                addSetting(new BooleanSetting("Use Client Color", "Match block outline to Client Menu Color", true));
                addSetting(new ColorModeSetting("Color", "Outline color when Use Client Color is off", "Sky Blue", BlockOutlineState.OUTLINE_COLORS));
                addSetting(new NumberSetting("Thickness", "Outline thickness multiplier", 2.0, 1.0, 5.0, 0.5));
                addSetting(new NumberSetting("Opacity", "Outline alpha opacity", 0.85, 0.1, 1.0, 0.05));
            }
        });

        register(new Module("No Hurt Cam", "Reduces or disables screen shake when taking damage.", Category.RENDER) {
            {
                addSetting(new NumberSetting("Hurt Shake %", "Percentage of vanilla hurt camera shake (0% = none)", 0.0, 0.0, 100.0, 5.0));
            }

            @Override
            public void onEnable() {
                NoHurtCamState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                NoHurtCamState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                NoHurtCamState.setActive(false);
            }

            private void sync() {
                double pct = getSetting("Hurt Shake %") instanceof NumberSetting ns ? ns.getValue() : 0.0;
                NoHurtCamState.setMultiplier(pct / 100.0);
            }
        });

        register(new Module("Low Fire", "Lowers the first-person fire overlay so it does not block your view.", Category.RENDER) {
            {
                addSetting(new NumberSetting("Height Offset", "How far to lower the fire overlay", -0.25, -0.5, 0.0, 0.05));
            }

            @Override
            public void onEnable() {
                LowFireState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                LowFireState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                LowFireState.setActive(false);
            }

            private void sync() {
                double offset = getSetting("Height Offset") instanceof NumberSetting ns ? ns.getValue() : -0.25;
                LowFireState.setOffset(offset);
            }
        });

        register(new Module("Low Shield", "Lowers the first-person shield model so it does not block your view.", Category.RENDER) {
            {
                addSetting(new NumberSetting("Height Offset", "How far to lower the shield (Y offset)", -0.25, -0.6, 0.0, 0.05));
                addSetting(new NumberSetting("Side Offset", "Horizontal shift (X offset)", 0.0, -0.3, 0.3, 0.05));
                addSetting(new NumberSetting("Scale", "Size scale of the first-person shield", 1.0, 0.5, 1.2, 0.05));
                addSetting(new BooleanSetting("Lower When Blocking", "Also lower the shield while actively blocking", true));
            }

            @Override
            public void onEnable() {
                LowShieldState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                LowShieldState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                LowShieldState.setActive(false);
            }

            private void sync() {
                double height = getSetting("Height Offset") instanceof NumberSetting ns ? ns.getValue() : -0.25;
                double side = getSetting("Side Offset") instanceof NumberSetting ns ? ns.getValue() : 0.0;
                double scale = getSetting("Scale") instanceof NumberSetting ns ? ns.getValue() : 1.0;
                boolean block = !(getSetting("Lower When Blocking") instanceof BooleanSetting bs) || bs.getValue();
                LowShieldState.setHeightOffset(height);
                LowShieldState.setSideOffset(side);
                LowShieldState.setScale(scale);
                LowShieldState.setLowerWhenBlocking(block);
            }
        });

        register(new Module("Combo Counter", "Displays your consecutive melee hit combo without taking damage.", Category.UI) {
            {
                addSetting(new NumberSetting("Reset Time", "Seconds without hitting before the combo resets", 4.0, 1.0, 10.0, 0.5));
            }
        });

        register(new Module("Toggle Sneak", "Tap your sneak key to toggle sneaking on or off.", Category.MOVEMENT) {
            {
                addSetting(new BooleanSetting("Unsneak in Containers", "Automatically release sneak when opening chests or inventories", true));
            }

            @Override
            public void onEnable() {
                ToggleSneakState.reset();
                ToggleSneakState.setUnsneakInContainers(isUnsneakInContainers());
            }

            @Override
            public void onTick(Minecraft client) {
                ToggleSneakState.setUnsneakInContainers(isUnsneakInContainers());
                ToggleSneakState.updateOnTick(client);
            }

            @Override
            public void onDisable() {
                ToggleSneakState.releaseSneak(mc);
            }

            private boolean isUnsneakInContainers() {
                Setting<?> s = getSetting("Unsneak in Containers");
                return s == null || (boolean) s.getValue();
            }
        });

        register(new Module("Item Physics", "Dropped items tumble in the air and lie flat on the ground.", Category.RENDER) {
            {
                addSetting(new NumberSetting("Rotation Speed", "Tumble speed of dropped items in mid-air", 2.0, 0.5, 5.0, 0.5));
            }

            @Override
            public void onEnable() {
                ItemPhysicsState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                ItemPhysicsState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                ItemPhysicsState.setActive(false);
            }

            private void sync() {
                double speed = getSetting("Rotation Speed") instanceof NumberSetting ns ? ns.getValue() : 2.0;
                ItemPhysicsState.setRotationSpeed(speed);
            }
        });

        register(new Module("Hit Particles", "Spawns extra customizable particles when hitting entities.", Category.RENDER) {
            {
                addSetting(new BooleanSetting("Always Crit", "Always spawn crit particles on every melee hit", true));
                addSetting(new ModeSetting("Particle Type", "Which particle effect to spawn on hits",
                        "Crit", List.of("Crit", "Enchanted", "Both", "Heart", "Flame")));
                addSetting(new NumberSetting("Multiplier", "Extra particle bursts per hit", 2.0, 1.0, 10.0, 1.0));
            }

            @Override
            public void onEnable() {
                HitParticlesState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                HitParticlesState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                HitParticlesState.setActive(false);
            }

            private void sync() {
                boolean alwaysCrit = !(getSetting("Always Crit") instanceof BooleanSetting bs) || bs.getValue();
                String type = getSetting("Particle Type") instanceof ModeSetting ms ? ms.getValue() : "Crit";
                int mult = getSetting("Multiplier") instanceof NumberSetting ns ? (int) Math.round(ns.getValue()) : 2;
                HitParticlesState.setAlwaysCrit(alwaysCrit);
                HitParticlesState.setParticleType(type);
                HitParticlesState.setMultiplier(mult);
            }
        });

        register(new Module("Motion Blur", "Applies a directional blur post-effect when rotating the camera.", Category.RENDER) {
            {
                addSetting(new NumberSetting("Strength", "Camera rotation blur intensity (1 = Very Subtle, 5 = Strong)", 2.0, 1.0, 5.0, 1.0));
            }

            @Override
            public void onEnable() {
                MotionBlurState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                MotionBlurState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                MotionBlurState.setActive(false);
            }

            private void sync() {
                double strength = getSetting("Strength") instanceof NumberSetting ns ? ns.getValue() : 2.0;
                MotionBlurState.setStrength(strength);
            }
        });

        register(new Module("TNT Timer", "Displays a live fuse countdown timer above primed TNT.", Category.SERVER) {
            @Override
            public void onEnable() {
                TntTimerState.setActive(true);
            }

            @Override
            public void onTick(Minecraft client) {
                TntTimerState.setActive(true);
            }

            @Override
            public void onDisable() {
                TntTimerState.setActive(false);
            }
        });

        register(new Module("Hand Customizer", "Customize first-person hand position, scale, rotation, and swing speed.", Category.RENDER) {
            {
                addSetting(new NumberSetting("Main Hand X", "Horizontal offset for main hand", 0.0, -1.0, 1.0, 0.05));
                addSetting(new NumberSetting("Main Hand Y", "Vertical offset for main hand", 0.0, -1.0, 1.0, 0.05));
                addSetting(new NumberSetting("Main Hand Z", "Depth offset for main hand", 0.0, -1.0, 1.0, 0.05));
                addSetting(new NumberSetting("Main Hand Scale", "Size multiplier for main hand", 1.0, 0.3, 2.0, 0.05));
                addSetting(new NumberSetting("Main Hand Pitch", "Pitch rotation in degrees", 0.0, -180.0, 180.0, 5.0));
                addSetting(new NumberSetting("Main Hand Yaw", "Yaw rotation in degrees", 0.0, -180.0, 180.0, 5.0));
                addSetting(new NumberSetting("Main Hand Roll", "Roll rotation in degrees", 0.0, -180.0, 180.0, 5.0));
                addSetting(new BooleanSetting("Mirror Offhand", "Automatically mirror main hand offsets and scale to offhand", true));
                addSetting(new NumberSetting("Offhand X", "Horizontal offset for offhand when Mirror Offhand is off", 0.0, -1.0, 1.0, 0.05));
                addSetting(new NumberSetting("Offhand Y", "Vertical offset for offhand when Mirror Offhand is off", 0.0, -1.0, 1.0, 0.05));
                addSetting(new NumberSetting("Offhand Z", "Depth offset for offhand when Mirror Offhand is off", 0.0, -1.0, 1.0, 0.05));
                addSetting(new NumberSetting("Offhand Scale", "Size multiplier for offhand when Mirror Offhand is off", 1.0, 0.3, 2.0, 0.05));
                addSetting(new NumberSetting("Swing Speed", "Hand attack swing animation speed multiplier (1.0 = normal)", 1.0, 0.2, 3.0, 0.1));
            }

            @Override
            public void onEnable() {
                HandCustomizerState.setActive(true);
                sync();
            }

            @Override
            public void onTick(Minecraft client) {
                HandCustomizerState.setActive(true);
                sync();
            }

            @Override
            public void onDisable() {
                HandCustomizerState.setActive(false);
            }

            private void sync() {
                double mx = num("Main Hand X", 0.0);
                double my = num("Main Hand Y", 0.0);
                double mz = num("Main Hand Z", 0.0);
                double mScale = num("Main Hand Scale", 1.0);
                double mPitch = num("Main Hand Pitch", 0.0);
                double mYaw = num("Main Hand Yaw", 0.0);
                double mRoll = num("Main Hand Roll", 0.0);
                boolean mirror = !(getSetting("Mirror Offhand") instanceof BooleanSetting bs) || bs.getValue();
                double ox = num("Offhand X", 0.0);
                double oy = num("Offhand Y", 0.0);
                double oz = num("Offhand Z", 0.0);
                double oScale = num("Offhand Scale", 1.0);
                double swing = num("Swing Speed", 1.0);
                HandCustomizerState.update(mx, my, mz, mScale, mPitch, mYaw, mRoll, mirror, ox, oy, oz, oScale, swing);
            }

            private double num(String name, double fallback) {
                return getSetting(name) instanceof NumberSetting ns ? ns.getValue() : fallback;
            }
        });

        register(new Module("Hitboxes", "Renders customizable 3D entity hitboxes, eye lines, and look vectors.", Category.RENDER) {
            {
                addSetting(new BooleanSetting("Use Client Color", "Match hitbox outlines to Client Menu Color", false));
                addSetting(new ColorModeSetting("Color", "Hitbox outline color when Use Client Color is off", "White", BlockOutlineState.OUTLINE_COLORS));
                addSetting(new BooleanSetting("Show Eye Line", "Draw red eye-height ring around living entities", true));
                addSetting(new BooleanSetting("Show Look Vector", "Draw blue view direction vector from entity eyes", true));
                addSetting(new BooleanSetting("Highlight Target", "Highlight the entity under your crosshair in gold", true));
                addSetting(new BooleanSetting("Players Only", "Only render hitboxes for players", false));
                addSetting(new BooleanSetting("Include Projectiles & Items", "Also show hitboxes on arrows, pearls, and dropped items", true));
                addSetting(new NumberSetting("Thickness", "Line thickness in pixels", 1.5, 1.0, 4.0, 0.5));
                addSetting(new NumberSetting("Opacity", "Line opacity", 0.9, 0.2, 1.0, 0.05));
                addSetting(new NumberSetting("Range", "Max render distance in blocks", 48.0, 8.0, 128.0, 4.0));
            }
        });

        register(new Module("Trajectories", "Predicts and renders 3D flight paths and landing points for bows, pearls, tridents, and potions.", Category.RENDER) {
            {
                addSetting(new BooleanSetting("Use Client Color", "Match trajectory arc to Client Menu Color", true));
                addSetting(new ColorModeSetting("Color", "Arc color when Use Client Color is off", "Sky Blue", BlockOutlineState.OUTLINE_COLORS));
                addSetting(new BooleanSetting("Preview Uncharged Bow", "Show full-charge trajectory while holding a bow or trident before drawing", true));
                addSetting(new BooleanSetting("Landing Box", "Draw a 3D box at the predicted impact point", true));
                addSetting(new BooleanSetting("Highlight Hit Entity", "Highlight entities in green when the trajectory will hit them", true));
                addSetting(new BooleanSetting("Show Distance", "Display distance and target name at the impact point", true));
                addSetting(new NumberSetting("Thickness", "Arc line thickness in pixels", 2.0, 1.0, 4.0, 0.5));
            }
        });

        register(new Module("Quick Commands", "Bind hotkeys to execute server commands (/l, /hub, /play) or chat messages.", Category.PLAYER) {
            {
                QuickCommandsState.setModule(this);
                addSetting(new MacroListSetting("Command Macros", "Manage your command & chat keybind macros"));
                addSetting(new BooleanSetting("Chat Feedback", "Show a subtle client confirmation in chat when a macro triggers", false));
            }
        });

        register(new Module("Clock Display", "Displays real-world system time on the HUD.", Category.UI) {
            {
                addSetting(new ModeSetting("Format", "12-hour AM/PM or 24-hour clock format", "24-Hour", List.of("24-Hour", "12-Hour")));
                addSetting(new BooleanSetting("Show Seconds", "Include seconds in the clock readout", true));
                addSetting(new BooleanSetting("Only Time", "Only display the time with no icon and no text", false));
                addSetting(new BooleanSetting("Show Icon", "Show clock icon next to the time", true));
                addSetting(new BooleanSetting("Show Text", "Show 'Time:' text prefix before the time", false));
            }
        });

        register(new Module("Compass Display", "Displays a directional compass ribbon or compact heading widget on the HUD.", Category.UI) {
            {
                addSetting(new ModeSetting("Style", "Top ribbon bar or compact stat card", "Ribbon", List.of("Ribbon", "Compact")));
                addSetting(new BooleanSetting("Show Degrees", "Show numeric heading badge (0° - 360°)", true));
                addSetting(new BooleanSetting("Show Intercardinals", "Show NE, SE, SW, NW markers on the ribbon", true));
                addSetting(new NumberSetting("Ribbon Width", "Width of the compass ribbon bar", 210.0, 140.0, 320.0, 10.0));
            }
        });

        register(new Module("Server Info", "Displays toggleable server IP/world, server brand, version, estimated TPS, and session time.", Category.UI) {
            {
                addSetting(new BooleanSetting("Show IP / World", "Display current server IP address or singleplayer world name", true));
                addSetting(new BooleanSetting("Show Server Brand", "Display server software/brand (e.g. Paper, Hypixel, Vanilla)", true));
                addSetting(new BooleanSetting("Show Version", "Display the Minecraft version the server is running on", true));
                addSetting(new BooleanSetting("Show TPS", "Display estimated server ticks per second (20.0 TPS)", true));
                addSetting(new BooleanSetting("Show Session Time", "Display elapsed time on the current server/session", true));
                addSetting(new BooleanSetting("Compact Mode", "Combine enabled items onto a single horizontal line", false));
            }
        });

        register(new Module("Memory Display", "Displays allocated RAM usage percentage, megabytes, usage bar, and live FPS graph.", Category.UI) {
            {
                addSetting(new BooleanSetting("Show Percentage", "Show RAM usage percentage", true));
                addSetting(new BooleanSetting("Show Megabytes", "Show used/allocated megabytes (MB)", false));
                addSetting(new BooleanSetting("Show Usage Bar", "Show a visual progress bar of memory usage", true));
                addSetting(new BooleanSetting("Show FPS Graph", "Show a live mini sparkline graph of recent FPS", false));
            }
        });

        register(new Module("Fog Customizer", "Customize or disable underwater, lava, blindness/darkness, and terrain distance fog.", Category.RENDER) {
            {
                FogCustomizerState.setModule(this);
                addSetting(new BooleanSetting("Disable Water Fog", "Remove underwater fog for crystal-clear water vision", true));
                addSetting(new BooleanSetting("Disable Lava Fog", "Remove thick orange fog when submerged in lava", true));
                addSetting(new BooleanSetting("Disable Blindness Fog", "Remove Blindness, Darkness, and Powder Snow fog", true));
                addSetting(new BooleanSetting("Disable Terrain Fog", "Completely disable distance/atmospheric terrain fog", false));
                addSetting(new NumberSetting("Fog Distance", "Multiplier for terrain fog start/end distance", 1.5, 0.2, 3.0, 0.1));
            }
        });

        register(new Module("Glint Customizer", "Customize enchantment glint speed, strength, or force/disable glint on items.", Category.RENDER) {
            {
                GlintCustomizerState.setModule(this);
                addSetting(new NumberSetting("Glint Speed", "Speed multiplier of the enchantment shimmer animation", 1.0, 0.0, 5.0, 0.1));
                addSetting(new NumberSetting("Glint Strength", "Brightness/opacity multiplier of the enchantment glint", 1.0, 0.0, 2.0, 0.05));
                addSetting(new BooleanSetting("Force Glint on All Items", "Render enchantment glint on all items even if unenchanted", false));
                addSetting(new BooleanSetting("Disable All Glint", "Completely hide enchantment glint on all items and armor", false));
            }
        });

        register(new Module("Auto Respawn", "Automatically respawns you when dying, with a configurable delay timer.", Category.PLAYER) {
            {
                AutoRespawnReconnectState.setAutoRespawnModule(this);
                addSetting(new NumberSetting("Respawn Delay", "Delay in seconds before automatically respawning", 0.0, 0.0, 10.0, 0.1));
                addSetting(new BooleanSetting("Show Countdown", "Display a countdown banner on the Death screen", true));
            }
        });

        register(new Module("Auto Reconnect", "Automatically reconnects to the last multiplayer server after being disconnected.", Category.PLAYER) {
            {
                AutoRespawnReconnectState.setAutoReconnectModule(this);
                addSetting(new NumberSetting("Reconnect Delay", "Delay in seconds before automatically reconnecting", 5.0, 1.0, 60.0, 0.5));
            }
        });

        register(new Module("Performance", "Boosts FPS with entity/block-entity distance culling, particle limiters, sign text culling, and dynamic unfocused FPS.", Category.RENDER) {
            {
                PerformanceState.setModule(this);
                // 1. Entity Distance Culling
                addSetting(new BooleanSetting("Cull Distant Entities", "Skip rendering entities beyond Max Entity Distance", true));
                addSetting(new NumberSetting("Max Entity Distance", "Maximum distance in blocks to render general entities", 64.0, 16.0, 128.0, 4.0));
                addSetting(new BooleanSetting("Cull Distant Players", "Also apply Max Entity Distance culling to other players", false));

                // 2. Armor Stand & Item Frame Culling
                addSetting(new BooleanSetting("Cull Armor Stands", "Cull distant armor stands in lobbies and bases", true));
                addSetting(new NumberSetting("Armor Stand Distance", "Maximum distance in blocks to render armor stands", 32.0, 8.0, 64.0, 4.0));
                addSetting(new BooleanSetting("Keep Named Holograms", "Always render armor stands with custom names (server holograms)", true));
                addSetting(new BooleanSetting("Cull Item Frames", "Cull distant item frames and glow item frames", true));
                addSetting(new NumberSetting("Item Frame Distance", "Maximum distance in blocks to render item frames", 24.0, 8.0, 64.0, 4.0));

                // 3. Dropped Item & XP Orb Culling
                addSetting(new BooleanSetting("Cull Dropped Items", "Cull distant dropped items and XP orbs on the ground", true));
                addSetting(new NumberSetting("Dropped Item Distance", "Maximum distance in blocks to render dropped items and XP orbs", 32.0, 8.0, 64.0, 4.0));

                // 4. Block Entity & Sign Text Culling
                addSetting(new BooleanSetting("Cull Block Entities", "Skip rendering distant chests, skulls, signs, banners, and shulkers", true));
                addSetting(new NumberSetting("Max Block Entity Distance", "Maximum distance in blocks to render block entities", 48.0, 16.0, 96.0, 4.0));
                addSetting(new BooleanSetting("Hide Distant Sign Text", "Skip expensive text layout on signs beyond Sign Text Distance", true));
                addSetting(new NumberSetting("Sign Text Distance", "Maximum distance in blocks to render text on signs", 16.0, 8.0, 48.0, 2.0));

                // 5. Particle Limiter & Selective Toggles
                addSetting(new NumberSetting("Particle Reduction", "Percentage of newly spawned particles to skip (0% - 100%)", 25.0, 0.0, 100.0, 5.0));
                addSetting(new BooleanSetting("Disable Explosion Particles", "Suppress TNT, crystal, and creeper explosion particles", false));
                addSetting(new BooleanSetting("Disable Firework Particles", "Suppress firework rocket spark and flash particles", false));
                addSetting(new BooleanSetting("Disable Block Break Particles", "Suppress block breaking and crumbling debris particles", false));
                addSetting(new BooleanSetting("Disable Potion Particles", "Suppress swirling potion status effect particles", false));
                addSetting(new BooleanSetting("Disable Weather Particles", "Suppress rain splash and dripping water particles", true));

                // 6. World Detail Toggles
                addSetting(new BooleanSetting("Disable Beacon Beams", "Hide vertical beacon light beams", false));
                addSetting(new BooleanSetting("Disable Weather Rendering", "Hide falling rain and snow weather sheets", false));
                addSetting(new BooleanSetting("Disable Clouds", "Skip 3D/2D cloud layer rendering", false));

                // 7. Dynamic FPS (Unfocused Window Limiter)
                addSetting(new BooleanSetting("Limit Unfocused FPS", "Throttle FPS when the Minecraft window is unfocused / alt-tabbed", true));
                addSetting(new NumberSetting("Unfocused Max FPS", "Maximum FPS while the game window is unfocused", 15.0, 5.0, 60.0, 5.0));
            }
        });

        register(new Module("Waypoints", "Custom 3D in-world waypoints per server/world with beacon beams, auto death markers, Nether 8:1 sync, and an inline overview editor.", Category.WORLD) {
            {
                WaypointsState.setWaypointsModule(this);
                addSetting(new BooleanSetting("Show Beam", "Render vertical beacon beams at waypoint locations", true));
                addSetting(new BooleanSetting("Show Distance", "Display distance in meters next to waypoint labels", true));
                addSetting(new BooleanSetting("Auto Death Waypoint", "Automatically create or update a Last Death waypoint upon dying", true));
                addSetting(new BooleanSetting("Cross-Dimension Sync (8:1)", "Project Overworld waypoints into the Nether (÷8) and vice versa (×8)", false));
                addSetting(new NumberSetting("Scale", "Size multiplier of 3D in-world waypoint tags", 1.0, 0.5, 2.5, 0.1));
                addSetting(new NumberSetting("Max Distance", "Maximum render distance in blocks for 3D waypoints", 5000.0, 100.0, 30000.0, 100.0));
                addSetting(new ModeSetting("Quick Add Action", "Action triggered by the Quick-Add Waypoint hotkey", "Open Creator",
                        List.of("Open Creator", "Instant Save")));
                addSetting(new WaypointListSetting("Waypoints List", "Overview list to toggle, edit, or delete waypoints for this server/world"));
            }
        });

        register(new Module("Minimap", "Top-down HUD minimap with terrain shading, Square/Circle modes, entity radar toggles, and Waypoint pins.", Category.UI) {
            {
                addSetting(new ModeSetting("Shape", "Minimap frame shape", "Square", List.of("Square", "Circle")));
                addSetting(new NumberSetting("Size", "Minimap size on screen in pixels", 96.0, 64.0, 160.0, 4.0));
                addSetting(new NumberSetting("Zoom", "Terrain zoom level", 1.0, 0.5, 4.0, 0.25));
                addSetting(new BooleanSetting("Rotate with Player", "Rotate map with your camera yaw instead of locking North-Up", true));
                addSetting(new BooleanSetting("Show Waypoints", "Render active Waypoints as pins on the minimap", true));
                addSetting(new BooleanSetting("Show Players", "Display nearby players on the minimap radar", true));
                addSetting(new BooleanSetting("Show Hostile Mobs", "Display nearby hostile monsters on the minimap radar", true));
                addSetting(new BooleanSetting("Show Passive Mobs", "Display nearby passive animals & mobs on the minimap radar", false));
                addSetting(new BooleanSetting("Show Items", "Display nearby dropped item stacks on the minimap radar", false));
                addSetting(new BooleanSetting("Show Cardinals", "Display N / E / S / W cardinal markers around the border", true));
                addSetting(new BooleanSetting("Show Coordinates", "Display compact X, Y, Z readout under the minimap", true));
            }
        });

        register(new Module("World Info", "Displays current Biome, In-Game Day count, Light Level (with mob-spawn warning), Chunk coordinates, and World Seed.", Category.UI) {
            {
                addSetting(new BooleanSetting("Show Biome", "Display the current biome name", true));
                addSetting(new BooleanSetting("Show Day", "Display the current world day counter", true));
                addSetting(new BooleanSetting("Show Light Level", "Display block/sky light level with mob-spawn warning color", true));
                addSetting(new BooleanSetting("Show Chunk", "Display chunk coordinates and intra-chunk position", false));
                addSetting(new BooleanSetting("Show Seed", "Display the world seed", false));
                addSetting(new BooleanSetting("Compact Mode", "Combine enabled lines into a single horizontal bar", false));
            }
        });

        register(new Module("Item Counter", "Displays live total counts of Totems, Gapples, Pearls, Arrows, Potions, Crystals, and Blocks across your inventory.", Category.UI) {
            {
                addSetting(new ModeSetting("Orientation", "Widget item layout orientation", "Horizontal", List.of("Horizontal", "Vertical")));
                addSetting(new BooleanSetting("Hide Zero", "Hide tracked items when you currently have 0 in your inventory", true));
                addSetting(new BooleanSetting("Totems", "Count Totems of Undying", true));
                addSetting(new BooleanSetting("Golden Apples", "Count regular Golden Apples", true));
                addSetting(new BooleanSetting("Enchanted Gapples", "Count Enchanted Golden Apples", false));
                addSetting(new BooleanSetting("Ender Pearls", "Count Ender Pearls", true));
                addSetting(new BooleanSetting("Arrows", "Count all arrow types", true));
                addSetting(new BooleanSetting("Healing Potions", "Count Instant Health & Regeneration potions", false));
                addSetting(new BooleanSetting("XP Bottles", "Count Bottles o' Enchanting", false));
                addSetting(new BooleanSetting("End Crystals", "Count End Crystals", false));
                addSetting(new BooleanSetting("Building Blocks", "Count placeable building blocks", false));
            }
        });

        register(new Module("Chat", "Upgrades chat with Timestamps, Compact anti-spam stacking, Click-to-Copy, Cross-Server History, Banned Words Filter, and Mention Highlights.", Category.UI) {
            {
                ChatPlusState.setModule(this);
                addSetting(new BooleanSetting("Timestamps", "Prepend local clock timestamps to incoming chat lines", true));
                addSetting(new ModeSetting("Time Format", "Timestamp clock format", "24h (HH:mm)",
                        List.of("24h (HH:mm)", "24h (HH:mm:ss)", "12h (hh:mm a)")));
                addSetting(new BooleanSetting("Compact Chat", "Stack consecutive duplicate chat messages with [x2], [x3] counters", true));
                addSetting(new BooleanSetting("Click to Copy", "Click chat messages to copy their text to the clipboard", true));
                addSetting(new BooleanSetting("Copy Server & Mod Info", "Also allow copying server announcements, system info, and mod chat messages", true));
                addSetting(new BooleanSetting("Save Chat History", "Keep chat messages across servers and worlds with a server name divider", true));
                addSetting(new NumberSetting("Extended History", "Maximum number of chat messages kept in scrollback history", 1000.0, 100.0, 5000.0, 100.0));
                addSetting(new BooleanSetting("Word Filter", "Censor banned words in chat by replacing each letter with '*'", true));
                addSetting(new BannedWordListSetting("Banned Words", "List of banned words or phrases to censor in chat"));
                addSetting(new BooleanSetting("Mention Highlight", "Color your username in chat when someone mentions you", true));
                addSetting(new ColorModeSetting("Mention Color", "Color applied to your username when someone mentions you in chat", "Yellow", ChatPlusState.MENTION_COLORS));
                addSetting(new BooleanSetting("Mention Sound", "Play a subtle ping sound when your username is mentioned", true));
            }
        });

        register(new Module("Scrollable Tooltips", "Scroll tall or wide item tooltips vertically and horizontally using the mouse wheel or arrow keys.", Category.UI) {
            {
                TooltipFeaturesState.setScrollableTooltipsModule(this);
                addSetting(new BooleanSetting("Vertical Scroll", "Allow scrolling tooltips up and down (Mouse Wheel or Up/Down Arrows)", true));
                addSetting(new BooleanSetting("Horizontal Scroll", "Allow scrolling tooltips left and right (Shift + Wheel or Left/Right Arrows)", true));
                addSetting(new NumberSetting("Scroll Speed", "Pixels scrolled per wheel notch or arrow press", 14.0, 4.0, 40.0, 2.0));
                addSetting(new BooleanSetting("Only When Overflowing", "Only scroll tooltips that exceed the screen bounds", false));
                addSetting(new BooleanSetting("Reset on New Item", "Reset tooltip scroll offset when hovering a different slot", true));
            }
        });

        register(new Module("Screenshots", "Async lag-free F2 screenshots with automatic Windows clipboard copy and clickable [Open] [Folder] [Copy] chat buttons.", Category.PLAYER) {
            {
                BetterScreenshotsState.setModule(this);
                addSetting(new BooleanSetting("Auto Copy to Clipboard", "Automatically copy every F2 screenshot directly to the clipboard", true));
                addSetting(new BooleanSetting("Chat Action Buttons", "Append clickable [Open], [Folder], and [Copy] buttons in chat", true));
            }
        });

        register(new Module("Container Preview", "Renders an inline visual preview card when hovering Shulker Boxes, Ender Chests, or Filled Maps.", Category.PLAYER) {
            {
                TooltipFeaturesState.setContainerPreviewModule(this);
                addSetting(new ModeSetting("Display Mode", "Show preview automatically or only while holding a key", "Always",
                        List.of("Always", "Hold Key")));
                addSetting(new ModeSetting("Preview Key", "Modifier key required when Display Mode is set to Hold Key", "Shift",
                        List.of("Shift", "Ctrl", "Alt")));
                addSetting(new BooleanSetting("Shulker Boxes", "Show 3×9 item grid preview for Shulker Boxes", true));
                addSetting(new BooleanSetting("Filled Maps", "Show rendered map canvas preview for Filled Maps", true));
                addSetting(new BooleanSetting("Ender Chest", "Show last-known contents when hovering an Ender Chest item", true));
            }
        });

        register(new Module("Auto GG", "Automatically sends customizable GG / GLHF messages at match end or start on multiplayer servers.", Category.SERVER) {
            {
                AutoGgState.setModule(this);
                addSetting(new TextSetting("GG Message", "Chat message sent when a match ends", "gg"));
                addSetting(new NumberSetting("GG Delay", "Delay in seconds before sending GG", 1.0, 0.0, 5.0, 0.1));
                addSetting(new BooleanSetting("Auto GLHF", "Also send a message automatically when a match starts", false));
                addSetting(new TextSetting("GLHF Message", "Chat message sent when a match starts", "glhf"));
                addSetting(new NumberSetting("GLHF Delay", "Delay in seconds before sending GLHF", 1.0, 0.0, 5.0, 0.1));
                addSetting(new NumberSetting("Cooldown", "Minimum seconds between automatic triggers", 8.0, 3.0, 30.0, 1.0));
            }
        });

        register(new Module("Nametags", "Enhances player nametags with Health & Ping readouts, 3rd-person own nametag toggle, custom scale, and transparent backdrop.", Category.RENDER) {
            {
                NametagsPlusState.setModule(this);
                addSetting(new BooleanSetting("Own Nametag (3rd Person)", "Display your own nametag above your head in 3rd-person view", true));
                addSetting(new BooleanSetting("Show Health", "Append live health hearts readout next to player nametags", true));
                addSetting(new BooleanSetting("Show Ping", "Append network latency in ms next to player nametags", true));
                addSetting(new NumberSetting("Scale", "Nametag size multiplier", 1.0, 0.5, 2.5, 0.1));
                addSetting(new BooleanSetting("Distance Scale", "Automatically scale up distant nametags so they stay readable", false));
            }
        });

        register(new Module("Block Info", "Displays a WAILA HUD card for the targeted block: 3D icon, harvestability, optimal tool, live break timer & progress bar, and crop/redstone states.", Category.UI) {
            {
                addSetting(new BooleanSetting("Show Icon", "Display the 3D block item icon next to the block name", true));
                addSetting(new BooleanSetting("Show Harvestability", "Show whether your held tool can harvest drops from this block", true));
                addSetting(new BooleanSetting("Show Optimal Tool", "Display the optimal tool type and minimum tier used to break this block", true));
                addSetting(new BooleanSetting("Show Break Time", "Display estimated mining duration and live breaking percentage", true));
                addSetting(new BooleanSetting("Show Block Details", "Show crop growth %, redstone power, honey level, repeater delay, and cauldron fill", true));
                addSetting(new BooleanSetting("Show Coordinates", "Display the targeted block's X, Y, Z coordinates", false));
                addSetting(new BooleanSetting("Include Fluids", "Also inspect Water and Lava source/flowing blocks", false));
                addSetting(new NumberSetting("Max Distance", "Maximum raycast reach distance in blocks", 5.5, 3.0, 20.0, 0.5));
            }
        });

        register(new Module("Hunger & Saturation", "AppleSkin-style golden saturation overlay, pulsing held-food restoration preview, exhaustion bar, and food tooltip stats.", Category.UI) {
            {
                HungerSaturationState.setModule(this);
                addSetting(new BooleanSetting("Show Saturation", "Render golden saturation outlines over saturated hunger shanks", true));
                addSetting(new BooleanSetting("Show Food Preview", "Flash a preview of restored hunger & saturation when holding edible food", true));
                addSetting(new BooleanSetting("Show Exhaustion Bar", "Render a subtle exhaustion progress bar behind the hunger bar", true));
                addSetting(new BooleanSetting("Food Tooltip Info", "Append hunger and saturation values to food item tooltips", true));
            }
        });

        register(new Module("Chat Calculator", "Evaluates live math expressions (= 27*64, = (1200+350)/8, 27*64=) and Nether/Overworld coord helpers (=nether, =ow) in chat with Tab-to-insert.", Category.UI) {
            {
                ChatCalculatorState.setModule(this);
                addSetting(new BooleanSetting("Math Expressions", "Evaluate math expressions starting or ending with '=' in chat", true));
                addSetting(new BooleanSetting("Show Item Stacks", "Show Minecraft 64-stack and Shulker Box breakdown next to whole results", true));
                addSetting(new BooleanSetting("Coord Converter", "Support '=nether [x z]' and '=ow [x z]' 8:1 coordinate helpers", true));
                addSetting(new BooleanSetting("Tab to Insert", "Press Tab to replace the expression in chat with the computed answer", true));
            }
        });

        register(new Module("Pinned Notes", "Draggable HUD checklist and sticky notes card for coordinates, build material goals, and reminders.", Category.UI) {
            {
                PinnedNotesState.setModule(this);
                addSetting(new TextSetting("Title", "Custom header title displayed at the top of the Pinned Notes card", "Pinned Notes"));
                addSetting(new NoteListSetting("Notes List", "Manage your pinned notes and to-do checklist items"));
                addSetting(new BooleanSetting("Show Checkboxes", "Display interactive completion status boxes next to each note", true));
                addSetting(new BooleanSetting("Show Completed", "Keep completed notes visible with strikethrough instead of hiding them", true));
                addSetting(new BooleanSetting("Hide When Empty", "Hide the HUD card during gameplay when no notes are pinned", false));
            }
        });

        register(new Module("Light Overlay", "Renders a 3D mob-spawn grid on nearby surfaces: Red = spawns anytime, Yellow = spawns at night.", Category.WORLD) {
            {
                addSetting(new ModeSetting("Style", "3D ground marker shape", "Cross", List.of("Cross", "Square", "Diamond")));
                addSetting(new NumberSetting("Range", "Horizontal scan radius around the player in blocks", 16.0, 6.0, 32.0, 2.0));
                addSetting(new BooleanSetting("Show Night Spawns", "Also render yellow markers where sky light prevents daytime spawns", true));
                addSetting(new NumberSetting("Max Safe Block Light", "Maximum block light at which hostile mobs can spawn (0 in vanilla 1.18+)", 0.0, 0.0, 7.0, 1.0));
                addSetting(new NumberSetting("Line Thickness", "3D marker line thickness in pixels", 1.5, 1.0, 3.5, 0.5));
            }

            @Override
            public void onDisable() {
                LightOverlayRenderer.clear();
            }
        });

        register(new Module("Chunk Borders", "Renders clean customizable 16x16 chunk borders, sub-chunk section rings, neighbor corners, and Slime Chunk highlights.", Category.WORLD) {
            {
                addSetting(new BooleanSetting("Use Client Color", "Match primary chunk border color to Client Menu Color", false));
                addSetting(new ColorModeSetting("Border Color", "Primary chunk corner/wall color when Use Client Color is off", "Yellow", BlockOutlineState.OUTLINE_COLORS));
                addSetting(new BooleanSetting("Show Sub-Chunks", "Draw horizontal 16x16 section rings every 16 Y levels", true));
                addSetting(new BooleanSetting("Show Wall Grid", "Draw vertical 4-block grid lines along the 4 chunk walls", true));
                addSetting(new BooleanSetting("Show Neighbor Corners", "Draw surrounding neighbor chunk corner pillars", true));
                addSetting(new BooleanSetting("Highlight Slime Chunks", "Highlight the chunk floor in green when standing inside an Overworld Slime Chunk (when seed is known)", true));
                addSetting(new NumberSetting("Height Range", "Vertical render height above and below your Y position", 32.0, 16.0, 96.0, 16.0));
                addSetting(new NumberSetting("Line Thickness", "3D wireframe line thickness in pixels", 1.5, 1.0, 4.0, 0.5));
            }
        });
    }

    private void register(Module module) {
        module.markDefaultEnabled(module.isEnabled());
        modules.add(module);
        if (module.getName() != null) {
            modulesByName.put(module.getName().toLowerCase(Locale.ROOT), module);
        }
        activeModulesDirty = true;
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getSortedActiveModules() {
        if (activeModulesDirty) {
            cachedSortedActiveModules = modules.stream()
                    .filter(Module::isEnabled)
                    .sorted(Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT)))
                    .toList();
            activeModulesDirty = false;
        }
        return cachedSortedActiveModules;
    }

    public List<Module> getModulesByCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }

    public Module getModuleByName(String name) {
        if (name == null) {
            return null;
        }
        Module cached = modulesByName.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return cached;
        }
        return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public void onTick(Minecraft client) {
        CombatStatsTracker.updateOnTick(client.player);
        for (Module module : modules) {
            if (module.isEnabled()) {
                if (!module.canUse()) {
                    module.setEnabled(false);
                    continue;
                }
                module.onTick(client);
            }
        }
    }

    public void handleKeybinds(Minecraft client) {
        for (Module module : modules) {
            if (!module.canUse()) {
                if (module.isEnabled()) {
                    module.setEnabled(false);
                }
                module.setKeyDownLastTick(false);
                continue;
            }
            int key = module.getKeyBind();
            boolean down = false;
            if (key != GLFW.GLFW_KEY_UNKNOWN) {
                down = InputConstants.isKeyDown(client.getWindow(), key);
            }
            boolean pressed = down && !module.isKeyDownLastTick();
            if (module.isHoldKey()) {
                module.setEnabled(down);
            } else if (pressed && module.isKeyBindToggleable()) {
                boolean wasEnabled = module.isEnabled();
                module.toggle();
                if (module.isEnabled() != wasEnabled) {
                    ToastNotificationManager.pushModuleToggle(module);
                }
            } else if (pressed) {
                // Action modules decide for themselves whether they can run, so a disabled one
                // gets the chance to tell the user instead of silently eating the keypress.
                module.onKeyPress(client);
            }
            module.setKeyDownLastTick(down);
        }
        QuickCommandsState.handleKeybinds(client);
        if (StarClient.getInstance() != null && StarClient.getInstance().getProfileManager() != null) {
            StarClient.getInstance().getProfileManager().handleHotkeys(client);
        }
    }
}

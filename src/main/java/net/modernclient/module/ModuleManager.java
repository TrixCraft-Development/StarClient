package net.modernclient.module;

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
import net.modernclient.api.HypixelApi;
import net.modernclient.gui.EntityEsp;
import net.modernclient.gui.HypixelLevelHead;
import net.modernclient.gui.HypixelPlayerStats;
import net.modernclient.mixin.FishingHookAccessor;
import net.modernclient.mixin.MinecraftAccessor;
import net.modernclient.mixin.MultiPlayerGameModeAccessor;
import net.modernclient.setting.BooleanSetting;
import net.modernclient.setting.ColorModeSetting;
import net.modernclient.setting.ModeSetting;
import net.modernclient.setting.NickEntryListSetting;
import net.modernclient.setting.NumberSetting;
import net.modernclient.setting.Setting;
import net.modernclient.setting.TextSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

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

        register(new Module("Armor HUD", "Displays held items & equipped armor durability.", Category.PLAYER) {
            {
                setEnabled(false);
                addSetting(new ModeSetting("Orientation", "HUD layout alignment", "Vertical", List.of("Horizontal", "Vertical")));
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

        register(new Module("Zoom", "Hold to zoom in; scroll wheel adjusts the zoom level.", Category.UI, GLFW.GLFW_KEY_C) {
            private double previousFov = 70.0;
            private boolean previousSmoothCamera = false;

            private static final double DEFAULT_ZOOM_FOV = 50.0;

            {
                ZoomState.setZoomModule(this);
                addSetting(new NumberSetting("FOV", "Zoomed-in field of view", DEFAULT_ZOOM_FOV, 20.0, 110.0, 1.0));
                addSetting(new BooleanSetting("Smooth Camera", "Enable cinematic smooth camera while zoomed", true));
            }

            @Override
            public boolean isHoldKey() {
                return true;
            }

            @Override
            public void onEnable() {
                previousFov = mc.options.fov().get();
                previousSmoothCamera = mc.options.smoothCamera;
                Setting<?> setting = getSetting("FOV");
                if (setting instanceof NumberSetting number) {
                    number.setValue(DEFAULT_ZOOM_FOV);
                }
                applyZoom();
            }

            @Override
            public void onTick(Minecraft client) {
                applyZoom();
            }

            @Override
            public void onDisable() {
                mc.options.fov().set((int) previousFov);
                mc.options.smoothCamera = previousSmoothCamera;
            }

            @Override
            public void onScroll(double amount) {
                NumberSetting setting = (NumberSetting) getSetting("FOV");
                if (setting == null) return;
                setting.setValue(setting.getValue() - amount);
                applyZoom();
            }

            private void applyZoom() {
                mc.options.fov().set((int) Math.clamp(getFov(), 30.0, 110.0));
                mc.options.smoothCamera = isSmoothCamera();
            }

            private double getFov() {
                Setting<?> setting = getSetting("FOV");
                return setting == null ? 30.0 : (double) setting.getValue();
            }

            private boolean isSmoothCamera() {
                Setting<?> setting = getSetting("Smooth Camera");
                return setting == null || (boolean) setting.getValue();
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
                if (holdingUse && Minecraft.getInstance().options != null) {
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
                    debug(client, "no target resolved (mode=" + getString("Target", "Crosshair") + ")");
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

                String mode = getString("Target", "Crosshair");
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

            private String getString(String name, String fallback) {
                Setting<?> setting = getSetting(name);
                return setting == null ? fallback : (String) setting.getValue();
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
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }

    public Module getModuleByName(String name) {
        return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public void onTick(Minecraft client) {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick(client);
            }
        }
    }

    public void handleKeybinds(Minecraft client) {
        for (Module module : modules) {
            int key = module.getKeyBind();
            boolean down = false;
            if (key != GLFW.GLFW_KEY_UNKNOWN) {
                down = InputConstants.isKeyDown(client.getWindow(), key);
            }
            boolean pressed = down && !module.isKeyDownLastTick();
            if (module.isHoldKey()) {
                module.setEnabled(down);
            } else if (pressed && module.isKeyBindToggleable()) {
                module.toggle();
            } else if (pressed) {
                // Action modules decide for themselves whether they can run, so a disabled one
                // gets the chance to tell the user instead of silently eating the keypress.
                module.onKeyPress(client);
            }
            module.setKeyDownLastTick(down);
        }
    }
}

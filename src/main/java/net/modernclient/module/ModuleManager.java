package net.modernclient.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.modernclient.gui.EntityEsp;
import net.modernclient.mixin.MinecraftAccessor;
import net.modernclient.mixin.MultiPlayerGameModeAccessor;
import net.modernclient.setting.BooleanSetting;
import net.modernclient.setting.ModeSetting;
import net.modernclient.setting.NumberSetting;
import net.modernclient.setting.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

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

            {
                ZoomState.setZoomModule(this);
                addSetting(new NumberSetting("FOV", "Zoomed-in field of view", 30.0, 1.0, 70.0, 1.0));
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
                mc.options.fov().set((int) getFov());
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
                        cooldownTicks = (int) Math.round(getWeaponCooldownSeconds(client.player.getMainHandItem()) * 20.0);
                    }
                }
            }

            @Override
            public void onDisable() {
                cooldownTicks = 0;
            }

            private static double getWeaponCooldownSeconds(ItemStack stack) {
                if (stack.isEmpty()) return 0.25;
                Item item = stack.getItem();
                if (item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD
                        || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD
                        || item == Items.COPPER_SWORD) {
                    return 0.63;
                }
                if (item == Items.WOODEN_SHOVEL || item == Items.STONE_SHOVEL || item == Items.IRON_SHOVEL
                        || item == Items.GOLDEN_SHOVEL || item == Items.DIAMOND_SHOVEL || item == Items.NETHERITE_SHOVEL
                        || item == Items.COPPER_SHOVEL) {
                    return 1.00;
                }
                if (item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE
                        || item == Items.GOLDEN_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE
                        || item == Items.COPPER_PICKAXE) {
                    return 0.83;
                }
                if (item == Items.WOODEN_AXE || item == Items.GOLDEN_AXE) return 1.00;
                if (item == Items.IRON_AXE || item == Items.COPPER_AXE) return 1.11;
                if (item == Items.STONE_AXE || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE) return 1.25;
                if (item == Items.WOODEN_HOE || item == Items.STONE_HOE) return 0.50;
                return 0.25;
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
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (entity == client.player || !entity.isAlive()) continue;
                    boolean isPlayer = entity instanceof Player;
                    boolean isMob = entity instanceof Mob;
                    boolean match = switch (targets) {
                        case "Players Only" -> isPlayer;
                        case "Mobs Only" -> isMob;
                        default -> isPlayer || isMob;
                    };
                    if (!match) continue;
                    assert client.player != null;
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
            if (module.isHoldKey()) {
                module.setEnabled(down);
            } else if (down && !module.isKeyDownLastTick()) {
                module.toggle();
            }
            module.setKeyDownLastTick(down);
        }
    }
}

package cn.unfair.module.modules.combat;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.HitBlockEvent;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.RightClickMouseEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.combat.blockhit.AmunixBlockHit;
import cn.unfair.property.properties.*;
import cn.unfair.util.client.TimerUtil;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class BlockHit extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public BlockHit() {
        super("BlockHit", false, false);
    }

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Helper", "Auto", "Lag", "Predict"});
    public final BooleanProperty smartUnblock = new BooleanProperty("Smart Unblock", true, () -> mode.getValue() == 2);
    public final IntProperty lagDuration = new IntProperty("Lag Duration", 200, 50, 1000, () -> mode.getValue() == 2);
    public final IntProperty minLagDuration = new IntProperty("Minimum Lag Duration", 100, 0, 1000, () -> mode.getValue() == 2);
    public final PercentProperty lagChance = new PercentProperty("Lag Chance", 100, () -> mode.getValue() == 2);
    public final IntProperty lagHoldTime = new IntProperty("Lag Hold Ticks", 1, 1, 10, () -> mode.getValue() == 2);
    public final FloatProperty predictRange = new FloatProperty("Predict Range", 4.0F, 1.0F, 6.0F, () -> mode.getValue() == 3);
    public final BooleanProperty lobbyCheck = new BooleanProperty("Lobby Check", true, () -> mode.getValue() == 3);
    public final IntProperty maxHold = new IntProperty("Maximum Block ms", 200, 150, 500, () -> mode.getValue() == 3);
    public final IntProperty unblockTime = new IntProperty("Unblock ms", 200, 50, 350, () -> mode.getValue() == 3);
    public final IntProperty unblockDelay = new IntProperty("Unblock Delay ms", 0, 0, 100, () -> mode.getValue() == 3);
    public final FloatProperty firstMeleeRange = new FloatProperty("First Melee Range", 3.5F, 2.0F, 5.0F, () -> mode.getValue() == 3);
    private final AmunixBlockHit amunix = new AmunixBlockHit(this);

    private final IntProperty stopTime = new IntProperty("Stop Ticks", 2, 1, 5, () -> mode.getValue() == 0);
    private final ModeProperty autoBlockTime = new ModeProperty("AutoBlock Time", 0, new String[]{"Delay", "HurtTime", "Sag", "Smart"}, () -> mode.getValue() == 1);
    private final ModeProperty autoMode = new ModeProperty("Auto Mode", 0, new String[]{"Spam", "Hold"}, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 0);
    private final BooleanProperty onFirstHit = new BooleanProperty("OnFirstHit", true, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 3);
    private final IntProperty smartBlockTick = new IntProperty("Smart Block Ticks", 2, 1, 5, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 3);
    private final BooleanProperty releaseAfterHit = new BooleanProperty("Release After Hit", true, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 3);
    private final IntProperty smartBlockHurtTime = new IntProperty("Smart Block HurtTime", 2, 0, 10, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 3);
    private final IntProperty blockDelay = new IntProperty("Block Delay", 100, 0, 1000, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 0);
    private final IntProperty holdTick = new IntProperty("Hold Ticks", 2, 2, 5, () -> mode.getValue() == 1 && autoMode.getValue() == 1 && autoBlockTime.getValue() == 0);
    private final IntProperty minHurtTime = new IntProperty("Min HurtTime", 10, 1, 10, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 1);
    private final IntProperty maxHurtTime = new IntProperty("Max HurtTime", 10, 1, 10, () -> mode.getValue() == 1 && autoBlockTime.getValue() == 1);
    private final PercentProperty chance = new PercentProperty("Block Hit Chance", 50, () -> mode.getValue() == 1);
    private final BooleanProperty smart = new BooleanProperty("Smart", true, () -> mode.getValue() == 1);
    private final BooleanProperty autoBlockRange = new BooleanProperty("AutoBlock Range", true, () -> mode.getValue() == 1);
    private final FloatProperty range = new FloatProperty("Range", 3.0F, 1.0F, 4.0F, () -> autoBlockRange.getValue() && mode.getValue() == 1);
    private int holdTicks;
    private int stopTick;
    private boolean startBlocking;
    private boolean attacking;
    private int attackTicks;
    private int sagTicks;
    private boolean canBlock;
    private int getBlockTicks;
    private EntityLivingBase target;
    private TimerUtil timer = new TimerUtil();

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (mode.getValue() >= 2) {
            if (event.type() == EventType.PRE) amunix.tick();
            return;
        }
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.type() == EventType.PRE) {
            if (this.mode.getValue() == 0) {
                if (mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (mc.thePlayer.isBlocking()) {
                        startBlocking = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    }
                }
                if (startBlocking) stopTick++;
                if (stopTick == 2) {
                    KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (stopTick > stopTime.getValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    startBlocking = false;
                    stopTick = 0;
                }
            }
            if (this.mode.getValue() == 1) {
                if (target == null) return;
                if (attacking) attackTicks++;
                if (attackTicks > 10) {
                    reset();
                    target = null;
                    return;
                }
                if (Math.random() * 100.0 > chance.getValue()) {
                    reset();
                    return;
                }
                if (autoBlockRange.getValue() && distance(target) >= range.getValue()) {
                    reset();
                    return;
                }
                if (smart.getValue() && target.hurtTime == 0) {
                    reset();
                    return;
                }
                if (attacking && ItemUtil.isHoldingSword()) {
                    if (autoBlockTime.getValue() == 0) {
                        if (timer.hasTimeElapsed(blockDelay.getValue().longValue())) {
                            if (this.autoMode.getValue() == 0) {
                                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                                timer.reset();
                                //reset();
                            }
                            if (this.autoMode.getValue() == 1) startBlocking = true;
                            if (startBlocking) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                                holdTicks++;
                            }
                            if (holdTicks > holdTick.getValue()) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                                startBlocking = false;
                                holdTicks = 0;
                                timer.reset();
                            }
                        }
                    }
                    if (autoBlockTime.getValue() == 1) {
                        if (mc.thePlayer.hurtTime >= minHurtTime.getValue() && mc.thePlayer.hurtTime <= maxHurtTime.getValue()) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            startBlocking = true;
                        } else if (startBlocking) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            startBlocking = false;
                        }
                    }
                    if (autoBlockTime.getValue() == 2) {
                        if (sagTicks < 10) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            sagTicks++;
                        }
                        if (sagTicks >= 10) {
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            sagTicks = 0;
                        }
                    }
                    if (autoBlockTime.getValue() == 3) {
                        if (mc.thePlayer.hurtTime == smartBlockHurtTime.getValue()) canBlock = true;
                        if (canBlock) {
                            getBlockTicks++;
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                        }
                        if (mc.thePlayer.hurtTime == 9 && releaseAfterHit.getValue()) {
                            canBlock = false;
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            getBlockTicks = 0;
                        }
                        if (getBlockTicks > smartBlockTick.getValue()) {
                            canBlock = false;
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            getBlockTicks = 0;
                        }
                    }
                }
            }
        }
    }

    private double distance(EntityLivingBase entity) {
        // Distance from player to target bounding box, used for Auto mode range check.
        return RotationUtil.distanceToBox(target.getCollisionBoundingBox());
    }

    private void reset() {
        attacking = canBlock = false;
        if (mc.gameSettings != null) KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        holdTicks = sagTicks = getBlockTicks = 0;
        timer.reset();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mode.getValue() >= 2) return;
        if (this.isEnabled() && ItemUtil.isHoldingSword() && event.getTarget() instanceof EntityLivingBase) {
            attacking = true;
            attackTicks = 0;
            target = (EntityLivingBase) event.getTarget();
            if (autoBlockTime.getValue() == 3 && mc.thePlayer.hurtTime == 0 && onFirstHit.getValue()) canBlock = true;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getValue() == 2 ? "Lag " + lagDuration.getValue() + "ms" : mode.getModeString()};
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE && mode.getValue() >= 2) amunix.update();
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (mode.getValue() >= 2 && event.getType() == EventType.SEND && !event.isCancelled()) amunix.packet(event.getPacket());
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (amunix.suppressUse()) event.setCancelled(true);
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (mode.getValue() == 2 && amunix.ownsBlink()) event.setCancelled(true);
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        cleanup();
    }

    private void cleanup() {
        amunix.reset();
        if (startBlocking || attacking || stopTick > 0) reset();
        startBlocking = false;
        stopTick = attackTicks = 0;
        target = null;
    }

    @Override
    public void onEnabled() {
        cleanup();
    }

    @Override
    public void onDisabled() {
        cleanup();
    }

    @Override
    public void verifyValue(String name) {
        if ("Mode".equals(name)) cleanup();
        if ("Lag Duration".equals(name) && minLagDuration.getValue() > lagDuration.getValue()) minLagDuration.setValue(lagDuration.getValue());
        if ("Minimum Lag Duration".equals(name) && minLagDuration.getValue() > lagDuration.getValue()) lagDuration.setValue(minLagDuration.getValue());
    }

    public boolean controlsUseInput() {
        return amunix.controlsInput();
    }
}

package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.event.EventTarget;
import cn.unfair.event.events.Event;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.RenderEntityEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.combat.AimAssist;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.module.modules.movement.Stuck;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.util.client.ChatUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.client.TimerUtil;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.player.PacketUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.rotation.RotationUtil;
import cn.unfair.util.rotation.advanced.AdvancedRotationMath;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class BackTrack
extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static Vec3 realPosition = zeroVec();
    public static Vec3 realLastPos = zeroVec();
    public static boolean shouldLag;
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Classic", "Rise", "B4"});
    public final BooleanProperty onlyWhenNeeded = new BooleanProperty("OnlyWhenNeeded", false, this::isClassic);
    public final FloatProperty attackRange = new FloatProperty("AttackRange", 3.0f, 0.1f, 8.0f, () -> this.isClassic() && this.onlyWhenNeeded.getValue() != false);
    public final FloatProperty rangeStart = new FloatProperty("RangeStart", 3.0f, 1.0f, 8.0f, () -> this.isClassic() && this.onlyWhenNeeded.getValue() == false);
    public final FloatProperty rangeEnd = new FloatProperty("RangeEnd", 6.0f, 1.0f, 8.0f, () -> this.isClassic() && this.onlyWhenNeeded.getValue() == false);
    public final ModeProperty rangeBase = new ModeProperty("RangeBase", 0, new String[]{"MouseOver", "HurtTime", "Attack"}, () -> this.isClassic() && this.onlyWhenNeeded.getValue() != false);
    public final BooleanProperty attackTickFix = new BooleanProperty("AttackTickFix", false, () -> this.isClassic() && this.onlyWhenNeeded.getValue() != false);
    public final IntProperty predictionTicks = new IntProperty("PredictionTicks", 1, 0, 10, () -> this.isClassic() && this.onlyWhenNeeded.getValue() != false);
    public final IntProperty hurtTimeToWork = new IntProperty("HurtTimeToWork", 3, 0, 10, () -> this.isClassic() && this.onlyWhenNeeded.getValue() != false);
    public final BooleanProperty extraCheck = new BooleanProperty("ExtraCheck", true, this::isClassic);
    public final IntProperty ms = new IntProperty("DelayMs", 50, 0, 1000, () -> this.isClassic() || this.isB4());
    public final BooleanProperty extraMS = new BooleanProperty("ExtraMs", false, this::isClassic);
    public final IntProperty maxPingSpoof = new IntProperty("MaxPingSpoof", 1000, 50, 2000, this::isLegitReach);
    public final BooleanProperty renderRealLocation = new BooleanProperty("RenderRealLocation", true, this::isLegitReach);
    public final ModeProperty esp = new ModeProperty("RenderMode", 1, new String[]{"FakePlayer", "Box", "OnlineBox", "None"}, this::isClassic);
    public final ModeProperty boxColor = new ModeProperty("BoxColor", 1, new String[]{"Default", "Hud", "Custom"}, () -> this.isClassic() && (this.esp.getValue() == 1 || this.esp.getValue() == 2) || this.isB4());
    public final ColorProperty boxCustomColor = new ColorProperty("BoxCustomColor", new Color(0, 0, 0).getRGB(), () -> (this.isClassic() && (this.esp.getValue() == 1 || this.esp.getValue() == 2) || this.isB4()) && this.boxColor.getValue() == 2);
    public final FloatProperty outlineWidth = new FloatProperty("OutlineWidth", 1.0f, 0.0f, 5.0f, () -> this.isClassic() && this.esp.getValue() == 2);
    public final BooleanProperty players = new BooleanProperty("Players", true);
    public final BooleanProperty mobs = new BooleanProperty("Mobs", false);
    public final BooleanProperty animals = new BooleanProperty("Animals", false);
    public final FloatProperty b4RangeMin = new FloatProperty("DistanceMin", 3.0f, 1.0f, 8.0f, this::isB4);
    public final FloatProperty b4RangeMax = new FloatProperty("DistanceMax", 6.0f, 1.0f, 8.0f, this::isB4);
    public final FloatProperty cooldown = new FloatProperty("Cooldown", 0.0f, 0.0f, 10.0f, this::isB4);
    public final FloatProperty fov = new FloatProperty("FOV", 360.0f, 1.0f, 360.0f, this::isB4);
    public final BooleanProperty disableAdventure = new BooleanProperty("DisableAdventure", false, this::isB4);
    public final BooleanProperty disableWhileChasing = new BooleanProperty("DisableWhileChasing", false, this::isB4);
    public final BooleanProperty flushOnDamage = new BooleanProperty("FlushOnDamage", true, this::isB4);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("IgnoreTeammates", false, this::isB4);
    public final BooleanProperty showServerPosition = new BooleanProperty("ShowServerPosition", true, this::isB4);
    public final BooleanProperty onlyHittable = new BooleanProperty("OnlyHittable", false, this::isB4);
    public final BooleanProperty onlyMovement = new BooleanProperty("OnlyMovement", false, this::isB4);
    public final BooleanProperty weaponOnly = new BooleanProperty("WeaponOnly", true, this::isB4);
    private final TimerUtil attackTimer = new TimerUtil();
    private final TreeMap<Integer, List<Packet<?>>> b4Held = new TreeMap<>();
    private final Object b4Lock = new Object();
    private volatile int b4Tick;
    private volatile int b4DelayTicks;
    private volatile boolean b4Holding;
    private volatile boolean b4Ready;
    private volatile boolean b4VelocityDelay;
    private volatile boolean b4Stuck;
    private volatile EntityLivingBase b4Target;
    private volatile Vec3 b4ServerPos = zeroVec();
    private volatile Packet<?> b4Processing;
    private volatile long b4LastFlush;
    private volatile double b4MinRange;
    private volatile double b4MaxRangeSq;
    private volatile double b4ScanRangeSq;
    public boolean isBackTracking;
    private Vec3 lastRenderPos;
    private Vec3 currentRenderPos;
    private EntityLivingBase target;
    private EntityLivingBase lastTarget;
    private boolean dispatched;
    private boolean outOfRange;
    private boolean attacked;
    private boolean velocityDelayWasActive;
    private int activeMode;

    public BackTrack() {
        super("BackTrack", false);
    }

    private static double getDistanceToEntityBox(Entity entity) {
        return getCustomDistanceToEntityBox(mc.thePlayer.getPositionEyes(1.0f), entity);
    }

    private static double getCustomDistanceToEntityBox(Vec3 eyes, Entity entity) {
        Vec3 pos = getBestHitVec(entity);
        double xDist = Math.abs(pos.xCoord - eyes.xCoord);
        double yDist = Math.abs(pos.yCoord - eyes.yCoord);
        double zDist = Math.abs(pos.zCoord - eyes.zCoord);
        return Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
    }

    private static Vec3 getBestHitVec(Entity entity) {
        return RotationUtil.getClosestPointOnBox((Vec3)mc.thePlayer.getPositionEyes(1.0f), (AxisAlignedBB)AdvancedRotationMath.getHitbox((Entity)entity));
    }

    private static double getDistToTargetFromMouseOver(Vec3 eyes, Vec3 look, Entity target, AxisAlignedBB targetBB) {
        double blockReachDistance = 64.0;
        Vec3 end = eyes.addVector(look.xCoord * blockReachDistance, look.yCoord * blockReachDistance, look.zCoord * blockReachDistance);
        Vec3 vec33 = null;
        MovingObjectPosition movingobjectposition = targetBB.calculateIntercept(eyes, end);
        if (targetBB.isVecInside(eyes)) {
            vec33 = movingobjectposition == null ? eyes : movingobjectposition.hitVec;
        } else if (movingobjectposition != null) {
            vec33 = movingobjectposition.hitVec;
        }
        return vec33 == null ? Double.MAX_VALUE : eyes.distanceTo(vec33);
    }

    private static Vec3 getPositionVector(Entity entity) {
        return new Vec3(entity.posX, entity.posY, entity.posZ);
    }

    private static Vec3 getServerPositionVector(Entity entity) {
        return new Vec3((double)entity.serverPosX / 32.0, (double)entity.serverPosY / 32.0, (double)entity.serverPosZ / 32.0);
    }

    private static Vec3 add(Vec3 vec, double x, double y, double z) {
        return new Vec3(vec.xCoord + x, vec.yCoord + y, vec.zCoord + z);
    }

    private static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
    }

    private static Vec3 subtract(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord - b.xCoord, a.yCoord - b.yCoord, a.zCoord - b.zCoord);
    }

    private static Vec3 inverse(Vec3 vec) {
        return new Vec3(-vec.xCoord, -vec.yCoord, -vec.zCoord);
    }

    private static AxisAlignedBB offset(AxisAlignedBB box, Vec3 vec) {
        return box.offset(vec.xCoord, vec.yCoord, vec.zCoord);
    }

    private static double distance(Entity entity, Vec3 vec) {
        double x = entity.posX - vec.xCoord;
        double y = entity.posY - vec.yCoord;
        double z = entity.posZ - vec.zCoord;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static Vec3 zeroVec() {
        return new Vec3(0.0, 0.0, 0.0);
    }

    private boolean isClassic() {
        return this.mode.getValue() == 0;
    }

    private boolean isLegitReach() {
        return this.mode.getValue() == 1;
    }

    private boolean isB4() {
        return this.mode.getValue() == 2;
    }

    private int getDelayMs() {
        return Math.max(0, this.ms.getValue());
    }

    public void onEnabled() {
        this.activeMode = this.mode.getValue();
        realPosition = zeroVec();
        realLastPos = zeroVec();
        this.lastRenderPos = null;
        this.currentRenderPos = null;
        this.lastTarget = null;
        this.isBackTracking = false;
        this.velocityDelayWasActive = false;
        synchronized (this.b4Lock) {
            this.b4Held.clear();
            this.b4Reset();
        }
    }

    public void onDisabled() {
        Unfair.lagManager.setBackTrackState(false, 0);
        shouldLag = false;
        this.b4Flush();
        realPosition = null;
        realLastPos = null;
        this.lastRenderPos = null;
        this.currentRenderPos = null;
        this.target = null;
        this.lastTarget = null;
        this.isBackTracking = false;
        this.velocityDelayWasActive = false;
    }

    public String[] getSuffix() {
        return new String[]{(this.isClassic() || this.isB4() ? this.getDelayMs() : this.maxPingSpoof.getValue()) + "ms"};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        this.checkModeChange();
        if (event.type() == EventType.POST) {
            EntityLivingBase active;
            EntityLivingBase entityLivingBase = active = this.isB4() ? this.b4Target : this.target;
            if (active != null && realPosition != null) {
                this.lastRenderPos = this.currentRenderPos;
                this.currentRenderPos = realPosition;
            } else {
                this.lastRenderPos = null;
                this.currentRenderPos = null;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        this.checkModeChange();
        if (this.isClassic()) {
            this.runBackTrack();
        } else if (this.isB4()) {
            this.runB4();
        } else {
            this.runLegitReach();
        }
    }

    private void runLegitReach() {
        double clientDistance;
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetTargetState();
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }
        if (mc.thePlayer.isDead || mc.currentScreen instanceof GuiGameOver) {
            this.stopLaggingForRespawn();
            return;
        }
        if (this.velocityDelayWasActive) {
            this.velocityDelayWasActive = false;
            if (this.target != null) {
                realLastPos = realPosition = getPositionVector((Entity)this.target);
            }
        }
        if (this.isVelocityDelaying()) {
            this.pauseForVelocityDelay();
            return;
        }
        EntityLivingBase newTarget = this.getTarget(9.0);
        if (newTarget == null) {
            this.resetTargetState();
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }
        if (newTarget != this.target || realPosition == null) {
            this.target = newTarget;
            this.lastTarget = newTarget;
            realLastPos = realPosition = getPositionVector((Entity)newTarget);
            this.lastRenderPos = realPosition;
            this.currentRenderPos = realPosition;
        }
        KillAura killAura = (KillAura)Unfair.moduleManager.modules.get(KillAura.class);
        if (!(mc.thePlayer.isSwingInProgress || killAura != null && killAura.isEnabled())) {
            shouldLag = false;
            this.isBackTracking = false;
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }
        double realDistance = distance((Entity)mc.thePlayer, realPosition);
        this.isBackTracking = shouldLag = realDistance > (clientDistance = (double)this.target.getDistanceToEntity((Entity)mc.thePlayer)) && realDistance > 2.3 && realDistance < 5.9;
        Unfair.lagManager.setBackTrackState(false, 0);
    }

    private void runBackTrack() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.isBackTracking = false;
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }
        if (mc.thePlayer.isDead || mc.currentScreen instanceof GuiGameOver) {
            this.stopLaggingForRespawn();
            return;
        }
        if (this.velocityDelayWasActive) {
            this.velocityDelayWasActive = false;
            if (this.target != null) {
                realLastPos = realPosition = getServerPositionVector((Entity)this.target);
            }
        }
        if (this.isVelocityDelaying()) {
            this.pauseForVelocityDelay();
            return;
        }
        if (this.ms.getValue() > (this.extraMS.getValue() != false ? 5000 : 1000)) {
            this.ms.setValue((this.extraMS.getValue() != false ? 5000 : 1000));
        }
        this.target = this.getTarget(8.0);
        if (this.target == null) {
            this.lastTarget = null;
            this.isBackTracking = false;
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }
        if (this.target != this.lastTarget || realPosition == null || realLastPos == null) {
            realLastPos = realPosition = getServerPositionVector((Entity)this.target);
            this.lastRenderPos = realPosition;
            this.currentRenderPos = realPosition;
            this.lastTarget = this.target;
        }
        Vec3 pred = AdvancedRotationMath.multiply((Vec3)subtract(getPositionVector((Entity)this.target), AdvancedRotationMath.getPrevPositionVector((Entity)this.target)), (double)this.predictionTicks.getValue());
        double realDistance = getCustomDistanceToEntityBox(add(add(realPosition, 0.0, this.target.getEyeHeight(), 0.0), AdvancedRotationMath.multiply((Vec3)AdvancedRotationMath.getMoveDeltaVector((Entity)this.target), (double)this.predictionTicks.getValue())), (Entity)mc.thePlayer);
        double clientDistance = getDistToTargetFromMouseOver(mc.thePlayer.getPositionEyes(1.0f), mc.thePlayer.getLook(1.0f), (Entity)this.target, offset(offset(AdvancedRotationMath.getHitbox((Entity)this.target), inverse(getPositionVector((Entity)this.target))), add(getPositionVector((Entity)this.target), pred)));
        if (clientDistance > (double)this.attackRange.getValue()) {
            this.outOfRange = true;
        }
        if (this.outOfRange) {
            switch (this.rangeBase.getValue()) {
                case 0: {
                    this.outOfRange = realDistance < 3.0;
                    break;
                }
                case 1: {
                    this.outOfRange = this.target.hurtTime == 10;
                    break;
                }
                default: {
                    this.outOfRange = this.attacked && (this.attackTickFix.getValue() == false || this.attackTimer.hasTimeElapsed((long)Math.min(this.getDelayMs(), 100)));
                }
            }
        }
        boolean distanceCheck = distance((Entity)mc.thePlayer, getPositionVector((Entity)this.target)) > distance((Entity)mc.thePlayer, AdvancedRotationMath.getPrevPositionVector((Entity)this.target));
        boolean extraCheck = distanceCheck || this.extraCheck.getValue() == false;
        boolean onlyNeeded = extraCheck && (realDistance > (double)this.attackRange.getValue() || this.outOfRange) && realDistance < 4.5 && clientDistance <= 3.0 && this.target.hurtTime <= this.hurtTimeToWork.getValue();
        boolean on = extraCheck && realDistance > (double)this.rangeStart.getValue() && realDistance < (double)this.rangeEnd.getValue();
        this.isBackTracking = shouldLag = this.onlyWhenNeeded.getValue() != false ? onlyNeeded : on;
        this.attacked = false;
        Unfair.lagManager.setBackTrackState(this.isBackTracking, this.getDelayMs());
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || !this.isClassic() || event.getTarget() != this.target || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }
        this.attacked = true;
        if (!this.attackTimer.hasTimeElapsed((long)Math.min(this.getDelayMs(), 100)) && this.attackTickFix.getValue() && !this.dispatched && this.onlyWhenNeeded.getValue()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }
        Packet packet = event.getPacket();
        if (packet instanceof C16PacketClientStatus && ((C16PacketClientStatus)packet).getStatus() == C16PacketClientStatus.EnumState.PERFORM_RESPAWN) {
            this.stopLaggingForRespawn();
            return;
        }
        if (event.getType() == EventType.RECEIVE) {
            if (this.isB4()) {
                this.b4HandlePacket(packet, event);
                return;
            }
            if (this.target != null && realPosition != null) {
                realLastPos = realPosition;
                if (packet instanceof S14PacketEntity) {
                    S14PacketEntity s14PacketEntity = (S14PacketEntity)packet;
                    if (s14PacketEntity.getEntityId() == this.target.getEntityId()) {
                        realPosition = realPosition.addVector((double)s14PacketEntity.func_149062_c() / 32.0, (double)s14PacketEntity.func_149061_d() / 32.0, (double)s14PacketEntity.func_149064_e() / 32.0);
                    }
                } else if (packet instanceof S18PacketEntityTeleport) {
                    S18PacketEntityTeleport s18PacketEntityTeleport = (S18PacketEntityTeleport)packet;
                    if (s18PacketEntityTeleport.getEntityId() == this.target.getEntityId()) {
                        realPosition = new Vec3((double)s18PacketEntityTeleport.getX() / 32.0, (double)s18PacketEntityTeleport.getY() / 32.0, (double)s18PacketEntityTeleport.getZ() / 32.0);
                    }
                    if (mc.thePlayer != null && s18PacketEntityTeleport.getEntityId() == mc.thePlayer.getEntityId()) {
                        this.dispatched = false;
                        shouldLag = false;
                        this.isBackTracking = false;
                    }
                }
            }
            if (Unfair.lagManager.handleIncomingPacket(event.getPacket())) {
                event.setCancelled(true);
            }
        } else if (event.getType() == EventType.SEND && this.isClassic() && packet instanceof C02PacketUseEntity && ((C02PacketUseEntity)packet).getAction().equals(C02PacketUseEntity.Action.ATTACK) && this.attackTickFix.getValue() && !this.dispatched && this.onlyWhenNeeded.getValue()) {
            this.attackTimer.reset();
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || this.target == null && this.b4Target == null || realPosition == null) {
            return;
        }
        if (this.isB4()) {
            if (!this.b4Holding || !this.showServerPosition.getValue()) {
                return;
            }
            AxisAlignedBB bb = this.getRenderBox(event.partialTicks(), this.b4Target);
            Color color = this.getBoxColor();
            RenderUtil.enableRenderState();
            RenderUtil.drawFilledBox((AxisAlignedBB)bb, (int)color.getRed(), (int)color.getGreen(), (int)color.getBlue(), (int)94);
            RenderUtil.disableRenderState();
            return;
        }
        if (this.isLegitReach()) {
            this.renderLegitReachPosition(event.partialTicks());
            return;
        }
        if (!shouldLag || this.esp.getValue() != 1 && this.esp.getValue() != 2) {
            return;
        }
        AxisAlignedBB bb = this.getRenderBox(event.partialTicks(), this.target);
        Color color = this.getBoxColor();
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox((AxisAlignedBB)bb, (int)color.getRed(), (int)color.getGreen(), (int)color.getBlue());
        if (this.esp.getValue() == 2) {
            RenderUtil.drawBoundingBox((AxisAlignedBB)bb, (int)color.getRed(), (int)color.getGreen(), (int)color.getBlue(), (int)255, (float)this.outlineWidth.getValue());
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onRenderEntity(RenderEntityEvent event) {
        if (!(this.isEnabled() && this.isClassic() && this.target != null && this.esp.getValue() == 0 && shouldLag)) {
            return;
        }
        float partialTicks = mc.timer.renderPartialTicks;
        Vec3 renderPosition = this.getRenderPosition(partialTicks).addVector(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ());
        mc.getRenderManager().doRenderEntity((Entity)this.target, renderPosition.xCoord, renderPosition.yCoord, renderPosition.zCoord, this.target.rotationYawHead, partialTicks, true);
    }

    private void renderLegitReachPosition(float partialTicks) {
        if (!this.renderRealLocation.getValue()) {
            return;
        }
        Vec3 renderPos = this.getRenderPosition(partialTicks);
        if (renderPos == null) {
            return;
        }
        double expand = 0.14;
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox().offset(-mc.thePlayer.posX, -mc.thePlayer.posY, -mc.thePlayer.posZ).offset(renderPos.xCoord, renderPos.yCoord, renderPos.zCoord).expand(expand, expand, expand).offset(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ());
        Unfair.moduleManager.modules.get(HUD.class);
        Color color = HUD.getColor((long)System.currentTimeMillis());
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox((AxisAlignedBB)bb, (int)color.getRed(), (int)color.getGreen(), (int)color.getBlue(), (int)50);
        RenderUtil.disableRenderState();
    }

    private Color getBoxColor() {
        EntityLivingBase target = this.isB4() ? this.b4Target : this.target;
        switch (this.boxColor.getValue()) {
            case 1: {
                Unfair.moduleManager.modules.get(HUD.class);
                return HUD.getColor((long)System.currentTimeMillis());
            }
            case 2: {
                return new Color(this.boxCustomColor.getValue());
            }
        }
        return target instanceof EntityPlayer ? TeamUtil.getTeamColor((EntityPlayer)((EntityPlayer)target), (float)1.0f) : new Color(0, 0, 0);
    }

    private AxisAlignedBB getRenderBox(float partialTicks, EntityLivingBase target) {
        Vec3 position = this.getRenderPosition(partialTicks);
        float size = target.getCollisionBorderSize();
        return new AxisAlignedBB(position.xCoord - (double)target.width / 2.0, position.yCoord, position.zCoord - (double)target.width / 2.0, position.xCoord + (double)target.width / 2.0, position.yCoord + (double)target.height, position.zCoord + (double)target.width / 2.0).expand((double)size, (double)size, (double)size).offset(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ());
    }

    private Vec3 getRenderPosition(float partialTicks) {
        if (this.currentRenderPos == null || this.lastRenderPos == null) {
            return this.currentRenderPos != null ? this.currentRenderPos : realPosition;
        }
        return new Vec3(RenderUtil.lerpDouble((double)this.currentRenderPos.xCoord, (double)this.lastRenderPos.xCoord, (double)partialTicks), RenderUtil.lerpDouble((double)this.currentRenderPos.yCoord, (double)this.lastRenderPos.yCoord, (double)partialTicks), RenderUtil.lerpDouble((double)this.currentRenderPos.zCoord, (double)this.lastRenderPos.zCoord, (double)partialTicks));
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity || entity instanceof EntityArmorStand || entity.isDead || entity.deathTime > 0) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)entity;
            if (!this.players.getValue() || TeamUtil.isFriend((EntityPlayer)player)) {
                return false;
            }
            return !TeamUtil.shouldBlockTarget((EntityPlayer)player);
        }
        String entityPackage = entity.getClass().getName();
        if (entityPackage.contains(".monster.") || entityPackage.contains(".boss.")) {
            return this.mobs.getValue();
        }
        if (entityPackage.contains(".passive.")) {
            return this.animals.getValue();
        }
        return false;
    }

    private EntityLivingBase getTarget(double distance) {
        KillAura killAura = (KillAura)Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }
        return mc.theWorld.loadedEntityList.stream().filter(EntityLivingBase.class::isInstance).map(EntityLivingBase.class::cast).filter(this::isValidTarget).filter(entity -> getDistanceToEntityBox((Entity)entity) <= distance + 0.4).min(Comparator.comparingDouble(BackTrack::getDistanceToEntityBox)).orElse(null);
    }

    private void checkModeChange() {
        if (this.activeMode == this.mode.getValue()) {
            return;
        }
        Unfair.lagManager.setBackTrackState(false, 0);
        this.activeMode = this.mode.getValue();
        this.b4Flush();
        this.resetTargetState();
        realPosition = zeroVec();
        realLastPos = zeroVec();
        this.lastRenderPos = null;
        this.currentRenderPos = null;
    }

    private boolean isVelocityDelaying() {
        Velocity velocityModule;
        Module velocity = Unfair.moduleManager.getModule(Velocity.class);
        return velocity instanceof Velocity && (velocityModule = (Velocity)velocity).isDelayingVelocity();
    }

    private void pauseForVelocityDelay() {
        if (this.isBackTracking || shouldLag || !this.dispatched) {
            Unfair.lagManager.setBackTrackState(false, 0);
            this.dispatched = true;
        }
        shouldLag = false;
        this.isBackTracking = false;
        this.velocityDelayWasActive = true;
    }

    private void resetTargetState() {
        shouldLag = false;
        this.isBackTracking = false;
        this.target = null;
        this.lastTarget = null;
        this.lastRenderPos = null;
        this.currentRenderPos = null;
        this.outOfRange = false;
        this.attacked = false;
        this.velocityDelayWasActive = false;
    }

    private void stopLaggingForRespawn() {
        Unfair.lagManager.setBackTrackState(false, 0);
        this.dispatched = true;
        this.b4Flush();
        this.resetTargetState();
    }

    public void verifyValue(String string) {
        if (string.equalsIgnoreCase("OnlyMovement") && this.isB4()) {
            this.b4Flush();
        } else if (this.b4RangeMin.getName().equalsIgnoreCase(string) && this.b4RangeMin.getValue() > this.b4RangeMax.getValue()) {
            this.b4RangeMax.setValue(this.b4RangeMin.getValue());
        } else if (this.b4RangeMax.getName().equalsIgnoreCase(string) && this.b4RangeMin.getValue() > this.b4RangeMax.getValue()) {
            this.b4RangeMin.setValue(this.b4RangeMax.getValue());
        }
    }

    private void runB4() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            synchronized (this.b4Lock) {
                this.b4Held.clear();
                this.b4Reset();
            }
            return;
        }
        this.b4VelocityDelay = this.isVelocityDelaying();
        Stuck stuck = (Stuck)Unfair.moduleManager.getModule(Stuck.class);
        this.b4Stuck = stuck != null && stuck.isEnabled();
        double maxRange = this.b4RangeMax.getValue();
        this.b4MinRange = this.b4RangeMin.getValue();
        this.b4MaxRangeSq = (maxRange + 1.2) * (maxRange + 1.2);
        this.b4ScanRangeSq = (maxRange + 2.5) * (maxRange + 2.5);
        EntityLivingBase e = this.b4FindTarget();
        if (this.b4Holding) {
            if (e != this.b4Target || !this.b4TargetValid()) {
                this.b4Flush();
                return;
            }
            this.b4ReleaseDue();
        } else {
            if (e != null) {
                this.b4Target = e;
                this.b4UpdateServerPos();
                this.b4SyncRenderPos();
            }
            this.b4Ready = this.b4Target != null && this.b4TargetValid();
        }
    }

    private void b4HandlePacket(Packet<?> packet, PacketEvent event) {
        if (packet == this.b4Processing) {
            return;
        }
        try {
            if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
                if (this.b4Holding) {
                    synchronized (this.b4Lock) {
                        this.b4Held.clear();
                        this.b4Reset();
                    }
                }
                return;
            }
            boolean flush = false;
            if (this.b4Holding) {
                S12PacketEntityVelocity s12;
                boolean bl = flush = packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect;
                if (!flush && this.flushOnDamage.getValue() && packet instanceof S12PacketEntityVelocity && (s12 = (S12PacketEntityVelocity)packet).getEntityID() == mc.thePlayer.getEntityId()) {
                    flush = true;
                }
            } else if (!this.b4Ready) {
                return;
            }
            if (flush) {
                this.b4Flush();
                return;
            }
            Vec3 pos = this.b4ServerPos;
            double x = pos.xCoord;
            double y = pos.yCoord;
            double z = pos.zCoord;
            boolean moves = false;
            boolean teleport = false;
            if (packet instanceof S14PacketEntity) {
                Entity entity;
                S14PacketEntity packetEntity = (S14PacketEntity)packet;
                if ((packetEntity instanceof S14PacketEntity.S15PacketEntityRelMove || packetEntity instanceof S14PacketEntity.S17PacketEntityLookMove) && (entity = packetEntity.getEntity((World)mc.theWorld)) != null && this.b4Target != null && entity.getEntityId() == this.b4Target.getEntityId()) {
                    moves = true;
                    x += (double)packetEntity.func_149062_c() / 32.0;
                    y += (double)packetEntity.func_149061_d() / 32.0;
                    z += (double)packetEntity.func_149064_e() / 32.0;
                }
            } else if (packet instanceof S18PacketEntityTeleport) {
                S18PacketEntityTeleport s18 = (S18PacketEntityTeleport)packet;
                if (this.b4Target != null && s18.getEntityId() == this.b4Target.getEntityId()) {
                    teleport = true;
                    moves = true;
                    x = (double)s18.getX() / 32.0;
                    y = (double)s18.getY() / 32.0;
                    z = (double)s18.getZ() / 32.0;
                }
            }
            if (moves) {
                float[] rot;
                AxisAlignedBB box;
                AxisAlignedBB newBox;
                flush = true;
                double distSq = mc.thePlayer.getDistanceSq(x, y, z);
                if (distSq <= this.b4MaxRangeSq && !b4RayHits(this.b4Target, newBox = (box = this.b4Target.getEntityBoundingBox()).offset(x - this.b4Target.posX, y - this.b4Target.posY, z - this.b4Target.posZ), (rot = b4ToBox(this.b4Target, newBox))[0], rot[1], this.b4MinRange) && b4RayHits(this.b4Target, newBox, rot[0], rot[1], this.b4RangeMax.getValue())) {
                    AxisAlignedBB oldBox = box.offset(pos.xCoord - this.b4Target.posX, pos.yCoord - this.b4Target.posY, pos.zCoord - this.b4Target.posZ);
                    float[] oldRot = b4ToBox(this.b4Target, oldBox);
                    double newDist = b4RayDistanceSq(this.b4Target, newBox, rot[0], rot[1], this.b4RangeMax.getValue());
                    double oldDist = b4RayDistanceSq(this.b4Target, oldBox, oldRot[0], oldRot[1], (double)this.b4RangeMax.getValue() + 2.0);
                    if (oldDist >= 0.0 && newDist > oldDist && (!teleport || newDist - oldDist < 64.0)) {
                        if (!this.b4Holding && this.b4CanStart()) {
                            this.b4Start();
                        }
                        this.b4ServerPos = new Vec3(x, y, z);
                        this.b4SyncRenderPos();
                        flush = false;
                    }
                }
                if (this.b4Holding && flush) {
                    this.b4Flush();
                    return;
                }
            }
            if (this.b4Holding && !(this.onlyMovement.getValue() == false ? BackTrack.b4PassesThrough(packet) : !moves)) {
                this.b4Hold(packet);
                event.setCancelled(true);
            }
        }
        catch (Throwable throwable) {
            ChatUtil.sendFormatted("&cbacktrack e3 " + throwable.getClass().getSimpleName());
        }
    }

    private static boolean b4PassesThrough(Packet<?> packet) {
        return packet instanceof S02PacketChat || packet instanceof S06PacketUpdateHealth || packet instanceof S14PacketEntity.S16PacketEntityLook || packet instanceof S19PacketEntityStatus || packet instanceof S22PacketMultiBlockChange || packet instanceof S23PacketBlockChange || packet instanceof S29PacketSoundEffect || packet instanceof S2APacketParticles || packet instanceof S3BPacketScoreboardObjective || packet instanceof S3CPacketUpdateScore || packet instanceof S3DPacketDisplayScoreboard || packet instanceof S3EPacketTeams;
    }

    private boolean b4CanStart() {
        if (this.b4Stuck || this.b4VelocityDelay) {
            return false;
        }
        return this.cooldown.getValue() <= 0.0f || (double)Math.abs(System.currentTimeMillis() - this.b4LastFlush) > (double)this.cooldown.getValue() * 1000.0 - 20.0;
    }

    private void b4Start() {
        this.b4DelayTicks = Math.max(1, this.getDelayMs() / 50);
        this.b4UpdateServerPos();
        this.b4SyncRenderPos();
        this.b4Holding = true;
    }

    private void b4UpdateServerPos() {
        EntityLivingBase e = this.b4Target;
        this.b4ServerPos = new Vec3((double)e.serverPosX / 32.0, (double)e.serverPosY / 32.0, (double)e.serverPosZ / 32.0);
    }

    private void b4SyncRenderPos() {
        realLastPos = realPosition;
        realPosition = this.b4ServerPos;
    }

    private void b4Hold(Packet<?> packet) {
        synchronized (this.b4Lock) {
            int n = this.b4Tick + this.b4DelayTicks;
            this.b4Held.computeIfAbsent(n, k -> new ArrayList<>()).add(packet);
        }
    }

    private void b4Flush() {
        List<Packet<?>> list = null;
        synchronized (this.b4Lock) {
            if (!this.b4Held.isEmpty()) {
                for (List<Packet<?>> packets : this.b4Held.values()) {
                    if (packets.isEmpty()) continue;
                    if (list == null) {
                        list = new ArrayList<>();
                    }
                    list.addAll(packets);
                    packets.clear();
                }
                this.b4Held.clear();
            }
            this.b4Reset();
        }
        if (list != null) {
            this.b4Process(list);
        }
    }

    private void b4ReleaseDue() {
        List<Packet<?>> list = null;
        synchronized (this.b4Lock) {
            if (!this.b4Held.isEmpty()) {
                Map.Entry<Integer, List<Packet<?>>> entry;
                Iterator<Map.Entry<Integer, List<Packet<?>>>> it = this.b4Held.entrySet().iterator();
                while (it.hasNext() && this.b4Tick >= (entry = it.next()).getKey()) {
                    List<Packet<?>> packets = entry.getValue();
                    if (!packets.isEmpty()) {
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        list.addAll(packets);
                        packets.clear();
                    }
                    it.remove();
                }
            }
            ++this.b4Tick;
        }
        if (list != null) {
            this.b4Process(list);
        }
    }

    private void b4Process(List<Packet<?>> list) {
        if (list.isEmpty()) {
            return;
        }
        for (Packet<?> packet : list) {
            try {
                this.b4Processing = packet;
                PacketEvent replay = new PacketEvent(EventType.RECEIVE, packet);
                EventManager.call(replay);
                if (replay.isCancelled()) continue;
                PacketUtil.receivePacketNoEvent(packet);
            }
            catch (Throwable throwable) {
                ChatUtil.sendFormatted("&cbacktrack e4 " + packet.getClass().getSimpleName() + " " + throwable.getClass().getSimpleName());
            }
            finally {
                this.b4Processing = null;
            }
        }
    }

    private void b4Reset() {
        this.b4Ready = false;
        this.b4Holding = false;
        this.b4Target = null;
        this.b4Tick = 0;
        this.b4LastFlush = System.currentTimeMillis();
    }

    private boolean b4TargetValid() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null || mc.isSingleplayer()) {
            return false;
        }
        EntityLivingBase target = this.b4Target;
        if (target == null || mc.theWorld.getEntityByID(target.getEntityId()) == null || target.deathTime != 0) {
            return false;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof AugustusClickGui)) {
            return false;
        }
        if (mc.thePlayer.capabilities.allowFlying || this.disableAdventure.getValue() && !mc.thePlayer.capabilities.allowEdit) {
            return false;
        }
        return this.weaponOnly.getValue() == false || holdingWeapon();
    }

    private boolean b4Valid(EntityLivingBase e) {
        if (this.onlyHittable.getValue() && e.hurtTime > 0) {
            return false;
        }
        return this.disableWhileChasing.getValue() == false || !b4AwayFromPlayer(e);
    }

    private EntityLivingBase b4FindTarget() {
        EntityPlayer e;
        EntityLivingBase e2;
        KillAura killAura = (KillAura)Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null && this.b4Valid(e2 = killAura.getTarget())) {
            return e2;
        }
        AimAssist aimAssist = (AimAssist)Unfair.moduleManager.getModule(AimAssist.class);
        if (aimAssist != null && aimAssist.getCurrentTarget() != null && this.b4Valid((EntityLivingBase)(e = aimAssist.getCurrentTarget()))) {
            return e;
        }
        float half = this.fov.getValue() / 2.0f;
        double best = 999.0;
        EntityPlayer found = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            double yaw;
            double d;
            if (p == mc.thePlayer || p.deathTime != 0 || !this.b4Valid((EntityLivingBase)p) || TeamUtil.isBot((EntityPlayer)p) || (d = mc.thePlayer.getDistanceSqToEntity((Entity)p)) > this.b4ScanRangeSq || (yaw = (double)Math.abs(RotationUtil.getYawBetween((double)mc.thePlayer.posX, (double)mc.thePlayer.posZ, (double)p.posX, (double)p.posZ))) > (double)half || TeamUtil.isFriend((EntityPlayer)p) || this.ignoreTeammates.getValue() && TeamUtil.isSameTeam((EntityPlayer)p) || best != 999.0 && !(yaw < best)) continue;
            best = yaw;
            found = p;
        }
        return found;
    }

    private static float[] b4ToBox(EntityLivingBase entity, AxisAlignedBB box) {
        Vec3 eyes;
        float f = entity.getCollisionBorderSize();
        AxisAlignedBB expanded = box.expand((double)f, (double)f, (double)f);
        if (expanded.isVecInside(eyes = mc.thePlayer.getPositionEyes(1.0f))) {
            return new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
        }
        double cx = (expanded.minX + expanded.maxX) / 2.0;
        double cy = expanded.minY + (expanded.maxY - expanded.minY) * 0.825;
        double cz = (expanded.minZ + expanded.maxZ) / 2.0;
        return RotationUtil.getRotations((double)cx, (double)cy, (double)cz, (double)eyes.xCoord, (double)eyes.yCoord, (double)eyes.zCoord);
    }

    private static boolean b4RayHits(EntityLivingBase entity, AxisAlignedBB box, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith()) {
            return false;
        }
        Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 dir = mc.thePlayer.getVectorForRotation(pitch, yaw);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        float f = entity.getCollisionBorderSize();
        return (box = box.expand((double)f, (double)f, (double)f)).calculateIntercept(from, to) != null;
    }

    private static double b4RayDistanceSq(EntityLivingBase entity, AxisAlignedBB box, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith()) {
            return -1.0;
        }
        Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 dir = mc.thePlayer.getVectorForRotation(pitch, yaw);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        float f = entity.getCollisionBorderSize();
        MovingObjectPosition hit = (box = box.expand((double)f, (double)f, (double)f)).calculateIntercept(from, to);
        if (hit == null) {
            return -1.0;
        }
        return from.squareDistanceTo(hit.hitVec);
    }

    private static boolean b4AwayFromPlayer(EntityLivingBase entity) {
        double d = mc.thePlayer.getDistanceSqToEntity((Entity)entity);
        return d > 1.0 && !b4FacingPlayer(entity);
    }

    private static boolean b4FacingPlayer(EntityLivingBase entity) {
        float f = b4YawBetween(entity.posX, entity.posZ, mc.thePlayer.posX, mc.thePlayer.posZ);
        double d = MathHelper.wrapAngleTo180_double((double)(f - entity.rotationYaw));
        return Math.abs(d) <= 90.0;
    }

    private static float b4YawBetween(double fromX, double fromZ, double toX, double toZ) {
        return (float)(-Math.toDegrees(Math.atan2(toX - fromX, toZ - fromZ)));
    }

    private static boolean holdingWeapon() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null) {
            return false;
        }
        return ItemUtil.isSword((ItemStack)stack) || ItemUtil.isTool((ItemStack)stack, (String)"axe");
    }
}

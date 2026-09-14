package sl.selene.module.impl.combat;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import sl.selene.Selene;
import sl.selene.event.EventInit;
import sl.selene.event.impl.EventChangeWorld;
import sl.selene.event.lifecycle.ClientTickEvent;
import sl.selene.module.api.Category;
import sl.selene.module.api.IModule;
import sl.selene.module.api.Module;
import sl.selene.module.api.setting.Setting;
import sl.selene.module.api.setting.impl.BooleanSetting;
import sl.selene.module.api.setting.impl.ModeSetting;
import sl.selene.module.api.setting.impl.SliderSetting;
import sl.selene.util.engine.HitEngine;

@IModule(
   name = "TriggerBot",
   description = "Attacks the entity under your crosshair automatically",
   category = Category.Combat,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class TriggerBot extends Module {

   public static SliderSetting reach = new SliderSetting("Reach", 3.0F, 2.5F, 3.0F, 0.01F, false);

   public static SliderSetting reachVariance = new SliderSetting("Reach Variance", 0.0F, 0.0F, 1.0F, 0.05F, false);

   public static SliderSetting reaction = new SliderSetting("Reaction (ms)", 0.0F, 0.0F, 500.0F, 5.0F, false);

   public static SliderSetting reactionVariance = new SliderSetting("Reaction Variance (ms)", 0.0F, 0.0F, 250.0F, 5.0F, false);

   public static SliderSetting cooldownPercent = new SliderSetting("Cooldown (%)", 85.0F, 50.0F, 100.0F, 1.0F, false);

   public static ModeSetting targetMode = new ModeSetting(
      "Target Mode", "Everything", "Players", "Hostiles", "Mobs", "Everything");

   public static ModeSetting weaponMode = new ModeSetting("Weapon", "Melee", "Any", "Sword", "Axe", "Melee");

   public static BooleanSetting hurtTime = new BooleanSetting("Hurt Time", true);
   public static BooleanSetting teams = new BooleanSetting("Teams", false);
   public static BooleanSetting crits = new BooleanSetting("Crits", true);
   public static BooleanSetting noInvisible = new BooleanSetting("No Invisible", true);
   public static BooleanSetting noCrystals = new BooleanSetting("No Crystals", true);
   public static BooleanSetting noShields = new BooleanSetting("No Shields", true);
   public static ModeSetting fireMode = new ModeSetting("Fire Mode", "Always", "Always", "Hold LMB");

   public static ModeSetting targetMode2 = new ModeSetting("Targeting", "Crosshair", "Crosshair", "Raycast");

   public static BooleanSetting pauseOnUse = new BooleanSetting("Pause On Use", true);

   public static SliderSetting minInterval = new SliderSetting("Min Interval (ms)", 0.0F, 0.0F, 500.0F, 5.0F, false);

   public static BooleanSetting antiBot = new BooleanSetting("Anti Bot", true);

   private static final double CAST_PADDING = 0.2;

   private long armedAt = -1L;
   private long swingAt = -1L;
   private long lastAttackMs = 0L;

   private final HitEngine hit = new HitEngine();

   public TriggerBot() {
      this.addSettings(new Setting[] {
         reach, reachVariance, reaction, reactionVariance, cooldownPercent, targetMode, weaponMode,
         hurtTime, teams, crits, noInvisible, noCrystals, noShields, fireMode,
         targetMode2, pauseOnUse, minInterval, antiBot
      });
   }

   @Override
   public String getArrayListSuffix() {
      return fmt(reach.get());
   }

   @Override
   public void onDisable() {
      super.onDisable();
      hit.release();
      disarm();
   }

   @EventInit
   public void onWorldChange(EventChangeWorld event) {
      hit.release();
      disarm();
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      if (mc.player == null || mc.world == null || mc.interactionManager == null) {
         return;
      }
      if (mc.currentScreen != null) {
         hit.release();
         disarm();
         return;
      }
      if (pauseOnUse.get() && mc.player.isUsingItem() && !mc.player.isBlocking()) {
         hit.release();
         disarm();
         return;
      }
      if (hit.isHeld()) {
         hit.tick();
         disarm();
         return;
      }

      if (!shouldFire()) {
         disarm();
         return;
      }
      if (!CombatUtil.isWeaponType(mc.player.getMainHandStack(), weaponMode.get())) {
         disarm();
         return;
      }

      if (!mc.player.isOnGround() && !mc.player.isClimbing()) {
         if (crits.get() && !(CombatUtil.canCrit(mc) && mc.player.getVelocity().y < -0.08D
               && mc.player.fallDistance >= 0.15F)) {
            if (armedAt < 0L) {
               long warm = System.currentTimeMillis();
               armedAt = warm;
               swingAt = warm + rollReaction();
            }
            return;
         }
         this.trySwing(resolveHit());
         return;
      }

      EntityHitResult hit = resolveHit();
      if (hit == null) {
         disarm();
         return;
      }
      this.trySwing(hit);
   }

   private EntityHitResult resolveHit() {
      if (targetMode2.is("Crosshair")) {
         if (mc.crosshairTarget instanceof EntityHitResult entityHit
               && isValidTarget(entityHit.getEntity())) {
            return entityHit;
         }
         return null;
      }
      return raycastTarget(rollReach());
   }

   private boolean trySwing(EntityHitResult hit) {
      if (hit == null || mc.player == null) {
         return false;
      }
      if (hurtTime.get() && hit.getEntity() instanceof LivingEntity living && living.hurtTime > 0) {
         return false;
      }
      long now = System.currentTimeMillis();
      if (armedAt < 0L) {
         armedAt = now;
         swingAt = now + rollReaction();
      }
      if (now < swingAt) {
         return false;
      }
      if (minInterval.get() > 0.0F && now - lastAttackMs < (long) minInterval.get()) {
         return false;
      }
      float required = Math.max(0.5F, Math.min(1.0F, cooldownPercent.get() / 100.0F));
      if (mc.player.getAttackCooldownProgress(0.5F) < required) {
         return false;
      }
      if (mc.player.age == this.hit.getLastAttackAge()) {
         return false;
      }
      if (!click(hit)) {
         return false;
      }
      lastAttackMs = now;
      return true;
   }

   private double rollReach() {
      float variance = Math.max(0.0F, reachVariance.get());
      return Math.max(1.0F, CombatUtil.clampReach(reach.get() - (float) Math.random() * variance));
   }

   private long rollReaction() {
      float base = Math.max(0.0F, reaction.get());
      float variance = Math.max(0.0F, reactionVariance.get());
      double roll = base + (Math.random() * 2.0 - 1.0) * variance;
      return Math.max(0L, (long) roll);
   }

   private EntityHitResult raycastTarget(double reach) {
      if (mc.player == null || mc.world == null) {
         return null;
      }
      Vec3d eye = mc.player.getEyePos();
      Vec3d look = mc.player.getRotationVec(1.0F);
      double length = reach + CAST_PADDING;
      Vec3d end = eye.add(look.x * length, look.y * length, look.z * length);

      double maxDistSq = reach * reach;
      BlockHitResult blockHit = mc.world.raycast(
            new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
      if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
         double blockDistSq = eye.squaredDistanceTo(blockHit.getPos());
         if (blockDistSq < maxDistSq) {
            maxDistSq = blockDistSq;
         }
      }

      EntityHitResult best = null;
      double bestSq = maxDistSq;
      for (Entity entity : mc.world.getEntities()) {
         if (!isValidTarget(entity)) {
            continue;
         }
         Box box = entity.getBoundingBox();
         Optional<Vec3d> hit = box.raycast(eye, end);
         if (hit.isPresent()) {
            double distSq = eye.squaredDistanceTo(hit.get());
            if (distSq <= bestSq) {
               bestSq = distSq;
               best = new EntityHitResult(entity, hit.get());
            }
         }
      }

      return best;
   }

   private boolean isValidTarget(Entity entity) {
      if (entity == null || mc.player == null || entity == mc.player || entity == mc.getCameraEntity()) {
         return false;
      }
      if (entity instanceof EndCrystalEntity) {
         return !noCrystals.get();
      }
      if (!(entity instanceof LivingEntity living)) {
         return false;
      }
      if (!living.isAlive() || living.isDead()) {
         return false;
      }
      if (living instanceof ArmorStandEntity) {
         return false;
      }
      if (teams.get() && living.isTeammate(mc.player)) {
         return false;
      }
      if (living instanceof Tameable) {
         return false;
      }
      if (!CombatUtil.matchesTargetMode(living, targetMode.get())) {
         return false;
      }
      if (noInvisible.get() && living.isInvisible()) {
         return false;
      }
      if (noShields.get() && living instanceof PlayerEntity blocking && blocking.isBlocking()) {
         return false;
      }
      if (antiBot.get() && AntiBot.isBot(living)) {
         return false;
      }
      if (living instanceof PlayerEntity player && Selene.get != null
            && Selene.get.friendManager != null
            && Selene.get.friendManager.isFriend(player.getName().getString())) {
         return false;
      }
      return true;
   }

   private boolean click(EntityHitResult hit) {
      if (mc.options == null) {
         disarm();
         return false;
      }
      if (!fireMode.is("Hold LMB") && mc.options.attackKey.isPressed()) {
         disarm();
         return false;
      }
      boolean pressed = this.hit.press(hit);
      if (pressed) {
         disarm();
      }
      return pressed;
   }

   private void disarm() {
      armedAt = -1L;
      swingAt = -1L;
   }


   private boolean shouldFire() {
      if (mc.options == null) {
         return false;
      }
      return switch (fireMode.get()) {
         case "Hold LMB" -> mc.options.attackKey.isPressed();
         default -> true;
      };
   }
}
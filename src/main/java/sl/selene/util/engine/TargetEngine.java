package sl.selene.util.engine;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import sl.selene.Selene;
import sl.selene.module.impl.combat.CombatUtil;
import sl.selene.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class TargetEngine implements IMinecraft {

   public static class Query {
      public double range = 4.5D;
      public float fov = 180.0F;
      public boolean throughWalls;
      public boolean noInvisible = true;
      public boolean ignoreFriends = true;
      public boolean noTameable = true;
      public boolean teamsProtect;
      public String targetMode = "Everything";
      public String pointMode = "Chest";
      public String sort = "Distance";
      public boolean preferAirborne;
   }

   private final Query query = new Query();

   public Query query() {
      return query;
   }

   public LivingEntity findBest() {
      if (mc.player == null || mc.world == null) {
         return null;
      }
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      Vec3d eye = mc.player.getEyePos();
      double rangeSq = query.range * query.range;

      for (Entity entity : mc.world.getEntities()) {
         if (!isValid(entity)) {
            continue;
         }
         LivingEntity living = (LivingEntity) entity;
         Vec3d point = CombatUtil.aimPoint(living, query.pointMode);
         double distSq = eye.squaredDistanceTo(point);
         if (distSq > rangeSq) {
            continue;
         }
         float[] rot = CombatUtil.rotationTo(mc, point);
         float yawDiff = MathHelper.wrapDegrees(rot[0] - mc.player.getYaw());
         float pitchDiff = rot[1] - mc.player.getPitch();
         if (query.fov < 180.0F
               && (Math.abs(yawDiff) > query.fov / 2.0F || Math.abs(pitchDiff) > query.fov / 2.0F)) {
            continue;
         }
         if (!query.throughWalls && !mc.player.canSee(living)) {
            continue;
         }
         double dist = Math.sqrt(distSq);
         double offAngle = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
         double score = switch (query.sort) {
            case "Angle" -> offAngle * 2.0D + dist * 0.2D;
            case "Health" -> living.getHealth() * 2.0D + dist + offAngle;
            default -> dist + offAngle * 2.0D;
         };
         if (query.preferAirborne && !living.isOnGround()) {
            score -= 5.0D;
         }
         if (score < bestScore) {
            bestScore = score;
            best = living;
         }
      }
      return best;
   }

   private boolean isValid(Entity entity) {
      if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) {
         return false;
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
      if (query.noTameable && living instanceof Tameable) {
         return false;
      }
      if (query.teamsProtect && living.isTeammate(mc.player)) {
         return false;
      }
      if (query.noInvisible && living.isInvisible()) {
         return false;
      }
      if (query.ignoreFriends && living instanceof PlayerEntity player
            && Selene.get != null
            && Selene.get.friendManager != null
            && Selene.get.friendManager.isFriend(player.getName().getString())) {
         return false;
      }
      return CombatUtil.matchesTargetMode(living, query.targetMode);
   }
}

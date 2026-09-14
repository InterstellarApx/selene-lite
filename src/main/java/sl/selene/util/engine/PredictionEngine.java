package sl.selene.util.engine;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class PredictionEngine {

   private static final double GRAVITY = 0.08D;
   private static final double DRAG = 0.98D;
   private static final double GROUND_FRICTION = 0.55D;
   private static final double TERMINAL = 3.92D;

   private PredictionEngine() {
   }

   public static Vec3d linear(Entity target, int ticks) {
      if (target == null) {
         return Vec3d.ZERO;
      }
      if (ticks <= 0) {
         return target.getEntityPos();
      }
      Vec3d vel = target.getVelocity();
      return target.getEntityPos().add(vel.x * ticks, vel.y * ticks, vel.z * ticks);
   }

   public static Vec3d simulated(Entity target, int ticks) {
      if (target == null) {
         return Vec3d.ZERO;
      }
      if (ticks <= 0) {
         return target.getEntityPos();
      }
      Vec3d pos = target.getEntityPos();
      Vec3d vel = target.getVelocity();
      boolean grounded = target.isOnGround();
      for (int i = 0; i < ticks; i++) {
         if (grounded) {
            vel = new Vec3d(vel.x * GROUND_FRICTION, 0.0D, vel.z * GROUND_FRICTION);
         } else {
            vel = new Vec3d(vel.x * DRAG, Math.max((vel.y - GRAVITY) * DRAG, -TERMINAL), vel.z * DRAG);
         }
         pos = pos.add(vel);
      }
      return pos;
   }

   public static Vec3d simulatedUntilGround(LivingEntity target, int maxTicks) {
      if (target == null) {
         return Vec3d.ZERO;
      }
      if (maxTicks <= 0) {
         return target.getEntityPos();
      }
      Vec3d pos = target.getEntityPos();
      Vec3d vel = target.getVelocity();
      boolean grounded = target.isOnGround();
      for (int i = 0; i < maxTicks; i++) {
         if (grounded) {
            break;
         }
         vel = new Vec3d(vel.x * DRAG, Math.max((vel.y - GRAVITY) * DRAG, -TERMINAL), vel.z * DRAG);
         pos = pos.add(vel);
         if (target.getEntityWorld() != null) {
            BlockPos below = BlockPos.ofFloored(pos.x, pos.y - 0.01D, pos.z);
            if (!target.getEntityWorld().getBlockState(below)
                  .getCollisionShape(target.getEntityWorld(), below).isEmpty()) {
               break;
            }
         }
      }
      return pos;
   }

   public static int leadTicks(double distance, double speed) {
      if (speed <= 1.0E-4D) {
         return 0;
      }
      return Math.max(1, (int) Math.round(distance / speed));
   }

   public static Vec3d aimPoint(LivingEntity target, Vec3d predictedPos, String pointMode) {
      double height = target.getHeight();
      double y = switch (pointMode == null ? "Chest" : pointMode) {
         case "Head" -> predictedPos.y + height * 0.9D;
         case "Feet" -> predictedPos.y + 0.1D;
         default -> predictedPos.y + height * 0.5D;
      };
      return new Vec3d(predictedPos.x, y, predictedPos.z);
   }

   public static Box sweptBox(Entity target, int ticks) {
      if (target == null) {
         return null;
      }
      Vec3d delta = simulated(target, ticks).subtract(target.getEntityPos());
      Box current = target.getBoundingBox();
      return current.offset(delta).union(current);
   }
}

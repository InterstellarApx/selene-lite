package sl.selene.util.sim;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import sl.selene.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class BreakSim implements IMinecraft {

   private BlockPos target;
   private Direction face;

   public boolean isBreaking() {
      return target != null;
   }

   public BlockPos getTarget() {
      return target;
   }

   public boolean start(BlockPos pos, Direction face) {
      if (mc.player == null || mc.interactionManager == null || pos == null || target != null) {
         return false;
      }
      Direction dir = face == null ? Direction.UP : face;
      if (!mc.interactionManager.attackBlock(pos, dir)) {
         return false;
      }
      mc.player.swingHand(Hand.MAIN_HAND);
      this.target = pos;
      this.face = dir;
      return true;
   }

   public boolean tick(BlockPos crosshairPos, Direction crosshairFace) {
      if (target == null || mc.player == null || mc.interactionManager == null) {
         return false;
      }
      if (crosshairPos == null || !crosshairPos.equals(target)) {
         stop();
         return false;
      }
      if (crosshairFace != null) {
         this.face = crosshairFace;
      }
      boolean progressed = mc.interactionManager.updateBlockBreakingProgress(target, face);
      mc.player.swingHand(Hand.MAIN_HAND);
      if (!progressed) {
         stop();
      }
      return progressed;
   }

   public void stop() {
      if (target != null && mc.interactionManager != null) {
         mc.interactionManager.cancelBlockBreaking();
      }
      target = null;
      face = null;
   }
}

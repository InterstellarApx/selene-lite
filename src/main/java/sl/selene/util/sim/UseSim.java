package sl.selene.util.sim;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import sl.selene.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class UseSim implements IMinecraft {

   private boolean useHeld;
   private InputUtil.Key boundUseKey;
   private int boundUseKeyCode = -1;

   public boolean isUseHeld() {
      return useHeld;
   }

   public boolean interactItem(Hand hand) {
      if (mc.player == null || mc.interactionManager == null) {
         return false;
      }
      return mc.interactionManager.interactItem(mc.player, hand).isAccepted();
   }

   public boolean interactBlock(BlockPos pos, Direction face, Hand hand) {
      if (mc.player == null || mc.interactionManager == null || pos == null || face == null) {
         return false;
      }
      Vec3d hitPos = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
      BlockHitResult hit = new BlockHitResult(hitPos, face, pos, false);
      ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hit);
      if (result.isAccepted()) {
         mc.player.swingHand(hand);
      }
      return result.isAccepted();
   }

   public void pressUse() {
      if (useHeld || mc.options == null) {
         return;
      }
      refreshBoundUseKey();
      if (boundUseKeyCode > 0 && boundUseKey != null) {
         KeyBinding.setKeyPressed(boundUseKey, true);
         KeyBinding.onKeyPressed(boundUseKey);
      } else if (boundUseKey != null) {
         KeyBinding.onKeyPressed(boundUseKey);
      }
      useHeld = true;
   }

   public void releaseUse() {
      if (!useHeld) {
         return;
      }
      if (mc.options != null && boundUseKeyCode > 0 && boundUseKey != null) {
         InputUtil.Key current = InputUtil.fromTranslationKey(mc.options.useKey.getBoundKeyTranslationKey());
         if (current != null && current.getCategory() == boundUseKey.getCategory()
               && current.getCode() == boundUseKeyCode) {
            if (!physicallyPressed(boundUseKey)) {
               KeyBinding.setKeyPressed(boundUseKey, false);
            }
         } else {
            mc.options.useKey.setPressed(false);
         }
      }
      useHeld = false;
      boundUseKeyCode = -1;
      boundUseKey = null;
   }

   private void refreshBoundUseKey() {
      boundUseKey = null;
      boundUseKeyCode = -1;
      if (mc.options == null) {
         return;
      }
      InputUtil.Key key = InputUtil.fromTranslationKey(mc.options.useKey.getBoundKeyTranslationKey());
      if (key == null || key.getCode() == InputUtil.UNKNOWN_KEY.getCode()) {
         return;
      }
      boundUseKey = key;
      boundUseKeyCode = key.getCode();
   }

   private boolean physicallyPressed(InputUtil.Key key) {
      long handle = mc.getWindow() != null ? mc.getWindow().getHandle() : 0L;
      if (handle == 0L) {
         return false;
      }
      if (key.getCategory() == InputUtil.Type.MOUSE) {
         return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
      }
      if (key.getCategory() == InputUtil.Type.KEYSYM) {
         return mc.getWindow() != null && InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
      }
      return false;
   }
}

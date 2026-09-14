package sl.selene.util.engine;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;
import sl.selene.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class HitEngine implements IMinecraft {

   private static final long HOLD_MIN = 30L;
   private static final long HOLD_SPREAD = 55L;

   private boolean held;
   private long releaseAt;
   private int boundKeyCode = -1;
   private InputUtil.Key boundKey;
   private int lastAttackAge = Integer.MIN_VALUE;

   public boolean isHeld() {
      return held;
   }

   public int getLastAttackAge() {
      return lastAttackAge;
   }

   public boolean cooldownReady(float minProgress) {
      return mc.player != null && mc.player.getAttackCooldownProgress(0.5F) >= minProgress;
   }

   public boolean attackReady(int minTickGap) {
      return mc.player != null && mc.player.age - lastAttackAge >= minTickGap;
   }

   public boolean press(EntityHitResult hit) {
      if (mc.options == null || hit == null || mc.player == null) {
         return false;
      }
      mc.crosshairTarget = hit;
      refreshBoundKey();
      if (boundKeyCode > 0) {
         KeyBinding.setKeyPressed(boundKey, true);
         KeyBinding.onKeyPressed(boundKey);
      } else if (boundKey != null) {
         KeyBinding.onKeyPressed(boundKey);
      }
      held = true;
      releaseAt = System.currentTimeMillis() + HOLD_MIN + (long) (Math.random() * HOLD_SPREAD);
      lastAttackAge = mc.player.age;
      return true;
   }

   public void tick() {
      if (held && System.currentTimeMillis() >= releaseAt) {
         release();
      }
   }

   public void release() {
      if (!held) {
         return;
      }
      if (mc.options != null && boundKeyCode > 0 && boundKey != null) {
         InputUtil.Key current = InputUtil.fromTranslationKey(mc.options.attackKey.getBoundKeyTranslationKey());
         if (current != null && current.getCategory() == boundKey.getCategory()
               && current.getCode() == boundKeyCode) {
            if (!physicallyPressed(boundKey)) {
               KeyBinding.setKeyPressed(boundKey, false);
            }
         } else {
            mc.options.attackKey.setPressed(false);
         }
      }
      held = false;
      boundKeyCode = -1;
      boundKey = null;
   }

   private void refreshBoundKey() {
      boundKey = null;
      boundKeyCode = -1;
      if (mc.options == null) {
         return;
      }
      InputUtil.Key key = InputUtil.fromTranslationKey(mc.options.attackKey.getBoundKeyTranslationKey());
      if (key == null || key.getCode() == InputUtil.UNKNOWN_KEY.getCode()) {
         return;
      }
      boundKey = key;
      boundKeyCode = key.getCode();
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

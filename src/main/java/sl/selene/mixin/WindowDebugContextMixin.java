package sl.selene.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sl.selene.util.debug.GlDebugLog;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public class WindowDebugContextMixin {

   @Redirect(
      method = "<init>",
      at = @At(
         value = "INVOKE",
         target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"
      )
   )
   private long selene$createDebugContext(int width, int height, CharSequence title, long monitor, long share) {
      if (GlDebugLog.enabled()) {
         GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);
      }
      return GLFW.glfwCreateWindow(width, height, title, monitor, share);
   }
}

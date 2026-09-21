package sl.selene.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sl.selene.util.debug.GlDebugLog;

@Environment(EnvType.CLIENT)
@Mixin(GlDebug.class)
public class GlDebugMixin {

   @ModifyVariable(method = "enableDebug", at = @At("HEAD"), argsOnly = true, index = 0)
   private static int selene$verbosity(int verbosity) {
      return GlDebugLog.verbosity(verbosity);
   }

   @ModifyVariable(method = "enableDebug", at = @At("HEAD"), argsOnly = true, index = 1)
   private static boolean selene$sync(boolean sync) {
      return GlDebugLog.sync(sync);
   }

   @Inject(method = "onDebugMessage", at = @At("HEAD"))
   private void selene$traceCallSite(
         int source, int type, int id, int severity, int length, long message, long userParam, CallbackInfo ci) {
      GlDebugLog.onMessage(source, type, id, severity);
   }
}

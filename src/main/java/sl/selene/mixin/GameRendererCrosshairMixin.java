package sl.selene.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sl.selene.util.player.RotationUtil;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererCrosshairMixin {

   @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
   private void selene$traceFromSilentRotation(float tickDelta, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null || client.world == null) {
         return;
      }
      if (!RotationUtil.hasSilentRotation()) {
         return;
      }
      double reach = Math.max(client.player.getBlockInteractionRange(), client.player.getEntityInteractionRange());
      HitResult traced = RotationUtil.recomputeTrace(client.crosshairTarget, tickDelta, reach);
      client.crosshairTarget = traced;
      client.targetedEntity = traced instanceof net.minecraft.util.hit.EntityHitResult entityHit
            ? entityHit.getEntity()
            : null;
   }
}
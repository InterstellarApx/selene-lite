package sl.selene.util.render.ui;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import sl.selene.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public final class EntityHead {

   private static final int GL_TEXTURE_2D = 3553;
   private static final int GL_TEXTURE_MAG_FILTER = 10240;
   private static final int GL_TEXTURE_MIN_FILTER = 10241;
   private static final int GL_NEAREST = 9728;

   private static final Map<EntityType<?>, Optional<Face>> FACES = new HashMap<>();

   private EntityHead() {
   }

   private record Face(float u0, float v0, float u1, float v1, float aspect) {
   }

   public static boolean draw(Renderer2D renderer, LivingEntity entity, float x, float y, float size, float rounding) {
      MinecraftClient client = MinecraftClient.getInstance();
      EntityRenderer<?, ?> renderer3d = client.getEntityRenderDispatcher().getRenderer(entity);
      if (!(renderer3d instanceof LivingEntityRenderer<?, ?, ?> living)) {
         return false;
      }

      Face face = FACES.computeIfAbsent(entity.getType(), type -> frontFace(living.getModel())).orElse(null);
      if (face == null) {
         return false;
      }

      float tickProgress = client.getRenderTickCounter().getTickProgress(true);
      int texture = textureId(client, texture(living, entity, tickProgress));
      if (texture <= 0) {
         return false;
      }

      float width = face.aspect() >= 1.0F ? size : size * face.aspect();
      float height = face.aspect() >= 1.0F ? size / face.aspect() : size;
      renderer.drawRgbaTextureWithUVRounded(texture, x + (size - width) * 0.5F, y + (size - height) * 0.5F, width,
            height, face.u0(), face.v0(), face.u1(), face.v1(), rounding);
      return true;
   }

   public static int textureId(MinecraftClient client, Identifier id) {
      if (id == null) {
         return 0;
      }

      AbstractTexture texture = client.getTextureManager().getTexture(id);
      if (texture == null || !(texture.getGlTexture() instanceof GlTexture glTexture) || glTexture.getGlId() <= 0) {
         return 0;
      }

      GlStateManager._bindTexture(glTexture.getGlId());
      GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
      GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
      return glTexture.getGlId();
   }

   @SuppressWarnings("unchecked")
   private static <T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> Identifier texture(
         LivingEntityRenderer<?, ?, ?> renderer, LivingEntity entity, float tickProgress) {
      LivingEntityRenderer<T, S, M> typed = (LivingEntityRenderer<T, S, M>) renderer;
      return typed.getTexture(typed.getAndUpdateRenderState((T) entity, tickProgress));
   }

   private static Optional<Face> frontFace(EntityModel<?> model) {
      ModelPart named = model.getRootPart().createPartGetter().apply("head");
      ModelPart head = named != null ? named : model.getRootPart();

      ModelPart.Cuboid[] largest = new ModelPart.Cuboid[1];
      head.forEachCuboid(new MatrixStack(), (entry, path, index, cuboid) -> {
         if (largest[0] == null || frontArea(cuboid) > frontArea(largest[0])) {
            largest[0] = cuboid;
         }
      });

      return largest[0] == null ? Optional.empty() : faceOf(largest[0]);
   }

   private static float frontArea(ModelPart.Cuboid cuboid) {
      return (cuboid.maxX - cuboid.minX) * (cuboid.maxY - cuboid.minY);
   }

   private static Optional<Face> faceOf(ModelPart.Cuboid cuboid) {
      for (ModelPart.Quad quad : cuboid.sides) {
         if (quad.direction().z() > -0.5F) {
            continue;
         }

         float u0 = Float.MAX_VALUE;
         float v0 = Float.MAX_VALUE;
         float u1 = -Float.MAX_VALUE;
         float v1 = -Float.MAX_VALUE;
         for (ModelPart.Vertex vertex : quad.vertices()) {
            u0 = Math.min(u0, vertex.u());
            v0 = Math.min(v0, vertex.v());
            u1 = Math.max(u1, vertex.u());
            v1 = Math.max(v1, vertex.v());
         }

         float height = cuboid.maxY - cuboid.minY;
         if (u1 > u0 && v1 > v0 && height > 0.0F) {
            return Optional.of(new Face(u0, v0, u1, v1, (cuboid.maxX - cuboid.minX) / height));
         }
      }

      return Optional.empty();
   }
}

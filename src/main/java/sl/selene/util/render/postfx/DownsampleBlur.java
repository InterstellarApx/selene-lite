package sl.selene.util.render.postfx;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import sl.selene.util.render.backends.gl.GlState;
import sl.selene.util.render.backends.gl.ShaderProgram;
import sl.selene.util.render.core.RenderFrameMetrics;

@Environment(EnvType.CLIENT)
public final class DownsampleBlur {
   private static final int GL_COLOR_ATTACHMENT0 = 36064;
   private static final int MAX_PASSES = 8;
   private static final IntBuffer DRAW_BUFFER_COLOR0 = BufferUtils.createIntBuffer(1);

   static {
      DRAW_BUFFER_COLOR0.put(GL_COLOR_ATTACHMENT0).flip();
   }

   private final ShaderProgram kawaseProgram;
   private final int intermediateInternalFormat;
   private final int intermediatePixelType;
   private final int samplerLoc;
   private final int texelSizeLoc;
   private final int offsetLoc;
   private int quadVao;
   private int quadVbo;
   private final DownsampleBlur.LevelTarget pingTarget = new DownsampleBlur.LevelTarget();
   private final DownsampleBlur.LevelTarget pongTarget = new DownsampleBlur.LevelTarget();

   public float minimumRadius() {
      return 0.5F;
   }

   public float smallKernelThreshold() {
      return 30.0F;
   }

   public DownsampleBlur() {

      this(32856, 5121);
   }

   public DownsampleBlur(int intermediateInternalFormat, int intermediatePixelType) {
      if (intermediateInternalFormat == 0) {
         throw new IllegalArgumentException("intermediateInternalFormat must be a valid OpenGL format constant");
      } else if (intermediatePixelType == 0) {
         throw new IllegalArgumentException("intermediatePixelType must be a valid OpenGL pixel type constant");
      } else {
         this.kawaseProgram = ShaderProgram.fromResources("assets/selene/shaders/blur/blur_fullscreen.vert", "assets/selene/shaders/blur/blur_downsample.frag");
         this.intermediateInternalFormat = intermediateInternalFormat;
         this.intermediatePixelType = intermediatePixelType;
         this.samplerLoc = this.kawaseProgram.getUniformLocation("uSource");
         this.texelSizeLoc = this.kawaseProgram.getUniformLocation("uTexelSize");
         this.offsetLoc = this.kawaseProgram.getUniformLocation("uOffset");

         this.quadVao = GL30.glGenVertexArrays();
         this.quadVbo = GL15.glGenBuffers();
         GL30.glBindVertexArray(this.quadVao);
         GL15.glBindBuffer(34962, this.quadVbo);
         float[] vertices = new float[]{-1.0F, -1.0F, 0.0F, 0.0F, 1.0F, -1.0F, 1.0F, 0.0F, -1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
         GL15.glBufferData(34962, vertices, 35044);
         int stride = 16;
         GL20.glEnableVertexAttribArray(0);
         GL20.glVertexAttribPointer(0, 2, 5126, false, stride, 0L);
         GL20.glEnableVertexAttribArray(1);
         GL20.glVertexAttribPointer(1, 2, 5126, false, stride, 8L);
         GL30.glBindVertexArray(0);
         GL15.glBindBuffer(34962, 0);
      }
   }

   public void destroy() {
      this.destroyLevel(this.pingTarget);
      this.destroyLevel(this.pongTarget);
      if (this.quadVao != 0) {
         GL30.glDeleteVertexArrays(this.quadVao);
         this.quadVao = 0;
      }

      if (this.quadVbo != 0) {
         GL15.glDeleteBuffers(this.quadVbo);
         this.quadVbo = 0;
      }

      this.kawaseProgram.delete();
   }

   public int blurFromColorTexture(int sourceTexture, int width, int height, float radiusPx) {
      return this.blurFromColorTexture(sourceTexture, width, height, radiusPx, true);
   }

   public int blurFromColorTexture(int sourceTexture, int width, int height, float radiusPx, boolean preserveState) {
      if (sourceTexture != 0 && width > 0 && height > 0) {
         int passCount = passCount(Math.max(radiusPx, 0.5F));
         int halfWidth = (width + 1) / 2;
         int halfHeight = (height + 1) / 2;
         this.ensureLevel(this.pingTarget, halfWidth, halfHeight);
         this.ensureLevel(this.pongTarget, halfWidth, halfHeight);

         GlState.Snapshot snapshot = preserveState ? GlState.push() : null;

         int var12;
         try (TextureUnitGuard unit0 = TextureUnitGuard.capture(0, 3553)) {
            GL11.glDisable(3089);
            GL11.glDisable(2929);
            GL11.glDisable(2884);
            GL11.glDisable(3042);
            GlState.disableFramebufferSrgb();
            GL13.glActiveTexture(33984);
            GL30.glBindVertexArray(this.quadVao);
            var12 = this.runKawase(sourceTexture, width, height, passCount);
         } finally {
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(36160, 0);
            GL13.glActiveTexture(33984);
            GL11.glBindTexture(3553, 0);
            if (preserveState && snapshot != null) {
               GlState.pop(snapshot);
            }
         }

         return var12;
      } else {
         return 0;
      }
   }

   private int runKawase(int sourceTexture, int width, int height, int passCount) {
      this.kawaseProgram.use();
      if (this.samplerLoc >= 0) {
         GL20.glUniform1i(this.samplerLoc, 0);
      }

      this.pass(sourceTexture, width, height, this.pingTarget, 1.0F);
      DownsampleBlur.LevelTarget current = this.pingTarget;
      for (int i = 1; i <= passCount; i++) {
         DownsampleBlur.LevelTarget target = current == this.pingTarget ? this.pongTarget : this.pingTarget;
         this.pass(current.texture, current.width, current.height, target, i - 0.5F);
         current = target;
      }

      return current.texture;
   }

   private void pass(int sourceTexture, int sourceWidth, int sourceHeight, DownsampleBlur.LevelTarget target, float offset) {
      if (this.texelSizeLoc >= 0) {
         GL20.glUniform2f(this.texelSizeLoc, 1.0F / Math.max(1, sourceWidth), 1.0F / Math.max(1, sourceHeight));
      }
      if (this.offsetLoc >= 0) {
         GL20.glUniform1f(this.offsetLoc, offset);
      }
      this.bindTarget(target);
      GL11.glBindTexture(3553, sourceTexture);
      this.drawQuad();
   }

   private void drawQuad() {
      RenderFrameMetrics.getInstance().recordDrawCall(2);
      GL11.glDrawArrays(5, 0, 4);
   }

   private void bindTarget(DownsampleBlur.LevelTarget target) {
      GL30.glBindFramebuffer(36160, target.fbo);
      GL11.glViewport(0, 0, target.width, target.height);

      GL20.glDrawBuffers(DRAW_BUFFER_COLOR0);
   }

   private void ensureLevel(DownsampleBlur.LevelTarget target, int width, int height) {
      if (target.texture != 0 && (target.width != width || target.height != height)) {
         GL11.glDeleteTextures(target.texture);
         GL30.glDeleteFramebuffers(target.fbo);
         target.texture = 0;
         target.fbo = 0;
      }

      if (target.texture == 0) {
         target.texture = this.createRenderTexture(width, height);
         target.fbo = this.createFramebuffer(target.texture);
      }

      target.width = width;
      target.height = height;
   }

   private void destroyLevel(DownsampleBlur.LevelTarget target) {
      if (target != null) {
         if (target.texture != 0) {
            GL11.glDeleteTextures(target.texture);
            target.texture = 0;
         }

         if (target.fbo != 0) {
            GL30.glDeleteFramebuffers(target.fbo);
            target.fbo = 0;
         }

         target.width = 0;
         target.height = 0;
      }
   }

   private int createRenderTexture(int width, int height) {
      int tex = GL11.glGenTextures();
      GL11.glBindTexture(3553, tex);
      GL11.glTexParameteri(3553, 10241, 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      GL11.glTexParameteri(3553, 10242, 33071);
      GL11.glTexParameteri(3553, 10243, 33071);
      GL11.glTexImage2D(3553, 0, this.intermediateInternalFormat, width, height, 0, 6408, this.intermediatePixelType, (ByteBuffer)null);
      GL11.glBindTexture(3553, 0);
      return tex;
   }

   private int createFramebuffer(int texture) {
      int fbo = GL30.glGenFramebuffers();
      GL30.glBindFramebuffer(36160, fbo);
      GL30.glFramebufferTexture2D(36160, 36064, 3553, texture, 0);
      int status = GL30.glCheckFramebufferStatus(36160);
      GL30.glBindFramebuffer(36160, 0);
      if (status != 36053) {
         GL30.glDeleteFramebuffers(fbo);
         GL11.glDeleteTextures(texture);
         throw new IllegalStateException("Blur framebuffer incomplete: status=" + status);
      } else {
         return fbo;
      }
   }

   private static int passCount(float radiusPx) {
      return Math.max(1, Math.min(MAX_PASSES, (int) Math.ceil(Math.sqrt(radiusPx))));
   }

   @Environment(EnvType.CLIENT)
   private static final class LevelTarget {
      int fbo;
      int texture;
      int width;
      int height;
   }
}
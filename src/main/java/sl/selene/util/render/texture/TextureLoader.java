package sl.selene.util.render.texture;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import sl.selene.util.render.backends.RenderBackend;
import sl.selene.util.render.backends.gl.ResourceUtils;

@Environment(EnvType.CLIENT)
public final class TextureLoader {
   private static RenderBackend backend;
   private static final Map<String, Integer> textureCache = new HashMap<>();

   private TextureLoader() {
   }

   public static void initialize(RenderBackend renderBackend) {
      backend = renderBackend;
   }

   public static RenderBackend getBackend() {
      return backend;
   }

   public static int load(String resourcePath) {
      if (backend == null) {
         throw new IllegalStateException("TextureLoader.initialize() must be called first");
      } else {
         Integer cached = textureCache.get(resourcePath);
         if (cached != null) {
            return cached;
         } else {
            int textureId = loadTexture(resourcePath);
            if (textureId > 0) {
               textureCache.put(resourcePath, textureId);
            }

            return textureId;
         }
      }
   }

   public static int loadBase64(String resourcePath) {
      if (backend == null) {
         throw new IllegalStateException("TextureLoader.initialize() must be called first");
      }

      String cacheKey = "base64:" + resourcePath;
      Integer cached = textureCache.get(cacheKey);
      if (cached != null) {
         return cached;
      }

      try {
         ByteBuffer encodedBuffer = ResourceUtils.readBinary(resourcePath);
         byte[] encodedBytes = new byte[encodedBuffer.remaining()];
         encodedBuffer.get(encodedBytes);
         byte[] pngBytes = Base64.getMimeDecoder().decode(new String(encodedBytes, StandardCharsets.US_ASCII));
         int textureId = loadFromPngBytes(pngBytes);
         if (textureId > 0) {
            textureCache.put(cacheKey, textureId);
         }
         return textureId;
      } catch (Exception exception) {
         System.err.println("Failed to load base64 texture resource: " + resourcePath);
         exception.printStackTrace();
         return 0;
      }
   }

   public static int loadDistanceField(String resourcePath, float range) {
      if (backend == null) {
         throw new IllegalStateException("TextureLoader.initialize() must be called first");
      }

      String cacheKey = "sdf:" + resourcePath;
      Integer cached = textureCache.get(cacheKey);
      if (cached != null) {
         return cached;
      }

      int textureId = decodeDistanceField(resourcePath, range);
      if (textureId > 0) {
         textureCache.put(cacheKey, textureId);
      }
      return textureId;
   }

   private static int decodeDistanceField(String resourcePath, float range) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         IntBuffer w = stack.mallocInt(1);
         IntBuffer h = stack.mallocInt(1);
         IntBuffer comp = stack.mallocInt(1);
         ByteBuffer image = STBImage.stbi_load_from_memory(ResourceUtils.readBinary(resourcePath), w, h, comp, 4);
         if (image == null) {
            System.err.println("Failed to decode texture: " + resourcePath + " - " + STBImage.stbi_failure_reason());
            return 0;
         }

         try {
            DistanceField.encodeFromAlpha(image, w.get(0), h.get(0), range);
            return backend.createMsdfTexture(w.get(0), h.get(0), image);
         } finally {
            STBImage.stbi_image_free(image);
         }
      } catch (Exception exception) {
         System.err.println("Failed to load distance field texture: " + resourcePath);
         exception.printStackTrace();
         return 0;
      }
   }

   private static int loadTexture(String resourcePath) {
      ByteBuffer imageBuffer;
      try {
         imageBuffer = ResourceUtils.readBinary(resourcePath);
      } catch (Exception var12) {
         System.err.println("Failed to read texture resource: " + resourcePath);
         var12.printStackTrace();
         return 0;
      }

      MemoryStack stack = MemoryStack.stackPush();

      int width;
      label49: {
         int var10;
         try {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, w, h, comp, 4);
            if (image == null) {
               System.err.println("Failed to decode texture: " + resourcePath + " - " + STBImage.stbi_failure_reason());
               width = 0;
               break label49;
            }

            width = w.get(0);
            int height = h.get(0);
            bleedTransparentColor(image, width, height);
            int textureId = backend.createImageTexture(width, height, image);
            STBImage.stbi_image_free(image);
            System.out.println("[TextureLoader] Loaded: " + resourcePath + " (" + width + "x" + height + ") -> ID " + textureId);
            var10 = textureId;
         } catch (Throwable var13) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var11) {
                  var13.addSuppressed(var11);
               }
            }

            throw var13;
         }

         if (stack != null) {
            stack.close();
         }

         return var10;
      }

      if (stack != null) {
         stack.close();
      }

      return width;
   }

   public static void clearCache() {
      textureCache.clear();
   }

   public static int loadFromPngBytes(byte[] pngData) {
      if (backend == null || pngData == null || pngData.length == 0) {
         return 0;
      }

      MemoryStack stack = MemoryStack.stackPush();

      try {
         IntBuffer w = stack.mallocInt(1);
         IntBuffer h = stack.mallocInt(1);
         IntBuffer comp = stack.mallocInt(1);
         ByteBuffer buffer = ByteBuffer.allocateDirect(pngData.length);
         buffer.put(pngData);
         buffer.flip();
         ByteBuffer image = STBImage.stbi_load_from_memory(buffer, w, h, comp, 4);
         if (image == null) {
            return 0;
         }

         bleedTransparentColor(image, w.get(0), h.get(0));
         int textureId = backend.createImageTexture(w.get(0), h.get(0), image);
         STBImage.stbi_image_free(image);
         return textureId;
      } catch (Throwable ignored) {
         return 0;
      } finally {
         stack.close();
      }
   }

   public static void releaseTexture(int textureId) {
      if (textureId > 0 && backend != null) {
         backend.destroyTexture(textureId);
      }
   }

   private static void bleedTransparentColor(ByteBuffer image, int width, int height) {
      int count = width * height;
      if (count <= 0 || image.capacity() < count * 4) {
         return;
      }

      boolean[] opaque = new boolean[count];
      for (int i = 0; i < count; i++) {
         opaque[i] = (image.get(i * 4 + 3) & 255) != 0;
      }

      boolean[] filled = opaque.clone();
      for (int pass = 0; pass < 16; pass++) {
         boolean changed = false;

         for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
               int index = y * width + x;
               if (opaque[index]) {
                  continue;
               }

               int r = 0;
               int g = 0;
               int b = 0;
               int n = 0;

               for (int dy = -1; dy <= 1; dy++) {
                  int ny = y + dy;
                  if (ny < 0 || ny >= height) {
                     continue;
                  }

                  for (int dx = -1; dx <= 1; dx++) {
                     int nx = x + dx;
                     if (nx < 0 || nx >= width) {
                        continue;
                     }

                     int neighbour = ny * width + nx;
                     if (!opaque[neighbour]) {
                        continue;
                     }

                     r += image.get(neighbour * 4) & 255;
                     g += image.get(neighbour * 4 + 1) & 255;
                     b += image.get(neighbour * 4 + 2) & 255;
                     n++;
                  }
               }

               if (n == 0) {
                  continue;
               }

               image.put(index * 4, (byte) (r / n));
               image.put(index * 4 + 1, (byte) (g / n));
               image.put(index * 4 + 2, (byte) (b / n));
               filled[index] = true;
               changed = true;
            }
         }

         if (!changed) {
            break;
         }

         System.arraycopy(filled, 0, opaque, 0, count);
      }
   }
}

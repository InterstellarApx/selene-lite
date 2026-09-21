package sl.selene.util.render.texture;

import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class DistanceField {

   private static final float FAR = 1.0E20F;

   private DistanceField() {
   }

   public static void encodeFromAlpha(ByteBuffer rgba, int width, int height, float range) {
      int count = width * height;
      float[] toInside = new float[count];
      float[] toOutside = new float[count];

      for (int i = 0; i < count; i++) {
         boolean inside = (rgba.get(i * 4 + 3) & 255) >= 128;
         toInside[i] = inside ? 0.0F : FAR;
         toOutside[i] = inside ? FAR : 0.0F;
      }

      squaredDistances(toInside, width, height);
      squaredDistances(toOutside, width, height);

      for (int i = 0; i < count; i++) {
         float outside = (float) Math.sqrt(toInside[i]);
         float inside = (float) Math.sqrt(toOutside[i]);
         float signed = inside > 0.0F ? inside - 0.5F : 0.5F - outside;
         float encoded = Math.max(0.0F, Math.min(1.0F, 0.5F + signed / range));
         byte value = (byte) Math.round(encoded * 255.0F);
         rgba.put(i * 4, value);
         rgba.put(i * 4 + 1, value);
         rgba.put(i * 4 + 2, value);
         rgba.put(i * 4 + 3, (byte) 255);
      }
   }

   private static void squaredDistances(float[] grid, int width, int height) {
      int longest = Math.max(width, height);
      float[] line = new float[longest];
      float[] result = new float[longest];
      int[] parabola = new int[longest];
      float[] boundary = new float[longest + 1];

      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            line[y] = grid[y * width + x];
         }
         transformLine(line, result, height, parabola, boundary);
         for (int y = 0; y < height; y++) {
            grid[y * width + x] = result[y];
         }
      }

      for (int y = 0; y < height; y++) {
         System.arraycopy(grid, y * width, line, 0, width);
         transformLine(line, result, width, parabola, boundary);
         System.arraycopy(result, 0, grid, y * width, width);
      }
   }

   private static void transformLine(float[] f, float[] d, int n, int[] v, float[] z) {
      int k = 0;
      v[0] = 0;
      z[0] = -FAR;
      z[1] = FAR;

      for (int q = 1; q < n; q++) {
         float s = intersection(f, v[k], q);
         while (s <= z[k]) {
            k--;
            s = intersection(f, v[k], q);
         }
         k++;
         v[k] = q;
         z[k] = s;
         z[k + 1] = FAR;
      }

      k = 0;
      for (int q = 0; q < n; q++) {
         while (z[k + 1] < q) {
            k++;
         }
         float offset = q - v[k];
         d[q] = offset * offset + f[v[k]];
      }
   }

   private static float intersection(float[] f, int p, int q) {
      return ((f[q] + (float) q * q) - (f[p] + (float) p * p)) / (2.0F * q - 2.0F * p);
   }
}

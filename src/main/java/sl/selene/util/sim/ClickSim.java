package sl.selene.util.sim;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.Random;

@Environment(EnvType.CLIENT)
public final class ClickSim {

   private final Random random = new Random();
   private long nextClickMs = -1L;
   private double fatigue;

   public void reset() {
      nextClickMs = -1L;
      fatigue = 0.0D;
   }

   public boolean isArmed() {
      return nextClickMs >= 0L;
   }

   public boolean tick(boolean wantClicking, double cps) {
      long now = System.currentTimeMillis();
      if (!wantClicking) {
         nextClickMs = -1L;
         fatigue = Math.max(0.0D, fatigue - 0.05D);
         return false;
      }
      fatigue = Math.min(1.0D, fatigue + 0.02D);
      if (nextClickMs < 0L) {
         nextClickMs = now + interval(cps);
         return false;
      }
      if (now < nextClickMs) {
         return false;
      }
      nextClickMs = now + interval(cps);
      return true;
   }

   private long interval(double cps) {
      double base = 1000.0D / Math.max(1.0D, Math.min(20.0D, cps));
      double gaussian = Math.max(-1.5D, Math.min(1.5D, random.nextGaussian()));
      long ms = Math.round(base + base * 0.18D * gaussian);
      if (random.nextDouble() < 0.04D) {
         ms += Math.round(base * (0.5D + random.nextDouble()));
      }
      ms = Math.round(ms * (1.0D + fatigue * 0.15D));
      return Math.max(30L, ms);
   }
}

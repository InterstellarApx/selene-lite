package sl.selene.ui.gui;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class GuiSoundPlayer {
   private static final boolean SUPPORTED = detectSoundSupport();

   private static boolean detectSoundSupport() {
      try {
         Class.forName("javax.sound.sampled.AudioSystem");
         return true;
      } catch (Throwable error) {
         return false;
      }
   }

   public static void preload() {
      if (SUPPORTED) {
         Engine.preload();
      }
   }

   public static void playOpenSound() {
      if (SUPPORTED) {
         Engine.playOpen();
      }
   }

   public static void playCloseSound() {
      if (SUPPORTED) {
         Engine.playClose();
      }
   }

   public static void releaseAll() {
      if (SUPPORTED) {
         Engine.releaseAll();
      }
   }

   private static final class Engine {
      private static Clip openClip;
      private static Clip closeClip;

      private static void preload() {
         Thread thread = new Thread(() -> {
            openClip = loadClip("open");
            closeClip = loadClip("close");
            System.out.println("[Selene] GUI sounds preloaded");
         }, "SeleneSoundPreload");
         thread.setDaemon(true);
         thread.start();
      }

      private static void playOpen() {
         playClip(openClip);
      }

      private static void playClose() {
         playClip(closeClip);
      }

      private static Clip loadClip(String location) {
         try {
            String resourcePath = "/assets/selene/sound/wav/" + location + ".wav";
            InputStream inputStream = GuiSoundPlayer.class.getResourceAsStream(resourcePath);
            if (inputStream == null) {
               System.out.println("[Selene] Sound not found: " + resourcePath);
               return null;
            }

            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedInputStream);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);

            FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            volumeControl.setValue((float) (Math.log(0.5) / Math.log(10.0) * 20.0));
            return clip;
         } catch (Exception var5) {
            System.out.println("[Selene] Sound load failed (" + location + "): " + var5.getMessage());
            return null;
         }
      }

      private static void playClip(Clip clip) {
         if (clip == null) {
            return;
         }
         try {
            clip.setFramePosition(0);
            clip.start();
         } catch (Exception var2) {
            System.out.println("[Selene] Sound playback failed: " + var2.getMessage());
         }
      }

      private static void releaseAll() {
         closeClip(openClip);
         openClip = null;
         closeClip(closeClip);
         closeClip = null;
      }

      private static void closeClip(Clip clip) {
         if (clip == null) return;
         try {
            if (clip.isRunning()) clip.stop();
         } catch (Exception ignored) {
         }
         try {
            if (clip.isOpen()) clip.close();
         } catch (Exception ignored) {
         }
      }
   }
}

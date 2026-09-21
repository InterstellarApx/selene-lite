package sl.selene.util.debug;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class GlDebugLog {

   private static final int TYPE_ERROR = 33356;
   private static final int TYPE_UNDEFINED_BEHAVIOR = 33358;
   private static final int MAX_FRAMES = 24;

   private static final boolean ENABLED = Boolean.getBoolean("selene.gldebug");
   private static final int VERBOSITY = Integer.getInteger("selene.gldebug.verbosity", 1);
   private static final int MAX_TRACES = Integer.getInteger("selene.gldebug.traces", 5);

   private static final Map<Long, Integer> HITS = new ConcurrentHashMap<>();

   private GlDebugLog() {
   }

   public static boolean enabled() {
      return ENABLED;
   }

   public static int verbosity(int vanilla) {
      return ENABLED ? Math.max(VERBOSITY, vanilla) : vanilla;
   }

   public static boolean sync(boolean vanilla) {
      return vanilla || ENABLED;
   }

   public static void onMessage(int source, int type, int id, int severity) {
      if (!ENABLED || (type != TYPE_ERROR && type != TYPE_UNDEFINED_BEHAVIOR)) {
         return;
      }

      long key = (long) type << 32 | id & 0xFFFFFFFFL;
      int hit = HITS.merge(key, 1, Integer::sum);
      if (hit > MAX_TRACES) {
         return;
      }

      StringBuilder out = new StringBuilder(512);
      out.append("[Selene/GlDebug] ")
         .append(net.minecraft.client.gl.GlDebug.getType(type))
         .append(" id=").append(id)
         .append(" source=").append(net.minecraft.client.gl.GlDebug.getSource(source))
         .append(" severity=").append(net.minecraft.client.gl.GlDebug.getSeverity(severity))
         .append(" (").append(hit).append('/').append(MAX_TRACES).append(')');
      if (hit == MAX_TRACES) {
         out.append(" [last trace for this id]");
      }
      out.append(System.lineSeparator());

      appendCallSite(out);
      System.err.print(out);
   }

   private static void appendCallSite(StringBuilder out) {
      StackTraceElement[] frames = Thread.currentThread().getStackTrace();
      int printed = 0;

      for (StackTraceElement frame : frames) {
         if (printed == 0 && isPlumbing(frame)) {
            continue;
         }
         out.append("\tat ").append(frame).append(System.lineSeparator());
         if (++printed >= MAX_FRAMES) {
            out.append("\t...").append(System.lineSeparator());
            break;
         }
      }

      if (printed == 0) {
         out.append("\t<no call site: debug output is asynchronous>").append(System.lineSeparator());
      }
   }

   private static boolean isPlumbing(StackTraceElement frame) {
      String owner = frame.getClassName();
      return owner.equals("java.lang.Thread")
            || owner.startsWith("sl.selene.util.debug.GlDebugLog")
            || owner.startsWith("net.minecraft.client.gl.GlDebug")
            || owner.startsWith("org.lwjgl.system.")
            || owner.startsWith("org.lwjgl.opengl.GLDebugMessage");
   }
}

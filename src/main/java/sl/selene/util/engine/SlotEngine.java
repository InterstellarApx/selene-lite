package sl.selene.util.engine;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import sl.selene.mixin.ClientPlayerInteractionManagerAccessor;
import sl.selene.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class SlotEngine implements IMinecraft {

   private int savedSlot = -1;
   private int activeSlot = -1;
   private int swapAge = -1;
   private int restoreAge = -1;

   public boolean isActive() {
      return activeSlot >= 0;
   }

   public int getActiveSlot() {
      return activeSlot;
   }

   public boolean holds(int slot) {
      return activeSlot >= 0 && activeSlot == slot;
   }

   public boolean begin(int slot) {
      if (mc.player == null || mc.player.getInventory() == null || slot < 0 || slot > 8) {
         return false;
      }
      if (activeSlot >= 0 && activeSlot != slot) {
         return switchTo(slot);
      }
      activeSlot = slot;
      if (savedSlot < 0) {
         savedSlot = mc.player.getInventory().getSelectedSlot();
      }
      swapAge = mc.player.age;
      if (mc.player.getInventory().getSelectedSlot() != slot) {
         mc.player.getInventory().setSelectedSlot(slot);
      }
      syncSelectedSlot();
      return true;
   }

   public boolean selectNow(int slot) {
      if (!begin(slot)) {
         return false;
      }
      syncSelectedSlot();
      return true;
   }

   public boolean ready(int delayTicks) {
      return activeSlot >= 0 && swapAge >= 0 && mc.player != null
            && mc.player.age - swapAge >= Math.max(1, delayTicks);
   }

   public int ageSinceSwap() {
      return swapAge >= 0 && mc.player != null ? mc.player.age - swapAge : Integer.MAX_VALUE;
   }

   public void scheduleRestore(int delayTicks) {
      if (activeSlot >= 0 && mc.player != null) {
         restoreAge = mc.player.age + Math.max(1, delayTicks);
      }
   }

   public boolean restoreDue() {
      return restoreAge >= 0 && mc.player != null && mc.player.age >= restoreAge;
   }

   public void restore() {
      restoreAge = -1;
      swapAge = -1;
      if (mc.player == null || mc.player.getInventory() == null) {
         savedSlot = -1;
         activeSlot = -1;
         return;
      }
      if (savedSlot >= 0 && mc.player.getInventory().getSelectedSlot() != savedSlot) {
         mc.player.getInventory().setSelectedSlot(savedSlot);
      }
      syncSelectedSlot();
      savedSlot = -1;
      activeSlot = -1;
   }

   public boolean switchTo(int slot) {
      if (mc.player == null || mc.player.getInventory() == null || slot < 0 || slot > 8) {
         return false;
      }
      if (activeSlot < 0) {
         return begin(slot);
      }
      activeSlot = slot;
      swapAge = mc.player.age;
      mc.player.getInventory().setSelectedSlot(slot);
      syncSelectedSlot();
      return true;
   }

   private void syncSelectedSlot() {
      if (mc.interactionManager instanceof ClientPlayerInteractionManagerAccessor accessor) {
         accessor.invokeSyncSelectedSlot();
      }
   }

   public void clearSwapTiming() {
      swapAge = -1;
   }

   public void reset() {
      savedSlot = -1;
      activeSlot = -1;
      swapAge = -1;
      restoreAge = -1;
   }
}

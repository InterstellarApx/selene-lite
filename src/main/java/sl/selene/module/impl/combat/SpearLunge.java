package sl.selene.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import sl.selene.event.EventInit;
import sl.selene.event.impl.EventChangeWorld;
import sl.selene.event.lifecycle.PreClientTickEvent;
import sl.selene.mixin.ClientPlayerInteractionManagerAccessor;
import sl.selene.module.api.Category;
import sl.selene.module.api.IModule;
import sl.selene.module.api.Module;
import sl.selene.module.api.setting.Setting;
import sl.selene.module.api.setting.impl.BindSettings;
import sl.selene.module.api.setting.impl.SliderSetting;
import sl.selene.util.engine.SlotEngine;

@IModule(
   name = "Spear Lunge",
   description = "Attribute swaps to a Lunge spear on a key press, jabs with it and swaps back",
   category = Category.Combat,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class SpearLunge extends Module {

   public static BindSettings activationKey = new BindSettings("Activation Key", -1);
   public static SliderSetting swapBackDelay = new SliderSetting("Swap Back Delay", 1.0F, 1.0F, 10.0F, 1.0F, false);

   private static final int SERVER_CHARGE_LENIENCY = 5;
   private static final int ARMED_TIMEOUT = 20;

   private enum Stage {
      IDLE,
      ARMED,
      JABBED
   }

   private final SlotEngine slots = new SlotEngine();
   private Stage stage = Stage.IDLE;
   private boolean keyWasDown;
   private int stageAge;

   public SpearLunge() {
      this.addSettings(new Setting[] { activationKey, swapBackDelay });
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.swapBack();
   }

   @EventInit
   public void onWorldChange(EventChangeWorld event) {
      this.slots.reset();
      this.stage = Stage.IDLE;
   }

   @EventInit
   public void onTick(PreClientTickEvent event) {
      if (mc.player == null || mc.world == null || mc.interactionManager == null) {
         return;
      }

      boolean keyDown = mc.currentScreen == null && activationKey.isKeyDown(activationKey.get());
      boolean pressed = keyDown && !this.keyWasDown;
      this.keyWasDown = keyDown;

      if (this.stage == Stage.IDLE && pressed) {
         this.enter(Stage.ARMED);
      }
      if (this.stage == Stage.ARMED) {
         this.lunge();
      }
      if (this.stage == Stage.JABBED && this.swapBackDue()) {
         this.swapBack();
      }
   }

   private void lunge() {
      int slot = SpearUtil.findSpearSlot(true);
      if (slot < 0 || mc.player.isUsingItem() || mc.player.age - this.stageAge > ARMED_TIMEOUT) {
         this.stage = Stage.IDLE;
         return;
      }

      ItemStack spear = mc.player.getInventory().getStack(slot);
      PiercingWeaponComponent weapon = spear.get(DataComponentTypes.PIERCING_WEAPON);
      if (weapon == null) {
         this.stage = Stage.IDLE;
         return;
      }
      if (mc.player.isBelowMinimumAttackCharge(spear, SERVER_CHARGE_LENIENCY)) {
         return;
      }

      if (mc.player.getInventory().getSelectedSlot() != slot) {
         this.slots.begin(slot);
      }
      ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).invokeAttackWithPiercingWeapon(weapon);
      mc.player.swingHand(Hand.MAIN_HAND);
      this.enter(Stage.JABBED);
   }

   private void swapBack() {
      this.slots.restore();
      this.stage = Stage.IDLE;
   }

   private boolean swapBackDue() {
      return mc.player.age - this.stageAge >= Math.max(1, Math.round(swapBackDelay.get()));
   }

   private void enter(Stage next) {
      this.stage = next;
      this.stageAge = mc.player.age;
   }
}

package sl.selene.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import sl.selene.module.api.setting.Setting;
import sl.selene.util.keyboard.Keyboard;

@Environment(EnvType.CLIENT)
public class BindSettings extends Setting {
   public int key;
   public final int defaultKey;
   public String description;
   public boolean active;

   public BindSettings(String name, int key) {
      this.name = name;
      this.key = key;
      this.defaultKey = key;
      this.description = this.description;
   }

   public int get() {
      return this.key;
   }

   public void set(int key) {
      this.key = key;
   }

   public void reset() {
      this.key = this.defaultKey;
   }

   public BindSettings hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }

   public boolean isKeyDown(int keyCode) {
      return Keyboard.isKeyDown(keyCode);
   }
}
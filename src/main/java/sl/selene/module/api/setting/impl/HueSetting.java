package sl.selene.module.api.setting.impl;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import sl.selene.module.api.setting.Setting;
import sl.selene.util.color.ColorUtil;
import sl.selene.util.render.math.animation.Animation;
import sl.selene.util.render.math.animation.impl.EaseInOutQuad;

@Environment(EnvType.CLIENT)
public class HueSetting extends Setting {
   public float current;
   public float defaultValue;
   public float defaultSaturation = 1.0F;
   public float defaultBrightness = 1.0F;
   public float minimum;
   public float maximum;
   public float increment;
   public float sliderWidth;
   public boolean sliding;
   public String description;
   public Animation animation = new EaseInOutQuad(300, 1.0);
   public float saturation = 1.0F;
   public float brightness = 1.0F;

   public HueSetting(String name, float current) {
      this.name = name;
      this.minimum = 0.0F;
      this.current = current;
      this.defaultValue = current;
      this.maximum = 106.0F;
      this.increment = 1.0F;
      this.saturation = 1.0F;
      this.brightness = 1.0F;
   }

   public HueSetting(String name, float current, float saturation, float brightness) {
      this.name = name;
      this.minimum = 0.0F;
      this.current = current;
      this.defaultValue = current;
      this.maximum = 106.0F;
      this.increment = 1.0F;
      this.saturation = saturation;
      this.defaultSaturation = saturation;
      this.brightness = brightness;
      this.defaultBrightness = brightness;
   }

   public void reset() {
      this.current = Math.max(this.minimum, Math.min(this.maximum, this.defaultValue));
      this.saturation = this.defaultSaturation;
      this.brightness = this.defaultBrightness;
   }

   public HueSetting hidden(Supplier<Boolean> hidden) {
      this.hidden = hidden;
      return this;
   }

   public int getColor() {
      return ColorUtil.hsbToRgb(this.current / this.maximum, this.saturation, this.brightness);
   }

   public float getHue() {
      return this.current / this.maximum;
   }

   public int getRGB() {
      return this.getColor();
   }

   public int getRGBA(int alpha) {
      return alpha << 24 | this.getColor() & 0xFFFFFF;
   }
}
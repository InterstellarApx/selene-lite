package sl.selene.module.impl.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import sl.selene.event.EventInit;
import sl.selene.event.impl.EventChangeWorld;
import sl.selene.event.lifecycle.ClientTickEvent;
import sl.selene.module.api.Category;
import sl.selene.module.api.IModule;
import sl.selene.module.api.Module;
import sl.selene.module.api.setting.Setting;
import sl.selene.module.api.setting.impl.BooleanSetting;
import sl.selene.module.api.setting.impl.SliderSetting;

@IModule(
   name = "AntiBot",
   description = "Filters out fake players so combat modules ignore them",
   category = Category.Combat,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class AntiBot extends Module {

   public static BooleanSetting detectTab = new BooleanSetting("Tab Check", true);
   public static BooleanSetting detectDuplicate = new BooleanSetting("Duplicate Names", true);
   public static SliderSetting graceTicks = new SliderSetting("Grace Ticks", 40.0F, 0.0F, 200.0F, 5.0F, false);
   public static BooleanSetting detectNoGameMode = new BooleanSetting("No Game Mode", true);

   private static AntiBot instance;

   private static final Map<UUID, Integer> seenTicks = new HashMap<>();
   private static final Set<UUID> tabKnown = new HashSet<>();
   private static final Map<Integer, Boolean> resultCache = new HashMap<>();
   private static int botCount;

   public AntiBot() {
      instance = this;
      this.addSettings(new Setting[] {
         detectTab, detectDuplicate, graceTicks, detectNoGameMode
      });
   }

   @Override
   public String getArrayListSuffix() {
      return String.valueOf(botCount);
   }

   @EventInit
   public void onWorldChange(EventChangeWorld event) {
      seenTicks.clear();
      tabKnown.clear();
      resultCache.clear();
      botCount = 0;
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      resultCache.clear();
      seenTicks.keySet().removeIf(uuid -> uuid == null);
      tabKnown.clear();

      if (mc == null || mc.world == null || mc.player == null) {
         seenTicks.clear();
         botCount = 0;
         return;
      }

      if (!isActive()) {
         botCount = 0;
         return;
      }

      if (detectTab.get() || detectNoGameMode.get()) {
         scanTabList();
      }

      int count = 0;
      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player == null) {
            continue;
         }
         UUID uuid = safeUuid(player);
         if (uuid != null) {
            Integer age = seenTicks.get(uuid);
            seenTicks.put(uuid, age == null ? 1 : age + 1);
         }
         if (resolve(player)) {
            count++;
         }
      }
      botCount = count;
   }

   private void scanTabList() {
      if (mc.getNetworkHandler() == null) {
         return;
      }
      try {
         for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) {
               continue;
            }
            UUID id = entry.getProfile().id();
            if (id != null) {
               tabKnown.add(id);
            }
         }
      } catch (RuntimeException ignored) {
      }
   }

   private boolean resolve(PlayerEntity player) {
      Boolean cached = resultCache.get(player.getId());
      if (cached != null) {
         return cached;
      }
      boolean result = evaluate(player);
      resultCache.put(player.getId(), result);
      return result;
   }

   private boolean evaluate(PlayerEntity player) {
      if (player == mc.player || player == mc.getCameraEntity()) {
         return false;
      }
      UUID uuid = safeUuid(player);
      if (uuid == null) {
         return false;
      }

      boolean tabListed = tabKnown.contains(uuid);
      int age = ageOf(uuid);

      if (detectTab.get() && !tabListed && age > graceTicks.get()) {
         return true;
      }
      if (detectNoGameMode.get() && !tabListed && age > graceTicks.get()) {
         return true;
      }
      if (detectDuplicate.get() && hasDuplicateName(player, uuid)) {
         return true;
      }
      return false;
   }

   private boolean hasDuplicateName(PlayerEntity player, UUID uuid) {
      String name = nameOf(player);
      if (name == null || name.isEmpty()) {
         return false;
      }
      for (PlayerEntity other : mc.world.getPlayers()) {
         if (other == null || other == player) {
            continue;
         }
         UUID otherUuid = safeUuid(other);
         if (otherUuid != null && otherUuid.equals(uuid)) {
            continue;
         }
         if (name.equalsIgnoreCase(nameOf(other))) {
            return true;
         }
      }
      return false;
   }

   private int ageOf(UUID uuid) {
      Integer age = seenTicks.get(uuid);
      return age == null ? 0 : age;
   }

   private static String nameOf(PlayerEntity player) {
      try {
         if (player.getGameProfile() != null && player.getGameProfile().name() != null) {
            return player.getGameProfile().name();
         }
      } catch (RuntimeException ignored) {
      }
      try {
         if (player.getName() != null && player.getName().getString() != null) {
            return player.getName().getString();
         }
      } catch (RuntimeException ignored) {
      }
      return null;
   }

   private static UUID safeUuid(Entity entity) {
      try {
         return entity.getUuid();
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   private static boolean isActive() {
      return detectTab.get() || detectDuplicate.get() || detectNoGameMode.get();
   }

   public static boolean isBot(Entity entity) {
      try {
         AntiBot module = instance;
         if (module == null || !module.enable) {
            return false;
         }
         if (!(entity instanceof PlayerEntity player)) {
            return false;
         }
         if (mc == null || mc.world == null || mc.player == null) {
            return false;
         }
         if (!isActive()) {
            return false;
         }
         return module.resolve(player);
      } catch (RuntimeException ignored) {
         return false;
      }
   }

   public static int getBotCount() {
      return botCount;
   }
}
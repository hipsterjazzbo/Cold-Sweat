package dev.momostudios.coldsweat.util.compat;

import de.teamlapen.werewolves.entities.player.werewolf.WerewolfPlayer;
import dev.momostudios.coldsweat.util.registries.ModDamageSources;
import dev.momostudios.coldsweat.util.math.CSMath;
import dev.momostudios.coldsweat.util.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.jwaresoftware.mcmods.lib.api.combat.Armory;
import weather2.ServerTickHandler;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;

@Mod.EventBusSubscriber
public class CompatManager
{
    private static final boolean BOP_LOADED = ModList.get().isLoaded("biomesoplenty");
    private static final boolean SEASONS_LOADED = ModList.get().isLoaded("sereneseasons");
    private static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");
    private static final boolean WEREWOLVES_LOADED = ModList.get().isLoaded("werewolves");
    private static final boolean SPIRIT_LOADED = ModList.get().isLoaded("spirit");
    private static final boolean ARMOR_UNDERWEAR_LOADED = ModList.get().isLoaded("armorunder");
    private static final boolean BYG_LOADED = ModList.get().isLoaded("byg");
    private static final boolean CREATE_LOADED = ModList.get().isLoaded("create");
    private static final boolean ATMOSPHERIC_LOADED = ModList.get().isLoaded("atmospheric");
    private static final boolean ENVIRONMENTAL_LOADED = ModList.get().isLoaded("environmental");
    private static final boolean TERRALITH_LOADED = ModList.get().isLoaded("terralith");
    private static final boolean WEATHER_LOADED = ModList.get().isLoaded("weather2");

    public static boolean isBiomesOPlentyLoaded()
    {   return BOP_LOADED;
    }
    public static boolean isSereneSeasonsLoaded()
    {   return SEASONS_LOADED;
    }
    public static boolean isCuriosLoaded()
    {   return CURIOS_LOADED;
    }
    public static boolean isWerewolvesLoaded()
    {   return WEREWOLVES_LOADED;
    }
    public static boolean isSpiritLoaded()
    {   return SPIRIT_LOADED;
    }
    public static boolean isArmorUnderwearLoaded()
    {   return ARMOR_UNDERWEAR_LOADED;
    }
    public static boolean isBiomesYoullGoLoaded()
    {   return BYG_LOADED;
    }
    public static boolean isCreateLoaded()
    {   return CREATE_LOADED;
    }
    public static boolean isAtmosphericLoaded()
    {   return ATMOSPHERIC_LOADED;
    }
    public static boolean isEnvironmentalLoaded()
    {   return ENVIRONMENTAL_LOADED;
    }
    public static boolean isTerralithLoaded()
    {   return TERRALITH_LOADED;
    }
    public static boolean isWeather2Loaded()
    {   return WEATHER_LOADED;
    }

    public static boolean hasOzzyLiner(ItemStack stack)
    {
        return ARMOR_UNDERWEAR_LOADED && Armory.getXLining(stack).has(Armory.XLining.TEMPERATURE_REGULATOR);
    }
    public static boolean hasOttoLiner(ItemStack stack)
    {
        return ARMOR_UNDERWEAR_LOADED && Armory.getXLining(stack).has(Armory.XLining.ANTIFREEZE_SHIELD);
    }
    public static boolean hasOllieLiner(ItemStack stack)
    {
        return ARMOR_UNDERWEAR_LOADED && Armory.getXLining(stack).has(Armory.XLining.ANTIBURN_SHIELD);
    }

    public static boolean isWerewolf(Player player)
    {
        return WEREWOLVES_LOADED && WerewolfPlayer.getOpt(player).filter(w -> w.getLevel() > 0).map(w -> w.getForm().isTransformed()).orElse(false);
    }

    public static boolean isWeather2RainingAt(Level level, BlockPos pos)
    {
        if (!WEATHER_LOADED) return false;
        WeatherManagerServer weatherManager = ServerTickHandler.getWeatherManagerFor(level.dimension());
        StormObject rainStorm = weatherManager.getClosestStormAny(new Vec3(pos.getX(), pos.getY(), pos.getZ()), 250);
        if (rainStorm == null) return false;

        return WorldHelper.canSeeSky(level, pos, 60) && rainStorm.isPrecipitating() && rainStorm.levelTemperature > 0.0f
            && Math.sqrt(Math.pow(pos.getX() - rainStorm.pos.x, 2) + Math.pow(pos.getX() - rainStorm.pos.x, 2)) < rainStorm.getSize();
    }

    @SubscribeEvent
    public static void onLivingTempDamage(LivingEvent event)
    {
        if (!(event instanceof LivingDamageEvent || event instanceof LivingAttackEvent)) return;
        // Armor Underwear compat
        if (ARMOR_UNDERWEAR_LOADED && !event.getEntityLiving().level.isClientSide)
        {
            // Get the damage source from the event (different methods for LivingDamage/LivingAttack)
            DamageSource source = event instanceof LivingDamageEvent
                                  ? ((LivingDamageEvent) event).getSource()
                                  : ((LivingAttackEvent) event).getSource();
            if (source == null) return;

            boolean isDamageCold;
            if (((isDamageCold = source == ModDamageSources.COLD) || source == ModDamageSources.HOT))
            {
                int liners = 0;
                for (ItemStack stack : event.getEntityLiving().getArmorSlots())
                {
                    if (isDamageCold ? hasOttoLiner(stack) : hasOllieLiner(stack))
                        liners++;
                }
                // Cancel the event if full liners
                if (liners >= 4)
                {   event.setCanceled(true);
                    return;
                }
                // Dampen the damage as the number of liners increases
                if (event instanceof LivingDamageEvent damageEvent)
                    damageEvent.setAmount(CSMath.blend(damageEvent.getAmount(), 0, liners, 0, 4));
            }
        }
    }
}

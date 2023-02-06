package dev.momostudios.coldsweat.common.capability;

import dev.momostudios.coldsweat.ColdSweat;
import dev.momostudios.coldsweat.api.temperature.modifier.TempModifier;
import dev.momostudios.coldsweat.api.util.Temperature;
import dev.momostudios.coldsweat.api.util.Temperature.Type;
import dev.momostudios.coldsweat.util.compat.ModGetters;
import dev.momostudios.coldsweat.util.config.ConfigSettings;
import dev.momostudios.coldsweat.util.entity.ModDamageSources;
import dev.momostudios.coldsweat.util.entity.NBTHelper;
import dev.momostudios.coldsweat.util.math.CSMath;
import dev.momostudios.coldsweat.util.registries.ModEffects;
import dev.momostudios.coldsweat.util.registries.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;

/**
 * Holds all the information regarding the entity's temperature. This should very rarely be used directly.
 */
public class PlayerTempCap implements ITemperatureCap
{
    static Type[] VALID_TEMPERATURE_TYPES = {Type.CORE, Type.BASE, Type.MAX, Type.MIN, Type.WORLD};
    static Type[] VALID_MODIFIER_TYPES    = {Type.CORE, Type.BASE, Type.RATE, Type.MAX, Type.MIN, Type.WORLD};

    private double[] syncedValues = new double[5];
    boolean neverSynced = true;

    // Map valid temperature types to a new EnumMap
    EnumMap<Type, Double> temperatures = Arrays.stream(VALID_MODIFIER_TYPES).collect(
            () -> new EnumMap<>(Type.class),
            (map, type) -> map.put(type, 0.0),
            EnumMap::putAll);

    // Map valid modifier types to a new EnumMap
    EnumMap<Type, List<TempModifier>> modifiers = Arrays.stream(VALID_MODIFIER_TYPES).collect(
            () -> new EnumMap<>(Type.class),
            (map, type) -> map.put(type, new ArrayList<>()),
            EnumMap::putAll);

    public boolean showBodyTemp;
    public boolean showWorldTemp;

    public double getTemp(Type type)
    {
        // Special case for BODY
        if (type == Type.BODY) return getTemp(Type.CORE) + getTemp(Type.BASE);
        // Throw exception if this temperature type is not supported
        return temperatures.computeIfAbsent(type, t ->
        {
            throw new IllegalArgumentException("Invalid temperature type: " + t);
        });
    }

    public void setTemp(Type type, double value)
    {
        // Throw exception if this temperature type is not supported
        if (temperatures.replace(type, value) == null)
        {
            throw new IllegalArgumentException("Invalid temperature type: " + type);
        }
    }

    public List<TempModifier> getModifiers(Type type)
    {
        // Throw exception if this modifier type is not supported
        return modifiers.computeIfAbsent(type, t ->
        {
            throw new IllegalArgumentException("Invalid modifier type: " + t);
        });
    }

    public boolean hasModifier(Type type, Class<? extends TempModifier> mod)
    {
        return getModifiers(type).stream().anyMatch(mod::isInstance);
    }

    public boolean shouldShowBodyTemp()
    {
        return showBodyTemp;
    }

    public boolean shouldShowWorldTemp()
    {
        return showWorldTemp;
    }

    public void clearModifiers(Type type)
    {
        getModifiers(type).clear();
    }

    public void tickDummy(LivingEntity entity)
    {
        if (!(entity instanceof Player player)) return;

        for (Type type : VALID_MODIFIER_TYPES)
        {
            Temperature.apply(0, player, getModifiers(type));
        }

        if (player.tickCount % 20 == 0)
        {
            calculateVisibility(player);
        }
    }

    public void tick(LivingEntity entity)
    {
        if (!(entity instanceof Player player)) return;

        ConfigSettings config = ConfigSettings.getInstance();

        // Tick expiration time for world modifiers
        double newWorldTemp = Temperature.apply(0, player, getModifiers(Type.WORLD));
        double newCoreTemp  = Temperature.apply(getTemp(Type.CORE), player, getModifiers(Type.CORE));
        double newBaseTemp  = Temperature.apply(0, player, getModifiers(Type.BASE));
        double newMaxOffset = Temperature.apply(0, player, getModifiers(Type.MAX));
        double newMinOffset = Temperature.apply(0, player, getModifiers(Type.MIN));

        double maxTemp = config.maxTemp + newMaxOffset;
        double minTemp = config.minTemp + newMinOffset;

        // 1 if newWorldTemp is above max, -1 if below min, 0 if between the values (safe)
        int magnitude = CSMath.getSignForRange(newWorldTemp, minTemp, maxTemp);

        // Don't change player temperature if they're in creative/spectator mode
        if (magnitude != 0 && !(player.isCreative() || player.isSpectator()))
        {
            double difference = Math.abs(newWorldTemp - CSMath.clamp(newWorldTemp, minTemp, maxTemp));
            double changeBy = Math.max((difference / 7d) * (float)config.rate, Math.abs((float) config.rate / 50d)) * magnitude;
            newCoreTemp += Temperature.apply(changeBy, player, getModifiers(Type.RATE));
        }
        // If the player's temperature and world temperature are not both hot or both cold
        int tempSign = CSMath.getSign(newCoreTemp);
        if (tempSign != 0 && magnitude != tempSign)
        {
            double factor = (tempSign == 1 ? newWorldTemp - maxTemp : newWorldTemp - minTemp) / 3;
            double changeBy = CSMath.most(factor * config.rate, config.rate / 10d * -tempSign);
            newCoreTemp += CSMath.least(changeBy, -getTemp(Type.CORE));
        }

        // Update whether certain UI elements are being displayed (temp isn't synced if the UI element isn't showing)
        if (player.tickCount % 20 == 0)
        {
            calculateVisibility(player);
        }

        // Write the new temperature values
        setTemp(Type.BASE, newBaseTemp);
        setTemp(Type.CORE, CSMath.clamp(newCoreTemp, -150f, 150f));
        setTemp(Type.WORLD, newWorldTemp);
        setTemp(Type.MAX, newMaxOffset);
        setTemp(Type.MIN, newMinOffset);

        // Sync the temperature values to the client
        if ((neverSynced
        || (((int) syncedValues[0] != (int) newCoreTemp && showBodyTemp))
        || (((int) syncedValues[1] != (int) newBaseTemp && showBodyTemp))
        || ((Math.abs(syncedValues[2] - newWorldTemp) >= 0.02 && showWorldTemp))
        || ((Math.abs(syncedValues[3] - newMaxOffset) >= 0.02 && showWorldTemp))
        || ((Math.abs(syncedValues[4] - newMinOffset) >= 0.02 && showWorldTemp))))
        {
            Temperature.updateTemperature(player, this, false);
            syncedValues = new double[] { newCoreTemp, newBaseTemp, newWorldTemp, newMaxOffset, newMinOffset };
            neverSynced = false;
        }

        // Calculate body/base temperatures with modifiers
        double bodyTemp = getTemp(Type.BODY);

        //Deal damage to the player if temperature is critical
        if (!player.isCreative() && !player.isSpectator())
        {
            if (player.tickCount % 40 == 0 && !player.hasEffect(ModEffects.GRACE))
            {
                boolean damageScaling = config.damageScaling;

                if (bodyTemp >= 100 && !(player.hasEffect(MobEffects.FIRE_RESISTANCE) && config.fireRes))
                {
                    player.hurt(damageScaling ? ModDamageSources.HOT.setScalesWithDifficulty() : ModDamageSources.HOT, 2f);
                }
                else if (bodyTemp <= -100 && !(player.hasEffect(ModEffects.ICE_RESISTANCE) && config.iceRes))
                {
                    player.hurt(damageScaling ? ModDamageSources.COLD.setScalesWithDifficulty() : ModDamageSources.COLD, 2f);
                }
            }
        }
        else setTemp(Type.CORE, 0);
    }

    public void calculateVisibility(Player player)
    {
        if (player.tickCount % 20 == 0)
        {
            showWorldTemp = !ConfigSettings.getInstance().requireThermometer
                    || player.getInventory().items.stream().limit(9).anyMatch(stack -> stack.getItem() == ModItems.THERMOMETER)
                    || player.getOffhandItem().getItem() == ModItems.THERMOMETER
                    || ModGetters.isCuriosLoaded() && CuriosApi.getCuriosHelper().findFirstCurio(player, ModItems.THERMOMETER).isPresent();
            showBodyTemp = !player.isCreative() && !player.isSpectator();
        }
    }

    @Override
    public void copy(ITemperatureCap cap)
    {
        // Copy temperature values
        for (Type type : VALID_TEMPERATURE_TYPES)
        {
            if (type == Type.BODY || type == Type.RATE) continue;
            this.setTemp(type, cap.getTemp(type));
        }

        // Copy the modifiers
        for (Type type : VALID_MODIFIER_TYPES)
        {
            this.getModifiers(type).clear();
            this.getModifiers(type).addAll(cap.getModifiers(type));
        }
    }

    @Override
    public CompoundTag serializeNBT()
    {
        // Save the player's temperatures
        CompoundTag nbt = this.serializeTemps();

        // Save the player's modifiers
        nbt.merge(this.serializeModifiers());
        return nbt;
    }

    @Override
    public CompoundTag serializeTemps()
    {
        CompoundTag nbt = new CompoundTag();

        // Save the player's temperature data
        for (Type type : VALID_TEMPERATURE_TYPES)
        {
            nbt.putDouble(NBTHelper.getTemperatureTag(type), this.getTemp(type));
        }
        return nbt;
    }

    @Override
    public CompoundTag serializeModifiers()
    {
        CompoundTag nbt = new CompoundTag();

        // Save the player's modifiers
        for (Type type : VALID_MODIFIER_TYPES)
        {
            ListTag modifiers = new ListTag();
            for (TempModifier modifier : this.getModifiers(type))
            {
                modifiers.add(NBTHelper.modifierToTag(modifier));
            }

            // Write the list of modifiers to the player's persistent data
            nbt.put(NBTHelper.getModifierTag(type), modifiers);
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        // Load the player's temperatures
        deserializeTemps(nbt);

        // Load the player's modifiers
        deserializeModifiers(nbt);
    }

    @Override
    public void deserializeTemps(CompoundTag nbt)
    {
        for (Type type : VALID_TEMPERATURE_TYPES)
        {
            setTemp(type, nbt.getDouble(NBTHelper.getTemperatureTag(type)));
        }
    }

    @Override
    public void deserializeModifiers(CompoundTag nbt)
    {
        for (Type type : VALID_MODIFIER_TYPES)
        {
            getModifiers(type).clear();

            // Get the list of modifiers from the player's persistent data
            ListTag modifiers = nbt.getList(NBTHelper.getModifierTag(type), 10);

            // For each modifier in the list
            modifiers.forEach(modNBT ->
            {
                TempModifier modifier = NBTHelper.tagToModifier((CompoundTag) modNBT);

                // Add the modifier to the player's temperature
                if (modifier != null)
                    getModifiers(type).add(modifier);
                else
                    ColdSweat.LOGGER.error("Failed to load modifier of type {}", type);
            });
        }
    }
}

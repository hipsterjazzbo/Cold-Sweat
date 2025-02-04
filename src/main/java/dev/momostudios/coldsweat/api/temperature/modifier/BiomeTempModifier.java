package dev.momostudios.coldsweat.api.temperature.modifier;

import com.mojang.datafixers.util.Pair;
import dev.momostudios.coldsweat.api.util.Temperature;
import dev.momostudios.coldsweat.util.config.ConfigSettings;
import dev.momostudios.coldsweat.util.math.CSMath;
import dev.momostudios.coldsweat.util.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.common.Tags;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class BiomeTempModifier extends TempModifier
{
    public BiomeTempModifier()
    {
        this(16);
    }

    public BiomeTempModifier(int samples)
    {
        this.getNBT().putInt("Samples", samples);
    }

    @Override
    public Function<Double, Double> calculate(LivingEntity entity, Temperature.Type type)
    {
        try
        {
            double worldTemp = 0;
            ResourceLocation dimensionID = entity.level.dimension().location();
            Number dimensionOverride = ConfigSettings.DIMENSION_TEMPS.get().get(dimensionID);

            if (dimensionOverride != null)
            {   return temp -> temp + dimensionOverride.doubleValue();
            }
            else
            {
                // Fallback for improper sample count
                int samples = this.getNBT().contains("Samples") ? this.getNBT().getInt("Samples") : 25;

                Level level = entity.level;

                for (BlockPos blockPos : WorldHelper.getPositionGrid(entity.blockPosition(), samples, 16))
                {
                    List<Holder<Biome>> biomeList = Stream.of(level.getBiomeManager().getBiome(blockPos),
                                                              level.getBiomeManager().getBiome(blockPos.above(16)),
                                                              level.getBiomeManager().getBiome(blockPos.below(16))).distinct().toList();
                    for (Holder<Biome> holder : biomeList)
                    {
                        if (holder.is(Tags.Biomes.IS_UNDERGROUND)) continue;

                        Biome biome = holder.value();
                        ResourceLocation biomeID = biome.getRegistryName();

                        Pair<Double, Double> configTemp;
                        double biomeVariance = 1 / Math.max(1, 2 + biome.getDownfall() * 2);
                        double baseTemp = biome.getBaseTemperature();

                        // Get the biome's temperature, either overridden by config or calculated
                        // Start with biome override
                        configTemp = CSMath.addPairs(ConfigSettings.BIOME_TEMPS.get().getOrDefault(biomeID, Pair.of(baseTemp - biomeVariance, baseTemp + biomeVariance)),
                                                     ConfigSettings.BIOME_OFFSETS.get().getOrDefault(biomeID, Pair.of(0d, 0d)));

                        // Biome temp at midnight (bottom of the sine wave)
                        double min = configTemp.getFirst();
                        // Biome temp at noon (top of the sine wave)
                        double max = configTemp.getSecond();

                        // Divide by this to get average
                        double divisor = samples * biomeList.size();

                        DimensionType dimension = level.dimensionType();
                        if (!dimension.hasCeiling())
                        {
                            double altitude = entity.getY();
                            double mid = (min + max) / 2;
                            // Biome temp with time of day
                            worldTemp += CSMath.blend(min, max, Math.sin(level.getDayTime() / (12000 / Math.PI)), -1, 1) / divisor
                                    // Altitude calculation
                                    + CSMath.blend(0, min - mid, altitude, level.getSeaLevel(), level.getMaxBuildHeight()) / divisor * 2;
                        }
                        // If dimension has ceiling (don't use time or altitude)
                        else worldTemp += CSMath.average(max, min) / divisor;
                    }
                }

                worldTemp += ConfigSettings.DIMENSION_OFFSETS.get().getOrDefault(dimensionID, 0d);
            }
            double finalWorldTemp = worldTemp;
            return temp -> temp + finalWorldTemp;
        }
        catch (Exception e)
        {   return (temp) -> temp;
        }
    }

    public String getID()
    {
        return "cold_sweat:biome";
    }
}
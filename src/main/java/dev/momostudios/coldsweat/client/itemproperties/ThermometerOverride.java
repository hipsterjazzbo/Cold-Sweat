package dev.momostudios.coldsweat.client.itemproperties;

import dev.momostudios.coldsweat.api.temperature.Temperature;
import dev.momostudios.coldsweat.util.config.ConfigSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import dev.momostudios.coldsweat.api.util.TempHelper;

import javax.annotation.Nullable;

public class ThermometerOverride implements IItemPropertyGetter
{
    @Override
    public float call(ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity entity)
    {
        ConfigSettings config = ConfigSettings.getInstance();
        float minTemp = (float) config.minTemp;
        float maxTemp = (float) config.maxTemp;

        float ambientTemp = (float) TempHelper.getTemperature(Minecraft.getInstance().player, Temperature.Type.WORLD).get();

        float ambientAdjusted = ambientTemp - minTemp;
        float tempScaleFactor = 1 / ((maxTemp - minTemp) / 2);

        return ambientAdjusted * tempScaleFactor - 1;
    }
}

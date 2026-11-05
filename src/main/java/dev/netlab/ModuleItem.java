package dev.netlab;

import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;

public final class ModuleItem extends Item {
    public final String module;
    public ModuleItem(Properties properties,String module){super(properties);this.module=module;}
    @Override public Component getName(ItemStack stack){return Component.translatableWithFallback(getDescriptionId(),module);}
    public static String itemId(String module){return "module_"+module.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","_");}
    public static Item find(String module){for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)if(item instanceof ModuleItem m&&m.module.equals(module))return item;return Items.AIR;}
}

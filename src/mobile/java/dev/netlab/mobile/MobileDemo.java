package dev.netlab.mobile;

import dev.netlab.mobile.core.MobileHardware;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Gives a kit only: tower construction remains the player's choice. */
public final class MobileDemo {
    public static void register(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("netlab").then(Commands.literal("mobile").then(Commands.literal("kit").requires(s->s.hasPermission(2)).executes(c->{var p=c.getSource().getPlayerOrException();for(var item:java.util.List.of(Mobile.ANTENNA.get().asItem(),Mobile.RRU.get().asItem(),Mobile.BBU.get().asItem(),Mobile.CORE.get().asItem(),Mobile.STATION.get().asItem(),Mobile.RF.get(),Mobile.PHONE.get()))give(p,new ItemStack(item));give(p,new ItemStack(Mobile.BLANK_SIM.get(),8));for(String module:MobileHardware.MODULES)give(p,new ItemStack(Mobile.MODULE_ITEMS.get(module).get()));c.getSource().sendSuccess(()->Component.literal("NetLab Mobile: оборудование выдано. Башню построй сам: антенна минимум на 12 блоков выше окрестной земли. Схема и настройка — Библия по CISCO → Мобильная связь. Рецептов нет."),false);return 1;}))));}
    private static void give(net.minecraft.server.level.ServerPlayer p,ItemStack item){if(!p.addItem(item))p.drop(item,false);}
}

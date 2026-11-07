package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Local Console access works without a SIM or cellular registration. */
public final class PhoneTerminals {
    public static void open(ServerPlayer player,ItemStack phone,DeviceEntity host,int port){
        MobileItemStorage.reconcile(player,phone);
        if(!PhoneDevices.configuration(phone).has("terminal")){player.displayClientMessage(net.minecraft.network.chat.Component.literal("Установи модуль терминала Cisco Pocket во вкладке «Модули»."),true);return;}
        String id=MobileItems.phoneId(phone);var tag=MobileItems.privateTag(phone);
        var memory=tag.contains("terminalHistory")?MobileState.JSON.fromJson(StoredJson.get(tag,"terminalHistory"),PhoneTerminalMemory.class):new PhoneTerminalMemory();
        PortableTerminals.open(player,host,port,new PortableTerminals.Carrier(){
            public boolean held(ServerPlayer current){var item=PhoneDevices.held(current,id);return !item.isEmpty()&&PhoneDevices.configuration(item).has("terminal");}
            public ConsoleTranscript open(String identity){return memory.open(identity);}
            public void save(){var item=PhoneDevices.held(player,id);if(item.isEmpty())return;var privateTag=MobileItems.privateTag(item);StoredJson.put(privateTag,"terminalHistory",MobileState.JSON.toJson(memory));MobileItems.seal(item,privateTag);MobileItemStorage.reconcile(player,item);}
        });
    }
}

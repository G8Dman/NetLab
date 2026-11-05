package dev.netlab;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Radio associations and pending DHCP/SLAAC refresh once a second. */
public final class NetworkServices {
    public static void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0)return;
        for(var level:event.getServer().getAllLevels()){
            var n=WirelessWorld.network(level);n.refreshServices();Study.publish(n,level);WirelessWorld.changed(level,n);
        }
    }
}

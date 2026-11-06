package dev.netlab;

import dev.netlab.core.WallCablePath;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

public final class PowerCableItem extends RoutedCableItem {
    private final boolean legacy;
    public PowerCableItem(Properties p){this(p,false);}
    /** Hidden registry alias: old debug items become ordinary power cables in inventories. */
    public PowerCableItem(Properties p,boolean legacy){super(p);this.legacy=legacy;}
    @Override public Component getName(ItemStack stack){return Component.translatable("item.netlab.power_cable");}
    @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
        if(legacy&&!level.isClientSide&&entity instanceof Player player&&player.getInventory().getItem(slot)==stack)
            player.getInventory().setItem(slot,stack.transmuteCopy(NetLab.POWER_CABLE.get()));
    }
    @Override protected int pick(DeviceEntity h,BlockHitResult hit,Player p){return PowerGeometry.clicked(h,hit,p);}
    @Override protected String identity(DeviceEntity h,int port){return PowerSystem.identity(h,port);}
    @Override protected boolean occupied(DeviceEntity h,int port){return h.powerLinks.containsKey(port);}
    @Override protected void disconnect(DeviceEntity h,int port,Player player){PowerSystem.disconnect(h,port,player);}
    @Override protected List<WallCablePath.Point> path(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){return PowerGeometry.fixedRoute(a,ap,b,bp,anchors);}
    @Override protected Connection connect(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){
        if(a.powerLinks.containsKey(ap)||b.powerLinks.containsKey(bp))return new Connection(false,"Разъём питания занят. Shift+ПКМ — отключить.");
        var message=PowerSystem.connect(a,ap,b,bp,anchors);var peer=a.powerLinks.get(ap);
        return new Connection(peer!=null&&peer.pos()==b.getBlockPos().asLong()&&peer.port()==bp,message);
    }
    @Override protected String portHint(){return "Нажми задний разъём питания. На шкафе сначала установи блок питания.";}
    @Override protected int color(){return 0xe7a52b;}
}

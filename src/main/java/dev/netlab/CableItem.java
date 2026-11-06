package dev.netlab;

import dev.netlab.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

public final class CableItem extends RoutedCableItem {
    public final String cableType;
    public CableItem(Properties p){this(p,"copper");}
    public CableItem(Properties p,String type){super(p);cableType=type;}
    @Override public Component getName(ItemStack stack){return Component.literal(switch(cableType){
        case "fiber"->"Оптический кабель";case "console"->"Консольный кабель RS232 / Console";case "serial_dce"->"Serial DCE";case "serial_dte"->"Serial DTE";
        case "phone"->"Телефонный кабель RJ11";case "async"->"Асинхронный консольный кабель";case "crossover"->"Медный crossover";default->CableKind.EXTRA_NAMES.getOrDefault(cableType,"Медный Ethernet");});}
    @Override protected int pick(DeviceEntity h,BlockHitResult hit,Player p){return CableGeometry.clickedPort(h,hit,p);}
    @Override protected String identity(DeviceEntity h,int port){
        var d=h.portDevice(port);int index=port%128;
        return d==null||index<0||index>=d.ports.size()||d.ports.get(index).hidden||d.ports.get(index).virtual?"":d.identity+":"+d.portName(index)+":"+h.portEpoch(port);
    }
    @Override protected boolean occupied(DeviceEntity h,int port){return h.links.containsKey(port);}
    @Override protected void disconnect(DeviceEntity h,int port,Player player){h.disconnect(port,player);}
    @Override protected List<WallCablePath.Point> path(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){return CableGeometry.route(a,ap,b,bp,anchors);}
    @Override protected Connection connect(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){
        if(a==b&&ap/128==bp/128)return new Connection(false,"Выбери другое устройство или другую ячейку шкафа.");
        var from=a.portDevice(ap);var to=b.portDevice(bp);
        if(from==null||to==null||!CableKind.compatible(cableType,from,ap%128,to,bp%128))
            return new Connection(false,"Этот кабель несовместим с выбранными разъёмами: "+cableType);
        if(a.links.containsKey(ap)||b.links.containsKey(bp))return new Connection(false,"Разъём занят. Shift+ПКМ кабелем — отключить.");
        if(!WallCablePath.valid(path(a,ap,b,bp,anchors),NetLab.MAX_LENGTH.get()))return new Connection(false,"Маршрут кабеля превышает максимальную длину.");
        // DCE clock ownership remains canonical, independently of the drawing endpoint.
        String stored=CableKind.storedType(cableType,a.getBlockPos().asLong(),ap,b.getBlockPos().asLong(),bp);
        a.connectionChanged(ap);b.connectionChanged(bp);
        a.links.put(ap,new DeviceEntity.Peer(b.getBlockPos().asLong(),bp,color(),"",stored,anchors));
        b.links.put(bp,new DeviceEntity.Peer(a.getBlockPos().asLong(),ap,color(),"",stored,anchors.reversed()));
        a.changed();b.changed();return new Connection(true,"Подключён кабель: "+getName(ItemStack.EMPTY).getString()+"; креплений: "+anchors.size());
    }
    @Override protected String portHint(){return "Нажми разъём на передней панели устройства.";}
    @Override protected int color(){return CableKind.color(cableType);}
}

package dev.netlab;

import dev.netlab.core.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sanitised AP/PC GUI state; passwords never appear in scan results or responses. */
public final class WirelessMenu {
    public record Settings(String ssid,String security,String password,int channel,int range,boolean isolated){}
    public record State(boolean ap,boolean adapter,boolean powered,boolean enabled,String ssid,String security,int channel,int range,boolean isolated,
        String connection,String address,List<Wireless.Candidate> networks,String status){}
    public static void send(ServerPlayer player,DeviceEntity entity,int bay,String status,boolean open){
        var d=bay==0?entity.device:entity.upperDevice;if(d==null)return;var w=Wireless.state(d);var n=entity.network();
        int radio=Wireless.radio(d);boolean ap=Wireless.accessPoint(d);var connected=n.devices.get(w.associated);
        String connection=connected!=null?Wireless.state(connected).ssid:"не подключено";
        var networks=d.kind.equals("pc")?Wireless.scan(n,WirelessWorld.positions(player.serverLevel()),entity.slotId(bay)):List.<Wireless.Candidate>of();
        var state=new State(ap,radio>=0,Hardware.powered(d),w.enabled,ap?w.ssid:w.clientSsid,ap?w.security:w.clientSecurity,w.channel,w.range,w.isolated,
            connection,radio>=0?d.ports.get(radio).ip:"",networks,status);
        WirelessWorld.changed(player.serverLevel(),n);
        PacketDistributor.sendToPlayer(player,new Packets.WifiData(entity.getBlockPos(),bay,FrontPanel.GSON.toJson(state),open));
    }
    public static void handle(ServerPlayer player,Packets.WifiRequest req){
        var level=player.serverLevel();var pos=req.pos();
        if(req.bay()<0||req.bay()>1||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof DeviceEntity e))return;
        var d=req.bay()==0?e.device:e.upperDevice;if(d==null||!d.kind.equals("pc")&&!Wireless.accessPoint(d))return;
        String status="";
        try{
            boolean ap=Wireless.accessPoint(d);var w=Wireless.state(d);
            if(!List.of("open","refresh").contains(req.action())&&!player.mayBuild())return;
            switch(req.action()){
                case "open","refresh"->{}
                case "configure"->{if(!ap)throw new IllegalArgumentException("Нужна точка доступа");var cfg=FrontPanel.GSON.fromJson(req.json(),Settings.class);Wireless.configure(d,cfg.ssid,cfg.security,cfg.password,cfg.channel,cfg.range,cfg.isolated);status="Настройки точки доступа сохранены";}
                case "join"->{
                    if(ap||!Hardware.powered(d))throw new IllegalArgumentException("Включи ПК/ноутбук");
                    var cfg=FrontPanel.GSON.fromJson(req.json(),Settings.class);Wireless.join(d,cfg.ssid,cfg.security,cfg.password);
                    var n=e.network();n.refreshServices();WirelessWorld.changed(level,n);Study.publish(n,level);
                    status=w.associated==Long.MIN_VALUE?"Подключение не удалось: проверь пароль, режим защиты, питание и дальность":"Wi-Fi подключён; "+(d.ports.get(Wireless.radio(d)).ip.isEmpty()?"DHCP ожидает сервер":"IP "+d.ports.get(Wireless.radio(d)).ip);
                }
                case "disconnect"->{if(ap)throw new IllegalArgumentException("Нужен клиент");Wireless.disconnect(d);status="Wi-Fi отключён; выбран Ethernet";}
                case "radio"->{w.enabled=!w.enabled;status=w.enabled?"Радио включено":"Радио выключено";}
                case "power"->{if(!d.kind.equals("ap"))throw new IllegalArgumentException("Питание ПК/роутера на физической панели");var h=Hardware.ensure(d);h.powered=!h.powered;h.epoch++;}
                default->throw new IllegalArgumentException("Неизвестное действие");
            }
            e.changed();
        }catch(IllegalArgumentException|NullPointerException|com.google.gson.JsonParseException ex){status=ex instanceof IllegalArgumentException&&ex.getMessage()!=null?ex.getMessage():"Некорректные параметры Wi-Fi";}
        send(player,e,req.bay(),status,req.action().equals("open"));
    }
}

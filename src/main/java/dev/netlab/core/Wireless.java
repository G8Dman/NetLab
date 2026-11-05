package dev.netlab.core;

import java.io.Serializable;
import java.util.*;

/** Association and AP bridging policy, independent of Minecraft and real host networking. */
public final class Wireless implements Serializable {
    private static final long serialVersionUID=1L;
    public String ssid="NetLab",security="open",keyHash="",clientSsid="",clientSecurity="open",clientKey="";
    public boolean enabled=true,hidden,isolated;
    public int channel=6,range=32;
    public long associated=Long.MIN_VALUE;
    public String wiredGateway="";
    public List<String> wiredDns=new ArrayList<>();
    public static Wireless state(Device d){if(d.wireless==null)d.wireless=new Wireless();return d.wireless;}
    public static int clientPort(Device d){int index=d.preferredPort;return index>=0&&index<d.ports.size()&&!d.ports.get(index).hidden?index:0;}
    public static int radio(Device d){for(int i=0;i<d.ports.size();i++){var p=d.ports.get(i);if(!p.hidden&&p.medium.equals("wireless"))return i;}return -1;}
    public static boolean accessPoint(Device d){return d.kind.equals("ap")||d.hardware!=null&&d.hardware.modules.containsValue("HWIC-AP-AG-B");}
    public static boolean active(Device d){int radio=radio(d);return radio>=0&&d.ports.get(radio).up&&Hardware.powered(d)&&state(d).enabled;}
    public static void configure(Device d,String ssid,String security,String password,int channel,int range,boolean isolated){
        if(!accessPoint(d))throw new IllegalArgumentException("Устройство не является точкой доступа");
        if(ssid.isBlank()||ssid.length()>32)throw new IllegalArgumentException("SSID: 1–32 символа");
        if(!List.of("open","wpa2").contains(security))throw new IllegalArgumentException("Open или WPA2-PSK");
        Addressing.number(""+channel,1,11);Addressing.number(""+range,4,64);
        var w=state(d);String hash=w.keyHash;
        if(security.equals("wpa2")){
            if(!password.isEmpty()){if(password.length()<8||password.length()>63)throw new IllegalArgumentException("WPA2 пароль: 8–63 символа");hash=ConfigStore.secret(password);}
            else if(hash.isEmpty())throw new IllegalArgumentException("Задай WPA2 пароль");
        }else hash="";
        w.ssid=ssid;w.security=security;w.keyHash=hash;w.channel=channel;w.range=range;w.isolated=isolated;
    }
    public static void join(Device client,String ssid,String security,String password){
        if(!client.kind.equals("pc")||radio(client)<0)throw new IllegalArgumentException("Установи WPC300N/WMP300N");
        if(ssid.isBlank()||ssid.length()>32||password.length()>63||!List.of("open","wpa2").contains(security))throw new IllegalArgumentException("Неверный профиль Wi-Fi");
        var w=state(client);w.clientSsid=ssid;w.clientSecurity=security;w.clientKey=ConfigStore.type7(password);w.enabled=true;
        if(client.preferredPort==0){w.wiredGateway=client.gateway;w.wiredDns=new ArrayList<>(client.os().dns);}
        client.preferredPort=radio(client);client.ports.get(client.preferredPort).up=true;client.ports.get(client.preferredPort).dhcp=true;
        client.ports.get(client.preferredPort).ip="";client.gateway="";client.os().dns.clear();
    }
    public static void disconnect(Device client){var w=state(client);w.clientSsid="";w.associated=Long.MIN_VALUE;client.preferredPort=0;client.gateway=w.wiredGateway;client.os().dns=new ArrayList<>(w.wiredDns);}
    public static boolean authenticated(Device client,Device ap){
        var c=state(client);var a=state(ap);
        return client.kind.equals("pc")&&accessPoint(ap)&&active(client)&&active(ap)&&c.clientSsid.equals(a.ssid)&&c.clientSecurity.equals(a.security)
            &&(a.security.equals("open")||ConfigStore.verify(ConfigStore.untype7(c.clientKey),a.keyHash));
    }
    public record Position(double x,double y,double z){public double distance(Position b){return Math.sqrt(Math.pow(x-b.x,2)+Math.pow(y-b.y,2)+Math.pow(z-b.z,2));}}
    public record Candidate(long id,String ssid,String security,int channel,int signal,double distance){}
    public static List<Candidate> scan(Network n,Map<Long,Position> positions,long client){
        var out=new ArrayList<Candidate>();var origin=positions.get(client);var device=n.devices.get(client);if(origin==null||device==null||!active(device))return out;
        n.devices.forEach((id,d)->{if(!accessPoint(d)||!active(d)||state(d).hidden||positions.get(id)==null)return;
            double distance=origin.distance(positions.get(id));if(distance<=state(d).range)out.add(new Candidate(id,state(d).ssid,state(d).security,state(d).channel,Math.max(1,(int)(100*(1-distance/(state(d).range+1)))),distance));});
        out.sort(Comparator.comparingInt(Candidate::signal).reversed().thenComparingLong(Candidate::id));return out;
    }
    public static void rebuild(Network n,Map<Long,Position> positions){
        n.wires.removeIf(w->w.type().equals("wifi"));
        for(var entry:n.devices.entrySet()){
            long id=entry.getKey();var client=entry.getValue();if(!client.kind.equals("pc"))continue;
            if(radio(client)<0){if(client.wireless!=null&&client.wireless.associated!=Long.MIN_VALUE){client.wireless.associated=Long.MIN_VALUE;n.changed.add(id);}continue;}
            var state=state(client);long previous=state.associated;long selected=Long.MIN_VALUE;
            for(var candidate:scan(n,positions,id)){var ap=n.devices.get(candidate.id);if(!authenticated(client,ap))continue;
                selected=candidate.id;n.wires.add(new Network.Wire(new Network.Endpoint(id,radio(client)),new Network.Endpoint(selected,radio(ap)),candidate.distance,"wifi"));break;}
            state.associated=selected;
            if(selected!=previous){n.changed.add(id);if(client.ports.get(radio(client)).dhcp&&selected!=Long.MIN_VALUE){client.ports.get(radio(client)).ip="";client.gateway="";client.os().dns.clear();}}
        }
    }
}

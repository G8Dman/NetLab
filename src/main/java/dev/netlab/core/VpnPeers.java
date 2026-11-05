package dev.netlab.core;

import java.util.*;
import java.security.SecureRandom;

/** Server-only VPN configuration. Never exported in block entity update tags. */
public final class VpnPeers {
    private final Map<String,String> keys=new LinkedHashMap<>();
    private final Map<String,CryptoChannel> channels=new HashMap<>();
    private final byte[] bootSalt=new byte[32];
    public VpnPeers(){new SecureRandom().nextBytes(bootSalt);}
    public Map<String,String> savedKeys(){return Map.copyOf(keys);}
    public void restore(String name,String hash){keys.put(name,hash);channels.clear();}
    public Map<String,String> export(long id){var out=new LinkedHashMap<String,String>();String prefix=id+"|";keys.forEach((k,v)->{if(k.startsWith(prefix))out.put(k.substring(prefix.length()),v);});return out;}
    public void replace(long id,Map<String,String> peers){String prefix=id+"|";keys.keySet().removeIf(k->k.startsWith(prefix));peers.forEach((ip,hash)->{Device.ip(ip);keys.put(prefix+ip,hash);});channels.clear();}
    public String configure(long id,String line){
        String[] a=line.split("\\s+");
        if(line.equals("show crypto netlab-vpn"))return "NetLab AES-GCM VPN peers (UDP 4500, authenticated payloads):\n"+String.join("\n",keys.keySet().stream().filter(k->k.startsWith(id+"|")).map(k->k.substring(k.indexOf('|')+1)).toList());
        boolean remove=a[0].equals("no");int offset=remove?1:0;
        if(a.length<(remove?5:6)||!a[2+offset].equals("peer"))return "% crypto netlab-vpn peer IP secret SECRET";
        String peer=a[3+offset];Device.ip(peer);String key=id+"|"+peer;
        if(remove)keys.remove(key);
        else{
            if(a.length!=6||!a[4].equals("secret")||a[5].length()<16)return "% Shared secret must contain at least 16 characters";
            if(keys.size()>=1024&&!keys.containsKey(key))return "% VPN peer limit reached";
            keys.put(key,Base64.getEncoder().encodeToString(CryptoChannel.derive(a[5],new byte[0])));
        }
        channels.clear();return "VPN peer updated";
    }
    public record Tunnel(long from,long to,CryptoChannel channel){}
    public boolean hasPolicy(Network n,List<Long> path){
        for(long id:path){var router=n.devices.get(id);if(router!=null&&router.kind.equals("router")&&keys.keySet().stream().anyMatch(k->k.startsWith(id+"|")))return true;}return false;
    }
    public Tunnel tunnel(Network n,List<Long> path){
        for(int a=0;a<path.size();a++){long id=path.get(a);var source=n.devices.get(id);if(source==null||!source.kind.equals("router"))continue;
            for(int b=path.size()-1;b>a;b--){long other=path.get(b);var peer=n.devices.get(other);if(peer==null||!peer.kind.equals("router"))continue;
                String s=null,t=null;
                for(var port:peer.ports)if(!port.ip.isEmpty()){String value=keys.get(id+"|"+port.ip);if(value!=null){s=value;break;}}
                for(var port:source.ports)if(!port.ip.isEmpty()){String value=keys.get(other+"|"+port.ip);if(value!=null){t=value;break;}}
                if(s!=null&&s.equals(t)){String cache=id+"|"+other+"|"+s;String secret=s+"|"+id+"|"+other;return new Tunnel(id,other,channels.computeIfAbsent(cache,k->new CryptoChannel(CryptoChannel.derive(secret,bootSalt))));}
            }
        }return null;
    }
}

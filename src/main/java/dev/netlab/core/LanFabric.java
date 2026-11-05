package dev.netlab.core;

import java.util.*;

/** Physical switched fabrics contain VLANs, but never join routed interfaces through a router. */
public final class LanFabric {
    private final Network network;
    private final Map<Network.Endpoint,Network.Endpoint> parent=new HashMap<>();
    public LanFabric(Network network){
        this.network=network;
        for(var e:network.devices.entrySet()){
            Network.Endpoint bridge=null;
            for(int p=0;p<e.getValue().ports.size();p++){
                var port=e.getValue().ports.get(p);if(port.hidden||port.virtual||port.console||!Set.of("copper","fiber","wireless","sfp").contains(port.medium))continue;
                var point=new Network.Endpoint(e.getKey(),p);parent.put(point,point);
                if(e.getValue().kind.equals("switch")||e.getValue().kind.equals("ap")||port.l2){if(bridge==null)bridge=point;else union(bridge,point);}
            }
        }
        for(var w:network.wires)if(parent.containsKey(w.a())&&parent.containsKey(w.b()))union(w.a(),w.b());
    }
    public Network.Endpoint root(Network.Endpoint p){var a=parent.get(p);if(a==null)return null;if(!a.equals(p)){a=root(a);parent.put(p,a);}return a;}
    private void union(Network.Endpoint a,Network.Endpoint b){var x=root(a);var y=root(b);if(x!=null&&y!=null&&!x.equals(y))parent.put(x,y);}
    public Set<String> anchors(Network.Endpoint p){
        var r=root(p);var out=new HashSet<String>();if(r==null)return out;
        for(var a:parent.keySet())if(r.equals(root(a))&&!network.devices.get(a.device()).kind.equals("traffic"))out.add(key(network,a));return out;
    }
    public static String key(Network n,Network.Endpoint e){return n.devices.get(e.device()).identity+"/"+e.port();}
    public int attachedVlan(long controller){
        var peer=network.switching.peer(controller,0);if(peer==null)return 1;var d=network.devices.get(peer.device());var p=d.ports.get(peer.port());
        return network.switching.trunk(peer.device(),peer.port())?network.switching.nativeVlan(p):network.switching.accessVlan(p);
    }
}

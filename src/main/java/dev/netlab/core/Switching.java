package dev.netlab.core;

import java.util.*;

/** Frame forwarding policy. Topology probes never learn MACs or increment traffic counters. */
public final class Switching {
    private final Network n;
    public Switching(Network n){this.n=n;}
    public static Set<Integer> vlans(String value){
        var out=new TreeSet<Integer>();
        if(value.equals("all")){for(int i=1;i<=4094;i++)out.add(i);return out;}
        if(value.isBlank()||value.equals("none"))return out;
        for(String part:value.split(",")){
            String[] range=part.split("-",-1);int a=Addressing.number(range[0],1,4094);
            int b=range.length==1?a:Addressing.number(range[1],a,4094);
            if(range.length>2)throw new IllegalArgumentException("Invalid VLAN range");
            for(int i=a;i<=b;i++)out.add(i);
        }
        return out;
    }
    public static String mac(String value){
        String clean=value.replace(":","").replace(".","").replace("-","").toLowerCase(Locale.ROOT);
        if(!clean.matches("[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid MAC address");return clean;
    }
    public Network.Endpoint peer(long id,int p){for(var w:n.wires){if(w.a().equals(new Network.Endpoint(id,p)))return w.b();if(w.b().equals(new Network.Endpoint(id,p)))return w.a();}return null;}
    public boolean trunk(long id,int p){
        var d=n.devices.get(id);var a=d.ports.get(p);
        if(a.trunk||a.switchMode.equals("trunk"))return true;
        if(!a.switchMode.startsWith("dynamic")||a.nonegotiate)return false;
        var e=peer(id,p);if(e==null)return false;var b=n.devices.get(e.device()).ports.get(e.port());
        return !b.nonegotiate && (b.trunk||b.switchMode.equals("trunk")||b.switchMode.equals("dynamic desirable")
            ||a.switchMode.equals("dynamic desirable")&&b.switchMode.equals("dynamic auto"));
    }
    public int nativeVlan(Device.Port p){return p.trunkNative!=1?p.trunkNative:p.vlan>0?p.vlan:1;}
    public int accessVlan(Device.Port p){return p.accessVlan!=1?p.accessVlan:p.vlan>0?p.vlan:1;}
    public boolean vlanExists(Device d,int vlan){return vlan==1||vlan>=1002&&vlan<=1005||d.os().vlans.containsKey(vlan)
        // Preserve pre-VLAN-database worlds configured with the original prototype.
        ||d.os().vlans.isEmpty()&&d.ports.stream().anyMatch(p->p.vlan==vlan);}
    public boolean carries(long id,int p,int vlan){
        var d=n.devices.get(id);var a=d.ports.get(p);
        if(d.kind.equals("router")&&!a.l2)return false;
        if(a.console||a.virtual||a.errDisabled||!n.linkUp(id,p)||!vlanExists(d,vlan))return false;
        if(d.os().spanDestinations.containsValue(d.portName(p)))return false;
        return trunk(id,p)?allowsVlan(a.allowedVlans,vlan):accessVlan(a)==vlan;
    }
    public static boolean allowsVlan(String list,int vlan){return list==null||list.equals("all")||vlans(list).contains(vlan);}
    private static boolean sameVlans(String a,String b){return Objects.equals(a,b)||vlans(a).equals(vlans(b));}
    private boolean channelPair(String a,String b){return a.equals("on")&&b.equals("on")
        ||a.equals("active")&&(b.equals("active")||b.equals("passive"))||b.equals("active")&&a.equals("passive")
        ||a.equals("desirable")&&(b.equals("desirable")||b.equals("auto"))||b.equals("desirable")&&a.equals("auto");}
    public boolean channelUp(long id,int p){
        var d=n.devices.get(id);var a=d.ports.get(p);if(a.channelGroup==0)return true;
        var e=peer(id,p);if(e==null)return false;var other=n.devices.get(e.device());var b=other.ports.get(e.port());
        if(b.channelGroup==0||!channelPair(a.channelMode,b.channelMode)||!a.speed.equals(b.speed)||!a.duplex.equals(b.duplex)
            ||trunk(id,p)!=trunk(e.device(),e.port())||accessVlan(a)!=accessVlan(b)||nativeVlan(a)!=nativeVlan(b)
            ||!sameVlans(a.allowedVlans,b.allowedVlans))return false;
        for(int index=0;index<d.ports.size();index++){var member=d.ports.get(index);if(member.channelGroup!=a.channelGroup)continue;
            if(!member.speed.equals(a.speed)||!member.duplex.equals(a.duplex)||member.trunk!=a.trunk||accessVlan(member)!=accessVlan(a)||nativeVlan(member)!=nativeVlan(a)||!member.allowedVlans.equals(a.allowedVlans))return false;
            var remote=peer(id,index);if(remote!=null&&(remote.device()!=e.device()||n.devices.get(remote.device()).ports.get(remote.port()).channelGroup!=b.channelGroup))return false;
        }
        return true;
    }
    public int member(long id,int group,int vlan,String source,String destination,Set<Network.Endpoint> blocked){
        var d=n.devices.get(id);var candidates=new ArrayList<Integer>();
        for(int p=0;p<d.ports.size();p++)if(d.ports.get(p).channelGroup==group&&carries(id,p,vlan)&&channelUp(id,p)&&!blocked.contains(new Network.Endpoint(id,p)))candidates.add(p);
        if(candidates.isEmpty())return -1;String key=d.os().portChannelLoadBalance.equals("dst-mac")?destination:source;
        return candidates.get(Math.floorMod(key.hashCode(),candidates.size()));
    }
    private boolean betterSender(Network.Endpoint a,Network.Endpoint b,int vlan){
        int c=compare(a.device(),b.device(),vlan);if(c!=0)return c<0;
        c=Integer.compare(n.devices.get(a.device()).ports.get(a.port()).stpPriorities.getOrDefault(vlan,128),n.devices.get(b.device()).ports.get(b.port()).stpPriorities.getOrDefault(vlan,128));
        return c!=0?c<0:a.port()<b.port();
    }
    private boolean guarded(long id,int p){
        var d=n.devices.get(id);var a=d.ports.get(p);var e=peer(id,p);
        boolean guard=a.bpduGuardMode.equals("enable")||!a.bpduGuardMode.equals("disable")&&
            (a.bpduGuard||d.os().spanningBpduGuardDefault&&(a.portfast||d.os().spanningPortfastDefault&&!trunk(id,p)));
        if(guard&&e!=null&&n.devices.get(e.device()).kind.equals("switch")){
            if(!a.errDisabled){a.errDisabled=true;log(d,"BPDU Guard err-disabled "+d.portName(p));n.changed.add(id);}return true;
        }return a.errDisabled;
    }
    private long bridge(long id,int vlan){var d=n.devices.get(id);return ((long)d.os().bridgePriorities.getOrDefault(vlan,d.os().spanningPriority)<<48)|(id&0x0000ffffffffffffL);}
    private int compare(long a,long b,int vlan){int c=Integer.compare(n.devices.get(a).os().bridgePriorities.getOrDefault(vlan,n.devices.get(a).os().spanningPriority),n.devices.get(b).os().bridgePriorities.getOrDefault(vlan,n.devices.get(b).os().spanningPriority));return c!=0?c:Long.compareUnsigned(a,b);}
    /** Per-VLAN shortest-path spanning tree, synchronous convergence. */
    public Set<Network.Endpoint> blocked(int vlan){
        var blocked=new HashSet<Network.Endpoint>();var nodes=new TreeSet<Long>();
        n.devices.forEach((id,d)->{if(d.kind.equals("switch")||d.ports.stream().anyMatch(p->p.l2))nodes.add(id);});
        record Edge(Network.Endpoint a,Network.Endpoint b,int cost){}
        var edges=new ArrayList<Edge>();var groups=new HashSet<String>();
        for(var w:n.wires){if(!nodes.contains(w.a().device())||!nodes.contains(w.b().device()))continue;
            var a=n.devices.get(w.a().device()).ports.get(w.a().port());var b=n.devices.get(w.b().device()).ports.get(w.b().port());
            if(guarded(w.a().device(),w.a().port())||guarded(w.b().device(),w.b().port())||!carries(w.a().device(),w.a().port(),vlan)||!carries(w.b().device(),w.b().port(),vlan)
                ||!channelUp(w.a().device(),w.a().port())||!channelUp(w.b().device(),w.b().port()))continue;
            String group=a.channelGroup>0?w.a().device()+":"+a.channelGroup+":"+w.b().device()+":"+b.channelGroup:"wire:"+w;
            if(!groups.add(group))continue;
            edges.add(new Edge(w.a(),w.b(),Math.max(a.stpCosts.getOrDefault(vlan,4),b.stpCosts.getOrDefault(vlan,4))));
        }
        var remaining=new TreeSet<>(nodes);
        while(!remaining.isEmpty()){
            long seed=remaining.first();var component=new HashSet<Long>();component.add(seed);
            for(boolean change=true;change;){change=false;for(var e:edges)if(component.contains(e.a.device())||component.contains(e.b.device())){change|=component.add(e.a.device());change|=component.add(e.b.device());}}
            remaining.removeAll(component);long root=component.stream().min((a,b)->compare(a,b,vlan)).orElse(seed);
            var distance=new HashMap<Long,Integer>();var parent=new HashMap<Long,Edge>();distance.put(root,0);
            for(int k=0;k<component.size();k++){boolean update=false;for(var e:edges){
                long a=e.a.device(),b=e.b.device();int da=distance.getOrDefault(a,100000000),db=distance.getOrDefault(b,100000000);
                var pb=parent.get(b);var pa=parent.get(a);
                if(da+e.cost<db||da+e.cost==db&&pb!=null&&betterSender(e.a,pb.a.device()==b?pb.b:pb.a,vlan)){distance.put(b,da+e.cost);parent.put(b,e);update=true;}
                if(db+e.cost<da||db+e.cost==da&&pa!=null&&betterSender(e.b,pa.a.device()==a?pa.b:pa.a,vlan)){distance.put(a,db+e.cost);parent.put(a,e);update=true;}
            }if(!update)break;}
            for(var e:edges){if(!component.contains(e.a.device()))continue;if(parent.get(e.a.device())==e||parent.get(e.b.device())==e)continue;
                int c=Integer.compare(distance.getOrDefault(e.a.device(),0),distance.getOrDefault(e.b.device(),0));
                if(c==0)c=compare(e.a.device(),e.b.device(),vlan);var end=c<=0?e.b:e.a;blocked.add(end);
                var port=n.devices.get(end.device()).ports.get(end.port());if(port.channelGroup>0)for(int p=0;p<n.devices.get(end.device()).ports.size();p++)if(n.devices.get(end.device()).ports.get(p).channelGroup==port.channelGroup)blocked.add(new Network.Endpoint(end.device(),p));
            }
        }return blocked;
    }
    public boolean security(long id,int p,String source){
        var d=n.devices.get(id);var a=d.ports.get(p);if(!a.portSecurity)return true;
        String mac=mac(source);var learned=d.os().secureLearned.computeIfAbsent(p,k->new LinkedHashSet<>());
        var configured=new HashSet<String>(a.stickyMacs);if(!a.secureMac.isEmpty())configured.add(mac(a.secureMac));
        if(configured.contains(mac)||learned.contains(mac))return true;
        if(configured.size()+learned.size()<a.securityMaximum){if(a.sticky)a.stickyMacs.add(mac);else learned.add(mac);n.changed.add(id);return true;}
        if(!a.violation.equals("protect"))d.os().securityViolations.merge(p,1L,Long::sum);
        a.drops++;if(a.violation.equals("shutdown"))a.errDisabled=true;
        log(d,"Port security violation "+d.portName(p)+" source "+mac);n.changed.add(id);return false;
    }
    public void learn(long id,int port,int vlan,String source){
        var d=n.devices.get(id);var os=d.os();long now=System.currentTimeMillis();
        os.learnedMacs.entrySet().removeIf(e->os.macAging>0&&now-e.getValue().learned()>os.macAging*1000L);
        if(os.learnedMacs.size()>=4096)os.learnedMacs.remove(os.learnedMacs.keySet().iterator().next());
        os.learnedMacs.put(vlan+":"+mac(source),new RouterState.MacEntry(port,now));n.changed.add(id);
    }
    public int lookup(long id,int vlan,String destination){
        var d=n.devices.get(id);String mac=mac(destination);
        String name=d.os().staticMacs.get(mac+" vlan "+vlan);if(name!=null)return d.findInterface(name,false);
        var row=d.os().learnedMacs.get(vlan+":"+mac);if(row==null)return -1;
        if(d.os().macAging>0&&System.currentTimeMillis()-row.learned()>d.os().macAging*1000L){d.os().learnedMacs.remove(vlan+":"+mac);return -1;}
        return row.port();
    }
    public static void log(Device d,String text){var l=d.os().switchLog;l.add(text);while(l.size()>100)l.removeFirst();}
    public void syncVtp(){
        var sources=n.devices.entrySet().stream().filter(e->e.getValue().kind.equals("switch")&&!e.getValue().os().vtpMode.equals("transparent"))
            .sorted((a,b)->Integer.compare(b.getValue().os().vtpRevision,a.getValue().os().vtpRevision)).toList();
        for(var source:sources){var s=source.getValue().os();if(s.vtpDomain.isEmpty())continue;
            var queue=new ArrayDeque<Long>();var seen=new HashSet<Long>();queue.add(source.getKey());
            while(!queue.isEmpty()&&seen.size()<1024){long current=queue.remove();if(!seen.add(current))continue;
                for(var w:n.wires){var a=w.a().device()==current?w.a():w.b().device()==current?w.b():null;if(a==null)continue;
                    var b=a.equals(w.a())?w.b():w.a();var dst=n.devices.get(b.device());
                    if(dst==null||!dst.kind.equals("switch")||!n.linkUp(a.device(),a.port())||!trunk(a.device(),a.port())||!trunk(b.device(),b.port()))continue;
                    var d=dst.os();if(!s.vtpDomain.equals(d.vtpDomain)||!s.vtpVersion.equals(d.vtpVersion))continue;
                    if(d.vtpMode.equals("transparent")){queue.add(b.device());continue;}
                    if(!s.vtpPassword.equals(d.vtpPassword))continue;
                    if(s.vtpRevision>d.vtpRevision){d.vlans=new TreeMap<>(s.vlans);d.vtpRevision=s.vtpRevision;n.changed.add(b.device());}
                    queue.add(b.device());
                }
            }
        }
    }
}

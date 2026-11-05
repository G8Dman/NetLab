package dev.netlab.core;

import java.util.*;

/** Commands backed by the switching engine rather than display-only configuration. */
public final class SwitchLogicCommands {
    public static String execute(Console c,Device d,Network n,long id,String line){
        String[] a=line.split("\\s+");String cmd=line.toLowerCase(Locale.ROOT);var os=d.os();
        if(cmd.equals("show logging"))return String.join("\n",os.switchLog);
        if(cmd.equals("show cdp interface")){var out=new StringBuilder("Interface CDP\n");for(int p=0;p<d.ports.size();p++){var port=d.ports.get(p);if(!port.hidden&&!port.console&&!port.virtual)out.append(d.portName(p)).append(' ').append(os.cdp&&port.cdp?"enabled":"disabled").append('\n');}return out.toString();}
        if(c.mode==8&&a[0].equals("name")){os.vlans.put(Integer.parseInt(c.context),a[1]);os.vtpRevision++;return "";}
        if(cmd.equals("show ip dhcp snooping binding"))return os.snoopingBindings.toString();
        if(cmd.equals("show vtp status"))return "VTP domain: "+os.vtpDomain+"\nMode: "+os.vtpMode+"\nVersion: "+os.vtpVersion+"\nRevision: "+os.vtpRevision;
        if(cmd.startsWith("show monitor session ")){int session=Addressing.number(a[3],1,64);return "Session "+session+" source "+os.spanSources.get(session)+" destination "+os.spanDestinations.get(session)+"\nMirrored packets are emitted as SPAN events.";}
        if(cmd.equals("show mac address-table")||cmd.startsWith("show mac address-table ")){
            String filterPort=cmd.contains(" interface ")?a[a.length-1]:"";
            int filterVlan=cmd.contains(" vlan ")?Addressing.number(a[a.length-1],1,4094):-1;
            var out=new StringBuilder("VLAN MAC Address       Type     Port\n");long now=System.currentTimeMillis();
            os.learnedMacs.entrySet().removeIf(e->os.macAging>0&&now-e.getValue().learned()>os.macAging*1000L);
            os.learnedMacs.forEach((key,row)->{int colon=key.indexOf(':');int vlan=Integer.parseInt(key.substring(0,colon));String port=d.portName(row.port());
                if((filterVlan<0||filterVlan==vlan)&&(filterPort.isEmpty()||d.findInterface(filterPort,false)==row.port()))out.append(vlan).append(' ').append(key.substring(colon+1)).append(" DYNAMIC ").append(port).append('\n');});
            if(!cmd.endsWith("dynamic"))os.staticMacs.forEach((key,port)->{if((filterPort.isEmpty()||d.findInterface(port,false)==d.findInterface(filterPort,false))&&(filterVlan<0||key.endsWith(" vlan "+filterVlan)))out.append(key).append(" STATIC ").append(port).append('\n');});return out.toString();
        }
        if(cmd.startsWith("clear mac address-table dynamic")){if(a.length>5){int p=iface(d,a[5]);os.learnedMacs.entrySet().removeIf(e->e.getValue().port()==p);}else os.learnedMacs.clear();return "Dynamic MAC entries cleared";}
        if(cmd.equals("show vlan brief")){
            var table=new TreeMap<>(os.vlans);table.putIfAbsent(1,"default");var out=new StringBuilder("VLAN Name Status Ports\n");
            table.forEach((v,name)->{out.append(v).append(' ').append(name).append(" active ");for(int i=0;i<d.ports.size();i++)if(!d.ports.get(i).console&&!d.ports.get(i).virtual&&!n.switching.trunk(id,i)&&n.switching.accessVlan(d.ports.get(i))==v)out.append(d.portName(i)).append(' ');out.append('\n');});return out.toString();
        }
        if(cmd.startsWith("show spanning-tree")){
            int v=cmd.contains(" vlan ")?Addressing.number(a[3],1,4094):1;var blocked=n.switching.blocked(v);
            var out=new StringBuilder("VLAN "+v+" "+os.spanningMode+" (synchronous convergence)\nPort Role State\n");
            int selected=cmd.contains(" interface ")?iface(d,a[3]):-1;
            for(int p=0;p<d.ports.size();p++)if((selected<0||p==selected)&&!d.ports.get(p).hidden&&!d.ports.get(p).virtual&&!d.ports.get(p).console)out.append(d.portName(p)).append(' ').append(blocked.contains(new Network.Endpoint(id,p))?"Alternate Blocking":n.switching.carries(id,p,v)?"Designated/Root Forwarding":"Disabled Down").append('\n');return out.toString();
        }
        if(cmd.startsWith("show etherchannel")||cmd.startsWith("show interfaces port-channel")){var out=new StringBuilder("Group Port Protocol State\n");for(int p=0;p<d.ports.size();p++){var port=d.ports.get(p);if(port.channelGroup>0&&(!cmd.contains("port-channel")||port.channelGroup==Addressing.number(a[a.length-1],1,64)))out.append(port.channelGroup).append(' ').append(d.portName(p)).append(' ').append(port.channelMode).append(' ').append(n.switching.channelUp(id,p)?"bundled":"suspended").append('\n');}return out.toString();}
        if(cmd.startsWith("show port-security")){int selected=cmd.contains(" interface ")?iface(d,a[3]):-1;var out=new StringBuilder("Port Maximum Learned Violations State Action\n");for(int p=0;p<d.ports.size();p++){var port=d.ports.get(p);if(port.portSecurity&&(selected<0||p==selected)){out.append(d.portName(p)).append(' ').append(port.securityMaximum).append(' ').append(os.secureLearned.getOrDefault(p,Set.of()).size()+port.stickyMacs.size()+(port.secureMac.isEmpty()?0:1)).append(' ').append(os.securityViolations.getOrDefault(p,0L)).append(' ').append(port.errDisabled?"err-disabled":"secure-up").append(' ').append(port.violation).append('\n');if(cmd.endsWith("address"))out.append(port.stickyMacs).append(' ').append(os.secureLearned.getOrDefault(p,Set.of())).append('\n');}}return out.toString();}
        if(cmd.equals("show interfaces trunk")){var out=new StringBuilder("Port Status Native VLAN Allowed VLANs\n");for(int p=0;p<d.ports.size();p++)if(!d.ports.get(p).console&&!d.ports.get(p).virtual&&n.switching.trunk(id,p))out.append(d.portName(p)).append(' ').append(n.linkUp(id,p)?"trunking":"down").append(' ').append(n.switching.nativeVlan(d.ports.get(p))).append(' ').append(d.ports.get(p).allowedVlans).append('\n');return out.toString();}
        if(cmd.equals("show interfaces switchport")||cmd.startsWith("show interfaces switchport ")){
            if(a.length>3)return execute(c,d,n,id,"show interfaces "+a[3]+" switchport");
            var out=new StringBuilder();for(int p=0;p<d.ports.size();p++)if(!d.ports.get(p).hidden&&!d.ports.get(p).console&&!d.ports.get(p).virtual)out.append(execute(c,d,n,id,"show interfaces "+d.portName(p)+" switchport")).append('\n');return out.toString();
        }
        if(cmd.startsWith("show interfaces ")&&cmd.endsWith(" switchport")){int p=iface(d,a[2]);var port=d.ports.get(p);return d.portName(p)+"\nAdministrative Mode: "+port.switchMode+"\nOperational Mode: "+(n.switching.trunk(id,p)?"trunk":"access")+"\nAccess VLAN: "+n.switching.accessVlan(port)+"\nNative VLAN: "+n.switching.nativeVlan(port)+"\nAllowed: "+port.allowedVlans;}
        if(c.mode==2){
            if(cmd.startsWith("monitor session ")){if(a.length!=6||!a[4].equalsIgnoreCase("interface"))throw new IllegalArgumentException("NUMBER source|destination interface INTERFACE");int session=Addressing.number(a[2],1,64),p=iface(d,a[5]);String side=CommandGrammar.keyword(a[3],List.of("source","destination"));if(side.equals("source"))os.spanSources.put(session,d.portName(p));else os.spanDestinations.put(session,d.portName(p));return "";}
            if(cmd.startsWith("interface range ")){
                String input=line.substring("interface range ".length()).replaceAll("\\s+","");String[] bounds=input.split("-",2);
                int first=iface(d,bounds[0]);String lastName=bounds.length==1?bounds[0]:bounds[1];
                if(lastName.matches("[0-9]+")){String name=d.portName(first);lastName=name.substring(0,name.lastIndexOf('/')+1)+lastName;}
                int last=iface(d,lastName);if(last<first)throw new IllegalArgumentException("Invalid interface range");
                c.context="range:"+first+":"+last;c.selected=first;c.mode=3;return "";
            }
            if(cmd.startsWith("default interface ")){int p=iface(d,String.join("",Arrays.asList(a).subList(2,a.length)));var old=d.ports.get(p);var fresh=new Device.Port(!old.virtual||!old.name.equals("Vlan1"));fresh.name=old.name;fresh.console=old.console;fresh.virtual=old.virtual;fresh.hidden=old.hidden;fresh.hardwareSlot=old.hardwareSlot;fresh.medium=old.medium;fresh.maxMbps=old.maxMbps;fresh.bandwidth=old.maxMbps>0?old.maxMbps*1000:64;fresh.l2=old.l2;if(!fresh.virtual&&d.hardware!=null&&PortLayout.paired(d.hardware.profile))fresh.switchMode="dynamic auto";d.ports.set(p,fresh);os.secureLearned.remove(p);os.learnedMacs.entrySet().removeIf(e->e.getValue().port()==p);return "";}
            if(cmd.startsWith("vlan ")||cmd.startsWith("no vlan ")){
                if(os.vtpMode.equals("client"))throw new IllegalArgumentException("VTP client cannot modify VLAN database");
                int v=Addressing.number(a[a.length-1],1,4094);if(v>=1002&&v<=1005||cmd.startsWith("no ")&&v==1)throw new IllegalArgumentException("Reserved VLAN");
                if(cmd.startsWith("no "))os.vlans.remove(v);else{os.vlans.putIfAbsent(v,"VLAN"+v);c.context=""+v;c.mode=8;}os.vtpRevision++;return "";
            }
            if(cmd.startsWith("mac address-table aging-time ")){os.macAging=Addressing.number(a[3],0,1000000);return "";}
            if(cmd.startsWith("mac address-table static ")||cmd.startsWith("no mac address-table static ")){
                int off=cmd.startsWith("no ")?1:0;if(a.length!=8+off||!a[4+off].equalsIgnoreCase("vlan")||!a[6+off].equalsIgnoreCase("interface"))throw new IllegalArgumentException("MAC vlan VLAN interface INTERFACE");
                String key=Switching.mac(a[3+off])+" vlan "+Addressing.number(a[5+off],1,4094);int p=iface(d,a[7+off]);if(off==1)os.staticMacs.remove(key);else os.staticMacs.put(key,d.portName(p));return "";
            }
            if(cmd.startsWith("spanning-tree vlan ")){int v=Addressing.number(a[2],1,4094);int priority;
                if(a.length==5&&a[3].equalsIgnoreCase("priority"))priority=Addressing.number(a[4],0,61440);
                else if(a.length==5&&a[3].equalsIgnoreCase("root"))priority=CommandGrammar.keyword(a[4],List.of("primary","secondary")).equals("primary")?24576:28672;
                else throw new IllegalArgumentException("VLAN priority VALUE | root primary|secondary");
                if(priority%4096!=0)throw new IllegalArgumentException("Bridge priority must be a multiple of 4096");os.bridgePriorities.put(v,priority);return "";
            }
        }
        if(c.mode==3&&c.selected>=0){
            if(cmd.startsWith("ip dhcp snooping limit rate ")){d.ports.get(c.selected).dhcpRate=Addressing.number(a[5],1,100000);return "";}
            if(d.portName(c.selected).startsWith("Port-channel")&&!c.context.startsWith("range:")){
                int group=Integer.parseInt(d.portName(c.selected).substring(12)),selected=c.selected;String result=null;
                try{for(int p=0;p<d.ports.size();p++)if(!d.ports.get(p).virtual&&d.ports.get(p).channelGroup==group){c.selected=p;String r=execute(c,d,n,id,line);if(r==null)r=SwitchCommands.executeSingle(c,d,n,id,line);if(r!=null&&r.startsWith("%"))return r;result=r;}}finally{c.selected=selected;}
                if(result!=null)return result;
            }
            if(c.context.startsWith("range:")){var bounds=c.context.split(":");int first=Integer.parseInt(bounds[1]),last=Integer.parseInt(bounds[2]),selected=c.selected;String context=c.context;c.context="";
                try{String result=null;for(int p=first;p<=last;p++){c.selected=p;String r=execute(c,d,n,id,line);if(r==null)r=SwitchCommands.executeSingle(c,d,n,id,line);if(r!=null&&r.startsWith("%"))return r;result=r;}return result;}finally{c.selected=selected;c.context=context;}}
            var port=d.ports.get(c.selected);
            if(cmd.equals("shutdown")){port.up=false;return "";}if(cmd.equals("no shutdown")){if(!port.up)port.errDisabled=false;port.up=true;return "";}
            if(cmd.startsWith("switchport mode ")){String mode=CommandGrammar.keyword(a[2],List.of("access","trunk","dynamic"));if(mode.equals("dynamic")){if(a.length!=4)throw new IllegalArgumentException("dynamic auto|desirable");mode+=" "+CommandGrammar.keyword(a[3],List.of("auto","desirable"));}port.switchMode=mode;port.trunk=mode.equals("trunk");return "";}
            if(cmd.startsWith("switchport access vlan ")){port.accessVlan=Addressing.number(a[3],1,4094);port.vlan=0;return "";}
            if(cmd.equals("no switchport access vlan")){port.accessVlan=1;port.vlan=0;return "";}
            if(cmd.startsWith("switchport trunk native vlan ")){port.trunkNative=Addressing.number(a[4],1,4094);return "";}
            if(cmd.equals("no switchport trunk native vlan")){if(port.vlan>0&&port.accessVlan==1)port.accessVlan=port.vlan;port.vlan=0;port.trunkNative=1;return "";}
            if(cmd.equals("no switchport trunk allowed vlan")){port.allowedVlans="all";return "";}
            if(cmd.startsWith("switchport trunk allowed vlan ")){String value=a[4];if(value.equalsIgnoreCase("add")||value.equalsIgnoreCase("remove")){
                if(a.length!=6)throw new IllegalArgumentException("add|remove VLAN-LIST");var set=Switching.vlans(port.allowedVlans);var change=Switching.vlans(a[5]);if(value.equalsIgnoreCase("add"))set.addAll(change);else set.removeAll(change);port.allowedVlans=set.isEmpty()?"none":String.join(",",set.stream().map(Object::toString).toList());
            }else{Switching.vlans(value);port.allowedVlans=value;}return "";}
            if(cmd.equals("switchport nonegotiate")){port.nonegotiate=true;return "";}if(cmd.equals("no switchport nonegotiate")){port.nonegotiate=false;return "";}
            if(cmd.startsWith("channel-group ")){if(port.portSecurity)throw new IllegalArgumentException("Port security cannot join EtherChannel");port.channelGroup=Addressing.number(a[1],1,64);int k=a.length==4&&a[2].equalsIgnoreCase("mode")?3:2;port.channelMode=CommandGrammar.keyword(a[k],List.of("active","passive","desirable","auto","on"));return "";}
            if(cmd.startsWith("no channel-group ")){port.channelGroup=0;port.channelMode="";return "";}
            if(cmd.equals("switchport port-security")){if(port.channelGroup>0||n.switching.trunk(id,c.selected))throw new IllegalArgumentException("Port security requires access port outside EtherChannel");port.portSecurity=true;return "";}
            if(cmd.startsWith("switchport port-security mac-address ")&&!a[3].equalsIgnoreCase("sticky")){port.secureMac=Switching.mac(a[3]);return "";}
            if(cmd.startsWith("spanning-tree bpduguard ")){port.bpduGuardMode=CommandGrammar.keyword(a[2],List.of("enable","disable"));return "";}
            if(cmd.startsWith("spanning-tree vlan ")){int v=Addressing.number(a[2],1,4094);if(a.length!=5)throw new IllegalArgumentException("VLAN cost|port-priority VALUE");if(a[3].equalsIgnoreCase("cost"))port.stpCosts.put(v,Addressing.number(a[4],1,200000000));else{int val=Addressing.number(a[4],0,240);if(val%16!=0)throw new IllegalArgumentException("Port priority must be multiple of 16");port.stpPriorities.put(v,val);}return "";}
        }
        return null;
    }
    private static int iface(Device d,String name){int p=d.findInterface(name,false);if(p<0||d.ports.get(p).console)throw new IllegalArgumentException("Interface does not exist");return p;}
}

package dev.netlab.automation.core;

import dev.netlab.core.*;
import java.util.*;

/** The world builder and dependency-free checks use this exact device/wiring plan. */
public final class AutomationDemoPlan {
    public static final int WIDTH=11,DEPTH=7,HEIGHT=3,SOURCE_COUNT=64,TRANSFER_COUNT=16;
    public enum Node {SERVER,SWITCH,SOURCE,DESTINATION,SERVER_TERMINAL,SOURCE_TERMINAL,DESTINATION_TERMINAL}
    public record Address(String prefix){public String server(){return prefix+"10";}public String source(){return prefix+"11";}public String destination(){return prefix+"12";}}
    public record Link(Node a,int ap,Node b,int bp,String cable){}
    public static final List<Link> LINKS=List.of(
        new Link(Node.SERVER,0,Node.SWITCH,2,"copper"),
        new Link(Node.SOURCE,0,Node.SWITCH,0,"copper"),new Link(Node.DESTINATION,0,Node.SWITCH,1,"copper"),
        new Link(Node.SERVER_TERMINAL,1,Node.SERVER,1,"console"),new Link(Node.SERVER_TERMINAL,0,Node.SWITCH,3,"copper"),
        new Link(Node.SOURCE_TERMINAL,1,Node.SOURCE,1,"console"),new Link(Node.DESTINATION_TERMINAL,1,Node.DESTINATION,1,"console"));
    public static Address choose(Collection<String> occupied){
        for(int offset=0;offset<253;offset++){int octet=2+(75+offset)%253;String prefix="192.168."+octet+".";if(occupied.stream().noneMatch(ip->ip.startsWith(prefix)))return new Address(prefix);}
        throw new IllegalArgumentException("Нет свободной подсети 192.168.2–254.0/24 для демо");
    }
    public static Device device(Node node,Address a){
        String kind=switch(node){case SERVER->"server";case SWITCH->"switch";case SOURCE,DESTINATION->"endpoint";default->"pc";};
        var d=new Device(kind);d.hostname=switch(node){case SERVER->"DEMO-SERVER";case SWITCH->"DEMO-SWITCH";case SOURCE->"DEMO-SOURCE";case DESTINATION->"DEMO-DEST";case SERVER_TERMINAL->"TERMINAL-SERVER";case SOURCE_TERMINAL->"TERMINAL-SOURCE";case DESTINATION_TERMINAL->"TERMINAL-DEST";};
        if(kind.equals("pc")){Hardware.initializeHost(d,true);d.hardware.powered=false;Hardware.install(d,"usb0","USB-RS232");if(node==Node.SERVER_TERMINAL){Hardware.install(d,"app0","CISCO-AUTOMATION");d.ports.get(0).ip=a.server().substring(0,a.server().lastIndexOf('.')+1)+"100";}d.hardware.powered=true;}
        else {d.ports.getFirst().ip=switch(node){case SERVER->a.server();case SOURCE->a.source();case DESTINATION->a.destination();default->"";};d.ports.getFirst().mask="255.255.255.0";d.ports.getFirst().up=true;}
        if(kind.equals("server")){ServerHardware.register();var h=Hardware.ensure(d);h.profile="Automation Server";h.powered=false;Hardware.install(d,"cooling0",ServerHardware.FAN);h.powered=true;}
        d.gateway="";return d;
    }
    public static Network network(Address a){var n=new Network();for(var node:Node.values())n.devices.put((long)node.ordinal(),device(node,a));for(var w:LINKS)n.wires.add(new Network.Wire(new Network.Endpoint(w.a.ordinal(),w.ap),new Network.Endpoint(w.b.ordinal(),w.bp),4,w.cable));return n;}
}

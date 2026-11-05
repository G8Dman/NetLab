package dev.netlab.automation.core;
import dev.netlab.core.*;
public final class ManagementAccess {
    public static boolean installed(Device pc){return pc.kind.equals("pc")&&Hardware.ensure(pc).modules.containsValue("CISCO-AUTOMATION");}
    public static boolean local(Network network,long pc,String ip){var from=network.devices.get(pc);if(from==null)return false;var port=from.ports.get(from.kind.equals("pc")?Wireless.clientPort(from):0);if(port.ip.isBlank()||(Device.ip(port.ip)&Device.mask(port.mask))!=(Device.ip(ip)&Device.mask(port.mask)))return false;var route=network.routePath(pc,ip);return route.success()&&route.path().stream().noneMatch(id->network.devices.get(id).kind.equals("router"));}
}

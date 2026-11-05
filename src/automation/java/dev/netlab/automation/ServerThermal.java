package dev.netlab.automation;

import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.automation.core.*;
import java.util.*;

/** Physical state stays on the device, including moves and cardless restarts. */
public final class ServerThermal {
    public static final String KEY="netlabautomation:heat";
    private static final Gson JSON=new Gson();
    public static ServerHeat read(Device d){var state=d.extensions.containsKey(KEY)?JSON.fromJson(d.extensions.get(KEY),ServerHeat.class):new ServerHeat();state.normalize();return state;}
    public static void preserve(Device next,Device previous){if(!next.kind.equals("server"))return;if(previous.extensions.containsKey(KEY))next.extensions.put(KEY,previous.extensions.get(KEY));else next.extensions.remove(KEY);}
    private static void save(AutomationNode node,ServerHeat state){node.device.extensions.put(KEY,JSON.toJson(state));node.host.changed();}
    public static boolean ready(Device d){return !d.kind.equals("server")||!read(d).protectedStop;}
    public static void require(AutomationNode node){if(!ready(node.device))throw new IllegalArgumentException("Перегрев сервера: "+String.format(Locale.ROOT,"%.1f °C",read(node.device).temperature)+". Охлади до 60 °C; грузы сохранены в журнале");}
    public static void work(AutomationNode node,double effort){if(!node.device.kind.equals("server"))return;require(node);var state=read(node.device);state.work(node.device,effort);save(node,state);require(node);}
    public static int delay(AutomationNode node){return read(node.device).offerTicks(node.device);}
    public static String summary(Device d){if(!d.kind.equals("server"))return "";var state=read(d);return String.format(Locale.ROOT,"T %.1f °C / кулер: %s%s",state.temperature,ServerHardware.cooler(d),state.protectedStop?" / ПЕРЕГРЕВ":state.temperature>=ServerHeat.THROTTLE?" / троттлинг":"");}
    public static JsonObject snapshot(Device d){var out=new JsonObject();var heat=read(d);out.addProperty("temperature",heat.temperature);out.addProperty("overheated",heat.protectedStop);out.addProperty("throttled",heat.temperature>=ServerHeat.THROTTLE);out.addProperty("cooler",ServerHardware.cooler(d));out.addProperty("itemLimit",ServerHardware.itemLimit(d));out.addProperty("offerSeconds",heat.offerTicks(d)/20.0);out.addProperty("energy",ServerHardware.energy(d));return out;}
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event){if(event.getServer().getTickCount()%20!=0)return;for(var level:event.getServer().getAllLevels())for(var node:AutomationNode.loaded(level))if(node.device.kind.equals("server")){var state=read(node.device);state.advance(node.device,1);save(node,state);}}
}

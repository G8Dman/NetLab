package dev.netlab.academy.client;
import com.google.gson.*;
import dev.netlab.academy.*;
import dev.netlab.academy.core.Course;
import dev.netlab.client.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid="netlabacademy",value=Dist.CLIENT)
public final class AcademyClient {
    private static Set<String> installed=Set.of(),completed=Set.of(),evidence=Set.of();
    private static Course.Policy policy=new Course.Policy(false,false,false,false);
    private static boolean goals;
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{GuideBookContents.LIVE.add(AcademyClient::sections);GuideBookContents.TICKED.add(()->{if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new AcademyPackets.Page(""));});GuideBookContents.OPENED.add(id->{if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new AcademyPackets.Page(id));});});}
    private static Set<String> strings(JsonObject o,String key){var s=new LinkedHashSet<String>();for(var v:o.getAsJsonArray(key))s.add(v.getAsString());return Set.copyOf(s);}
    public static void state(AcademyPackets.State q){var j=JsonParser.parseString(q.json()).getAsJsonObject();installed=strings(j,"mods");completed=strings(j,"completed");evidence=strings(j,"evidence");policy=new Gson().fromJson(j.get("policy"),Course.Policy.class);goals=j.get("goals").getAsBoolean();var mc=Minecraft.getInstance();if(q.open())mc.setScreen(new GuideBookScreen("netlabacademy:course"));else if(mc.screen instanceof GuideBookScreen book)book.refreshContents();}
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){installed=Set.of();completed=Set.of();evidence=Set.of();goals=false;}
    public static List<GuideBookContents.Section> sections(){return CourseBook.sections(installed,completed,evidence,policy,goals);}
}

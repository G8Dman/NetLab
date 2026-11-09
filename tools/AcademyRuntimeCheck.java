import dev.netlab.academy.*;
import dev.netlab.academy.core.Course;
import dev.netlab.academy.client.CourseBook;
import dev.netlab.client.GuideSearchText;
import dev.netlab.core.GuideSearch;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.*;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.*;
import java.nio.file.*;
import java.util.*;
/** Uses actual Minecraft codecs/components, but never constructs Minecraft, a Level or a server. */
public final class AcademyRuntimeCheck {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static void main(String[] args)throws Exception{
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),new ArrayList<>(),Map.of());
        SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();var course=Catalog.COURSE;var json=new Gson();
        var all=Set.of("netlab","netlabautomation","netlabmobile","netlabsputnik","netlabmedia","voicechat","carryon","minecolonies","ae2");var policy=new Course.Policy(true,true,true,true);
        check(course.available(all,policy).size()==215,"all reviewed tasks load");check(course.main.size()==29,"29 main steps");
        String[] optional={"netlabautomation","netlabmobile","netlabsputnik","netlabmedia","voicechat","carryon","minecolonies","ae2"};
        for(int bits=0;bits<256;bits++){
            var mods=new HashSet<String>(Set.of("netlab"));for(int i=0;i<optional.length;i++)if((bits&(1<<i))!=0)mods.add(optional[i]);
            for(boolean ae2:new boolean[]{false,true}){var pol=new Course.Policy(false,false,false,ae2);var active=course.available(mods,pol);var ids=new HashSet<String>();active.forEach(t->ids.add(t.id()));
                for(var t:active){check(ids.containsAll(t.parents()),"no absent parent in "+mods+" for "+t.id());check(mods.containsAll(t.mods()),"no missing addon task");}
                var data=new Course.Progress();for(var t:course.available(all,policy))data.prove(t.id());course.advance(data,mods,pol);check(data.completed.equals(ids),"valid facts close exactly the active DAG, including finals");
                var chapters=CourseBook.sections(mods,data.completed,data.evidence,pol,course.currentGoalsComplete(data,mods,pol));check(chapters.size()==1,"one handbook section");check(chapters.getFirst().articles().size()==active.size()+1,"book matches server mask exactly");
                for(var a:chapters.getFirst().articles()){check(!a.body().isBlank(),"nonempty task help");if(a.id().endsWith("route"))continue;String id=a.id().substring(a.id().lastIndexOf('/')+1).toUpperCase(Locale.ROOT);check(ids.contains(id),"book cannot expose absent-addon chapters");check(a.body().contains(course.task(id).task()),"book contains exact approved outcome");}
            }
        }
        var base=Set.of("netlab");var safe=new Course.Policy(false,false,false,false);var p=new Course.Progress();p.prove("R02");course.advance(p,base,safe);check(!p.completed.contains("R02"),"a ping alone cannot skip manufacturing course");check(!p.completed.contains("F01"),"final is not just R02");
        Set<String> required=new HashSet<>();for(String id:course.main)closure(course,id,required);required.forEach(p::prove);course.advance(p,base,safe);check(p.completed.contains("F01"),"full dependency closure of main route awards base final");check(p.completed.contains("F03"),"base final never needs optional mods");
        var old=new HashSet<>(p.completed);course.advance(p,all,policy);check(p.completed.containsAll(old),"adding addons never revokes old earned badges");check(!course.currentGoalsComplete(p,all,policy),"new installed-suite goals are tracked independently of old badge");
        var round=json.fromJson(json.toJson(p),Course.Progress.class);check(round.completed.equals(p.completed)&&round.evidence.equals(p.evidence),"real Gson progress round trip");
        for(var t:course.available(all,policy))p.prove(t.id());course.advance(p,all,policy);var allDone=Set.copyOf(p.completed);course.advance(p,base,safe);check(p.completed.equals(allDone),"removal keeps optional progress in persistent model");course.advance(p,all,policy);check(p.completed.equals(allDone),"reinstall restores without duplicate rewards");
        check(!course.goals(Set.of("netlab","netlabautomation"),safe).contains("A20"),"fluid fixtures never required by default");check(!course.goals(all,safe).contains("S12")&&!course.goals(all,safe).contains("V09"),"internet/microphone do not gate default final");
        var registries=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);var ops=net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,registries);
        int nativeCount=0;try(var paths=Files.walk(Path.of("src/academy/resources/data/netlabacademy/advancement"))){for(var file:paths.filter(f->f.toString().endsWith(".json")).toList()){
            var node=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var value=Advancement.CODEC.parse(ops,node).getOrThrow();check(value.rewards().experience()==0,"no XP rewards");check(value.rewards().loot().isEmpty(),"no free item rewards");check(value.criteria().values().stream().allMatch(c->c.triggerInstance() instanceof net.minecraft.advancements.critereon.ImpossibleTrigger.TriggerInstance),"no inventory/nearby backfill loophole");nativeCount++;
        }}check(nativeCount==230,"215 native advancements and 15 course headers parse with actual codec");
        String text="🙂 VLAN и vlan, затем ВЛАН. VLAN";var highlighted=GuideSearchText.highlight(text,"vlan");check(highlighted.getString().equals(text),"highlighting preserves all original text");var marks=new ArrayList<String>();highlighted.visit((style,s)->{if(style.isBold()&&style.isUnderlined()&&style.getColor()!=null&&style.getColor().getValue()==0x9b2400)marks.add(s);return Optional.empty();},Style.EMPTY);check(marks.equals(List.of("VLAN","vlan","VLAN")),"actual Component styles mark every literal occurrence");
        String russian="КОНСОЛЬ: консоль, Консоль";var h=GuideSearchText.highlight(russian,"консоль");var ranges=GuideSearch.matches(russian,"консоль");check(ranges.size()==3&&h.getString().equals(russian),"Cyrillic highlights retain case and punctuation");
        var splitter=new net.minecraft.client.StringSplitter((code,style)->style.isBold()?7:6);var lines=splitter.splitLines(h,42,Style.EMPTY);int styled=0;for(var line:lines){var total=new int[]{0};line.visit((style,s)->{if(style.isBold())total[0]+=s.length();return Optional.empty();},Style.EMPTY);styled+=total[0];}check(styled==21,"highlight style survives wrapping inside Russian words");
        var data=new AcademyStore();var id=UUID.randomUUID();data.player(id).completed.addAll(p.completed);data.player(id).values.put("saved:device","hash-without-credentials");var tag=data.save(new net.minecraft.nbt.CompoundTag(),registries);check(!tag.toString().contains("password"),"persisted course has only derived state");
        System.out.println("Academy: "+checks+" runtime checks passed (core-only classpath, no Minecraft/server/world launched)");
    }
    static void closure(Course c,String id,Set<String> out){if(!out.add(id))return;for(String parent:c.task(id).parents())closure(c,parent,out);}
}

import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.media.*;
import dev.netlab.media.core.*;
import dev.netlab.media.client.PlayerPage;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.nio.file.*;
import java.util.*;

/** Real registries, CISCO cards, codecs, equipment picking and API bridge. No game/server/Level. */
public final class MediaRuntimeCheck {
    static int checks;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    public static void register(){
        for(var entry:Media.DEVICES.entrySet())Registry.register(BuiltInRegistries.BLOCK,entry.getValue().getId(),new MediaBlock(BlockBehaviour.Properties.of(),entry.getKey()));
        for(var entry:Media.ITEMS.getEntries()){
            String name=entry.getId().getPath();var module=MediaHardware.MODULES.stream().filter(code->ModuleItem.itemId(code).equals(name)).findFirst().orElse(null);
            Item item=name.endsWith("_cable")?new CableItem(new Item.Properties(),name.replace("_cable","")):name.equals("phone_sip_module")?new PortableModules.ModuleItem(new Item.Properties(),"CISCO-POCKET-SIP","telephony","Phone SIP"):module!=null?new ModuleItem(new Item.Properties(),module):BuiltInRegistries.BLOCK.containsKey(entry.getId())?new BlockItem(BuiltInRegistries.BLOCK.get(entry.getId()),new Item.Properties()):new Item(new Item.Properties());Registry.register(BuiltInRegistries.ITEM,entry.getId(),item);if(item instanceof BlockItem b)b.registerBlocks(Item.BY_BLOCK,item);
        }
        Media.integrate();
    }
    public static void main(String[] args)throws Exception{
        SputnikRuntimeCheck.main(args);
        register();
        var owner=UUID.randomUUID();var d=new Device(MediaHardware.HEADEND);MediaHardware.initialize(d);var s=MediaState.get(d);s.owner=owner.toString();s.admin=ConfigStore.secret("test-admin-password");var c=new MediaState.Channel();c.name="Teaching channel";c.owner=owner.toString();c.running=true;c.started=123;c.playlist.add(new MediaPlaylist.Entry("https://example.org/lesson.webm","Lesson",100));s.channels.put(7,c);s.save(d);d.ports.getFirst().ip="192.168.50.10";MemoryCards.demo(d);
        var stack=RackDevices.item(d);var regs=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);var stored=ItemStack.parse(regs,stack.save(regs)).orElseThrow();var restored=RackDevices.restore(stored,d.kind);var rs=MediaState.get(restored);check(restored.ports.getFirst().ip.equals("192.168.50.10"),"rack item preserves configured IP");check(rs.channels.get(7).playlist.getFirst().seconds()==100&&rs.channels.get(7).started==123,"CISCO card preserves playlist and shared clock");check(ConfigStore.verify("test-admin-password",rs.admin),"admin hash survives real item/card serialization");check(rs.owner.equals(owner.toString()),"physical ownership remains with server");
        var replica=new Device(d.kind);MediaHardware.initialize(replica);var physical=MediaState.get(replica);physical.owner=UUID.randomUUID().toString();physical.save(replica);replica.extensions.put("netlab.memory",d.extensions.get("netlab.memory"));MemoryCards.read(replica);check(MediaState.get(replica).channels.size()==1,"card applies channel configuration");check(MediaState.get(replica).owner.equals(physical.owner),"card cannot steal physical ownership");
        var phone=new Device(MediaHardware.PHONE);MediaHardware.initialize(phone);var ps=MediaState.get(phone);ps.number="1001";ps.server="192.168.60.10";ps.password="fixture-phone-password";ps.save(phone);MemoryCards.demo(phone);var phoneItem=MemoryCards.deviceItem(Media.DEVICES.get(phone.kind).get(),phone);var restoredPhone=RackDevices.restore(ItemStack.parse(regs,phoneItem.save(regs)).orElseThrow(),phone.kind);check(MediaState.get(restoredPhone).password.equals(ps.password)&&MediaState.get(restoredPhone).number.equals("1001"),"breaking and replacing phone preserves actual active-card credentials");
        var pocket=new ItemStack(dev.netlab.mobile.Mobile.PHONE.get());var tag=new net.minecraft.nbt.CompoundTag();StoredJson.put(tag,"phoneMedia",MediaState.JSON.toJson(ps));dev.netlab.mobile.MobileItems.seal(pocket,tag);var pocketSaved=ItemStack.parse(regs,pocket.save(regs)).orElseThrow();var pocketState=MediaState.JSON.fromJson(StoredJson.get(dev.netlab.mobile.MobileItems.privateTag(pocketSaved),"phoneMedia"),MediaState.class);check(pocketState.server.equals(ps.server)&&pocketState.password.equals(ps.password),"Pocket media credentials persist in physical item NBT");
        var publicCopy=MediaState.JSON.fromJson(MediaState.JSON.toJson(d),Device.class);MediaState.redact(publicCopy);check(MediaState.get(publicCopy).admin.isBlank()&&MediaState.get(publicCopy).channels.isEmpty(),"shared block data hides hashes and links");check(MediaState.get(d).channels.size()==1,"redaction leaves actual server intact");
        for(String kind:MediaHardware.KINDS){var device=new Device(kind);MediaHardware.initialize(device);var model=net.minecraft.client.renderer.block.model.BlockModel.fromString(Files.readString(Path.of("src/media/resources/assets/netlabmedia/models/block/"+kind.substring(6)+".json")));var bs=Media.DEVICES.get(kind).get().defaultBlockState();check(!model.getElements().isEmpty(),"equipment model parses: "+kind);
            for(var jack:PortLayout.jacks(device,false,0))for(double dx:new double[]{-.45*jack.width(),0,.45*jack.width()})for(double dy:new double[]{-.45*jack.height(),0,.45*jack.height()}){double x=jack.x()+dx,y=jack.y()+dy;double front=model.getElements().stream().filter(box->box.rotation==null&&x*16>=box.from.x&&x*16<=box.to.x&&y*16>=box.from.y&&y*16<=box.to.y).mapToDouble(box->box.from.z/16).min().orElseThrow(()->new AssertionError("Socket floats outside model: "+kind));check(jack.z()<front,"port is visible ahead of case: "+kind);var hit=bs.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).clip(new net.minecraft.world.phys.Vec3(x,y,-1),new net.minecraft.world.phys.Vec3(x,y,1),BlockPos.ZERO);check(hit!=null&&hit.getDirection()==Direction.NORTH,"port lies on selectable front: "+kind);}
        }
        var ops=RegistryOps.create(JsonOps.INSTANCE,regs);try(var files=Files.list(Path.of("src/media/resources/data/netlabmedia/recipe"))){for(var file:files.toList()){var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var recipe=Recipe.CODEC.parse(ops,data).getOrThrow();var input=RecipeCoverageRuntimeCheck.input(data);check(recipe instanceof CraftingRecipe&&((CraftingRecipe)recipe).matches(input,null),"actual media recipe matches: "+file);check(!recipe.getResultItem(regs).isEmpty(),"registered media recipe output: "+file);}}
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),regs);var action=new MediaPackets.Action(BlockPos.ZERO,1,"login","x".repeat(8192));MediaPackets.Action.CODEC.encode(buffer,action);check(MediaPackets.Action.CODEC.decode(buffer).json().length()==8192,"bounded action codec roundtrip");buffer.release();
        for(String section:List.of("iptv","ip-telephony")){var json=JsonParser.parseString(Files.readString(Path.of("src/media/resources/assets/netlabmedia/netlab_guide/"+section+".json"))).getAsJsonObject();check(json.getAsJsonArray("articles").size()>=14,"full device-by-device guide: "+section);for(var article:json.getAsJsonArray("articles"))check(!article.getAsJsonObject().get("body").getAsString().isBlank(),"guide chapter has instructions");}
        check(PlayerPage.html("https://youtu.be/M7lc1UVf-VE",5).contains("YouTubeIframeAPIReady"),"YouTube player uses official API");var html=PlayerPage.html("https://example.org/video.webm?x=';alert(1)//",3);check(html.contains("document.createElement('video')")&&html.contains("\\u0027")&&!html.contains("x=';alert"),"media URL cannot terminate JavaScript string");
        check(PortableModules.DEFINITIONS.get("telephony").code().equals("CISCO-POCKET-SIP"),"Mobile discovers optional phone hardware through base API");
        MediaVoiceRuntimeCheck.run();
        System.out.println(checks+" Media registry/card/recipe/model/payload checks passed; no game, server, world or microphone launched.");
    }
}

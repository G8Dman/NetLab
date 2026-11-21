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
        for(var entry:Media.DEVICES.entrySet()){
            var properties=BlockBehaviour.Properties.of();
            var block=entry.getKey().equals(MediaHardware.TV)?new TelevisionBlock(properties):new MediaBlock(properties,entry.getKey());
            Registry.register(BuiltInRegistries.BLOCK,entry.getValue().getId(),block);
        }
        for(var entry:Media.ITEMS.getEntries()){
            String name=entry.getId().getPath();var module=MediaHardware.MODULES.stream().filter(code->ModuleItem.itemId(code).equals(name)).findFirst().orElse(null);
            Item item=name.equals("tv_remote")?new TelevisionControls(new Item.Properties().stacksTo(1)):name.endsWith("_cable")?new CableItem(new Item.Properties(),name.replace("_cable","")):name.equals("phone_sip_module")?new PortableModules.ModuleItem(new Item.Properties(),"CISCO-POCKET-SIP","telephony","Phone SIP"):module!=null?new ModuleItem(new Item.Properties(),module):BuiltInRegistries.BLOCK.containsKey(entry.getId())?new BlockItem(BuiltInRegistries.BLOCK.get(entry.getId()),new Item.Properties()):new Item(new Item.Properties());Registry.register(BuiltInRegistries.ITEM,entry.getId(),item);if(item instanceof BlockItem b)b.registerBlocks(Item.BY_BLOCK,item);
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
            if(kind.equals(MediaHardware.TV))continue;
            for(var jack:PortLayout.jacks(device,false,0))for(double dx:new double[]{-.45*jack.width(),0,.45*jack.width()})for(double dy:new double[]{-.45*jack.height(),0,.45*jack.height()}){double x=jack.x()+dx,y=jack.y()+dy;double front=model.getElements().stream().filter(box->box.rotation==null&&x*16>=box.from.x&&x*16<=box.to.x&&y*16>=box.from.y&&y*16<=box.to.y).mapToDouble(box->box.from.z/16).min().orElseThrow(()->new AssertionError("Socket floats outside model: "+kind));check(jack.z()<front,"port is visible ahead of case: "+kind);var hit=bs.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO).clip(new net.minecraft.world.phys.Vec3(x,y,-1),new net.minecraft.world.phys.Vec3(x,y,1),BlockPos.ZERO);check(hit!=null&&hit.getDirection()==Direction.NORTH,"port lies on selectable front: "+kind);}
        }
        televisionMounts();
        missingCablePort();
        televisionAudioRange();
        televisionRemote(regs);
        var ops=RegistryOps.create(JsonOps.INSTANCE,regs);try(var files=Files.list(Path.of("src/media/resources/data/netlabmedia/recipe"))){for(var file:files.toList()){var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var recipe=Recipe.CODEC.parse(ops,data).getOrThrow();var input=RecipeCoverageRuntimeCheck.input(data);check(recipe instanceof CraftingRecipe&&((CraftingRecipe)recipe).matches(input,null),"actual media recipe matches: "+file);check(!recipe.getResultItem(regs).isEmpty(),"registered media recipe output: "+file);}}
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),regs);var action=new MediaPackets.Action(BlockPos.ZERO,1,"login","x".repeat(8192));MediaPackets.Action.CODEC.encode(buffer,action);check(MediaPackets.Action.CODEC.decode(buffer).json().length()==8192,"bounded action codec roundtrip");buffer.release();
        for(String section:List.of("iptv","ip-telephony")){var json=JsonParser.parseString(Files.readString(Path.of("src/media/resources/assets/netlabmedia/netlab_guide/"+section+".json"))).getAsJsonObject();check(json.getAsJsonArray("articles").size()>=14,"full device-by-device guide: "+section);for(var article:json.getAsJsonArray("articles"))check(!article.getAsJsonObject().get("body").getAsString().isBlank(),"guide chapter has instructions");}
        check(PlayerPage.html("https://youtu.be/M7lc1UVf-VE",5).contains("YouTubeIframeAPIReady"),"YouTube player uses official API");var html=PlayerPage.html("https://example.org/video.webm?x=';alert(1)//",3);check(html.contains("document.createElement('video')")&&html.contains("\\u0027")&&!html.contains("x=';alert"),"media URL cannot terminate JavaScript string");
        var oldEntry=MediaState.JSON.fromJson("{\"url\":\"https://youtu.be/M7lc1UVf-VE\",\"title\":\"Old channel\",\"seconds\":60}",MediaPlaylist.Entry.class);
        check(oldEntry.fallback().isEmpty()&&oldEntry.seconds()==60,"previously saved channel entries load without a fallback field");
        for(var entry:Map.of("https://rutube.ru/video/57e1cb24939d657284dc2900deb5949a/","player:setCurrentTime","https://vkvideo.ru/video-1_2","VK.VideoPlayer","https://www.twitch.tv/videos/40464143","v40464143","https://twitch.tv/twitchdev","TWITCH_OFFLINE","https://clips.twitch.tv/TwitchExample","netlab.media").entrySet()){
            var page=PlayerPage.html(entry.getKey(),12);
            check(page.contains(entry.getValue())&&page.contains("let target=12.0"),"provider document matches source and channel clock: "+entry.getKey());
        }
        var mediaNet=new Network();var satellite=new Network.Wire(new Network.Endpoint(2,4),new Network.Endpoint(3,4),0,"satellite");
        mediaNet.wires.add(satellite);
        var throughSatellite=MediaInternet.class.getDeclaredMethod("usesSatellite",Network.class,Network.Result.class);throughSatellite.setAccessible(true);
        for(var path:List.of(List.of(1L,2L,3L,4L),List.of(4L,3L,2L,1L)))
            check((boolean)throughSatellite.invoke(null,mediaNet,new Network.Result(true,"",path)),"registered VSAT/HUB link recognized in both directions");
        check(!(boolean)throughSatellite.invoke(null,mediaNet,new Network.Result(true,"",List.of(1L,2L,5L,3L,4L))),"a wired detour past VSAT and HUB does not count as a satellite hop");
        mediaNet.wires.add(new Network.Wire(new Network.Endpoint(2,0),new Network.Endpoint(3,0),1,"copper"));
        check(!(boolean)throughSatellite.invoke(null,mediaNet,new Network.Result(true,"",List.of(1L,2L,3L,4L))),"an ambiguous parallel wired link is not accepted as satellite-only");
        check(PortableModules.DEFINITIONS.get("telephony").code().equals("CISCO-POCKET-SIP"),"Mobile discovers optional phone hardware through base API");
        MediaVoiceRuntimeCheck.run();
        System.out.println(checks+" Media registry/card/recipe/model/payload checks passed; no game, server, world or microphone launched.");
    }

    private static void televisionRemote(HolderLookup.Provider registries) throws Exception {
        var remote=new ItemStack(Media.TV_REMOTE.get());
        var receiver=new Device(MediaHardware.STB);
        var other=new Device(MediaHardware.STB);
        MediaHardware.initialize(receiver);
        MediaHardware.initialize(other);
        receiver.ports.getFirst().ip="192.168.50.30";
        other.ports.getFirst().ip=receiver.ports.getFirst().ip;
        var pair=TelevisionControls.class.getDeclaredMethod("pair",ItemStack.class,Device.class);
        var matches=TelevisionControls.class.getDeclaredMethod("pairedWith",ItemStack.class,Device.class);
        pair.setAccessible(true);
        matches.setAccessible(true);
        check(!(boolean)matches.invoke(null,remote,receiver),"old remotes start unpaired");
        var custom=new net.minecraft.nbt.CompoundTag();
        custom.putString("other_metadata","keep");
        remote.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(custom));
        pair.invoke(null,remote,receiver);
        check((boolean)matches.invoke(null,remote,receiver)&&!(boolean)matches.invoke(null,remote,other),"remote matches the physical receiver, even when another has the same IP");
        var saved=ItemStack.parse(registries,remote.save(registries)).orElseThrow();
        receiver.ports.getFirst().ip="192.168.51.30";
        check((boolean)matches.invoke(null,saved,receiver),"pairing survives saving and a changed receiver IP");
        check(saved.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getString("other_metadata").equals("keep"),"pairing preserves other item metadata");
        pair.invoke(null,saved,other);
        check((boolean)matches.invoke(null,saved,other)&&!(boolean)matches.invoke(null,saved,receiver),"re-pairing replaces the old receiver");

        var television=MediaState.initial(new Device(MediaHardware.TV));
        television.volume=.5;
        var power=TelevisionBlock.class.getDeclaredMethod("togglePower",MediaState.class);
        power.setAccessible(true);
        power.invoke(null,television);
        check(!television.enabled&&television.speakers&&television.volume==.5,"remote power only disables the television screen and playback");
        power.invoke(null,television);
        check(television.enabled,"the same power action can turn the television back on");
    }

    private static void televisionAudioRange() throws Exception {
        var tv=BlockPos.ZERO;
        var speaker=new MediaAudio.Output(new BlockPos(50,0,0),.75f);
        var nearSpeaker=speaker.pos().getCenter();
        var atLimit=nearSpeaker.add(32,0,0);
        var muted=new MediaAudio.Output(speaker.pos(),0);
        check(tv.getCenter().distanceToSqr(nearSpeaker)>MediaAudio.RANGE_SQUARED,"the listener is well beyond the television range");
        check(MediaAudio.audienceDistanceSquared(tv,List.of(speaker),nearSpeaker)==0,"a live remote speaker is enough to receive the TV scene");
        check(MediaAudio.audienceDistanceSquared(tv,List.of(muted),nearSpeaker)>MediaAudio.RANGE_SQUARED,"a muted remote speaker cannot subscribe to the TV scene");
        check(MediaAudio.audienceDistanceSquared(tv,List.of(speaker),atLimit)==MediaAudio.RANGE_SQUARED&&!speaker.audibleAt(atLimit),"audio fades to silence at the same boundary on server and client");
        check(speaker.equals(MediaAudio.Output.fromJson(speaker.json())),"the scene preserves the speaker position and gain");

        var state=new JsonObject();
        state.addProperty("source","test-headend");
        state.addProperty("channel",7);
        state.addProperty("url","https://example.org/check.webm");
        state.addProperty("gateway","172.30.80.2");
        state.addProperty("seconds",0);
        state.addProperty("entry",0);
        state.addProperty("cycle",0);
        state.addProperty("started",0);
        var outputs=new JsonArray();
        outputs.add(speaker.json());
        state.add("outputs",outputs);
        var sessionClass=dev.netlab.media.client.MediaVideoSession.class;
        var create=sessionClass.getDeclaredConstructor(BlockPos.class,JsonObject.class);
        var needsPlayback=sessionClass.getDeclaredMethod("needsPlayback",net.minecraft.world.phys.Vec3.class);
        create.setAccessible(true);
        needsPlayback.setAccessible(true);
        try(var session=create.newInstance(tv,state)){
            check((boolean)needsPlayback.invoke(session,nearSpeaker),"audio keeps playback active before any frame has been rendered");
            check(!(boolean)needsPlayback.invoke(session,atLimit),"playback can become idle after leaving the unrendered TV and all speakers");
        }
    }

    private static void missingCablePort() {
        var block=new DeviceBlock(BlockBehaviour.Properties.of(),"rack",true);
        var type=net.minecraft.world.level.block.entity.BlockEntityType.Builder.of((p,s)->null,block).build(null);
        var rack=new DeviceEntity(type,BlockPos.ZERO,block.defaultBlockState()){};
        var peer=new DeviceEntity(type,new BlockPos(2,0,0),block.defaultBlockState()){};
        rack.device=new Device("switch");
        rack.upperDevice=new Device("router");
        peer.device=new Device("switch");
        check(rack.device.ports.size()==5,"the switch has the five ports reported in the crash");
        rack.links.put(5,new DeviceEntity.Peer(peer.getBlockPos().asLong(),0));
        peer.links.put(0,new DeviceEntity.Peer(rack.getBlockPos().asLong(),5));
        check(dev.netlab.client.DiagnosticsOverlay.endpoint(rack,5).contains("порт недоступен"),"examining a saved cable to the removed port does not crash the HUD");
        check(rack.portLabel(4).equals("Console")&&rack.portLabel(130).equals("Console"),"live ports keep their names in both bays");
        check(!rack.hasPort(-1)&&!rack.hasPort(256)&&!rack.hasPort(131)&&!peer.hasPort(128),"invalid indices and the empty rack bay are rejected");
        for(int port:new int[]{5,24,131}){
            var route=CableGeometry.route(rack,port,peer,0,List.of());
            check(Double.isFinite(WallCablePath.length(route))&&!route.isEmpty(),"a cable to a missing port still has finite geometry and can be picked");
            var point=WallCablePath.sample(route,.5);
            var below=new WallCablePath.Point(point.x(),point.y()-1,point.z());
            var above=new WallCablePath.Point(point.x(),point.y()+1,point.z());
            check(WallCablePath.pick(route,below,above,.12)!=null,"cable with missing port can still be selected");
        }
    }

    private static void televisionMounts() throws Exception {
        var tv=(TelevisionBlock)Media.DEVICES.get(MediaHardware.TV).get();
        var type=net.minecraft.world.level.block.entity.BlockEntityType.Builder.of((p,s)->null,tv).build(null);
        var assets=Path.of("src/media/resources/assets/netlabmedia");
        var variants=JsonParser.parseString(Files.readString(assets.resolve("blockstates/tv.json"))).getAsJsonObject().getAsJsonObject("variants");
        check(variants.size()==8,"all wall and stand directions have a model variant");
        for(boolean wall:new boolean[]{false,true}){
            String modelId=wall?"tv_wall":"tv";
            var model=net.minecraft.client.renderer.block.model.BlockModel.fromString(Files.readString(assets.resolve("models/block/"+modelId+".json")));
            check(model.getElements().size()==(wall?2:4),"only the standing model contains neck and foot");
            var panel=model.getElements().getFirst();
            check(panel.to.z==(wall?16:5),"wall model sits flush against its support");
            var surface=model.getElements().get(1);
            check(surface.from.z/16.0>TelevisionSurface.FRONT+(wall?TelevisionSurface.WALL_OFFSET:0),"live video is in front of the screen texture");
        }
        var placement=TelevisionBlock.class.getDeclaredMethod("placement",Direction.class,Direction.class);
        placement.setAccessible(true);
        var supportCells=new HashSet<BlockPos>();
        var supportReader=(net.minecraft.world.level.LevelReader)java.lang.reflect.Proxy.newProxyInstance(
                MediaRuntimeCheck.class.getClassLoader(),new Class<?>[]{net.minecraft.world.level.LevelReader.class},
                (proxy,method,args)->{
                    if(method.getName().equals("getBlockState"))
                        return supportCells.contains(args[0])?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                    throw new AssertionError("Unexpected support lookup: "+method.getName());
                });
        check(!tv.defaultBlockState().getValue(TelevisionBlock.WALL_MOUNTED),"saved TVs with no new state use the standing model");
        for(var facing:Direction.Plane.HORIZONTAL)for(boolean wall:new boolean[]{false,true}){
            var state=(net.minecraft.world.level.block.state.BlockState)placement.invoke(tv,wall?facing:Direction.UP,facing.getOpposite());
            check(state.getValue(DeviceBlock.FACING)==facing&&state.getValue(TelevisionBlock.WALL_MOUNTED)==wall,"mounting follows face, standing TV faces player");
            var shape=state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
            check(wall?shape.bounds().minY>0:shape.bounds().minY==0,"only tabletop TV has a foot");
            Direction support=wall?facing.getOpposite():Direction.DOWN;
            supportCells.add(BlockPos.ZERO.relative(support));
            check(state.canSurvive(supportReader,BlockPos.ZERO),"proper support carries the television");
            supportCells.clear();
            check(!state.canSurvive(supportReader,BlockPos.ZERO),"air cannot support the television");
            int turns=DeviceBlock.turns(state);
            var near=rotated(.5,.65,-1,turns);var far=rotated(.5,.65,1.5,turns);
            var screen=shape.clip(near,far,BlockPos.ZERO);
            var back=shape.clip(far,near,BlockPos.ZERO);
            check(screen!=null&&screen.getDirection()==facing,"screen remains on the face looking out from the wall");
            check(back!=null&&Math.abs(back.getLocation().distanceTo(rotated(.5,.65,wall?1:5/16.0,turns)))<1e-5,"wall version reaches the wall, tabletop uses its own depth");
            var host=new DeviceEntity(type,BlockPos.ZERO,state){};
            MediaHardware.initialize(host.device);
            var normal=net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal());
            check(CableGeometry.facing(host)==facing.getCounterClockWise()&&PowerGeometry.facing(host)==CableGeometry.facing(host),"all plugs face the same side after rotation");
            for(int port:new int[]{0,1,PowerGeometry.BOARD_IN}){
                boolean power=port==PowerGeometry.BOARD_IN;
                var socket=power?PowerGeometry.point(host,port):CableGeometry.port(host,port);
                var eye=socket.add(normal.scale(.65)).add(0,.04,0);var target=socket.subtract(normal.scale(.07)).add(0,-.0043,0);
                var hit=shape.clip(eye,target,BlockPos.ZERO);
                check(hit!=null&&hit.getDirection()==CableGeometry.facing(host),"side jack is reachable on actual collision geometry");
                check((power?PowerGeometry.clicked(host,hit,eye):CableGeometry.clickedPort(host,hit,eye))==port,"side socket can be selected with a real angled hit");
                var path=power?PowerGeometry.fixedRoute(host,port,host,port,List.of()):CableGeometry.route(host,port,host,port,List.of());
                var exit=CableGeometry.vector(path.get(1)).subtract(socket);
                check(exit.dot(normal)>.11,"cable exits through the side instead of the panel or wall");
            }
        }
    }

    private static net.minecraft.world.phys.Vec3 rotated(double x,double y,double z,int turns){
        var p=DeviceOrientation.rotate(x,y,z,turns);return new net.minecraft.world.phys.Vec3(p.x(),p.y(),p.z());
    }
}

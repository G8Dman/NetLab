package dev.netlab.automation;

import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.api.ApplicationTransport;
import dev.netlab.automation.core.*;
import dev.netlab.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Every desktop operation is sent PC -> server, executed there, then returned over the network. */
public final class ManagementService {
    private static final Gson JSON=new Gson();
    private static final String HISTORY="netlabautomation:successfulServers";
    private static List<String> history(ServerPlayer player){var saved=player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getList(HISTORY,net.minecraft.nbt.Tag.TAG_STRING);return ServerHistory.clean(saved.stream().map(net.minecraft.nbt.Tag::getAsString).toList());}
    private static JsonArray historyJson(ServerPlayer player){var out=new JsonArray();history(player).forEach(out::add);return out;}
    private static JsonArray historyFor(ServerPlayer player,DeviceEntity host){if(host instanceof dev.netlab.api.PortableHosts.Host portable&&portable.binding()!=null){var out=new JsonArray();out.add(portable.binding().address());return out;}return historyJson(player);}
    private static void rememberServer(ServerPlayer player,String address){var saved=new net.minecraft.nbt.ListTag();ServerHistory.success(history(player),address).forEach(ip->saved.add(net.minecraft.nbt.StringTag.valueOf(ip)));var persisted=player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);persisted.put(HISTORY,saved);player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,persisted);}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> PENDING=new HashMap<>(),ATTEMPTS=new HashMap<>();
    private static long serial;
    private static final class Session {
        String token=UUID.randomUUID().toString(),passwordHash;DeviceEntity pc;Device pcDevice;AutomationNode server;long pcEpoch,serverEpoch,expires;int page,jobPage;
        JsonObject catalog=new JsonObject(),snapshot=new JsonObject();
        Session(DeviceEntity pc,AutomationNode server,long now){this.pc=pc;this.server=server;pcDevice=pc.device;pcEpoch=Hardware.ensure(pc.device).epoch;serverEpoch=Hardware.ensure(server.device).epoch;passwordHash=server.passwordHash;expires=now+6000;}
    }
    public static void clear(){SESSIONS.clear();PENDING.clear();ATTEMPTS.clear();}
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){if(event.getServer().getTickCount()%20!=0)return;for(var key:new ArrayList<>(SESSIONS.keySet())){var p=event.getServer().getPlayerList().getPlayer(key);try{if(p==null)throw new IllegalArgumentException();valid(p,SESSIONS.get(key));}catch(IllegalArgumentException e){SESSIONS.remove(key);PENDING.remove(key);}}PENDING.keySet().removeIf(key->event.getServer().getPlayerList().getPlayer(key)==null);ATTEMPTS.keySet().removeIf(key->event.getServer().getPlayerList().getPlayer(key)==null);}
    private static void pc(ServerPlayer player,DeviceEntity pc){dev.netlab.api.PortableHosts.require(player,pc,"CISCO-AUTOMATION");}
    private static boolean reachesServer(DeviceEntity pc,AutomationNode server){
        if(pc instanceof dev.netlab.api.PortableHosts.Host)return dev.netlab.api.PortableHosts.bound(pc,server.device.identity)&&pc.network().routePath(pc.slotId(0),server.ip()).success();
        return AutomationService.local(pc,0,server);
    }
    private static void valid(ServerPlayer player,Session s){pc(player,s.pc);if(s.pcDevice!=s.pc.device||s.server.isRemoved()||!Hardware.powered(s.server.device)||s.pcEpoch!=Hardware.ensure(s.pc.device).epoch||s.serverEpoch!=Hardware.ensure(s.server.device).epoch||!s.passwordHash.equals(s.server.passwordHash)||s.expires<player.serverLevel().getGameTime()&&!PENDING.containsKey(player.getUUID())||!reachesServer(s.pc,s.server))throw new IllegalArgumentException("Сеанс закрыт: устройство, пароль или локальная сеть изменились");if(PENDING.containsKey(player.getUUID()))s.expires=player.serverLevel().getGameTime()+6000;}
    private static String text(JsonObject q,String key){return q.has(key)?q.get(key).getAsString().strip():"";}
    private static int number(JsonObject q,String key,int fallback,int min,int max){int value=q.has(key)?q.get(key).getAsInt():fallback;if(value<min||value>max)throw new IllegalArgumentException(key+": "+min+".."+max);return value;}
    private static String ip(JsonObject q,String key){String value=text(q,key);Device.ip(value);return value;}
    private static String item(JsonObject q,String key){String value=text(q,key);var id=ResourceLocation.tryParse(value);if(id==null||!BuiltInRegistries.ITEM.containsKey(id)||value.equals("minecraft:air"))throw new IllegalArgumentException("Неизвестный предмет: "+value);return value;}
    private static String fluid(JsonObject q){String value=text(q,"fluid");var id=ResourceLocation.tryParse(value);if(id==null||!BuiltInRegistries.FLUID.containsKey(id)||BuiltInRegistries.FLUID.get(id)==net.minecraft.world.level.material.Fluids.EMPTY)throw new IllegalArgumentException("Неизвестная жидкость: "+value);return value;}
    private static CompletableFuture<JsonObject> wire(ServerPlayer player,Session s,boolean reply,JsonObject payload){
        valid(player,s);String json=JSON.toJson(payload);if(json.length()>150000)throw new IllegalArgumentException("Ответ слишком большой; выбери другой интерфейс");
        if(reply&&json.getBytes(StandardCharsets.UTF_8).length>48000){
            var accumulated=new StringBuilder();CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
            for(int offset=0;offset<json.length();){int end=Math.min(json.length(),offset+8000);if(end<json.length()&&Character.isHighSurrogate(json.charAt(end-1)))end--;String part=json.substring(offset,end);offset=end;var fragment=new JsonObject();fragment.addProperty("op","MANAGEMENT_REPLY_PART");fragment.addProperty("body",part);chain=chain.thenCompose(v->wire(player,s,true,fragment).thenAccept(piece->accumulated.append(piece.get("body").getAsString())));}
            return chain.thenApply(v->JsonParser.parseString(accumulated.toString()).getAsJsonObject());
        }
        var a=new ApplicationTransport.Endpoint(s.pc,0);var b=new ApplicationTransport.Endpoint(s.server.host,s.server.slot);var bytes=json.getBytes(StandardCharsets.UTF_8);
        return ApplicationTransport.sendAsync(player.serverLevel(),reply?b:a,reply?a:b,bytes,s.server.requireVpn).thenApply(r->{valid(player,s);if(!r.success())throw new IllegalArgumentException("Сеть: "+r.status());return JsonParser.parseString(new String(r.payload(),StandardCharsets.UTF_8)).getAsJsonObject();});
    }
    public static void handle(ServerPlayer player,ManagementPackets.Request request){
        var level=player.serverLevel();var host=dev.netlab.api.PortableHosts.resolve(player,request.pc());if(host==null){error(player,request.pc(),"Устройство недоступно; держи телефон в руке");return;}
        try{pc(player,host);}catch(IllegalArgumentException e){error(player,request.pc(),e.getMessage());return;}
        if(request.action().equals("history")){var out=new JsonObject();out.addProperty("historyOnly",true);out.add("history",historyFor(player,host));if(host instanceof dev.netlab.api.PortableHosts.Host portable&&portable.binding()!=null){out.addProperty("boundServer",portable.binding().address());var bound=new JsonArray();bound.add(portable.binding().address());out.add("history",bound);}PacketDistributor.sendToPlayer(player,new ManagementPackets.State(request.pc(),out.toString()));return;}
        if(request.action().equals("logout")){SESSIONS.remove(player.getUUID());PENDING.remove(player.getUUID());return;}
        if(PENDING.containsKey(player.getUUID()))return;long id=++serial;PENDING.put(player.getUUID(),id);
        CompletableFuture<JsonObject> future;
        try{
            JsonObject q=JsonParser.parseString(request.json()).getAsJsonObject();
            if(request.action().equals("login")){
                long now=level.getGameTime();if(now-ATTEMPTS.getOrDefault(player.getUUID(),now-40)<40)throw new IllegalArgumentException("Подожди две секунды перед следующим входом");ATTEMPTS.put(player.getUUID(),now);
                var server=AutomationService.find(level,ip(q,"server"));if(!server.device.kind.equals("server")||server.passwordHash.isBlank())throw new IllegalArgumentException("На сервере сначала configure terminal → management password ПАРОЛЬ");
                if(!reachesServer(host,server))throw new IllegalArgumentException(host instanceof dev.netlab.api.PortableHosts.Host?"Нужен маршрут к серверу, к которому привязан модуль телефона":"Для входа сервер должен быть в той же LAN/VLAN без маршрутизатора");
                var session=new Session(host,server,now);var login=q.deepCopy();login.addProperty("op","MANAGEMENT_LOGIN");
                future=wire(player,session,false,login).thenCompose(received->{current(player,id);if(!ServerPassword.verify(text(received,"password"),server.passwordHash))throw new IllegalArgumentException("Неверный пароль сервера");SESSIONS.put(player.getUUID(),session);return CompletableFuture.completedFuture((Void)null).thenCompose(v->AutomationService.catalog(server)).handle((catalog,error)->{session.catalog=error==null?catalog:new JsonObject();return state(session,error==null?"Вход выполнен":AutomationService.reason(error));});}).thenCompose(out->{current(player,id);return wire(player,session,true,out);});
            }else{
                var session=SESSIONS.get(player.getUUID());if(session==null||session.pc!=host||!session.token.equals(request.token()))throw new IllegalArgumentException("Сначала войди на сервер");valid(player,session);session.expires=level.getGameTime()+6000;
                var body=q.deepCopy();body.addProperty("op","MANAGEMENT_"+request.action());body.addProperty("session",session.token);
                future=wire(player,session,false,body).thenCompose(received->{current(player,id);if(SESSIONS.get(player.getUUID())!=session)throw new IllegalArgumentException("Сеанс изменился");return execute(session,request.action(),received);}).thenApply(status->state(session,status)).thenCompose(out->{current(player,id);return wire(player,session,true,out);});
            }
        }catch(RuntimeException error){future=CompletableFuture.failedFuture(error);}
        future.whenComplete((result,error)->{if(!PENDING.remove(player.getUUID(),id))return;if(error!=null)error(player,request.pc(),AutomationService.reason(error));else{if(request.action().equals("login"))rememberServer(player,result.get("server").getAsString());result.add("history",historyFor(player,host));PacketDistributor.sendToPlayer(player,new ManagementPackets.State(request.pc(),result.toString()));}});
    }
    private static void current(ServerPlayer p,long id){if(!Objects.equals(PENDING.get(p.getUUID()),id))throw new CancellationException("Запрос отменён");}
    private static CompletableFuture<String> execute(Session s,String action,JsonObject q){
        var server=s.server;var level=(ServerLevel)server.getLevel();
        switch(action){
            case "refresh":if(q.has("jobPage")){s.jobPage=number(q,"jobPage",s.jobPage,0,31);return CompletableFuture.completedFuture("Журнал");}s.page=number(q,"page",s.page,0,63);return AutomationService.catalog(server,s.page).thenApply(c->{s.catalog=c;return "Обновлено";});
            case "inspect":return AutomationService.inspect(server,AutomationService.find(level,ip(q,"source"))).thenApply(snapshot->{s.snapshot=snapshot;return "Состояние получено по сети";});
            case "transfer":return AutomationService.start(server,ip(q,"source"),ip(q,"destination"),item(q,"item"),number(q,"count",1,1,ServerHardware.itemLimit(server.device)),number(q,"sourceSlot",-1,-1,255),number(q,"destinationSlot",-1,-1,255));
            case "hardware":return CompletableFuture.completedFuture(ServerThermal.summary(server.device));
            case "transferFluid":return AutomationService.startFluid(server,ip(q,"source"),ip(q,"destination"),fluid(q),number(q,"amount",1000,1,MachineFluids.MAX_AMOUNT),q.has("fluidHash")?q.get("fluidHash").getAsInt():null);
            case "running":server.running=!server.running;break;
            case "ruleAdd":if(server.stocks.size()>=16)throw new IllegalArgumentException("Максимум 16 правил");boolean any=AutomationProgram.ANY_ITEM.equals(text(q,"item"));server.stocks.add(new AutomationProgram.Stock(ip(q,"source"),ip(q,"destination"),any?AutomationProgram.ANY_ITEM:item(q,"item"),number(q,"sourceSlot",-1,-1,255),number(q,"destinationSlot",-1,-1,255),any?1:number(q,"minimum",64,1,4096),number(q,"count",16,1,ServerHardware.itemLimit(server.device))));break;
            case "ruleDelete":server.stocks.remove(number(q,"index",0,0,server.stocks.size()-1));break;
            case "patternDelete":server.patterns.remove(number(q,"index",0,0,server.patterns.size()-1));break;
            case "patternAdd":{
                if(server.patterns.size()>=16)throw new IllegalArgumentException("Максимум 16 шаблонов");var grid=q.getAsJsonArray("grid");if(grid.size()!=9)throw new IllegalArgumentException("Нужно 9 ячеек");var cells=new ArrayList<String>();for(var cell:grid){String value=cell.getAsString().strip();if(!value.equals("-")){var tmp=new JsonObject();tmp.addProperty("cell",value);item(tmp,"cell");}cells.add(value);}
                var stacks=cells.stream().map(cell->cell.equals("-")?net.minecraft.world.item.ItemStack.EMPTY:new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(cell)))).toList();
                var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,stacks);var recipe=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level).orElseThrow(()->new IllegalArgumentException("Для такой сетки нет рецепта"));
                String output=item(q,"item");if(!recipe.value().assemble(input,level.registryAccess()).is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(output))))throw new IllegalArgumentException("Указанный результат не соответствует рецепту");
                String name=text(q,"name");var pattern=new AutomationProgram.Pattern(name,ip(q,"source"),ip(q,"crafter"),ip(q,"destination"),output,number(q,"minimum",1,1,4096),cells);server.patterns.removeIf(p->p.name().equals(name));server.patterns.add(pattern);break;
            }
            case "claim":return AutomationService.claim(server,UUID.fromString(text(q,"ticket")),ip(q,"destination"),false);
            case "return":return AutomationService.claim(server,UUID.fromString(text(q,"ticket")),"",true);
            default:throw new IllegalArgumentException("Неизвестное действие");
        }server.changed();return CompletableFuture.completedFuture("Сохранено на сервере. "+MemoryCards.status(server.device));
    }
    private static JsonObject state(Session s,String status){
        var out=s.catalog.deepCopy();out.addProperty("token",s.token);out.addProperty("server",s.server.ip());out.addProperty("status",status);out.addProperty("running",s.server.running);out.addProperty("memory",MemoryCards.status(s.server.device));out.add("snapshot",s.snapshot);out.add("rules",JSON.toJsonTree(s.server.stocks));out.add("patterns",JSON.toJsonTree(s.server.patterns));
        out.add("hardware",ServerThermal.snapshot(s.server.device));
        var ownedJobs=new ArrayList<>(TransferJournal.get((ServerLevel)s.server.getLevel()).jobs.values()).reversed().stream().filter(j->j.controllerId.equals(s.server.identity)).toList();int pages=Math.max(1,(ownedJobs.size()+15)/16);s.jobPage=Math.clamp(s.jobPage,0,pages-1);
        var jobs=new JsonArray();for(var j:ownedJobs.stream().skip(s.jobPage*16L).limit(16).toList()){var row=new JsonObject();row.addProperty("ticket",j.ticket.toString());row.addProperty("state",j.state);row.addProperty("count",j.amount());row.addProperty("unit",j.unit());row.addProperty("kind",j.liquid?"fluid":"item");row.addProperty("note",j.note);if(j.hasCargo())row.addProperty(j.liquid?"fluid":"item",j.cargoId());jobs.add(row);}out.add("jobs",jobs);out.addProperty("jobPage",s.jobPage);out.addProperty("jobPages",pages);out.addProperty("audit",TransferJournal.get((ServerLevel)s.server.getLevel()).audit.head());return out;
    }
    private static void error(ServerPlayer player,BlockPos pc,String status){var out=new JsonObject();if(status.length()>2000)status=status.substring(0,2000);out.addProperty("status",status);boolean logout=!SESSIONS.containsKey(player.getUUID());if(!logout)try{valid(player,SESSIONS.get(player.getUUID()));}catch(IllegalArgumentException e){SESSIONS.remove(player.getUUID());logout=true;}out.addProperty("logout",logout);PacketDistributor.sendToPlayer(player,new ManagementPackets.State(pc,out.toString()));}
}

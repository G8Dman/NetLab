package dev.netlab.automation;

import com.google.gson.*;
import dev.netlab.*;
import dev.netlab.api.ApplicationTransport;
import dev.netlab.automation.core.Claim;
import dev.netlab.core.Hardware;
import dev.netlab.core.TrafficMark;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class AutomationService {
    private static final Gson JSON=new Gson();
    private static final int OFFER_DELAY=400;
    private static final Set<UUID> BUSY=new HashSet<>();
    public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event){BUSY.clear();ManagementService.clear();AutomationNode.clear();}
    public static AutomationNode find(ServerLevel level,String ip){
        var found=AutomationNode.loaded(level).stream().filter(a->a.ip().equals(ip)&&!ip.isBlank()&&Hardware.powered(a.device)).toList();
        if(found.size()!=1)throw new IllegalArgumentException("IP "+ip+": найдено устройств "+found.size()+" (нужно одно, в загруженном чанке)");return found.getFirst();
    }
    private static AutomationNode at(ServerLevel l,long position,UUID identity){
        return AutomationNode.loaded(l).stream().filter(a->a.id()==position&&a.identity.equals(identity)).findFirst().orElse(null);
    }
    private static void live(AutomationNode a){if(a.isRemoved()||!a.getLevel().hasChunkAt(a.getBlockPos())||a.getLevel().getBlockEntity(a.getBlockPos())!=a.host||!Hardware.powered(a.device))throw new IllegalArgumentException("Устройство выгружено, заменено или выключено");}
    public static void managed(AutomationNode server,AutomationNode endpoint){live(server);live(endpoint);ServerThermal.require(server);if(!server.device.kind.equals("server")||!endpoint.managedBy(server))throw new IllegalArgumentException("Мультиинтерфейс должен принадлежать владельцу сервера и доверять его IP");}
    private static JsonObject message(String op){var o=new JsonObject();o.addProperty("op",op);return o;}
    private static <T> CompletableFuture<T> run(AutomationNode server,Supplier<CompletableFuture<T>> action){
        var learning=AutomationLearning.origin(server);
        if(!BUSY.add(server.identity))throw new IllegalArgumentException("Сервер ожидает завершения предыдущего сетевого запроса");
        try{
            if(!server.device.kind.equals("server"))return action.get().whenComplete((v,e)->BUSY.remove(server.identity));
            live(server);ServerThermal.work(server,.8);long epoch=Hardware.ensure(server.device).epoch;
            long due=dev.netlab.Simulation.now((ServerLevel)server.getLevel())+ServerThermal.read(server.device).processingNanos(server.device);
            return dev.netlab.Simulation.after((ServerLevel)server.getLevel(),due,()->{live(server);ServerThermal.require(server);if(Hardware.ensure(server.device).epoch!=epoch)throw new IllegalArgumentException("Сервер перезапущен во время обработки");return true;}).thenCompose(ignored->AutomationLearning.scoped(learning,action)).whenComplete((v,e)->BUSY.remove(server.identity));
        }catch(RuntimeException e){BUSY.remove(server.identity);throw e;}
    }
    public static String reason(Throwable e){while(e instanceof CompletionException&&e.getCause()!=null)e=e.getCause();return e.getMessage()==null?"Ошибка сетевой операции":e.getMessage();}
    public static boolean busy(AutomationNode server){return BUSY.contains(server.identity);}
    public static void destination(AutomationNode server,AutomationNode endpoint){live(server);live(endpoint);ServerThermal.require(server);if(!endpoint.managedBy(server)&&!(endpoint.device.kind.equals("endpoint")&&endpoint.publicReceiver&&endpoint.owner!=null&&endpoint.insert))throw new IllegalArgumentException("Получатель не доверяет серверу и не опубликован как принимающий интерфейс");}
    public static boolean local(DeviceEntity from,int bay,AutomationNode to){try{return dev.netlab.automation.core.ManagementAccess.local(from.network(),from.slotId(bay),to.ip());}catch(RuntimeException e){return false;}}
    public static CompletableFuture<JsonObject> catalog(AutomationNode server){return catalog(server,0);}
    public static CompletableFuture<JsonObject> catalog(AutomationNode server,int page){return run(server,()->{
        var result=new JsonObject();var endpoints=new JsonArray();var receivers=new JsonArray();CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
        var localNodes=AutomationNode.loaded(server.getLevel()).stream().filter(e->e.managedBy(server)&&local(server.host,server.slot,e)).sorted(Comparator.comparing(AutomationNode::ip)).toList();result.addProperty("page",page);result.addProperty("pages",Math.max(1,(localNodes.size()+15)/16));
        for(var endpoint:localNodes.stream().skip(page*16L).limit(16).toList()){
            chain=chain.thenCompose(v->inspectRaw(server,endpoint).handle((snapshot,error)->{if(error==null){var row=snapshot.deepCopy();row.remove("inventory");row.remove("tanks");endpoints.add(row);}return null;}));
        }
        var publicNodes=new LinkedHashMap<UUID,AutomationNode>();for(var endpoint:AutomationNode.loaded(server.getLevel()))if(endpoint.publicReceiver&&endpoint.owner!=null&&endpoint.insert){var previous=publicNodes.get(endpoint.owner);if(previous==null||previous.receiverVersion<endpoint.receiverVersion)publicNodes.put(endpoint.owner,endpoint);}
        for(var endpoint:publicNodes.values().stream().sorted(Comparator.comparing(AutomationNode::ip)).skip(page*16L).limit(16).toList())chain=chain.thenCompose(v->{var query=message("DIRECTORY_QUERY");return send(server,endpoint,query,server.requireVpn).thenCompose(q->{var row=new JsonObject();row.addProperty("op","DIRECTORY_REPLY");row.addProperty("ip",endpoint.ip());row.addProperty("owner",endpoint.owner.toString());var profile=((ServerLevel)server.getLevel()).getServer().getProfileCache().get(endpoint.owner);row.addProperty("name",profile.map(com.mojang.authlib.GameProfile::getName).orElse(endpoint.owner.toString().substring(0,8)));row.addProperty("label",endpoint.device.hostname);return send(endpoint,server,row,server.requireVpn);}).handle((reply,error)->{if(error==null){receivers.add(reply);AutomationLearning.emit(endpoint,"automation.directory");}return null;});});
        return chain.thenApply(v->{result.add("endpoints",endpoints);result.add("receivers",receivers);return result;});
    });}
    public static CompletableFuture<String> craft(AutomationNode server,AutomationNode endpoint,String expected){return run(server,()->{managed(server,endpoint);return send(server,endpoint,message("CRAFT"),server.requireVpn).thenCompose(q->{managed(server,endpoint);String result=CraftingGrids.get((ServerLevel)server.getLevel()).craft(endpoint,expected);var reply=message("CRAFT_RESULT");reply.addProperty("result",result);return send(endpoint,server,reply,server.requireVpn).thenApply(r->{String text=r.get("result").getAsString();if(!text.startsWith("%"))AutomationLearning.emit(server,"automation.craft");return text;});});});}
    public static CompletableFuture<JsonObject> send(AutomationNode from,AutomationNode to,JsonObject body,boolean required){
        String op=body.has("op")?body.get("op").getAsString():"";boolean data=op.equals("ITEM_DATA"),liquid=op.equals("FLUID_DATA");
        var mark=Set.of("ITEM_DATA","FLUID_DATA","OFFER","RESERVE","RESERVE_READY","DELIVERY_CHECK","DELIVERY_READY","REDEEM","RETURN","ACCEPT").contains(op)?new TrafficMark(op,data?body.get("item").getAsString():liquid?body.get("fluid").getAsString():"",data?body.get("count").getAsInt():liquid?body.get("amount").getAsInt():0):TrafficMark.NONE;
        live(from);live(to);return ApplicationTransport.sendAsync((ServerLevel)from.getLevel(),new ApplicationTransport.Endpoint(from.host,from.slot),new ApplicationTransport.Endpoint(to.host,to.slot),JSON.toJson(body).getBytes(StandardCharsets.UTF_8),required,mark).thenApply(receipt->{
            if(!receipt.success())throw new IllegalArgumentException("Сеть: "+receipt.status());
            if((data||liquid)&&body.has("ticket")){var job=TransferJournal.get((ServerLevel)from.getLevel()).jobs.get(UUID.fromString(body.get("ticket").getAsString()));if(job!=null){job.learningEncrypted=receipt.encrypted();job.learningSatellite=receipt.path().stream().map(from.host.network().devices::get).filter(Objects::nonNull).anyMatch(d->d.kind.startsWith("sputnik_"));}}
            return JsonParser.parseString(new String(receipt.payload(),StandardCharsets.UTF_8)).getAsJsonObject();
        });
    }
    private static CompletableFuture<JsonObject> inspectRaw(AutomationNode server,AutomationNode endpoint){
        managed(server,endpoint);return send(server,endpoint,message("SNAPSHOT"),server.requireVpn).thenCompose(request->{
            managed(server,endpoint);var response=MachineAccess.snapshot(endpoint);response.addProperty("op","SNAPSHOT_RESULT");return send(endpoint,server,response,server.requireVpn);
        }).thenApply(result->{server.remoteSnapshot=JSON.toJson(result);AutomationLearning.emit(server,"automation.inspect");return result;});
    }
    public static CompletableFuture<JsonObject> inspect(AutomationNode server,AutomationNode endpoint){return run(server,()->inspectRaw(server,endpoint));}
    public static CompletableFuture<String> action(AutomationNode server,String ip,String action){return run(server,()->{
        var endpoint=find((ServerLevel)server.getLevel(),ip);managed(server,endpoint);var req=message("CONTROL");req.addProperty("action",action);
        return send(server,endpoint,req,server.requireVpn).thenCompose(delivered->{
            managed(server,endpoint);String result=MachineAccess.act(endpoint,delivered.get("action").getAsString());var reply=message("CONTROL_RESULT");reply.addProperty("result",result);
            return send(endpoint,server,reply,server.requireVpn).thenApply(response->response.get("result").getAsString());
        });
    });}
    public static CompletableFuture<String> start(AutomationNode server,String source,String destination,String item,int amount){return start(server,source,destination,item,amount,-1,-1);}
    public static CompletableFuture<String> start(AutomationNode server,String source,String destination,String item,int amount,int sourceSlot,int destinationSlot){return run(server,()->startBatchRaw(server,source,destination,item,amount,sourceSlot,destinationSlot));}
    public static CompletableFuture<String> startFluid(AutomationNode server,String source,String destination,String fluid,int amount,Integer componentsHash){return run(server,()->startCargoRaw(server,source,destination,fluid,amount,-1,-1,true,componentsHash));}
    private static CompletableFuture<String> startRaw(AutomationNode server,String source,String destination,String item,int amount){return startBatchRaw(server,source,destination,item,amount,-1,-1);}
    private static CompletableFuture<String> startBatchRaw(AutomationNode server,String source,String destination,String item,int amount,int sourceSlot,int destinationSlot){
        if(amount<1||amount>dev.netlab.automation.core.ServerHardware.itemLimit(server.device))throw new IllegalArgumentException("Партия до "+dev.netlab.automation.core.ServerHardware.itemLimit(server.device)+" предметов; установи плату объёма в buffer0");
        var learning=AutomationLearning.origin(server);var notes=new ArrayList<String>();int[] reserved={0},tickets={0},left={amount};long epoch=Hardware.ensure(server.device).epoch;
        CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
        // At most one request per item also handles native stack limits 1 and 16 without oversized stacks.
        for(int part=0;part<amount;part++)chain=chain.thenCompose(ignored->{
            if(left[0]<=0)return CompletableFuture.completedFuture(null);ServerThermal.require(server);if(Hardware.ensure(server.device).epoch!=epoch)throw new IllegalArgumentException("Сервер перезапущен; уже созданные билеты сохранены");
            int[] created={0};
            return AutomationLearning.scoped(learning,()->startCargoRaw(server,source,destination,item,dev.netlab.automation.core.ServerHardware.part(left[0]),sourceSlot,destinationSlot,false,null,job->created[0]=job.amount())).handle((note,error)->{
                reserved[0]+=created[0];left[0]-=created[0];if(created[0]>0)tickets[0]++;
                if(error!=null){if(reserved[0]==0)throw new CompletionException(error);notes.add(reason(error));left[0]=0;return null;}
                notes.add(note);if(created[0]==0)left[0]=0;return null;
            });
        });
        return chain.handle((ignored,error)->{if(error!=null){if(reserved[0]==0)throw new CompletionException(error);notes.add(reason(error));}return "Партия: "+reserved[0]+" / "+amount+" предметов; билетов: "+tickets[0]+"\n"+String.join("\n",notes);});
    }
    private static CompletableFuture<String> startRaw(AutomationNode server,String source,String destination,String item,int amount,int sourceSlot,int destinationSlot){
        return startCargoRaw(server,source,destination,item,amount,sourceSlot,destinationSlot,false,null);
    }
    private static CompletableFuture<String> startCargoRaw(AutomationNode server,String source,String destination,String item,int amount,int sourceSlot,int destinationSlot,boolean liquid,Integer componentsHash){return startCargoRaw(server,source,destination,item,amount,sourceSlot,destinationSlot,liquid,componentsHash,null);}
    private static CompletableFuture<String> startCargoRaw(AutomationNode server,String source,String destination,String item,int amount,int sourceSlot,int destinationSlot,boolean liquid,Integer componentsHash,java.util.function.Consumer<TransferJournal.Job> reserved){
        if(sourceSlot< -1||sourceSlot>255||destinationSlot< -1||destinationSlot>255||amount<1||amount>(liquid?MachineFluids.MAX_AMOUNT:64))throw new IllegalArgumentException("Недопустимый слот или количество");
        var learning=AutomationLearning.origin(server);var l=(ServerLevel)server.getLevel();var src=find(l,source);var dst=find(l,destination);managed(server,src);destination(server,dst);
        if(src==dst||MachineAccess.target(src).equals(MachineAccess.target(dst)))throw new IllegalArgumentException("Источник и получатель должны быть разными блоками");
        if(!src.extract||!dst.insert)throw new IllegalArgumentException("Извлечение/вставка запрещены мультиинтерфейсом");
        var journal=TransferJournal.get(l);if(!journal.audit.valid())throw new IllegalArgumentException("Хеш-цепочка журнала повреждена; выдача остановлена");if(journal.full())throw new IllegalArgumentException("Журнал передач заполнен");
        var reserve=message("RESERVE");reserve.addProperty(liquid?"fluid":"item",item);reserve.addProperty(liquid?"amount":"count",amount);
        return send(server,src,reserve,server.requireVpn)
            .thenCompose(request->send(src,server,message("RESERVE_READY"),server.requireVpn))
            .thenCompose(reply->send(server,dst,message("DELIVERY_CHECK"),server.requireVpn))
            .thenCompose(reply->send(dst,server,message("DELIVERY_READY"),server.requireVpn))
            .thenCompose(reply->{
                managed(server,src);destination(server,dst);if(journal.full()||!src.extract||!dst.insert)throw new IllegalArgumentException("Разрешения или состояние журнала изменились");
                var job=new TransferJournal.Job();job.learning=learning;job.destinationSlot=destinationSlot;job.source=src.id();job.destination=dst.id();job.sourceId=src.identity;job.destinationId=dst.identity;job.owner=server.owner;job.controllerId=server.identity;job.ready=l.getGameTime()+OFFER_DELAY;job.liquid=liquid;
                if(liquid){
                    var handler=MachineFluids.handler(src);if(MachineFluids.handler(dst)==null)throw new IllegalArgumentException("Получатель не предоставляет FluidHandler на выбранной стороне");
                    var preview=MachineFluids.preview(handler,item,amount,componentsHash);if(preview.isEmpty())throw new IllegalArgumentException("Жидкость недоступна для слива на выбранной стороне источника");
                    // A throwing adapter may already have modified its tank. Hold an uncertain reservation for manual reconciliation, never pay it out automatically.
                    job.fluid=preview.copy();job.claim=new Claim(preview.getAmount());job.state="quarantined";job.note="Слив не подтверждён";journal.add(job);
                    try{job.fluid=MachineFluids.drain(handler,preview);job.claim=new Claim(job.amount());job.state=job.hasCargo()?"pending":"cancelled";}catch(RuntimeException error){job.note="Ошибка FluidHandler при сливе; автоматическая выдача остановлена, нужна сверка источника";journal.setDirty();throw new IllegalArgumentException(job.note,error);}
                    if(!job.hasCargo()){job.note="Источник отказал в сливе";journal.setDirty();throw new IllegalArgumentException(job.note);}
                }else{job.stack=MachineAccess.extract(src,item,Math.clamp(amount,1,64),sourceSlot);if(job.stack.isEmpty())throw new IllegalArgumentException("Нет доступных предметов на выбранной стороне источника");job.claim=new Claim(job.amount());journal.add(job);}
                if(reserved!=null)reserved.accept(job);job.note="OFFER в пути";journal.setDirty();ServerThermal.work(server,.25+(liquid?.25:job.amount()*.012));
                return offer(src,dst,job,server.requireVpn).handle((response,error)->{
                    if(job.hasCargo()){int delay=ServerThermal.delay(server);job.note=error==null?"Предложение доставлено. Выдача через "+delay/20.0+" секунд":reason(error);job.ready=l.getGameTime()+(error==null?delay:40);}
                    journal.setDirty();return "Передача: "+job.ticket+"; "+job.note;
                });
            });
    }
    private static CompletableFuture<JsonObject> offer(AutomationNode src,AutomationNode dst,TransferJournal.Job j,boolean requireVpn){
        var offer=message("OFFER");offer.addProperty("ticket",j.ticket.toString());offer.addProperty(j.liquid?"fluid":"item",j.cargoId());offer.addProperty(j.liquid?"amount":"count",j.amount());offer.addProperty("from",src.ip());offer.addProperty("to",dst.ip());return send(src,dst,offer,requireVpn);
    }
    public static CompletableFuture<String> claim(AutomationNode server,UUID ticket,String destination,boolean returning){return run(server,()->{
        var l=(ServerLevel)server.getLevel();var j=TransferJournal.get(l).jobs.get(ticket);
        if(j==null||!j.hasCargo()){if(j!=null&&Objects.equals(j.owner,server.owner))AutomationLearning.emit(server,"automation.replay.denied");throw new IllegalArgumentException("Билет неизвестен или уже погашен");}
        if(j.state.equals("quarantined"))throw new IllegalArgumentException("Нужна ручная сверка журнала после ошибки адаптера");
        var src=at(l,j.source,j.sourceId);if(src==null)throw new IllegalArgumentException("Исходный мультиинтерфейс не загружен или заменён. Груз сохранён");
        var dst=returning?src:find(l,destination);destination(server,dst);if(returning&&!Objects.equals(j.owner,server.owner))throw new IllegalArgumentException("Вернуть может владелец передачи");return deliver(server,j,src,dst,returning);
    });}
    private static CompletableFuture<String> deliver(AutomationNode server,TransferJournal.Job j,AutomationNode src,AutomationNode dst,boolean returning){
        destination(server,dst);if(!dst.insert)throw new IllegalArgumentException("Вставка запрещена получателем");
        if(!TransferJournal.get((ServerLevel)server.getLevel()).audit.valid())throw new IllegalArgumentException("Хеш-цепочка журнала повреждена");var redeem=message(returning?"RETURN":"REDEEM");redeem.addProperty("ticket",j.ticket.toString());redeem.addProperty("destination",dst.ip());boolean[] locked={false};
        return send(server,src,redeem,server.requireVpn).thenCompose(received->{
            if(!j.ticket.toString().equals(received.get("ticket").getAsString())||!j.claim.begin())throw new IllegalArgumentException("Билет уже обрабатывается или погашен");
            locked[0]=true;return src==dst?CompletableFuture.completedFuture(received):offer(src,dst,j,server.requireVpn).thenCompose(offer->send(dst,src,message("ACCEPT"),server.requireVpn));
        }).thenCompose(acceptedMessage->{
            // The journal retains the real stack until this source -> receiver packet arrives.
            // Acknowledgement travels in the opposite direction and cannot stand in for delivery.
            destination(server,dst);live(src);
            if(src==dst)return CompletableFuture.completedFuture(acceptedMessage);
            var data=message(j.liquid?"FLUID_DATA":"ITEM_DATA");data.addProperty("ticket",j.ticket.toString());data.addProperty(j.liquid?"fluid":"item",j.cargoId());data.addProperty(j.liquid?"amount":"count",j.amount());
            j.note=(j.liquid?"Пакет жидкости в пути: ":"Пакет предметов в пути: ")+j.amount()+j.unit();TransferJournal.get((ServerLevel)server.getLevel()).setDirty();
            return send(src,dst,data,server.requireVpn).thenApply(received->{if(!j.ticket.toString().equals(received.get("ticket").getAsString())||received.get(j.liquid?"amount":"count").getAsInt()!=j.amount()||!received.get(j.liquid?"fluid":"item").getAsString().equals(j.cargoId()))throw new IllegalArgumentException("Пакет груза не соответствует билету");return received;});
        }).thenApply(acceptedMessage->{
            destination(server,dst);live(src);if(!dst.insert)throw new IllegalArgumentException("Право вставки отозвано во время передачи");int before=j.amount();
            try{
                if(j.liquid)j.fluid=MachineFluids.fill(MachineFluids.handler(dst),j.fluid);else {var remaining=MachineAccess.insert(dst,j.stack,returning||!dst.identity.equals(j.destinationId)?-1:j.destinationSlot);if(remaining.getCount()>before)throw new IllegalStateException("Item handler violated insertion contract");j.stack=remaining;}
                int accepted=before-j.amount();if(accepted<0||accepted>before)throw new IllegalStateException("Handler violated insertion contract");
                j.claim.finish(accepted);locked[0]=false;boolean stolen=!dst.identity.equals(j.destinationId)&&!returning;
                j.note=(returning?"Возвращено":stolen?"Перехвачено":"Доставлено")+": "+accepted+j.unit()+"; остаток "+j.amount()+j.unit();
                if(!returning)AutomationLearning.delivered(server,j,src,dst,accepted,stolen);
                if(!j.hasCargo())j.state=returning?"returned":stolen?"stolen":"delivered";TransferJournal.get((ServerLevel)server.getLevel()).setDirty();return j.note;
            }catch(RuntimeException e){j.state="quarantined";j.note="Адаптер прервал вставку. Автоповтор остановлен; проверь бак/инвентарь и журнал";TransferJournal.get((ServerLevel)server.getLevel()).setDirty();throw new IllegalArgumentException(j.note,e);}
        }).whenComplete((v,error)->{if(locked[0])j.claim.abort();});
    }
    public static void capture(ApplicationTransport.Capture packet){
        var analyzer=AutomationNode.loaded(packet.level()).stream().filter(a->a.id()==packet.to()&&a.device.kind.equals("sniffer")).findFirst().orElse(null);if(analyzer==null)return;
        if(packet.encrypted()&&analyzer.capturedTicket.isBlank())AutomationLearning.emit(analyzer,"automation.encrypted.capture.denied");
        String body=packet.encrypted()?"[AES-256-GCM] "+Base64.getEncoder().encodeToString(Arrays.copyOf(packet.bytes(),Math.min(96,packet.bytes().length))):new String(packet.bytes(),StandardCharsets.UTF_8);
        if(!packet.encrypted())try{var json=JsonParser.parseString(body).getAsJsonObject();if(json.has("op")&&json.get("op").getAsString().equals("OFFER"))analyzer.capturedTicket=UUID.fromString(json.get("ticket").getAsString()).toString();}catch(RuntimeException ignored){}
        String line=packet.level().getGameTime()+" | "+BlockPos.of(packet.from()).toShortString()+" -> analyzer/"+packet.toPort()+" | "+body;
        if(line.length()>2400)line=line.substring(0,2400)+"…";analyzer.captures.addFirst(line);while(analyzer.captures.size()>24)analyzer.captures.removeLast();
    }

    public static void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0)return;
        for(var l:event.getServer().getAllLevels()){
            if(event.getServer().getTickCount()%100==0)for(var endpoint:AutomationNode.loaded(l))if(endpoint.device.kind.equals("endpoint")&&!endpoint.manager.isBlank()&&endpoint.owner!=null&&!BUSY.contains(endpoint.identity)){
                try{var server=find(l,endpoint.manager);managed(server,endpoint);run(endpoint,()->{
                    var status=MachineAccess.snapshot(endpoint);status.addProperty("op","REGISTER_STATUS");status.addProperty("address",endpoint.ip());
                    return send(endpoint,server,status,server.requireVpn).thenCompose(report->{managed(server,endpoint);if(server.reports.size()>=256)server.reports.clear();server.reports.put(endpoint.ip(),"tick "+l.getGameTime()+" "+report);var ack=message("REGISTER_ACK");return send(server,endpoint,ack,server.requireVpn);});
                }).whenComplete((reply,error)->{if(error==null)AutomationLearning.emit(endpoint,"automation.report","endpoint",endpoint.identity,"insert",endpoint.insert);endpoint.last=error==null?"Сервер принял отчёт: "+server.ip():reason(error);});}catch(IllegalArgumentException error){endpoint.last=error.getMessage();}
            }
            var journal=TransferJournal.get(l);int work=0;
            for(var j:journal.jobs.values().stream().sorted(Comparator.comparingLong(job->job.ready)).toList()){
                if(!j.hasCargo()||j.state.equals("quarantined")||j.ready>l.getGameTime())continue;
                var src=at(l,j.source,j.sourceId);var dst=at(l,j.destination,j.destinationId);
                var server=AutomationNode.loaded(l).stream().filter(a->a.identity.equals(j.controllerId)&&Objects.equals(a.owner,j.owner)).findFirst().orElse(null);
                if(src==null||dst==null||server==null){j.note="Устройство не загружено. Груз в журнале";continue;}
                if(BUSY.contains(server.identity)||!ServerThermal.ready(server.device))continue;if(work++>=4)break;j.ready=l.getGameTime()+40;journal.setDirty();
                try{managed(server,src);destination(server,dst);run(server,()->deliver(server,j,src,dst,false)).whenComplete((v,e)->{if(e!=null&&j.hasCargo())j.note=reason(e);journal.setDirty();});}catch(IllegalArgumentException e){j.note=e.getMessage();}
            }
            for(var server:AutomationNode.loaded(l))if(server.device.kind.equals("server")&&server.running&&ServerThermal.ready(server.device)&&server.stocks.isEmpty()&&server.patterns.isEmpty()&&!server.rules.isEmpty()&&server.owner!=null&&!BUSY.contains(server.identity)){
                var r=server.rules.get((int)((l.getGameTime()/20)%server.rules.size()));
                try{
                    var dst=find(l,r.destination());run(server,()->inspectRaw(server,dst).thenCompose(snapshot->{
                        if(!server.running||!server.rules.contains(r))return CompletableFuture.completedFuture("Правило остановлено");
                        int count=0;if(snapshot.has("inventory"))for(var entry:snapshot.getAsJsonArray("inventory")){var s=entry.getAsJsonObject();if(s.get("item").getAsString().equals(r.item()))count+=s.get("count").getAsInt();}
                        boolean pending=journal.jobs.values().stream().anyMatch(j->j.hasCargo()&&j.controllerId.equals(server.identity)&&j.destinationId.equals(dst.identity));
                        if(count<r.minimum()&&!pending){int requested=Math.min(dev.netlab.automation.core.ServerHardware.itemLimit(server.device),Math.min(r.batch(),r.minimum()-count));return AutomationLearning.rule(server,false,()->startRaw(server,r.source(),r.destination(),r.item(),requested));}
                        return CompletableFuture.completedFuture("Правило: запас "+count+" / "+r.minimum()+(pending?"; доставка в пути":""));
                    })).whenComplete((v,e)->server.last=e==null?v:reason(e));
                }catch(IllegalArgumentException e){server.last=e.getMessage();}
            }
        }
    }
}

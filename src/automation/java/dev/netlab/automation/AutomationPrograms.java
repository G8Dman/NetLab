package dev.netlab.automation;

import com.google.gson.*;
import dev.netlab.automation.core.AutomationProgram;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Stock rules and pattern phases issue the same inspect/transfer RPCs as manual operations. */
public final class AutomationPrograms {
    public static int count(JsonObject snapshot,String item,int slot){int count=0;if(snapshot.has("inventory"))for(var row:snapshot.getAsJsonArray("inventory")){var entry=row.getAsJsonObject();if((slot<0||entry.get("slot").getAsInt()==slot)&&entry.get("item").getAsString().equals(item))count+=entry.get("count").getAsInt();}return count;}
    public static Optional<AutomationProgram.Dispatch> drain(AutomationProgram.Stock rule,JsonObject snapshot){
        var cells=new ArrayList<AutomationProgram.Available>();
        if(snapshot.has("inventory"))for(var row:snapshot.getAsJsonArray("inventory")){var cell=row.getAsJsonObject();cells.add(new AutomationProgram.Available(cell.get("slot").getAsInt(),cell.get("item").getAsString(),Math.min(cell.get("count").getAsInt(),cell.has("extractable")?cell.get("extractable").getAsInt():0)));}
        return AutomationProgram.drain(rule,cells);
    }
    private static boolean pending(AutomationNode server){return TransferJournal.get((ServerLevel)server.getLevel()).jobs.values().stream().anyMatch(j->j.controllerId.equals(server.identity)&&j.hasCargo());}
    public static void tick(ServerTickEvent.Post event){if(event.getServer().getTickCount()%20!=0)return;for(var level:event.getServer().getAllLevels())for(var server:AutomationNode.loaded(level)){
        if(!server.device.kind.equals("server")||!server.running||!ServerThermal.ready(server.device)||server.owner==null||AutomationService.busy(server)||server.stocks.isEmpty()&&server.patterns.isEmpty()||pending(server))continue;
        int total=server.rules.size()+server.stocks.size()+server.patterns.size();int index=(int)((level.getGameTime()/20)%total);
        try{CompletableFuture<String> future;
            if(index<server.rules.size()){var old=server.rules.get(index);future=stock(server,new AutomationProgram.Stock(old.source(),old.destination(),old.item(),-1,-1,old.minimum(),old.batch()));}
            else if(index<server.rules.size()+server.stocks.size())future=stock(server,server.stocks.get(index-server.rules.size()));
            else future=pattern(server,server.patterns.get(index-server.rules.size()-server.stocks.size()));
            future.whenComplete((result,error)->{server.last=error==null?result:AutomationService.reason(error);server.changed();});
        }catch(IllegalArgumentException e){server.last=e.getMessage();}
    }}
    private static CompletableFuture<String> stock(AutomationNode server,AutomationProgram.Stock r){
        if(r.anyItem()){
            var source=AutomationService.find((ServerLevel)server.getLevel(),r.source());
            return AutomationService.inspect(server,source).thenCompose(snapshot->{
                if(!server.running||!server.stocks.contains(r))return CompletableFuture.completedFuture("Правило остановлено");
                var selected=drain(r,snapshot);if(selected.isEmpty())return CompletableFuture.completedFuture("Любой предмет: источник пуст или извлечение недоступно");
                var dispatch=selected.get();return AutomationService.start(server,r.source(),r.destination(),dispatch.item(),Math.min(dev.netlab.automation.core.ServerHardware.itemLimit(server.device),dispatch.amount()),dispatch.sourceSlot(),dispatch.destinationSlot());
            });
        }
        var dst=AutomationService.find((ServerLevel)server.getLevel(),r.destination());return AutomationService.inspect(server,dst).thenCompose(snapshot->{if(!server.running)return CompletableFuture.completedFuture("Остановлено");int amount=count(snapshot,r.item(),r.destinationSlot());if(amount>=r.minimum())return CompletableFuture.completedFuture("Запас достаточен");return AutomationService.start(server,r.source(),r.destination(),r.item(),Math.min(dev.netlab.automation.core.ServerHardware.itemLimit(server.device),Math.min(r.batch(),r.minimum()-amount)),r.sourceSlot(),r.destinationSlot());});
    }
    private static CompletableFuture<String> pattern(AutomationNode server,AutomationProgram.Pattern p){
        var level=(ServerLevel)server.getLevel();var destination=AutomationService.find(level,p.destination());
        return AutomationService.inspect(server,destination).thenCompose(snapshot->{
            if(!server.running||!server.patterns.contains(p))return CompletableFuture.completedFuture("Шаблон остановлен");
            var crafter=AutomationService.find(level,p.crafter());AutomationService.managed(server,crafter);
            
            return AutomationService.inspect(server,crafter).thenCompose(grid->{if(!server.running||!server.patterns.contains(p))return CompletableFuture.completedFuture("Шаблон остановлен");
                boolean hasInputs=false;int outputCount=0;if(grid.has("inventory"))for(var row:grid.getAsJsonArray("inventory")){var cell=row.getAsJsonObject();if(cell.get("slot").getAsInt()<9)hasInputs|=cell.get("count").getAsInt()>0;else outputCount=cell.get("count").getAsInt();}
                if(!hasInputs&&outputCount==0&&count(snapshot,p.output(),-1)>=p.minimum())return CompletableFuture.completedFuture("Шаблон: запас достаточен");
                if(!CraftingGrids.get(level).lock(crafter,server.identity+":"+p.name()))return CompletableFuture.completedFuture("Верстак занят другим шаблоном");
                if(outputCount>0){if(count(grid,p.output(),9)==0)throw new IllegalArgumentException("Освободи выходной слот 9 верстака");return AutomationService.start(server,p.crafter(),p.destination(),p.output(),Math.min(64,outputCount),9,-1);}
                for(int slot=0;slot<9;slot++){
                    String expected=p.grid().get(slot);int present=0;String actual="minecraft:air";
                    if(grid.has("inventory"))for(var row:grid.getAsJsonArray("inventory")){var cell=row.getAsJsonObject();if(cell.get("slot").getAsInt()==slot){present=cell.get("count").getAsInt();actual=cell.get("item").getAsString();}}
                    if(present>0&&(expected.equals("-")||!actual.equals(expected)))throw new IllegalArgumentException("Очисти слот "+slot+" верстака: он не соответствует шаблону "+p.name());
                    if(!expected.equals("-")&&present==0)return AutomationService.start(server,p.source(),p.crafter(),expected,1,-1,slot);
                }
                return AutomationService.craft(server,crafter,p.output());
            });
        });
    }
}

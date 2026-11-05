package dev.netlab.automation;

import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.core.*;
import dev.netlab.automation.core.AutomationCli;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** All mutations require the physical console session and the owning player's authorization. */
public final class AutomationConsole implements ConsoleExtensions.Handler {
    @Override public void flush(ConsoleAccess.Target target){var node=AutomationNode.of(target.entity(),target.id()==target.entity().slotId(1)?1:0);if(node!=null)node.persist();}
    private AutomationNode node(ConsoleExtensions.Context c){var t=c.target();var a=AutomationNode.of(t.entity(),t.id()==t.entity().slotId(1)?1:0);if(a==null||!a.owns(c.player().getUUID()))throw new IllegalArgumentException("Устройство принадлежит другому игроку");if(a.owner==null){a.owner=c.player().getUUID();a.changed();}return a;}
    @Override public String open(ConsoleExtensions.Context c){try{var a=node(c);return "NetLab Automation / Console 9600 8N1\nНастройка: enable → configure terminal. help — команды приложения.\n"+(a.device.kind.equals("server")&&a.passwordHash.isBlank()?"Первичная настройка: management password ПАРОЛЬ (8..64 символа).\n":"")+MemoryCards.status(a.device)+"\n"+c.target().cli().prompt(a.device);}catch(IllegalArgumentException e){return "% "+e.getMessage();}}
    @Override public CompletableFuture<String> execute(ConsoleExtensions.Context c,String line){
        var a=node(c);var cli=c.target().cli();var n=c.network();var l=(ServerLevel)a.getLevel();
        try{
            if(line.strip().equals("help")||line.strip().equals("?"))return done(AutomationCli.help()+"\n"+CommandGrammar.help(cli,a.device));
            var command=AutomationCli.parse(line,cli.mode,a.device.kind);if(command==null){
                var resolved=CommandGrammar.resolve(cli,a.device,line);String canonical=resolved.value();
                if(canonical.startsWith("ip address ")){
                    String[] parts=canonical.split("\\s+");long inverse=(~Device.mask(parts[3]))&0xffffffffL;if((inverse&(inverse+1))!=0)throw new IllegalArgumentException("Маска должна быть непрерывной");
                    for(var device:n.devices.entrySet())for(int port=0;port<device.getValue().ports.size();port++)if(!(device.getKey()==a.id()&&port==cli.selected)&&device.getValue().ports.get(port).ip.equals(parts[2]))throw new IllegalArgumentException("IP уже занят");
                }
                String result=cli.execute(a.device,n,a.id(),line);a.changed();return done(result);
            }
            var v=command.args();String result="OK";CompletableFuture<?> operation=null;
            switch(command.name()){
                case "management password"->{a.passwordHash=dev.netlab.automation.core.ServerPassword.create(v.getFirst());result="Пароль приложения установлен. "+MemoryCards.status(a.device);}
                case "receiver"->{a.publicReceiver=CommandGrammar.keyword(v.getFirst(),List.of("public","private")).equals("public");a.receiverVersion=l.getGameTime();if(a.publicReceiver)for(var other:AutomationNode.loaded(l))if(other!=a&&Objects.equals(other.owner,a.owner)&&other.publicReceiver){other.publicReceiver=false;other.changed();}result=a.publicReceiver?"Опубликован основной приёмный интерфейс игрока":"Публичный приём отключён";}
                case "server-address"->{Device.ip(v.getFirst());a.manager=v.getFirst();}
                case "target"->{var side=net.minecraft.core.Direction.byName(v.getFirst());if(side==null)throw new IllegalArgumentException("down|up|north|south|west|east");a.target=side;}
                case "permission"->{boolean value=CommandGrammar.keyword(v.get(1),List.of("on","off")).equals("on");switch(v.getFirst()){case "extract"->a.extract=value;case "insert"->a.insert=value;case "control"->{a.control=value;if(!value){a.signal=0;l.updateNeighborsAt(a.getBlockPos(),a.getBlockState().getBlock());}}default->throw new IllegalArgumentException("extract|insert|control");}}
                case "vpn"->a.requireVpn=CommandGrammar.keyword(v.getFirst(),List.of("required","optional")).equals("required");
                case "automation rule"->{if(a.rules.size()>=16)throw new IllegalArgumentException("Максимум 16 правил");Device.ip(v.get(0));Device.ip(v.get(1));if(!v.get(2).matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("namespace:item");a.rules.add(new AutomationEntity.Rule(v.get(0),v.get(1),v.get(2),Addressing.number(v.get(3),1,4096),Addressing.number(v.get(4),1,dev.netlab.automation.core.ServerHardware.itemLimit(a.device))));}
                case "automation clear"->{a.rules.clear();a.stocks.clear();a.patterns.clear();a.running=false;}
                case "automation run"->a.running=true;
                case "automation stop"->a.running=false;
                case "inspect"->operation=AutomationService.inspect(a,AutomationService.find(l,v.getFirst()));
                case "transfer"->operation=AutomationService.start(a,v.get(0),v.get(1),v.get(2),Addressing.number(v.get(3),1,dev.netlab.automation.core.ServerHardware.itemLimit(a.device)));
                case "show server"->result=ServerThermal.summary(a.device)+"\nПартия: "+dev.netlab.automation.core.ServerHardware.itemLimit(a.device)+" предметов\nОжидание OFFER: "+ServerThermal.delay(a)/20.0+" с\nМодули: "+Hardware.ensure(a.device).modules+"\nРасход: "+dev.netlab.automation.core.ServerHardware.energy(a.device)+" FE/тик\n70 °C — троттлинг; 90 °C — пауза; 60 °C — возобновление.";
                case "transfer-fluid"->operation=AutomationService.startFluid(a,v.get(0),v.get(1),v.get(2),Addressing.number(v.get(3),1,MachineFluids.MAX_AMOUNT),null);
                case "machine"->operation=AutomationService.action(a,v.getFirst(),String.join(" ",v.subList(1,v.size())));
                case "claim"->operation=AutomationService.claim(a,UUID.fromString(v.getFirst()),v.get(1),false);
                case "return"->operation=AutomationService.claim(a,UUID.fromString(v.getFirst()),"",true);
                case "show rules"->result=a.rules.toString();
                case "show automation"->result="IP: "+a.ip()+"\nGateway: "+a.device.gateway+"\nServer: "+a.manager+"\nRunning: "+a.running+"\nTarget: "+a.target+"\nPermissions: extract="+a.extract+", insert="+a.insert+", control="+a.control+"\nVPN required: "+a.requireVpn+" (шифруют роутеры)\n"+a.last;
                case "show machine"->result=a.device.kind.equals("endpoint")?MachineAccess.snapshot(a).toString():a.remoteSnapshot;
                case "show endpoints"->result=a.reports.toString();
                case "show jobs"->{var journal=TransferJournal.get(l);var out=new StringBuilder("Audit SHA-256: "+journal.audit.head()+" / valid="+journal.audit.valid()+"\n");for(var job:journal.jobs.values())if(a.identity.equals(job.controllerId))out.append(job.ticket).append(' ').append(job.state).append(' ').append(job.amount()).append(job.unit()).append(' ').append(job.note).append('\n');result=out.toString();}
                default->throw new IllegalArgumentException("Неизвестная команда");
            }
            a.changed();if(operation==null)return done(result);
            return operation.handle((value,error)->{a.last=error==null?String.valueOf(value):"% "+AutomationService.reason(error);a.changed();return a.last;});
        }catch(IllegalArgumentException error){return done("% "+error.getMessage());}
    }
    private static CompletableFuture<String> done(String value){return CompletableFuture.completedFuture(value);}
    @Override public CommandGrammar.Completion complete(String prefix){var choices=new ArrayList<>(AutomationCli.HELP.keySet());choices.addAll(List.of("enable","configure terminal","interface GigabitEthernet0/0","ip address ","ip default-gateway ","hostname ","shutdown","no shutdown","exit","end","show running-config","show ip interface brief"));var matches=choices.stream().filter(v->v.startsWith(prefix)).toList();return new CommandGrammar.Completion(matches.size()==1?matches.getFirst():prefix,matches);}
}

package dev.netlab.mobile;

import dev.netlab.*;
import dev.netlab.api.*;
import dev.netlab.mobile.core.*;
import dev.netlab.core.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Pending physical SIM survives disconnects and retries under the same identity. */
public final class MobileProvisioner {
    private static CompletableFuture<Void> exchange(MobileRef from,MobileRef to,String text){
        return ApplicationTransport.sendAsync(from.level(),new ApplicationTransport.Endpoint(from.host(),from.bay()),new ApplicationTransport.Endpoint(to.host(),to.bay()),text.getBytes(StandardCharsets.UTF_8),false).thenApply(r->{if(!r.success())throw new IllegalArgumentException(r.status());return null;});
    }
    public static CompletableFuture<String> pair(MobileRef station,String password){
        var core=MobileRef.core(station.level(),station.state().coreAddress);var c=core.state();if(c.adminVerifier.isEmpty())throw new IllegalArgumentException("Сначала mobile password на ядре EPC");
        String nonce=MobileAuth.secret(),key=MobileAuth.derive(password,MobileAuth.salt(c.adminVerifier)),proof=MobileAuth.proof(key,nonce,"station|"+station.device().identity);
        return exchange(core,station,"PAIR-CHALLENGE "+nonce).thenCompose(v->exchange(station,core,"PAIR-PROOF "+proof)).thenApply(v->{
            if(!station.live()||!core.live()||!Hardware.powered(core.device())||!MobileAuth.verify(MobileAuth.verifierKey(core.state().adminVerifier),nonce,"station|"+station.device().identity,proof))throw new IllegalArgumentException("Пароль не принят либо ядро изменилось");
            var s=station.state();s.stationKey=key;s.stationSalt=MobileAuth.salt(c.adminVerifier);s.stationCore=core.device().identity;station.changed(s);return "Станок авторизован в EPC";
        });
    }
    public static CompletableFuture<String> issue(MobileRef station,String label){
        if(!(station.host() instanceof MobileEntity machine))throw new IllegalArgumentException("Нужен SIM-станок");
        if(machine.issuing)throw new IllegalArgumentException("Уже выполняется запрос");
        var s=station.state();var core=MobileRef.core(station.level(),machine.pending==null?s.coreAddress:machine.pendingCore);var c=core.state();
        if(!Hardware.powered(station.device())||!Hardware.powered(core.device()))throw new IllegalArgumentException("Нет питания");
        if(!c.adminVerifier.isEmpty()&&!s.stationCore.equals(core.device().identity))throw new IllegalArgumentException("Сначала авторизуй станок: mobile pair ПАРОЛЬ");
        if(c.adminVerifier.isEmpty()||s.stationKey.isEmpty())throw new IllegalArgumentException("Ядро или станок не настроены");
        if(machine.pending==null){if(machine.blank.isEmpty()||!machine.output.isEmpty())throw new IllegalArgumentException("Нужна пустая SIM и свободный выход");machine.pending=new SimCredentials();machine.pending.issuer=core.device().identity;machine.pending.operator=c.plmn;machine.pending.apn=c.apn;machine.pending.label=DeviceEntity.cleanName(label.isBlank()?"SIM "+c.operatorName:label);machine.pendingCore=core.ip();machine.blank.shrink(1);machine.phase="provision";machine.changed();}
        var sim=machine.pending;String nonce=MobileAuth.secret(),body="issue|"+sim.id,proof=MobileAuth.proof(s.stationKey,nonce,body);machine.issuing=true;
        return exchange(station,core,"SIM-PROVISION "+sim.id+" "+nonce+" "+proof).thenCompose(v->{
            if(!station.live()||!core.live()||!MobileAuth.verify(MobileAuth.verifierKey(core.state().adminVerifier),nonce,body,proof))throw new IllegalArgumentException("Станок больше не авторизован");
            var state=core.state();state.subscribers.provision(sim);core.changed(state);machine.phase="activate";machine.changed();
            return exchange(core,station,"SIM-PROVISION-ACK "+sim.id);
        }).thenCompose(v->exchange(station,core,"SIM-ACTIVATE "+sim.id)).thenCompose(v->{
            if(!station.live()||!core.live())throw new IllegalArgumentException("Устройство отключено");var state=core.state();state.subscribers.activate(sim.id,MobileAuth.proof(sim.key,nonce,"activate|"+sim.id),nonce);core.changed(state);
            return exchange(core,station,"SIM-ACTIVE-ACK "+sim.id);
        }).thenApply(v->{if(!station.live()||machine.pending!=sim)throw new IllegalArgumentException("Станок заменён");machine.output=MobileItems.sim(sim);machine.pending=null;machine.phase="";machine.pendingCore="";machine.changed();return "SIM выпущена и активирована: "+sim.label;}).whenComplete((v,e)->{machine.issuing=false;machine.last=e==null?v:MobileService.reason(e);if(station.live())machine.changed();});
    }
}

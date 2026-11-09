package dev.netlab.academy;
import dev.netlab.*;
import dev.netlab.api.LearningEvents;
import dev.netlab.core.*;
import dev.netlab.academy.core.Course;
import net.minecraft.server.level.*;
import java.util.*;
import static dev.netlab.academy.Evidence.*;
/** Domain integrations emit only after their authoritative service has confirmed an outcome. */
final class AddonEvidence {
    static void event(LearningEvents.Event e,Course.Progress p,Device d){var m=e.data();String id=d==null?"":d.identity;
        switch(e.kind()){
            case "automation.ready"->{yes(p,flag(m,"crafted"),"A01");yes(p,flag(m,"cooled"),"A02");}
            case "automation.report"->{p.prove("A04");p.prove("A05");p.fact("endpoint:"+m.getOrDefault("endpoint",""),true);yes(p,p.facts.stream().filter(x->x.startsWith("endpoint:")).count()>=2&&flag(m,"insert"),"A09");}
            case "automation.inspect"->p.prove("A06");
            case "automation.login"->{p.prove("A07");yes(p,flag(m,"phone"),"X01");}
            case "automation.slots"->p.prove("A08");
            case "automation.deliver"->{if(number(m,"accepted")<=0)break;boolean liquid=flag(m,"liquid");p.prove(liquid?"A20":"A10");yes(p,!liquid&&p.fact("cargo.visible"),"A12");yes(p,!liquid&&flag(m,"gui"),"A11");yes(p,flag(m,"phone"),"X02");yes(p,!liquid&&number(m,"slot")>=0,"A13");yes(p,!liquid&&flag(m,"furnaceFuel"),"A14");yes(p,liquid&&number(m,"remaining")>0,"A21");yes(p,flag(m,"routed"),"A19");yes(p,flag(m,"encrypted"),"A24");yes(p,flag(m,"satellite"),liquid?"X08":"X07");yes(p,flag(m,"rule"),"A15");yes(p,flag(m,"anyRule"),"A16");yes(p,number(m,"batchDelivered")>64,"A26");yes(p,flag(m,"accelerated"),"A27");yes(p,flag(m,"cooling"),"A28");yes(p,flag(m,"rule")&&p.fact("cycle.id:"+id),"A29");yes(p,flag(m,"stolen")&&flag(m,"ownCargo"),"A23");}
            case "automation.craft"->p.prove("A17");case "automation.directory"->p.prove("A18");case "automation.replay.denied"->p.prove("A22");case "automation.encrypted.capture.denied"->p.fact("encrypted.denied",true);
            case "mobile.diagnosis"->{p.prove("M11");yes(p,flag(m,"routed"),"M13");}
            case "mobile.antenna"->p.prove("M02");case "mobile.rf"->p.prove("M03");case "mobile.fronthaul"->p.prove("M04");case "mobile.core"->p.prove("M05");case "mobile.chain"->p.prove("M06");case "mobile.station"->p.prove("M07");case "mobile.sim.issue"->{p.prove("M08");p.fact("issued:"+m.getOrDefault("sim",""),true);}case "mobile.sim.insert"->p.prove("M09");
            case "mobile.attach"->{p.prove(flag(m,"phone")?"M10":"M12");String sim=m.getOrDefault("sim","");for(var issuer:AcademyStore.get(e.level()).players.entrySet())if(!issuer.getKey().equals(e.actor().toString())&&issuer.getValue().fact("issued:"+sim)){issuer.getValue().prove("M14");p.prove("M14");Academy.changed(e.level(),UUID.fromString(issuer.getKey()));}
                String sector=m.getOrDefault("sector","");String old=p.values.put("sector:"+sim,sector);yes(p,old!=null&&!old.equals(sector),"M15");}
            case "mobile.sim.transfer"->p.prove("M14");case "mobile.signal.improved"->p.prove("M16");case "mobile.revoke"->p.prove("M17");
            case "sputnik.hub"->p.prove("S02");case "sputnik.provider.rf"->p.prove("S03");case "sputnik.subscriber.rf"->p.prove("S04");case "sputnik.radio"->p.prove("S05");case "sputnik.aligned"->p.prove("S06");case "sputnik.parameters"->p.prove("S07");case "sputnik.subscriber"->p.prove("S08");case "sputnik.lock"->{p.prove("S09");yes(p,List.of("sputnik_hub","sputnik_modem","sputnik_gateway").stream().allMatch(k->p.fact("cycle:"+k)),"S16");}case "sputnik.gateway"->p.prove("S10");case "sputnik.restore"->p.prove("S16");
            case "browser.loaded"->{p.prove("S12");yes(p,flag(m,"mobile"),"X03");}
            case "browser.audio"->{p.prove("S13");yes(p,flag(m,"background"),"S14");yes(p,number(m,"speakers")>=2,"X10");}
            case "media.headend.ready"->p.prove("T01");case "media.admin.ready"->p.prove("T02");case "media.admin.login"->p.prove("T03");case "media.channel.create"->p.prove("T04");case "media.playlist.add"->p.prove("T05");case "media.playlist.move"->p.prove("T06");case "media.channel.start"->p.prove("T07");case "media.stb.ready"->p.prove("T08");case "media.hdmi"->p.prove("T09");case "media.frame"->{p.prove("T10");yes(p,flag(m,"synchronized"),"T11");yes(p,flag(m,"lateJoin"),"T12");}case "media.audio"->{p.prove("T13");yes(p,number(m,"speakers")>=2,"T14");}case "media.editor"->p.prove("T15");case "media.restore"->p.prove("T16");
            case "voice.pbx.ready"->p.prove("V01");case "voice.subscriber"->{p.prove("V02");yes(p,number(m,"subscribers")>=2,"V03");}case "voice.phone.ready"->p.prove("V04");case "voice.registered"->{p.prove("V05");yes(p,number(m,"registered")>=2,"V06");yes(p,flag(m,"phone"),"X04");}case "voice.ringing"->p.prove("V07");case "voice.active"->p.prove("V08");case "voice.bidirectional"->{p.prove("V09");yes(p,flag(m,"speaker"),"V10");yes(p,flag(m,"routed"),"V12");yes(p,flag(m,"repaired"),"V13");yes(p,number(m,"pockets")>=1,"X05");yes(p,number(m,"pockets")==2,"X06");yes(p,flag(m,"satellite"),"X09");}case "voice.hangup"->p.prove("V11");case "voice.capacity"->p.prove("V14");
            case "carryon.rack.restored"->p.prove("E01");case "minecolonies.kitchen.completed"->p.prove("E02");case "ae2.manual"->p.prove("E03");
            case "traffic.admin"->p.prove("D05");case "traffic.delayed"->{p.prove("D06");yes(p,flag(m,"specific"),"D07");}case "traffic.vlan.login"->p.prove("D08");case "traffic.scope.denied"->p.fact("traffic.scope.denied",true);case "traffic.vlan.allowed"->yes(p,p.fact("traffic.scope.denied"),"D09");case "traffic.revoke"->p.prove("D10");
        }
        yes(p,p.fact("encrypted.denied")&&p.evidence.contains("A24"),"A25");
    }
    static void command(LearningEvents.Command c,Course.Progress p,boolean ok){if(!ok)return;var a=c.after();var id=a.identity;var ext=extension(a,"automation");
        if(a.kind.equals("server")&&!str(ext,"passwordHash").isBlank()&&p.values.getOrDefault("saved:"+id,"").equals(fingerprint(a))&&a.ports.stream().anyMatch(x->x.up&&!x.ip.isBlank()))p.prove("A03");
    }
    static void scan(ServerLevel level,ServerPlayer actor,Course.Progress p,DeviceEntity h,int bay,Device d){
        if(d.kind.equals("traffic")&&Hardware.powered(d)&&h.links.entrySet().stream().anyMatch(x->x.getKey()/128==bay&&d.ports.get(x.getKey()%128).console)&&h.links.entrySet().stream().anyMatch(x->x.getKey()/128==bay&&!d.ports.get(x.getKey()%128).console))p.prove("D04");
        if(d.kind.equals("server")&&Hardware.powered(d)&&MemoryCards.present(d)&&MemoryCards.card(d).active){yes(p,p.fact("craft:netlabautomation:management_server")||p.fact("craft:netlabautomation:server"),"A01");yes(p,Hardware.ensure(d).modules.containsValue("NL-SERVER-COOLER")&&!bool(extension(d,"netlabautomation:heat"),"protectedStop"),"A02");}
        if(d.kind.startsWith("media_")){}
    }
}

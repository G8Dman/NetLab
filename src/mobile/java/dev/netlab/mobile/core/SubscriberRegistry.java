package dev.netlab.mobile.core;

import dev.netlab.core.Device;
import java.util.*;

/** Idempotent provision/activate registry. The SIM station reserves one real blank card. */
public final class SubscriberRegistry {
    public static final class Subscriber {public String id="",key="",label="",ip="";public boolean active;}
    public Map<String,Subscriber> entries=new LinkedHashMap<>();
    public Subscriber provision(SimCredentials sim){
        UUID.fromString(sim.id);if(sim.key.isBlank())throw new IllegalArgumentException("Пустой ключ SIM");
        var old=entries.get(sim.id);if(old!=null){if(!old.key.equals(sim.key))throw new IllegalArgumentException("Конфликт ключа SIM");return old;}
        if(entries.size()>=256)throw new IllegalArgumentException("Лимит 256 SIM: удали неиспользуемые записи");var s=new Subscriber();s.id=sim.id;s.key=sim.key;s.label=sim.label;entries.put(s.id,s);return s;
    }
    public Subscriber activate(String id,String proof,String nonce){var s=entries.get(id);if(s==null||!MobileAuth.verify(s.key,nonce,"activate|"+id,proof))throw new IllegalArgumentException("SIM не прошла активацию");s.active=true;return s;}
    public String address(Subscriber s,String gateway){
        long base=Device.ip(gateway)&0xffffff00L;if(!s.ip.isEmpty()&&(Device.ip(s.ip)&0xffffff00L)==base)return s.ip;
        for(int host=10;host<=250;host++){String ip=dev.netlab.core.Addressing.ip(base+host);if(ip.equals(gateway)||entries.values().stream().anyMatch(other->other!=s&&other.ip.equals(ip)))continue;s.ip=ip;return ip;}throw new IllegalArgumentException("Пул адресов абонентов заполнен");
    }
    public boolean authenticate(SimCredentials sim,String issuer,String operator,String nonce,String proof){var s=entries.get(sim.id);return sim.issuer.equals(issuer)&&sim.operator.equals(operator)&&s!=null&&s.active&&MobileAuth.verify(s.key,nonce,"attach|"+sim.id,proof);}
}

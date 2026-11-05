package dev.netlab.core;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;

/** Persistent server-only LAN credentials and VLAN-limited controller authorizations. */
public final class TrafficAuthority {
    public static final class Grant {public String hash;public long revision;public Grant(String h,long r){hash=h;revision=r;}}
    public static final class Domain {
        public String id=UUID.randomUUID().toString(),adminHash="";public long revision=1,nextGrant=1;
        public Map<Integer,Grant> grants=new TreeMap<>();
    }
    public static final class Lease {
        public String domain,controller;public int vlan;public long revision;
        public Lease(String d,String c,int v,long r){domain=d;controller=c;vlan=v;revision=r;}
    }
    public static final class Rule {
        public String domain,controller,protocol;public int vlan;public double speed;
        public Rule(String d,String c,int v,String p,double s){domain=d;controller=c;vlan=v;protocol=p;speed=s;}
    }
    public final Map<String,Domain> domains=new LinkedHashMap<>();
    public final Map<String,String> anchors=new HashMap<>();
    public final Map<String,Lease> leases=new HashMap<>();
    public final List<Rule> rules=new ArrayList<>();
    public static final Set<String> PROTOCOLS=Set.of("all","arp","icmp","icmpv6","tcp","udp","dhcp","ndp");
    public static String hash(String secret){
        if(secret.length()<12||secret.length()>128)throw new IllegalArgumentException("Пароль должен содержать 12–128 символов");
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);return HexFormat.of().formatHex(salt)+":"+HexFormat.of().formatHex(derive(secret,salt));
    }
    private static byte[] derive(String value,byte[] salt){
        var spec=new PBEKeySpec(value.toCharArray(),salt,120000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}catch(GeneralSecurityException e){throw new IllegalStateException(e);}finally{spec.clearPassword();}
    }
    public static boolean verify(String value,String hash){try{String[] a=hash.split(":");return a.length==2&&MessageDigest.isEqual(HexFormat.of().parseHex(a[1]),derive(value,HexFormat.of().parseHex(a[0])));}catch(IllegalArgumentException e){return false;}}
    public Domain resolve(Set<String> members,boolean create){
        var ids=new HashSet<String>();for(String a:members)if(anchors.containsKey(a))ids.add(anchors.get(a));
        if(ids.size()>1)throw new IllegalArgumentException("Объединены LAN с разными регистрациями. Раздели сети; захват управления запрещён.");
        Domain d=ids.isEmpty()?null:domains.get(ids.iterator().next());
        if(d==null&&create){if(members.isEmpty())throw new IllegalArgumentException("Подключи RJ45 к локальной сети");if(domains.size()>=1024)throw new IllegalArgumentException("Лимит LAN достигнут");d=new Domain();domains.put(d.id,d);}
        if(d!=null&&create){if(anchors.size()+members.size()>65536)throw new IllegalArgumentException("Лимит регистраций LAN достигнут");for(String a:members)anchors.put(a,d.id);}
        return d;
    }
    public Lease authenticate(Domain d,String controller,int vlan,String password){
        if(d.adminHash.isBlank())throw new IllegalArgumentException("Сначала создай пароль администратора LAN");
        var previous=leases.get(controller);if(vlan!=0&&previous!=null&&previous.domain.equals(d.id)&&previous.vlan==0)throw new IllegalArgumentException("Это администраторский контроллер LAN. Пароль VLAN не может изменить его назначение.");
        Grant grant=vlan==0?null:d.grants.get(vlan);String expected=vlan==0?d.adminHash:grant==null?"":grant.hash;
        if(expected.isBlank()||!verify(password,expected))throw new IllegalArgumentException("Неверный пароль или доступ VLAN отозван");
        var lease=new Lease(d.id,controller,vlan,vlan==0?d.revision:grant.revision);leases.put(controller,lease);return lease;
    }
    public Lease setup(Domain d,String controller,String hash){
        if(!d.adminHash.isBlank())throw new IllegalArgumentException("LAN уже зарегистрирована. Нужен её существующий пароль администратора.");
        d.adminHash=hash;var l=new Lease(d.id,controller,0,d.revision);leases.put(controller,l);return l;
    }
    public boolean valid(Lease l){
        if(l==null)return false;var d=domains.get(l.domain);if(d==null||d.adminHash.isBlank())return false;
        var current=leases.get(l.controller);if(current==null||!current.domain.equals(l.domain)||current.vlan!=l.vlan||current.revision!=l.revision)return false;
        return l.vlan==0?l.revision==d.revision: d.grants.containsKey(l.vlan)&&d.grants.get(l.vlan).revision==l.revision;
    }
    public Lease changeAdminPassword(Lease actor,String hash){require(actor,0);var d=domains.get(actor.domain);d.adminHash=hash;d.revision++;var role=new Lease(d.id,actor.controller,0,d.revision);leases.put(actor.controller,role);return role;}
    public void grant(Lease actor,int vlan,String hash){
        require(actor,0);if(vlan<1||vlan>4094)throw new IllegalArgumentException("VLAN: 1..4094");var d=domains.get(actor.domain);
        if(d.grants.size()>=128&&!d.grants.containsKey(vlan))throw new IllegalArgumentException("Лимит делегированных VLAN достигнут");
        d.grants.put(vlan,new Grant(hash,++d.nextGrant));
    }
    public void grantPassword(Lease actor,int vlan,String password){require(actor,0);if(verify(password,domains.get(actor.domain).adminHash))throw new IllegalArgumentException("Пароль VLAN должен отличаться от пароля администратора LAN");grant(actor,vlan,hash(password));}
    public void revoke(Lease actor,int vlan){require(actor,0);domains.get(actor.domain).grants.remove(vlan);rules.removeIf(r->r.domain.equals(actor.domain)&&r.vlan==vlan&&leases.get(r.controller)!=null&&leases.get(r.controller).vlan!=0);}
    public void require(Lease actor,int vlan){if(!valid(actor)||actor.vlan!=0&&(vlan==0||vlan!=actor.vlan))throw new IllegalArgumentException("Недостаточно прав: разрешена только своя VLAN");}
    public void speed(Lease actor,int vlan,String protocol,double speed){
        require(actor,vlan);if(vlan<0||vlan>4094||!PROTOCOLS.contains(protocol)||!Double.isFinite(speed)||speed<.000001||speed>1)throw new IllegalArgumentException("speed: VLAN 1..4094, protocol all/arp/icmp/tcp/udp/dhcp/ndp, factor 0.000001..1");
        rules.removeIf(r->r.controller.equals(actor.controller)&&r.domain.equals(actor.domain)&&r.vlan==vlan&&r.protocol.equals(protocol));
        if(rules.size()>=4096)throw new IllegalArgumentException("Лимит правил достигнут");rules.add(new Rule(actor.domain,actor.controller,vlan,protocol,speed));
    }
    public void clear(Lease actor,int vlan){require(actor,vlan);rules.removeIf(r->r.domain.equals(actor.domain)&&(vlan==0||r.vlan==vlan)&&(actor.vlan==0||leases.get(r.controller)!=null&&leases.get(r.controller).vlan==actor.vlan));}
    public static boolean matches(String rule,String protocol){String p=protocol.toLowerCase(Locale.ROOT).replace("span/","");return rule.equals("all")||rule.equals(p)||rule.equals("dhcp")&&p.startsWith("dhcp-");}
    public double factor(String domain,int vlan,String protocol,Set<String> active){
        double speed=1;for(var r:rules){var lease=leases.get(r.controller);if(r.domain.equals(domain)&&active.contains(r.controller)&&valid(lease)&&lease.domain.equals(domain)&&(lease.vlan==0||lease.vlan==r.vlan)&&(r.vlan==0||r.vlan==vlan)&&matches(r.protocol,protocol))speed=Math.min(speed,r.speed);}return speed;
    }
}

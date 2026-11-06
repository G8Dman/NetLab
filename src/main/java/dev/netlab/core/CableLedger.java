package dev.netlab.core;

import java.util.*;

/** One physical item per connection; pending ends prevent duplicate refunds across chunk unloads. */
public final class CableLedger {
    public record End(long position,int port,boolean power){}
    public static final class Cable {
        public String item;
        public boolean paid,closed;
        public List<End> ends,pending;
    }
    public Map<String,Cable> cables=new LinkedHashMap<>();
    public String install(End a,End b,String item,boolean paid){String token=UUID.randomUUID().toString();register(token,a,b,item,paid);return token;}
    public void register(String token,End a,End b,String item,boolean paid){
        if(token==null||token.isBlank()||a.equals(b)||item==null||!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid physical cable");
        if(cables.containsKey(token))return;var cable=new Cable();cable.item=item;cable.paid=paid;cable.ends=List.of(a,b);cable.pending=new ArrayList<>(cable.ends);cables.put(token,cable);
    }
    public boolean active(String token,End end){var cable=cables.get(token);return cable!=null&&!cable.closed&&cable.ends.contains(end);}
    public String claim(String token,End end){var cable=cables.get(token);if(cable==null||cable.closed||!cable.ends.contains(end))return "";cable.closed=true;return cable.paid?cable.item:"";}
    public void acknowledge(String token,End end){var cable=cables.get(token);if(cable==null||!cable.closed)return;cable.pending.remove(end);if(cable.pending.isEmpty())cables.remove(token);}
    public static String legacy(End a,End b){
        String first=a.position()+":"+a.port()+":"+a.power(),second=b.position()+":"+b.port()+":"+b.power();
        if(first.compareTo(second)>0){String swap=first;first=second;second=swap;}
        return "legacy:"+UUID.nameUUIDFromBytes((first+"/"+second).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

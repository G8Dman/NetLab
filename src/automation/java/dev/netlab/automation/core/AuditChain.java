package dev.netlab.automation.core;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
/** Append-only hash-linked audit, not distributed consensus or an authorization token. */
public final class AuditChain {
    public record Entry(String previous,String event,String hash){}
    public String anchor="GENESIS";
    public List<Entry> entries=new ArrayList<>();
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public void add(String event){String previous=entries.isEmpty()?anchor:entries.getLast().hash();entries.add(new Entry(previous,event,hash(previous+"\n"+event)));if(entries.size()>4096)anchor=entries.removeFirst().hash();}
    public boolean valid(){if(anchor==null||entries==null||entries.size()>4096)return false;String previous=anchor;for(var entry:entries){if(entry==null||entry.previous()==null||entry.hash()==null||entry.event()==null||!entry.previous().equals(previous)||!entry.hash().equals(hash(previous+"\n"+entry.event())))return false;previous=entry.hash();}return true;}
    public String head(){return entries.isEmpty()?anchor:entries.getLast().hash();}
}

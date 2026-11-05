package dev.netlab.core;

import java.util.*;

/** Text retention is tied to the physical wire generation, not to GUI or power cycles. */
public class ConsoleTranscript {
    public String token=UUID.randomUUID().toString(),text="";
    public List<String> history=new ArrayList<>();
    private transient String liveSession;
    private transient long revision;
    public boolean attachSession(String id){if(Objects.equals(liveSession,id))return false;liveSession=id;revision++;return true;}
    public long beginRequest(){return ++revision;}
    public boolean currentRequest(long value){return revision==value;}
    public static String binding(String device,int source,int remote,long localWire,long remoteWire){return device+"|"+source+"|"+remote+"|"+localWire+"|"+remoteWire;}
    public void appendText(String value){if(value.isEmpty())return;text+=text.isEmpty()?value:"\n"+value;if(text.length()>150000)text=text.substring(text.length()-150000);}
    public void remember(String command,boolean secret){if(!secret&&!command.isBlank()){history.add(command);while(history.size()>100)history.removeFirst();}}
}

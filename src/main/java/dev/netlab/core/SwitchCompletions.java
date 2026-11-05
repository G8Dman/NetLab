package dev.netlab.core;

import java.util.*;

public final class SwitchCompletions {
    public static CommandGrammar.Completion complete(Console c,Device d,CommandGrammar.Spec spec,List<String> input,String fragment,String original){
        String word=spec.words();int count=word.split(" ").length;int arg=Math.max(0,input.size()-count);List<String> values=List.of();
        if(word.equals("switchport mode"))values=arg==0?List.of("access","trunk","dynamic"):List.of("auto","desirable");
        else if(word.equals("vtp mode"))values=List.of("server","client","transparent");
        else if(word.equals("spanning-tree mode"))values=List.of("pvst","rapid-pvst");
        else if(word.equals("switchport port-security violation"))values=List.of("shutdown","restrict","protect");
        else if(word.equals("switchport port-security mac-address"))values=List.of("sticky");
        else if(word.equals("switchport trunk allowed vlan"))values=List.of("all","none","add","remove");
        else if(word.equals("channel-group")&&arg>0)values=arg==1?List.of("mode"):List.of("active","passive","desirable","auto","on");
        else if(word.equals("vlan")||word.equals("no vlan")||word.endsWith("access vlan")||word.endsWith("native vlan"))values=d.os().vlans.keySet().stream().map(Object::toString).toList();
        if(values.isEmpty())return RouterCompletions.complete(c,d,spec,input,fragment,original);
        String base=String.join(" ",input)+(input.isEmpty()?"":" ");
        var choices=values.stream().filter(v->v.startsWith(fragment.toLowerCase(Locale.ROOT))).map(v->base+v+" ").toList();
        return new CommandGrammar.Completion(choices.size()==1?choices.getFirst():original,choices);
    }
}

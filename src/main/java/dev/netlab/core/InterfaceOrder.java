package dev.netlab.core;

import java.util.*;
import java.util.regex.Pattern;

/** Display order only; cables keep their saved physical port index. */
public final class InterfaceOrder {
    private static final Pattern PARTS=Pattern.compile("[0-9]+|[^0-9]+");
    private InterfaceOrder(){}
    private static int family(String name){
        String n=name.toLowerCase(Locale.ROOT);
        if(n.startsWith("ethernet")||n.startsWith("eth"))return 0;
        if(n.startsWith("fastethernet"))return 1;
        if(n.startsWith("gigabitethernet"))return 2;
        if(n.startsWith("tengigabitethernet"))return 3;
        if(n.startsWith("serial"))return 4;
        if(n.startsWith("console")||n.startsWith("rs232"))return 99;
        return 10;
    }
    public static int compare(String a,String b){
        int family=Integer.compare(family(a),family(b));if(family!=0)return family;
        var left=PARTS.matcher(a.toLowerCase(Locale.ROOT));var right=PARTS.matcher(b.toLowerCase(Locale.ROOT));
        while(left.find()&&right.find()){
            String x=left.group(),y=right.group();int c;
            if(Character.isDigit(x.charAt(0))&&Character.isDigit(y.charAt(0))){
                x=x.replaceFirst("^0+(?!$)","");y=y.replaceFirst("^0+(?!$)","");
                c=Integer.compare(x.length(),y.length());if(c==0)c=x.compareTo(y);
            }else c=x.compareTo(y);
            if(c!=0)return c;
        }
        return a.length()==b.length()?a.compareToIgnoreCase(b):Integer.compare(a.length(),b.length());
    }
    public static List<Integer> visible(Device d){
        var indices=new ArrayList<Integer>();for(int i=0;i<d.ports.size();i++)if(!d.ports.get(i).hidden&&!d.ports.get(i).console)indices.add(i);
        indices.sort((a,b)->{int c=Boolean.compare(d.ports.get(a).virtual,d.ports.get(b).virtual);return c==0?compare(d.portName(a),d.portName(b)):c;});
        return indices;
    }
}

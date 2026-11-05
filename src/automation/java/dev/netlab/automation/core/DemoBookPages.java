package dev.netlab.automation.core;

import java.util.*;

/** Conservative vanilla-book layout; wrapped command fragments retain the complete copy action. */
public final class DemoBookPages {
    public static final int COLUMNS=18,ROWS=12;
    public record Span(String text,String copy){}
    public static List<List<Span>> paginate(List<Span> spans){
        var pages=new ArrayList<List<Span>>();var page=new ArrayList<Span>();
        for(var span:spans)for(String paragraph:span.text.split("\n",-1)){
            String remaining=paragraph;
            do{
                int end=Math.min(COLUMNS,remaining.length());
                if(end<remaining.length()){int space=remaining.lastIndexOf(' ',end);if(space>0)end=space;}
                String line=remaining.substring(0,end);page.add(new Span(line+"\n",span.copy));
                remaining=remaining.substring(end).stripLeading();
                if(page.size()==ROWS){pages.add(List.copyOf(page));page.clear();}
            }while(!remaining.isEmpty());
        }
        if(!page.isEmpty())pages.add(List.copyOf(page));return List.copyOf(pages);
    }
}

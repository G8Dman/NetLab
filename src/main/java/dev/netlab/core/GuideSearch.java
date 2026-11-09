package dev.netlab.core;

import java.util.*;

/** Literal, case-insensitive search; offsets always refer to the original UTF-16 text. */
public final class GuideSearch {
    public record Match(int start,int end){}
    public static List<Match> matches(String text,String query){
        String q=query==null?"":query.strip();if(q.isEmpty())return List.of();
        var result=new ArrayList<Match>();
        for(int at=0;at<=text.length()-q.length();){
            if(text.regionMatches(true,at,q,0,q.length())){result.add(new Match(at,at+q.length()));at+=q.length();}
            else at+=Character.charCount(text.codePointAt(at));
        }
        return List.copyOf(result);
    }
    public static boolean contains(String text,String query){return query==null||query.isBlank()||!matches(text,query).isEmpty();}
    private GuideSearch(){}
}

package dev.netlab.media.core;

import java.net.URI;
import java.util.*;

/** Deterministic channel clock; server time, not whichever viewer loaded first. */
public final class MediaPlaylist {
    public record Entry(String url,String title,int seconds,String fallback) {
        public Entry(String url,String title,int seconds) { this(url,title,seconds,""); }
        public Entry {
            url=validate(url);title=clean(title,80);
            if(seconds<1||seconds>86400)throw new IllegalArgumentException("Длительность: 1..86400 секунд");
            fallback=fallback==null||fallback.isBlank()?"":validate(fallback);
            if(!fallback.isEmpty()&&(MediaSource.parse(url).kind()!=MediaSource.Kind.TWITCH_LIVE
                    || MediaSource.parse(fallback).kind()==MediaSource.Kind.TWITCH_LIVE))
                throw new IllegalArgumentException("Запасное видео задаётся для прямого эфира Twitch и должно быть записью");
        }
    }
    public record Playing(int index,Entry entry,double seconds,long cycle){}
    public static String clean(String text,int max){String s=text==null?"":text.replaceAll("[\\p{Cntrl}§]","").strip();if(s.length()>max)throw new IllegalArgumentException("Не больше "+max+" символов");return s;}
    public static String validate(String text){try{var u=URI.create(text.strip());if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.toASCIIString().length()>1024)throw new IllegalArgumentException();return u.toASCIIString();}catch(RuntimeException e){throw new IllegalArgumentException("Нужна HTTP/HTTPS ссылка до 1024 символов");}}
    public static String youtube(String url){try{var source=MediaSource.parse(url);return source.kind()==MediaSource.Kind.YOUTUBE?source.id():"";}catch(IllegalArgumentException e){return "";}}
    public static Playing at(List<Entry> list,long started,long now,boolean loop){
        if(list.isEmpty())return null;long total=list.stream().mapToLong(e->e.seconds()*20L).sum(),elapsed=Math.max(0,now-started);
        if(!loop&&elapsed>=total)return null;long cursor=loop?elapsed%total:elapsed,cycle=elapsed/total;
        for(int i=0;i<list.size();i++){long span=list.get(i).seconds()*20L;if(cursor<span)return new Playing(i,list.get(i),cursor/20.0,cycle);cursor-=span;}throw new IllegalStateException();
    }
    private MediaPlaylist(){}
}

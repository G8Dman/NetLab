package dev.netlab.media.core;

import java.net.URI;
import java.util.*;

/** Deterministic channel clock; server time, not whichever viewer loaded first. */
public final class MediaPlaylist {
    public record Entry(String url,String title,int seconds) {
        public Entry {url=validate(url);title=clean(title,80);if(seconds<1||seconds>86400)throw new IllegalArgumentException("Длительность: 1..86400 секунд");}
    }
    public record Playing(int index,Entry entry,double seconds,long cycle){}
    public static String clean(String text,int max){String s=text==null?"":text.replaceAll("[\\p{Cntrl}§]","").strip();if(s.length()>max)throw new IllegalArgumentException("Не больше "+max+" символов");return s;}
    public static String validate(String text){try{var u=URI.create(text.strip());if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||text.length()>1024)throw new IllegalArgumentException();return u.toASCIIString();}catch(RuntimeException e){throw new IllegalArgumentException("Нужна HTTP/HTTPS ссылка до 1024 символов");}}
    public static String youtube(String url){var u=URI.create(validate(url));String host=u.getHost().toLowerCase(Locale.ROOT),id="";if(host.equals("youtu.be"))id=u.getPath().substring(1).split("/")[0];else if(Set.of("youtube.com","www.youtube.com","m.youtube.com","www.youtube-nocookie.com").contains(host)){String path=u.getPath();if(path.startsWith("/embed/")||path.startsWith("/shorts/")||path.startsWith("/live/"))id=path.substring(path.lastIndexOf('/')+1);else if(u.getRawQuery()!=null)for(String pair:u.getRawQuery().split("&"))if(pair.startsWith("v="))id=pair.substring(2);}return id.matches("[A-Za-z0-9_-]{11}")?id:"";}
    public static Playing at(List<Entry> list,long started,long now,boolean loop){
        if(list.isEmpty())return null;long total=list.stream().mapToLong(e->e.seconds()*20L).sum(),elapsed=Math.max(0,now-started);
        if(!loop&&elapsed>=total)return null;long cursor=loop?elapsed%total:elapsed,cycle=elapsed/total;
        for(int i=0;i<list.size();i++){long span=list.get(i).seconds()*20L;if(cursor<span)return new Playing(i,list.get(i),cursor/20.0,cycle);cursor-=span;}throw new IllegalStateException();
    }
    private MediaPlaylist(){}
}

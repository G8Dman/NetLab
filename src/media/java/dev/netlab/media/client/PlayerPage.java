package dev.netlab.media.client;
import com.google.gson.Gson;
import dev.netlab.media.core.MediaPlaylist;
/** Only validated URLs are interpolated, encoded as JSON (never HTML attributes). */
public final class PlayerPage {
    public static String html(String url,double seconds){url=MediaPlaylist.validate(url);String id=MediaPlaylist.youtube(url);String base="<!doctype html><meta charset=utf-8><meta name=referrer content=strict-origin-when-cross-origin><style>html,body{margin:0;width:100%;height:100%;background:#07151d;color:white;overflow:hidden}#p,video{width:100%;height:100%;border:0}</style><div id=p></div><script>const initial="+Math.max(0,seconds)+";";
        if(!id.isEmpty())return base+"let player;window.onYouTubeIframeAPIReady=()=>{player=new YT.Player('p',{videoId:"+new Gson().toJson(id)+",playerVars:{autoplay:1,controls:1,playsinline:1,origin:'https://netlab.media',start:Math.floor(initial)},events:{onReady:e=>e.target.playVideo(),onError:e=>document.title='YouTube: '+e.data}})};window.netlabSeek=t=>{if(player&&player.getCurrentTime&&Math.abs(player.getCurrentTime()-t)>3)player.seekTo(t,true)};</script><script src=https://www.youtube.com/iframe_api></script>";
        return base+"const v=document.createElement('video');v.controls=true;v.autoplay=true;v.src="+new Gson().toJson(url)+";document.getElementById('p').replaceWith(v);v.onloadedmetadata=()=>{v.currentTime=initial;v.play().catch(()=>{})};window.netlabSeek=t=>{if(Number.isFinite(v.duration)&&Math.abs(v.currentTime-t)>3)v.currentTime=t};</script>";
    }
}

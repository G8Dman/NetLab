package dev.netlab.media.client;

import com.google.gson.Gson;
import dev.netlab.media.core.MediaSource;

/** The local player page embeds only the selected provider's player or an HTML video. */
public final class PlayerPage {
    private static final Gson JSON = new Gson();

    public static String html(String url, double seconds) {
        return html(url, seconds, "");
    }

    static String html(String url, double seconds, String revision) {
        return html(MediaSource.parse(url), seconds, revision);
    }

    static String html(MediaSource source, double seconds, String revision) {
        String base = """
                <!doctype html><meta charset=utf-8>
                <meta name=referrer content=strict-origin-when-cross-origin>
                <style>html,body{margin:0;width:100%%;height:100%%;background:#07151d;color:white;overflow:hidden}
                #p,iframe,video{width:100%%;height:100%%;border:0;display:block}video{object-fit:contain}</style>
                <div id=p></div><script>
                let revision=%s;
                let target=%s, align=()=>{};
                const signal=(kind,value)=>document.title='NETLAB_'+kind+':'+revision+':'+value;
                const proof=t=>{if(Number.isFinite(t)&&t>=0)signal('FRAME',t)};
                const status=s=>signal('STATUS',s);
                window.netlabSeek=t=>{if(Number.isFinite(t)&&t>=0){target=t;align()}};
                function frame(url){const f=document.createElement('iframe');f.allow='autoplay; encrypted-media; fullscreen';
                  f.allowFullscreen=true; f.src=url; document.getElementById('p').append(f); return f}
                </script>
                """.formatted(JSON.toJson(revision),Double.isFinite(seconds)?Math.max(0, seconds):0);
        return base + switch (source.kind()) {
            case YOUTUBE -> youtube(source);
            case RUTUBE -> rutube(source);
            case VK -> vk(source);
            case TWITCH_VIDEO, TWITCH_LIVE -> twitch(source);
            case TWITCH_CLIP -> script("frame(" + JSON.toJson(source.location() + "&parent=" + MediaSource.PLAYER_HOST + "&autoplay=true") + ");");
            case DIRECT -> direct(source);
        };
    }

    private static String youtube(MediaSource source) {
        return script("""
                let player, ready=false, videoId=%s;
                const playSelected=()=>player.loadVideoById({videoId,startSeconds:target});
                window.netlabYouTube=(id,seconds,nextRevision)=>{
                  target=seconds;
                  if(nextRevision===revision){align();return}
                  revision=nextRevision;videoId=id;
                  if(ready)playSelected();
                };
                window.onYouTubeIframeAPIReady=()=>{
                  const initialRevision=revision;
                  player=new YT.Player('p',{videoId,
                    playerVars:{autoplay:1,controls:1,playsinline:1,origin:location.origin,start:Math.floor(target)},
                    events:{onReady:e=>{ready=true;if(initialRevision===revision)e.target.playVideo();else playSelected()},
                      onError:e=>status('YouTube: '+e.data),onAutoplayBlocked:()=>status('Нажми Play на экране')}});
                };
                align=()=>{
                  if(!ready||player.getPlayerState()!==1)return;
                  let playing;try{playing=new URL(player.getVideoUrl()).searchParams.get('v')}catch{return}
                  if(playing!==videoId)return;
                  const now=player.getCurrentTime();
                  if(Math.abs(now-target)>3)player.seekTo(target,true);else proof(now);
                };
                """.formatted(JSON.toJson(source.id()))) + "<script src=https://www.youtube.com/iframe_api></script>";
    }

    private static String rutube(MediaSource source) {
        return script("""
                const url=new URL(%s);url.searchParams.set('autoplay','true');
                const f=frame(url.href), origin=url.origin;
                let last=NaN, playing=false, ad=false;
                const send=(type,data)=>f.contentWindow.postMessage(JSON.stringify({type,data}),origin);
                window.addEventListener('message',e=>{
                  if(e.origin!==origin||e.source!==f.contentWindow)return;
                  let m;try{m=typeof e.data==='string'?JSON.parse(e.data):e.data}catch{ return }
                  if(!m||!m.data)return;
                  if(m.type==='player:ready')send('player:play',{});
                  if(m.type==='player:changeState')playing=m.data.state==='playing';
                  if(m.type==='player:currentTime')last=Number(m.data.time);
                  if(m.type==='player:adStart')ad=true;
                  if(m.type==='player:adEnd')ad=false;
                  if(m.type==='player:rollState')ad=m.data.state==='play';
                  if(m.type==='player:error')status('RUTUBE: видео недоступно');
                });
                align=()=>{if(!playing||ad||!Number.isFinite(last))return;
                  if(Math.abs(last-target)>3)send('player:setCurrentTime',{time:target});else proof(last)};
                """.formatted(JSON.toJson(source.location())));
    }

    private static String vk(MediaSource source) {
        return "<script src=https://vkvideo.ru/js/api/videoplayer.js></script>" + script("""
                const url=new URL(%s);url.searchParams.set('js_api','1');url.searchParams.set('autoplay','1');
                const f=frame(url.href);
                if(!window.VK||!VK.VideoPlayer)status('VK Видео: плеер недоступен');
                else {
                  const player=VK.VideoPlayer(f);
                  let playing=false, ad=false;
                  player.on('inited',()=>player.play());
                  player.on('started',()=>playing=true); player.on('resumed',()=>playing=true);
                  player.on('paused',()=>playing=false); player.on('ended',()=>playing=false);
                  player.on('adStarted',()=>ad=true); player.on('adCompleted',()=>ad=false);
                  player.on('error',()=>status('VK Видео: проверь ссылку для встраивания'));
                  align=()=>{if(!playing||ad)return;const now=player.getCurrentTime();
                    if(Number.isFinite(now)){if(Math.abs(now-target)>3)player.seek(target);else proof(now)}};
                }
                """.formatted(JSON.toJson(source.location())));
    }

    private static String twitch(MediaSource source) {
        boolean live = source.kind()==MediaSource.Kind.TWITCH_LIVE;
        String option=live?"channel:":"video:";
        String identifier=JSON.toJson(live?source.id():"v"+source.id());
        return "<script src=https://player.twitch.tv/js/embed/v1.js></script>" + script("""
                if(!window.Twitch||!Twitch.Player)status('Twitch: плеер недоступен');
                else {
                  const live=%s;
                  const player=new Twitch.Player('p',{width:'100%%',height:'100%%',%s%s,parent:[location.hostname],autoplay:true});
                  let playing=false;
                  player.addEventListener(Twitch.Player.PLAYING,()=>{playing=true;align()});
                  player.addEventListener(Twitch.Player.PAUSE,()=>playing=false);
                  player.addEventListener(Twitch.Player.ENDED,()=>playing=false);
                  player.addEventListener(Twitch.Player.PLAYBACK_BLOCKED,()=>status('Нажми Play на экране'));
                  if(live)player.addEventListener(Twitch.Player.OFFLINE,()=>status('TWITCH_OFFLINE'));
                  align=()=>{if(!playing||live)return;const now=player.getCurrentTime();
                    if(Number.isFinite(now)){if(Math.abs(now-target)>3)player.seek(target);else proof(now)}};
                }
                """.formatted(live,option,identifier));
    }

    private static String direct(MediaSource source) {
        return script("""
                const v=document.createElement('video');v.controls=true;v.autoplay=true;v.playsInline=true;v.src=%s;
                document.getElementById('p').replaceWith(v);
                v.onloadedmetadata=()=>{if(Number.isFinite(v.duration))v.currentTime=Math.min(target,v.duration);
                  v.play().catch(()=>status('Нажми Play на экране'))};
                v.onerror=()=>status('Не удалось открыть прямое видео');
                align=()=>{if(Number.isFinite(v.duration)&&!v.seeking&&Math.abs(v.currentTime-target)>3)
                  v.currentTime=Math.min(target,v.duration)};
                if(v.requestVideoFrameCallback){const f=(n,m)=>{proof(m.mediaTime);v.requestVideoFrameCallback(f)};v.requestVideoFrameCallback(f)};
                """.formatted(JSON.toJson(source.location())));
    }

    private static String script(String contents) { return "<script>"+contents+"</script>"; }
    private PlayerPage() {}
}

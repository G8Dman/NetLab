package dev.netlab.core;
import dev.netlab.media.core.*;
import java.util.*;
public final class MediaTest {
    static int checks;
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;}
    static void refuse(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
    public static int run(){
        var a=new MediaPlaylist.Entry("https://example.org/a.webm","A",10);var b=new MediaPlaylist.Entry("https://example.org/b.webm","B",20);var entries=List.of(a,b);
        for(long start:new long[]{0,100,100000}){check(MediaPlaylist.at(entries,start,start,true).index()==0,"channel starts first video");check(MediaPlaylist.at(entries,start,start+199,true).index()==0,"last tick before next entry");check(MediaPlaylist.at(entries,start,start+200,true).index()==1,"exact next-entry boundary");check(MediaPlaylist.at(entries,start,start+599,true).index()==1,"last channel tick");check(MediaPlaylist.at(entries,start,start+600,true).index()==0,"loop restarts");check(MediaPlaylist.at(entries,start,start+600,false)==null,"nonloop channel finishes");check(MediaPlaylist.at(entries,start,start+640,true).seconds()==2,"late joiner receives current timestamp");}
        for(long tick=0;tick<1800;tick+=7){var p=MediaPlaylist.at(entries,0,tick,true);check(p.index()==(tick%600<200?0:1),"same channel schedule for any viewer");check(p.seconds()>=0&&p.seconds()<p.entry().seconds(),"media timestamp remains in entry");}
        check(MediaPlaylist.at(List.of(),0,100,true)==null,"empty channel is silent");
        String id="M7lc1UVf-VE";for(String url:List.of("https://youtu.be/"+id,"https://www.youtube.com/watch?v="+id+"&t=8","https://youtube.com/embed/"+id,"https://youtube.com/shorts/"+id))check(MediaPlaylist.youtube(url).equals(id),"YouTube URL adapters");
        check(MediaPlaylist.youtube("https://youtube.com.evil.test/watch?v="+id).isEmpty(),"YouTube host is matched exactly");
        String rutubeId="57e1cb24939d657284dc2900deb5949a";
        String rutube="https://rutube.ru/video/"+rutubeId+"/";
        String vk="https://vkvideo.ru/video-139157852_456239750";
        String twitch="https://www.twitch.tv/videos/40464143";
        check(MediaSource.parse(rutube).location().equals("https://rutube.ru/play/embed/"+rutubeId),"RUTUBE link maps to public player");
        check(MediaSource.parse(vk).location().equals("https://vkvideo.ru/video_ext.php?oid=-139157852&id=456239750"),"VK link retains negative owner ID");
        check(MediaSource.parse("https://vk.com/video_ext.php?oid=-1&id=2&hash=a9&autoplay=0").location().endsWith("oid=-1&id=2&hash=a9"),"VK export hash survives but supplied player options do not");
        check(MediaSource.parse("https://vk.com/wall-10_15?z=video-139157852_456239750%2Fplaylist").equals(MediaSource.parse(vk)),"VK community video link");
        check(MediaSource.parse(twitch).kind()==MediaSource.Kind.TWITCH_VIDEO,"Twitch recording is seekable source");
        check(MediaSource.parse("https://www.twitch.tv/twitchdev").kind()==MediaSource.Kind.TWITCH_LIVE,"Twitch channel is a live slot");
        check(MediaSource.parse("https://clips.twitch.tv/TwitchExample").equals(MediaSource.parse("https://www.twitch.tv/twitchdev/clip/TwitchExample")),"both Twitch clip URL forms select same embed");
        check(MediaSource.parse("https://rutube.ru.example.org/video/"+rutubeId).kind()==MediaSource.Kind.DIRECT,"host matching does not confuse arbitrary media with RUTUBE");
        refuse(()->MediaSource.parse("https://www.twitch.tv/directory"),"a Twitch browsing page is not a stream");
        refuse(()->MediaSource.parse("https://rutube.ru/video/%22"),"embed IDs cannot contain syntax");
        refuse(()->MediaSource.parse("https://example.org:9000/video.webm"),"IPTV gateway does not change its allowed ports");
        var mixed=List.of(new MediaPlaylist.Entry("https://youtu.be/"+id,"YT",10),new MediaPlaylist.Entry(rutube,"RT",10),new MediaPlaylist.Entry(vk,"VK",10),new MediaPlaylist.Entry(twitch,"Twitch",10));
        for(int i=0;i<mixed.size();i++)check(MediaPlaylist.at(mixed,0,i*200L+80,true).entry().equals(mixed.get(i)),"one clock spans all providers");
        var fallback=new MediaPlaylist.Entry("https://twitch.tv/twitchdev","Live",120,vk);
        check(fallback.fallback().equals(vk),"live Twitch slot can carry a fallback recording");
        refuse(()->new MediaPlaylist.Entry(twitch,"VOD",120,vk),"fallback belongs to a live slot");
        for(int direction=0;direction<4;direction++){
            var left=DeviceOrientation.rotate(.9,.7,TelevisionSurface.FRONT,direction);
            var right=DeviceOrientation.rotate(.1,.7,TelevisionSurface.FRONT,direction);
            var leftPixel=TelevisionSurface.pixel(left.x(),left.y(),left.z(),direction);
            var rightPixel=TelevisionSurface.pixel(right.x(),right.y(),right.z(),direction);
            check(leftPixel!=null&&rightPixel!=null&&leftPixel.u()<.1&&rightPixel.u()>.9,"clicks agree with screen image in all rotations");
        }
        for(String url:List.of("file:///etc/passwd","javascript:alert(1)","data:text/html,test","https://user:password@example.org/x","https:///x"))refuse(()->MediaPlaylist.validate(url),"reject nonmedia URL");refuse(()->new MediaPlaylist.Entry("https://example.org/a","bad",0),"reject zero duration");
        var book=new CallBook();var c=book.invite("a","b",100);check(book.find("a").id().equals(book.find("b").id()),"both endpoints share call");check(c.other("a").equals("b"),"peer resolved");refuse(()->book.invite("a","c",110),"caller is busy");refuse(()->book.invite("c","b",110),"callee is busy");refuse(()->book.answer("a",120),"caller cannot answer own invitation");book.answer("b",120);check(book.find("a").state()==CallBook.State.ACTIVE,"callee accepts call");book.expire(10000);check(book.find("b")!=null,"active call does not expire like ringing");book.hangup("b");check(book.all().isEmpty(),"either party hangs up");book.invite("a","b",0);book.expire(600);check(book.all().isEmpty(),"ringing timeout");refuse(()->book.invite("a","a",0),"self call forbidden");
        MediaHardware.init();var phone=new Device(MediaHardware.PHONE);MediaHardware.initialize(phone);var pbx=new Device(MediaHardware.PBX);MediaHardware.initialize(pbx);var stb=new Device(MediaHardware.STB);MediaHardware.initialize(stb);var tv=new Device(MediaHardware.TV);MediaHardware.initialize(tv);check(DeviceKinds.console(phone.kind)&&DeviceKinds.memory(phone.kind),"phones support console and CISCO memory");check(!DeviceKinds.router(pbx.kind),"PBX is endpoint, not a router");check(CableKind.compatible("hdmi",stb,2,tv,0),"video cable fits intended ports");check(!CableKind.compatible("copper",stb,2,tv,0),"Ethernet cannot replace HDMI");check(CableKind.compatible("console",new Device("pc"),1,pbx,1),"PBX console is reachable with real console cable");
        var net=new Network();var sw=new Device("switch");phone.ports.getFirst().ip="192.168.60.20";pbx.ports.getFirst().ip="192.168.60.10";net.devices.put(1L,phone);net.devices.put(2L,sw);net.devices.put(3L,pbx);net.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(2,0),4));net.wires.add(new Network.Wire(new Network.Endpoint(3,0),new Network.Endpoint(2,1),4));byte[] voice={1,5,9,12};var packet=net.transmit(1,"192.168.60.10","udp",16384,voice);check(packet.result().success()&&Arrays.equals(packet.payload(),voice),"actual UDP voice payload crosses switching model");check(packet.result().path().contains(2L),"voice travels through switch");pbx.ports.getFirst().up=false;check(!net.transmit(1,"192.168.60.10","udp",5060,voice).result().success(),"shutdown blocks registration");
        var handset=new HandsetConfiguration();handset.install("telephony",new HandsetConfiguration.Installed("CISCO-POCKET-SIP",null));check(handset.has("telephony"),"Pocket accepts independent telephone module");handset.eject("telephony");check(!handset.has("telephony"),"removing phone module removes capability");
        return checks;
    }
}

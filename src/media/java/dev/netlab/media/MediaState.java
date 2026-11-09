package dev.netlab.media;
import com.google.gson.*;
import dev.netlab.core.*;
import dev.netlab.media.core.*;
import java.util.*;

/** Private configuration belongs to the CISCO card. Ownership stays physical. */
public final class MediaState {
    public static final String KEY="netlabmedia";
    public static final Gson JSON=new Gson();
    public static final class Channel {public String name="",owner="";public Set<String> editors=new LinkedHashSet<>();public List<MediaPlaylist.Entry> playlist=new ArrayList<>();public boolean loop=true,running;public long started;}
    public String owner="",admin="",server="",gateway="",number="",password="";
    public boolean enabled=true,speakers=true;public int channel=1;public double volume=.7;public Map<Integer,Channel> channels=new TreeMap<>();public Map<String,String> subscribers=new TreeMap<>();
    public static MediaState get(Device d){var s=d.extensions.containsKey(KEY)?JSON.fromJson(d.extensions.get(KEY),MediaState.class):initial(d);if(s.channels==null)s.channels=new TreeMap<>();if(s.subscribers==null)s.subscribers=new TreeMap<>();return s;}
    public static MediaState initial(Device d){var s=new MediaState();s.speakers=!Set.of(MediaHardware.PHONE,"pc").contains(d.kind);return s;}
    public void save(Device d){d.extensions.put(KEY,JSON.toJson(this));}
    public boolean owns(UUID player){return owner.isEmpty()||owner.equals(player.toString());}
    public static void physical(Device next,Device old){if(old.extensions.containsKey(KEY)){var s=get(next);s.owner=get(old).owner;s.save(next);}}
    public static void redact(Device d){if(!d.extensions.containsKey(KEY))return;var s=get(d);s.admin="";s.password="";s.subscribers.clear();s.channels.clear();s.save(d);}
    public static void password(String text){if(text.length()<8||text.length()>64||text.chars().anyMatch(Character::isWhitespace))throw new IllegalArgumentException("Пароль: 8..64 символа без пробелов");}
    public static void number(String text){if(!text.matches("[0-9]{2,12}"))throw new IllegalArgumentException("Номер: 2..12 цифр");}
}

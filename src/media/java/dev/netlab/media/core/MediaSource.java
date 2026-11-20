package dev.netlab.media.core;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Recognizes supported public links and constructs player locations from validated identifiers. */
public record MediaSource(Kind kind, String id, String location) {
    public static final String PLAYER_HOST = "netlab.media";
    public static final String PLAYER_URL = "https://" + PLAYER_HOST + "/player";

    public enum Kind {
        YOUTUBE("YouTube"), RUTUBE("RUTUBE"), VK("VK Видео"), TWITCH_VIDEO("Twitch · запись"),
        TWITCH_LIVE("Twitch · эфир"), TWITCH_CLIP("Twitch · клип"), DIRECT("Прямое видео");

        public final String label;
        Kind(String label) { this.label = label; }
    }

    private static final Set<String> YOUTUBE = Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "www.youtube-nocookie.com", "youtube-nocookie.com", "youtu.be");
    private static final Set<String> VK = Set.of("vkvideo.ru", "www.vkvideo.ru", "vk.com", "www.vk.com", "m.vk.com", "vk.ru", "www.vk.ru");
    private static final Set<String> TWITCH = Set.of("twitch.tv", "www.twitch.tv", "m.twitch.tv", "clips.twitch.tv", "player.twitch.tv");
    private static final Set<String> TWITCH_RESERVED = Set.of("directory", "videos", "downloads", "settings", "subscriptions", "inventory", "drops", "search", "turbo", "wallet", "login");
    private static final Pattern VK_PATH = Pattern.compile("/(?:video|clip)(-?[0-9]{1,19})_([0-9]{1,19})/?");

    public static MediaSource parse(String text) {
        String url = MediaPlaylist.validate(text);
        URI uri = URI.create(url);
        if (uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443)
            throw new IllegalArgumentException("Интернет-шлюз принимает веб-порты 80 и 443");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath();
        String[] parts = Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toArray(String[]::new);
        if (YOUTUBE.contains(host)) {
            String id = host.equals("youtu.be") && parts.length == 1 ? parts[0]
                    : parts.length == 2 && Set.of("embed", "shorts", "live").contains(parts[0]) ? parts[1]
                    : path.equals("/watch") ? query(uri, "v") : "";
            return identified(Kind.YOUTUBE, id, "[A-Za-z0-9_-]{11}", "");
        }
        if (Set.of("rutube.ru", "www.rutube.ru").contains(host)) {
            String id = parts.length == 2 && parts[0].equals("video") ? parts[1]
                    : parts.length == 3 && parts[0].equals("play") && parts[1].equals("embed") ? parts[2] : "";
            return identified(Kind.RUTUBE, id, "[A-Fa-f0-9]{32}", "https://rutube.ru/play/embed/");
        }
        if (VK.contains(host)) return vk(uri);
        if (TWITCH.contains(host)) {
            if (host.equals("clips.twitch.tv")) {
                String id = path.equals("/embed") ? query(uri, "clip") : parts.length == 1 ? parts[0] : "";
                return identified(Kind.TWITCH_CLIP, id, "[A-Za-z0-9_-]{1,128}", "https://clips.twitch.tv/embed?clip=");
            }
            if (host.equals("player.twitch.tv")) {
                String video = query(uri, "video");
                if (!video.isEmpty()) return identified(Kind.TWITCH_VIDEO, video.replaceFirst("^v", ""), "[0-9]{1,20}", "");
                return twitchLive(query(uri, "channel"));
            }
            if (parts.length == 2 && parts[0].equals("videos")) return identified(Kind.TWITCH_VIDEO, parts[1], "[0-9]{1,20}", "");
            if (parts.length == 3 && parts[1].equals("clip")) return identified(Kind.TWITCH_CLIP, parts[2], "[A-Za-z0-9_-]{1,128}", "https://clips.twitch.tv/embed?clip=");
            return twitchLive(parts.length == 1 ? parts[0] : "");
        }
        return new MediaSource(Kind.DIRECT, "", url);
    }

    private static MediaSource vk(URI uri) {
        String source = "https://vkvideo.ru/video_ext.php";
        if (uri.getPath().equals("/video_ext.php")) {
            String owner = query(uri, "oid"), id = query(uri, "id"), hash = query(uri, "hash");
            if (!owner.matches("-?[0-9]{1,19}") || !id.matches("[0-9]{1,19}") || !hash.matches("[A-Za-z0-9_-]{0,128}")) throw invalid(Kind.VK);
            return new MediaSource(Kind.VK, id, source + "?oid=" + owner + "&id=" + id + (hash.isEmpty() ? "" : "&hash=" + hash));
        }
        var match = VK_PATH.matcher(uri.getPath());
        if (!match.matches()) match = VK_PATH.matcher("/" + query(uri, "z").split("%2[Ff]|/", 2)[0]);
        if (!match.matches()) throw invalid(Kind.VK);
        return new MediaSource(Kind.VK, match.group(2), source + "?oid=" + match.group(1) + "&id=" + match.group(2));
    }

    private static MediaSource twitchLive(String id) {
        id = id.toLowerCase(Locale.ROOT);
        if (TWITCH_RESERVED.contains(id)) throw invalid(Kind.TWITCH_LIVE);
        return identified(Kind.TWITCH_LIVE, id, "[a-z0-9_]{1,25}", "");
    }

    private static MediaSource identified(Kind kind, String id, String pattern, String prefix) {
        if (!id.matches(pattern)) throw invalid(kind);
        return new MediaSource(kind, id, prefix.isEmpty() ? "" : prefix + id);
    }

    private static String query(URI uri, String key) {
        if (uri.getRawQuery() != null) for (String pair : uri.getRawQuery().split("&")) {
            if (pair.startsWith(key + "=")) return pair.substring(key.length() + 1);
        }
        return "";
    }

    private static IllegalArgumentException invalid(Kind kind) {
        return new IllegalArgumentException("Нужна ссылка на видео или трансляцию: " + kind.label);
    }
}

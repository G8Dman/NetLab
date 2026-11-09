package dev.netlab.client;
import dev.netlab.core.GuideSearch;
import net.minecraft.network.chat.Component;
/** Highlight before line wrapping so every occurrence and wrapped fragment keeps its style. */
public final class GuideSearchText {
    public static Component highlight(String text,String query){
        var out=Component.empty();int cursor=0;
        for(var match:GuideSearch.matches(text,query)){if(match.start()>cursor)out.append(Component.literal(text.substring(cursor,match.start())));out.append(Component.literal(text.substring(match.start(),match.end())).withStyle(s->s.withColor(0x9b2400).withBold(true).withUnderlined(true)));cursor=match.end();}
        if(cursor<text.length())out.append(Component.literal(text.substring(cursor)));return out;
    }
    private GuideSearchText(){}
}

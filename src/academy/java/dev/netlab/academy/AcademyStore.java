package dev.netlab.academy;
import com.google.gson.Gson;
import dev.netlab.StoredJson;
import dev.netlab.academy.core.Course;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
/** Progress remains even when optional advancement JSON disappears from the datapack. */
public final class AcademyStore extends SavedData {
    private static final Gson JSON=new Gson();
    public Map<String,Course.Progress> players=new LinkedHashMap<>();
    public Course.Progress player(UUID id){return players.computeIfAbsent(id.toString(),k->new Course.Progress());}
    public static AcademyStore get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(AcademyStore::new,(tag,registries)->{var d=new AcademyStore();if(tag.contains("course")){var old=JSON.fromJson(StoredJson.get(tag,"course"),AcademyStore.class);d.players=old.players;}return d;},null),"netlab_academy");}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){StoredJson.put(tag,"course",JSON.toJson(this));return tag;}
}

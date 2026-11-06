import dev.netlab.*;
import dev.netlab.core.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.nio.file.*;
import java.util.*;

/** Actual recipe codecs/matching and survival reachability. No Level or Minecraft process. */
public final class RecipeCoverageRuntimeCheck {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    record Entry(String name,Recipe<?> recipe,JsonObject data,ItemStack output,CraftingInput input){}
    static final List<Entry> entries=new ArrayList<>();
    static CraftingInput input(JsonObject data){
        var stacks=new ArrayList<ItemStack>();int width=3,height=3;
        if(data.has("pattern")){var pattern=data.getAsJsonArray("pattern");height=pattern.size();width=pattern.get(0).getAsString().length();
            for(var row:pattern)for(char c:row.getAsString().toCharArray())stacks.add(c==' '?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.getAsJsonObject("key").getAsJsonObject(""+c).get("item").getAsString()))));
        }else{for(var item:data.getAsJsonArray("ingredients"))stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.getAsJsonObject().get("item").getAsString()))));while(stacks.size()<9)stacks.add(ItemStack.EMPTY);}
        return CraftingInput.of(width,height,stacks);
    }
    static List<String> ingredients(JsonObject data){var result=new ArrayList<String>();if(data.has("key"))for(var e:data.getAsJsonObject("key").entrySet())result.add(e.getValue().getAsJsonObject().get("item").getAsString());else if(data.has("ingredients"))for(var e:data.getAsJsonArray("ingredients"))result.add(e.getAsJsonObject().get("item").getAsString());else result.add(data.getAsJsonObject("ingredient").get("item").getAsString());return result;}
    public static void main(String[] args)throws Exception{
        SputnikRuntimeCheck.main(args);var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);var ops=RegistryOps.create(JsonOps.INSTANCE,registries);
        for(String addon:List.of("main","automation","mobile","sputnik")){String namespace=addon.equals("main")?"netlab":"netlab"+addon;var root=Path.of("src",addon,"resources/data",namespace,"recipe");
            try(var files=Files.list(root)){for(var file:files.filter(f->f.toString().endsWith(".json")).sorted().toList()){
                var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var recipe=Recipe.CODEC.parse(ops,data).getOrThrow();var out=recipe.getResultItem(registries);check(!out.isEmpty(),"real recipe result: "+file);check(out.getCount()<=out.getMaxStackSize(),"recipe output fits an actual item stack: "+file);
                for(String ingredient:ingredients(data)){check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(ingredient)),"registered ingredient "+ingredient);check(ingredient.startsWith("minecraft:")||ingredient.startsWith("netlab:")||ingredient.startsWith(namespace+":"),"addon recipe works without other optional addons: "+file);}
                CraftingInput in=null;if(recipe instanceof CraftingRecipe crafting){in=input(data);check(crafting.matches(in,null),"advertised grid matches "+file);var result=crafting.assemble(in,registries);check(result.is(out.getItem())&&result.getCount()==out.getCount(),"exact item and quantity "+file);
                    var missing=new ArrayList<>(in.items());for(int i=0;i<missing.size();i++)if(!missing.get(i).isEmpty()){missing.set(i,ItemStack.EMPTY);break;}check(!crafting.matches(CraftingInput.of(in.width(),in.height(),missing),null),"missing material rejects craft "+file);
                }
                entries.add(new Entry(file.toString(),recipe,data,out,in));
            }}
        }
        for(var a:entries)if(a.input()!=null)for(var b:entries)if(a!=b&&b.recipe() instanceof CraftingRecipe other&&other.matches(a.input(),null))check(a.output().is(b.output().getItem())&&a.output().getCount()==b.output().getCount(),"unambiguous recipe grid: "+a.name()+" vs "+b.name());
        var available=new HashSet<String>();for(var id:BuiltInRegistries.ITEM.keySet())if(id.getNamespace().equals("minecraft"))available.add(id.toString());available.addAll(List.of("netlab:ciscium_ore","netlab:deepslate_ciscium_ore","netlab:raw_ciscium"));
        int previous;do{previous=available.size();for(var e:entries)if(available.containsAll(ingredients(e.data())))available.add(BuiltInRegistries.ITEM.getKey(e.output().getItem()).toString());
            if(available.containsAll(List.of("netlab:module_assembler","netlab:basic_circuit_board","minecraft:copper_ingot","minecraft:redstone")))for(String id:ModuleFabrication.MODULES)available.add("netlab:"+ModuleItem.itemId(id));
            if(available.containsAll(List.of("netlabmobile:sim_station","netlabmobile:blank_sim")))available.add("netlabmobile:sim_card");
        }while(previous!=available.size());
        int items=0;for(var id:BuiltInRegistries.ITEM.keySet())if(Set.of("netlab","netlabautomation","netlabmobile","netlabsputnik").contains(id.getNamespace())){
            if(id.toString().equals("netlab:creative_energy_source")||id.toString().equals("netlab:wall_power_cable"))continue;
            check(available.contains(id.toString()),"survival crafting/production chain reaches "+id);items++;
        }
        check(entries.stream().noneMatch(e->BuiltInRegistries.ITEM.getKey(e.output().getItem()).toString().equals("netlab:creative_energy_source")),"intentional creative generator remains without survival recipe");
        check(entries.stream().noneMatch(e->BuiltInRegistries.ITEM.getKey(e.output().getItem()).toString().equals("netlabmobile:sim_card")),"authenticated SIM cannot be forged on a crafting table");
        int cables=0;for(var item:BuiltInRegistries.ITEM)if(item instanceof RoutedCableItem&&!BuiltInRegistries.ITEM.getKey(item).getPath().equals("wall_power_cable")){check(entries.stream().anyMatch(e->e.output().is(item)),"every physical cable has a real crafting recipe: "+BuiltInRegistries.ITEM.getKey(item));cables++;}check(cables==15,"all fifteen physical cable types are covered across addons");
        System.out.println(checks+" recipe checks passed: "+entries.size()+" recipes; "+items+" survival-obtainable items across four mods. No game launched.");
    }
}

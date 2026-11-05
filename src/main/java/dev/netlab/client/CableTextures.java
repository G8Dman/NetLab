package dev.netlab.client;

import dev.netlab.NetLab;
import dev.netlab.core.CableKind;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Grayscale jackets keep user-applied dye colors while adding type-specific surface detail. */
public final class CableTextures {
    private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
    static {for(String kind:CableKind.TYPES)TEXTURES.put(kind,ResourceLocation.fromNamespaceAndPath(NetLab.ID,"textures/cable/"+kind+".png"));TEXTURES.put("power",ResourceLocation.fromNamespaceAndPath(NetLab.ID,"textures/cable/power.png"));}
    public static ResourceLocation texture(String kind){return TEXTURES.getOrDefault(kind,TEXTURES.get("copper"));}
    public static void register(String kind,ResourceLocation texture){TEXTURES.put(kind,texture);}
    public static double radius(String kind){return switch(kind){case "fiber"->.013;case "power","serial_dce","serial_dte"->.021;case "phone"->.014;default->.018;};}
}

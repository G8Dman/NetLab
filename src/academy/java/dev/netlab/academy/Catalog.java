package dev.netlab.academy;
import com.google.gson.*;
import dev.netlab.academy.core.Course;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class Catalog {
    public static final Course COURSE=load();
    private static Course load(){try(var in=Catalog.class.getResourceAsStream("/assets/netlabacademy/catalog.json")){if(in==null)throw new IOException("Missing catalog");var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();var gson=new Gson();return new Course(Arrays.asList(gson.fromJson(root.get("entries"),Course.Task[].class)),Arrays.asList(gson.fromJson(root.get("main"),String[].class)));}catch(IOException error){throw new ExceptionInInitializerError(error);}}
    private Catalog(){}
}

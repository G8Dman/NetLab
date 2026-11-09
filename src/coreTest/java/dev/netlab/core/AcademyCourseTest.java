package dev.netlab.core;
import dev.netlab.academy.core.Course;
import java.util.*;
public final class AcademyCourseTest {
    static int checks;static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    public static int run(){checks=0;
        var a=new Course.Task("B01","base/b01","Start","Read",List.of(),List.of("netlab"),"base","Base","Guide",true);
        var b=new Course.Task("A01","automation/a01","Automation","Transfer",List.of("B01"),List.of("netlab","netlabautomation"),"automation","Automation","Guide",false);
        var course=new Course(List.of(a,b),List.of("B01"));var policy=new Course.Policy(false,false,false,false);var p=new Course.Progress();
        p.prove("A01");check(course.advance(p,Set.of("netlab","netlabautomation"),policy).isEmpty(),"facts cannot bypass prerequisites");
        p.prove("B01");check(course.advance(p,Set.of("netlab"),policy).size()==1,"missing addon never awards its task");check(!p.completed.contains("A01"),"absent branch has no completion");
        check(course.advance(p,Set.of("netlab","netlabautomation"),policy).size()==1,"reinstalled branch reuses saved evidence");check(course.advance(p,Set.of("netlab","netlabautomation"),policy).isEmpty(),"same evidence never awards twice");
        course.advance(p,Set.of("netlab"),policy);check(p.completed.contains("A01"),"removing addon retains old badge");
        try{new Course(List.of(a,a),List.of());throw new AssertionError("duplicate accepted");}catch(IllegalArgumentException expected){checks++;}
        var cycle=new Course.Task("B01","base/b01","Cycle","Bad",List.of("A01"),List.of("netlab"),"base","Base","Guide",true);
        try{new Course(List.of(cycle,b),List.of());throw new AssertionError("cycle accepted");}catch(IllegalArgumentException expected){checks++;}
        check(GuideSearch.matches("VLAN vlan VlAn","vLaN").equals(List.of(new GuideSearch.Match(0,4),new GuideSearch.Match(5,9),new GuideSearch.Match(10,14))),"all occurrences, case insensitive");
        check(GuideSearch.matches("КОНСОЛЬ и консоль","Консоль").size()==2,"Russian case insensitive");
        check(GuideSearch.matches("🙂 VLAN 🙂 VLAN","vlan").equals(List.of(new GuideSearch.Match(3,7),new GuideSearch.Match(11,15))),"UTF16 offsets preserve non-BMP text");
        check(GuideSearch.matches("a.* [x] a.*","a.*").size()==2,"queries are literal, never regex");
        check(GuideSearch.matches("aaaa","aa").size()==2,"nonoverlapping highlights");
        check(GuideSearch.matches("hello","").isEmpty()&&GuideSearch.matches("hello","  ").isEmpty(),"empty search adds no highlights");
        check(GuideSearch.contains("Адрес шлюза"," ШЛЮЗ ")&&!GuideSearch.contains("Адрес","шлюз"),"query whitespace normalized");
        return checks;
    }
}

package dev.netlab.academy.core;

import java.util.*;

/** No Minecraft, Gson, optional addon classes, wall clock or client-provided completions. */
public final class Course {
    public record Task(String id,String path,String title,String task,List<String> parents,List<String> mods,String group,String groupTitle,String guide,boolean main){}
    public record Policy(boolean external,boolean voice,boolean fluids,boolean ae2){}
    public static final class Progress {
        public Set<String> completed=new LinkedHashSet<>(),evidence=new LinkedHashSet<>(),facts=new LinkedHashSet<>();
        public Map<String,String> values=new LinkedHashMap<>();
        public boolean fact(String key){return facts.contains(key);}
        public void fact(String key,boolean yes){if(yes&&facts.size()<8192)facts.add(key);}
        public void prove(String id){evidence.add(id);}
    }
    private final Map<String,Task> tasks=new LinkedHashMap<>();
    public final List<String> main;
    public Course(List<Task> input,List<String> main){
        for(var t:input)if(tasks.put(t.id(),t)!=null)throw new IllegalArgumentException("Duplicate task: "+t.id());
        for(var t:input){for(var p:t.parents())if(!tasks.containsKey(p))throw new IllegalArgumentException("Unknown parent: "+p);visit(t.id(),new HashSet<>(),new HashSet<>());}
        for(String id:main)if(!tasks.containsKey(id))throw new IllegalArgumentException("Unknown main task: "+id);this.main=List.copyOf(main);
    }
    private void visit(String id,Set<String> path,Set<String> done){if(done.contains(id))return;if(!path.add(id))throw new IllegalArgumentException("Course cycle: "+id);for(String p:tasks.get(id).parents())visit(p,path,done);path.remove(id);done.add(id);}
    public List<Task> available(Set<String> installed,Policy policy){return tasks.values().stream().filter(t->installed.containsAll(t.mods())&&(!t.id().equals("E03")||policy.ae2())).toList();}
    public List<String> goals(Set<String> installed,Policy policy){
        var out=new ArrayList<String>(List.of("F01"));
        if(installed.contains("netlabautomation")){out.addAll(List.of("A11","A15"));if(policy.fluids())out.add("A20");}
        if(installed.contains("netlabmobile"))out.add("M11");
        if(installed.contains("netlabsputnik"))out.add(policy.external()?"S12":"S11");
        if(installed.containsAll(Set.of("netlabmedia","netlabsputnik","voicechat"))){out.add(policy.external()?"T10":"T09");out.add(policy.voice()?"V09":"V06");}
        return List.copyOf(out);
    }
    public List<Task> advance(Progress p,Set<String> installed,Policy policy){
        var visible=available(installed,policy);var active=new HashSet<String>();visible.forEach(t->active.add(t.id()));
        var earned=new ArrayList<Task>();boolean changed;
        do{changed=false;for(var t:visible){
            if(p.completed.contains(t.id())||!p.completed.containsAll(t.parents())||!active.containsAll(t.parents()))continue;
            boolean proof=switch(t.id()){case "F01"->p.completed.containsAll(main);case "F03"->p.completed.containsAll(goals(installed,policy));default->p.evidence.contains(t.id());};
            if(proof){p.completed.add(t.id());earned.add(t);changed=true;}
        }}while(changed);
        return List.copyOf(earned);
    }
    public boolean currentGoalsComplete(Progress p,Set<String> installed,Policy policy){return p.completed.containsAll(goals(installed,policy));}
    public Task task(String id){return tasks.get(id);}
}

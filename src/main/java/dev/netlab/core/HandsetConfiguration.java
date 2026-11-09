package dev.netlab.core;

import java.util.*;

/** Persistent modules belong to the physical phone; login passwords never do. */
public final class HandsetConfiguration {
    public record Binding(String identity,String address,String dimension) implements java.io.Serializable {
        public Binding {UUID.fromString(identity);Device.ip(address);if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Некорректное измерение сервера");}
    }
    public record Installed(String code,Binding binding) implements java.io.Serializable {}
    public static final List<String> SLOTS=List.of("diagnostics","browser","automation","terminal","telephony");
    public Map<String,Installed> modules=new LinkedHashMap<>();
    public long epoch;
    public boolean has(String slot){return modules!=null&&modules.containsKey(slot);}
    public Binding binding(){return has("automation")?modules.get("automation").binding():null;}
    public boolean boundTo(String identity,String dimension){var b=binding();return b!=null&&b.identity().equals(identity)&&b.dimension().equals(dimension);}
    public void install(String slot,Installed module){
        if(!SLOTS.contains(slot)||module==null||module.code()==null||module.code().isBlank())throw new IllegalArgumentException("Неизвестный модуль телефона");
        if(has(slot))throw new IllegalArgumentException("Сначала извлеки установленный модуль");
        if(slot.equals("automation")&&module.binding()==null)throw new IllegalArgumentException("Сначала привяжи модуль к серверу: ПКМ модулем по серверу");
        if(modules==null)modules=new LinkedHashMap<>();modules.put(slot,module);epoch++;
    }
    public Installed eject(String slot){if(!has(slot))throw new IllegalArgumentException("Модуль не установлен");epoch++;return modules.remove(slot);}
}

package dev.netlab.automation.core;
import java.util.*;
public final class AutomationProgram {
    public static final String ANY_ITEM="*";
    public record Stock(String source,String destination,String item,int sourceSlot,int destinationSlot,int minimum,int batch){
        public Stock {if(minimum<1||minimum>4096||batch<1||batch>256||sourceSlot< -1||sourceSlot>255||destinationSlot< -1||destinationSlot>255)throw new IllegalArgumentException("Слот: -1..255; минимум: 1..4096; партия: 1..256");}
        public boolean anyItem(){return ANY_ITEM.equals(item);}
    }
    public record Available(int slot,String item,int extractable){}
    public record Dispatch(String item,int amount,int sourceSlot,int destinationSlot){}
    /** A drain rule selects an actual extractable stack, without a destination stock threshold. */
    public static Optional<Dispatch> drain(Stock rule,List<Available> inventory){
        if(!rule.anyItem())throw new IllegalArgumentException("Нужно правило «Любой предмет»");
        for(var cell:inventory){
            if(cell.slot()<0||cell.slot()>255||rule.sourceSlot()>=0&&cell.slot()!=rule.sourceSlot()||cell.extractable()<=0||cell.item()==null||cell.item().equals("minecraft:air")||!cell.item().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))continue;
            return Optional.of(new Dispatch(cell.item(),Math.min(rule.batch(),cell.extractable()),cell.slot(),rule.destinationSlot()));
        }
        return Optional.empty();
    }
    public record Pattern(String name,String source,String crafter,String destination,String output,int minimum,List<String> grid){
        public Pattern {grid=List.copyOf(grid);if(name.isBlank()||name.length()>40||minimum<1||minimum>4096||grid.size()!=9||grid.stream().allMatch(x->x.equals("-")))throw new IllegalArgumentException("Имя до 40 символов, минимум 1..4096, сетка из 9 предметов (- = пусто)");}
    }
}

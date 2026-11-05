package dev.netlab.core;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Deterministic delivery queue; advancing simulation time is the only way to complete future work. */
public final class TimedQueue {
    private record Task(long due,long order,Runnable action){}
    private final PriorityQueue<Task> queue=new PriorityQueue<>(Comparator.comparingLong(Task::due).thenComparingLong(Task::order));
    private long sequence;
    public <T> CompletableFuture<T> submit(long now,long due,Supplier<T> action){
        var result=new CompletableFuture<T>();
        if(queue.size()>=16384||due-now>3_600_000_000_000L){result.completeExceptionally(new IllegalArgumentException("Очередь заполнена или задержка превышает час"));return result;}
        Runnable run=()->{try{result.complete(action.get());}catch(RuntimeException e){result.completeExceptionally(e);}};
        if(due<=now)run.run();else queue.add(new Task(due,sequence++,run));return result;
    }
    public void advance(long now,int budget){int work=0;while(!queue.isEmpty()&&queue.peek().due<=now&&work++<budget)queue.remove().action.run();}
}

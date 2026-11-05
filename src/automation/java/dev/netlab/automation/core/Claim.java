package dev.netlab.automation.core;

/** Counts only. Item stacks live exclusively in the server journal, never in wire manifests. */
public final class Claim {
    private int remaining;private boolean busy;
    public Claim(int count){if(count<0)throw new IllegalArgumentException("Negative count");remaining=count;}
    public int remaining(){return remaining;}
    public boolean begin(){if(busy||remaining==0)return false;busy=true;return true;}
    public void finish(int accepted){if(!busy||accepted<0||accepted>remaining)throw new IllegalStateException("Invalid claim settlement");remaining-=accepted;busy=false;}
    public void abort(){busy=false;}
}

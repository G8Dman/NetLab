package dev.netlab.core;

public final class CableLedgerTest {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    public static int run(){
        checks=0;var ledger=new CableLedger();var a=new CableLedger.End(10,0,false);var b=new CableLedger.End(20,1,false);var stranger=new CableLedger.End(30,0,false);
        String id=ledger.install(a,b,"netlab:serial_dce_cable",true);
        check(ledger.active(id,a)&&ledger.active(id,b)&&!ledger.active(id,stranger),"only the physical endpoints own a cable");
        check(ledger.claim(id,stranger).isEmpty()&&ledger.active(id,a),"another endpoint cannot refund or invalidate a wire");
        check(ledger.claim(id,b).equals("netlab:serial_dce_cable"),"disconnecting either end returns the consumed item type");
        check(!ledger.active(id,a)&&ledger.claim(id,a).isEmpty()&&ledger.claim(id,b).isEmpty(),"a cable can be refunded only once");
        ledger.acknowledge(id,b);check(ledger.cables.containsKey(id)&&ledger.cables.get(id).pending.equals(java.util.List.of(a)),"unloaded first endpoint keeps a durable removal marker");
        ledger.acknowledge(id,a);check(!ledger.cables.containsKey(id),"fully removed connection releases its ledger entry");
        String next=ledger.install(a,b,"netlab:fiber_cable",true);check(!id.equals(next)&&ledger.claim(next,a).equals("netlab:fiber_cable"),"reusing the same ports creates a different physical cable");
        check(!ledger.active(id,a),"an old unloaded endpoint cannot become the new link");
        String free=ledger.install(a,b,"netlab:ethernet_cable",false);check(ledger.claim(free,a).isEmpty()&&!ledger.active(free,b),"creative placements do not mint refundable survival cables");
        String legacy=CableLedger.legacy(a,b);check(legacy.equals(CableLedger.legacy(b,a)),"legacy link identity is independent of disconnecting side");
        ledger.register(legacy,a,b,"netlab:console_cable",true);check(ledger.claim(legacy,a).equals("netlab:console_cable"),"old-world connections can be recovered once");ledger.acknowledge(legacy,a);
        ledger.register(legacy,b,a,"netlab:console_cable",true);check(ledger.claim(legacy,b).isEmpty(),"loading a stale legacy end cannot recreate a refunded cable");ledger.acknowledge(legacy,b);check(!ledger.cables.containsKey(legacy),"legacy cleanup completes after the delayed endpoint loads");
        check(!CableLedger.legacy(a,b).equals(CableLedger.legacy(new CableLedger.End(10,0,true),b)),"network and power port namespaces cannot share a material claim");
        return checks;
    }
}

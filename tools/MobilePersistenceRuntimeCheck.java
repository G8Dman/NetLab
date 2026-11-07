import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.mobile.*;
import dev.netlab.mobile.core.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.io.*;
import java.util.*;

/** Real item wire/NBT roundtrips and two-player cache boundaries; no world or game startup. */
public final class MobilePersistenceRuntimeCheck {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static MobileItemStorage reload(MobileItemStorage store,RegistryAccess registries)throws Exception{
        var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(store.save(new CompoundTag(),registries),bytes);
        return new MobileItemStorage(NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()),NbtAccounter.unlimitedHeap()));
    }
    public static void main(String[] args)throws Exception{
        PortableDevicesRuntimeCheck.main(args);
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        check(Mobile.SIM.get() instanceof SimCardItem,"registered SIM restores transferred item data on inventory ticks");
        var sim=new SimCredentials();sim.issuer=UUID.randomUUID().toString();sim.operator="90170";sim.label="Transfer SIM";
        var card=MobileItems.sim(sim);var oldOwner=new CompoundTag();ItemConfigurations.reconcile(oldOwner,card);
        var recipient=PortableDevicesRuntimeCheck.wire(card,registries);
        check(MobileItems.sim(recipient)==null,"reproduce creative echo: private SIM payload is absent on client wire");
        ItemConfigurations.reconcile(new CompoundTag(),recipient);
        check(MobileItems.sim(recipient)==null,"reproduce bug: recipient cannot restore SIM from issuer's per-player cache");
        var store=new MobileItemStorage();store.rememberStack(card);recipient=PortableDevicesRuntimeCheck.wire(card,registries);
        check(!recipient.get(DataComponents.CUSTOM_DATA).toString().contains(sim.key),"transfer metadata contains no SIM key");
        store.reconcileStack(recipient);
        check(MobileItems.sim(recipient).id.equals(sim.id)&&MobileItems.sim(recipient).key.equals(sim.key)&&MobileItems.sim(recipient).issuer.equals(sim.issuer),"different holder restores original credentials without issuer's player cache");
        var anotherHolder=PortableDevicesRuntimeCheck.wire(recipient,registries);store=reload(store,registries);store.reconcileStack(anotherHolder);
        check(MobileItems.sim(anotherHolder).id.equals(sim.id)&&MobileItems.sim(anotherHolder).key.equals(sim.key),"world disk reload restores transferred SIM for another holder");
        var ledger=new MobileState().subscribers;ledger.provision(sim);String nonce=MobileAuth.secret();ledger.activate(sim.id,MobileAuth.proof(sim.key,nonce,"activate|"+sim.id),nonce);
        var restoredSim=MobileItems.sim(anotherHolder);nonce=MobileAuth.secret();check(ledger.authenticate(restoredSim,sim.issuer,sim.operator,nonce,MobileAuth.proof(restoredSim.key,nonce,"attach|"+sim.id)),"transferred SIM still authenticates at its original EPC");

        var phone=new ItemStack(Mobile.PHONE.get());String phoneId=MobileItems.phoneId(phone);
        var config=new HandsetConfiguration();config.install("terminal",new HandsetConfiguration.Installed("CISCO-POCKET-CONSOLE",null));PhoneDevices.save(phone,config);
        var history=new PhoneTerminalMemory();String peer=UUID.randomUUID().toString();history.open(peer).remember("show ip route",false);
        var tag=MobileItems.privateTag(phone);StoredJson.put(tag,"terminalHistory",MobileState.JSON.toJson(history));MobileItems.seal(phone,tag);
        store.rememberStack(phone);var beforeInsert=PortableDevicesRuntimeCheck.wire(phone,registries);
        MobileItems.setPhoneSim(phone,sim);store.rememberStack(phone);
        store.reconcileStack(beforeInsert);
        check(MobileItems.sim(beforeInsert).id.equals(sim.id),"stale pre-insert creative echo cannot remove newly inserted SIM");
        var token=phone.get(DataComponents.CUSTOM_DATA).copyTag().getString("netlabMobileItem");
        for(int i=0;i<70;i++){
            var memory=MobileState.JSON.fromJson(StoredJson.get(MobileItems.privateTag(phone),"terminalHistory"),PhoneTerminalMemory.class);
            memory.open(peer).remember("show clock",false);PhoneTerminalMemory.trim(memory.open(peer));
            tag=MobileItems.privateTag(phone);StoredJson.put(tag,"terminalHistory",MobileState.JSON.toJson(memory));MobileItems.seal(phone,tag);store.rememberStack(phone);
        }
        check(phone.get(DataComponents.CUSTOM_DATA).copyTag().getString("netlabMobileItem").equals(token),"handset backing identity remains stable across history changes");
        check(store.save(new CompoundTag(),registries).getCompound("items").getAllKeys().size()==2,"history changes update one phone record, not unbounded private snapshots");
        var echo=PortableDevicesRuntimeCheck.wire(phone,registries);
        var savedInventory=ItemStack.parse(registries,echo.save(registries)).orElseThrow();store=reload(store,registries);store.reconcileStack(savedInventory);
        check(MobileItems.phoneId(savedInventory).equals(phoneId)&&MobileItems.sim(savedInventory).key.equals(sim.key),"logout/login after private payload loss keeps phone identity and installed SIM");
        check(PhoneDevices.configuration(savedInventory).has("terminal")&&MobileItems.privateTag(savedInventory).contains("terminalHistory"),"phone modules and terminal history survive recovery together with SIM");
        check(!echo.get(DataComponents.CUSTOM_DATA).toString().contains(sim.key)&&!echo.get(DataComponents.CUSTOM_DATA).toString().contains("show ip route"),"phone item wire still contains neither authentication key nor terminal transcript");

        var beforeEject=PortableDevicesRuntimeCheck.wire(phone,registries);
        var checkpoint=store.save(new CompoundTag(),registries);MobileItems.setPhoneSim(phone,null);store.rememberStack(phone);store=reload(store,registries);store.reconcileStack(beforeEject);
        check(MobileItems.sim(beforeEject)==null,"stale inserted-SIM echo cannot resurrect an explicitly ejected SIM after world reload");
        check(MobileItems.phoneId(beforeEject).equals(phoneId)&&PhoneDevices.configuration(beforeEject).has("terminal"),"ejection preserves handset identity and installed modules");
        var nativeSave=ItemStack.parse(registries,phone.save(registries)).orElseThrow();var olderVault=new MobileItemStorage(checkpoint);olderVault.reconcileStack(nativeSave);
        check(MobileItems.sim(nativeSave)==null,"newer native player save wins over an older world-vault checkpoint");

        var wrong=new ItemStack(Mobile.PHONE.get());wrong.set(DataComponents.CUSTOM_DATA,card.get(DataComponents.CUSTOM_DATA));store.reconcileStack(wrong);
        check(MobileItems.privateTag(wrong).isEmpty(),"SIM reference cannot restore credentials onto a different item type");
        var unknown=PortableDevicesRuntimeCheck.wire(phone,registries);var missing=new MobileItemStorage();missing.reconcileStack(unknown);
        var labelBefore=unknown.get(DataComponents.CUSTOM_DATA).copyTag();boolean rejected=false;
        try{MobileItems.phoneId(unknown);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected&&unknown.get(DataComponents.CUSTOM_DATA).copyTag().equals(labelBefore),"missing backing data never silently overwrites old phone identity with a blank handset");

        var legacy=MobileItems.sim(sim);var oldLabel=legacy.get(DataComponents.CUSTOM_DATA).copyTag();oldLabel.remove("netlabMobileItem");oldLabel.remove("netlabMobileRevision");legacy.set(DataComponents.CUSTOM_DATA,CustomData.of(oldLabel));
        store.rememberStack(legacy);var legacyEcho=PortableDevicesRuntimeCheck.wire(MobileItems.sim(sim),registries);store.reconcileStack(legacyEcho);
        check(MobileItems.sim(legacyEcho).id.equals(sim.id),"pre-fix fingerprint-only SIM can migrate from an existing world record");
        var upgradingPhone=new ItemStack(Mobile.PHONE.get());String upgradingId=MobileItems.phoneId(upgradingPhone);
        var preUpgradeEcho=PortableDevicesRuntimeCheck.wire(upgradingPhone,registries);var preUpgradeAfterEject=preUpgradeEcho.copy();
        store.rememberStack(upgradingPhone);MobileItems.setPhoneSim(upgradingPhone,sim);store.rememberStack(upgradingPhone);
        store=reload(store,registries);store.reconcileStack(preUpgradeEcho);
        check(MobileItems.phoneId(preUpgradeEcho).equals(upgradingId)&&MobileItems.sim(preUpgradeEcho).id.equals(sim.id),"first pre-upgrade echo without a vault token restores latest state after SIM insertion");
        MobileItems.setPhoneSim(upgradingPhone,null);store.rememberStack(upgradingPhone);store=reload(store,registries);store.reconcileStack(preUpgradeAfterEject);
        check(MobileItems.phoneId(preUpgradeAfterEject).equals(upgradingId)&&MobileItems.sim(preUpgradeAfterEject)==null,"original pre-upgrade fingerprint cannot undo a later ejection");
        System.out.println(checks+" SIM handoff, logout, stale inventory, world-NBT and private-wire checks passed; no game launched.");
    }
}

package dev.netlab;

import dev.netlab.core.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** FE cabinet -> rack PSU -> rear board inputs. All allocation happens on the server thread. */
public final class PowerSystem {
    public record Peer(long pos,int port,String localId,String remoteId,boolean surface,List<WallCablePath.Point> anchors){
        public Peer {anchors=List.copyOf(anchors);}
    }
    public static CompoundTag savePeer(int port,Peer peer){
        var t=new CompoundTag();t.putInt("port",port);t.putLong("target",peer.pos);t.putInt("remote",peer.port);t.putString("localId",peer.localId);t.putString("remoteId",peer.remoteId);
        t.putBoolean("surface",peer.surface);CableRouteNbt.write(t,peer.anchors);return t;
    }
    public static Peer loadPeer(CompoundTag t){
        var anchors=CableRouteNbt.read(t);if(anchors==null)return null;
        return new Peer(t.getLong("target"),t.getInt("remote"),t.getString("localId"),t.getString("remoteId"),t.getBoolean("surface"),t.getBoolean("surface")?anchors:List.of());
    }
    public static void prepare(Device d){if(PowerModel.board(d))Hardware.ensure(d).energyRequired=true;LaptopBattery.prepare(d);}
    public static String identity(DeviceEntity h,int port){if(PowerGeometry.jack(h,port)==null)return "";if(port>=PowerGeometry.BOARD_IN){var d=port==PowerGeometry.BOARD_IN?h.device:h.upperDevice;return d==null?"":d.identity;}return port>=PowerGeometry.PSU_IN?h.rackSupplyId:h.device.identity;}
    public static DeviceEntity peer(DeviceEntity h,int port){var p=h.powerLinks.get(port);if(p==null||h.getLevel()==null)return null;var pos=BlockPos.of(p.pos);if(!h.getLevel().hasChunkAt(pos)||!(h.getLevel().getBlockEntity(pos) instanceof DeviceEntity other)||other.isRemoved())return null;var back=other.powerLinks.get(p.port);return back!=null&&back.pos==h.getBlockPos().asLong()&&back.port==port&&identity(h,port).equals(p.localId)&&identity(other,p.port).equals(p.remoteId)&&back.localId.equals(p.remoteId)&&back.remoteId.equals(p.localId)?other:null;}
    public static String connect(DeviceEntity a,int ap,DeviceEntity b,int bp){
        return connect(a,ap,b,bp,List.of());
    }
    /** A non-null anchor list selects the fixed surface cable, including a route with no intermediate anchors. */
    public static String connect(DeviceEntity a,int ap,DeviceEntity b,int bp,List<WallCablePath.Point> anchors){
        var x=PowerGeometry.jack(a,ap);var y=PowerGeometry.jack(b,bp);if(x==null||y==null)return "Разъём питания отсутствует";
        if(a.getLevel()!=b.getLevel()||a==b&&ap==bp)return "Выбери два разных разъёма";
        if(a.getBlockPos().distSqr(b.getBlockPos())>Math.pow(NetLab.MAX_LENGTH.get(),2))return "Энергокабель слишком длинный";
        if(anchors!=null&&(anchors.size()>WallCablePath.MAX_ANCHORS||!WallCablePath.valid(PowerGeometry.fixedRoute(a,ap,b,bp,anchors),NetLab.MAX_LENGTH.get())))return "Маршрут энергокабеля слишком длинный или содержит слишком много точек";
        boolean valid=ap>=300&&ap<304&&bp==310||bp>=300&&bp<304&&ap==310||ap>=320&&ap<328&&bp>=512&&bp<=513||bp>=320&&bp<328&&ap>=512&&ap<=513;
        valid|=ap>=300&&ap<304&&bp==512&&LaptopBattery.laptop(b.device)||bp>=300&&bp<304&&ap==512&&LaptopBattery.laptop(a.device);
        if(!valid||x.output()==y.output())return "Схема: OUT энергошкафа → IN блока питания или DC ноутбука; OUT блока питания → питание устройства";
        if(a.powerLinks.containsKey(ap)||b.powerLinks.containsKey(bp))return "Разъём занят. Shift+ПКМ энергокабелем — отключить";
        String aid=identity(a,ap),bid=identity(b,bp);boolean surface=anchors!=null;var points=surface?List.copyOf(anchors):List.<WallCablePath.Point>of();
        a.powerLinks.put(ap,new Peer(b.getBlockPos().asLong(),bp,aid,bid,surface,points));b.powerLinks.put(bp,new Peer(a.getBlockPos().asLong(),ap,bid,aid,surface,points.reversed()));a.changed();b.changed();return "Кабель питания подключён; креплений: "+points.size();
    }
    public static void disconnect(DeviceEntity h,int port){var p=h.powerLinks.remove(port);if(p!=null&&h.getLevel()!=null&&h.getLevel().hasChunkAt(BlockPos.of(p.pos))&&h.getLevel().getBlockEntity(BlockPos.of(p.pos)) instanceof DeviceEntity other){var back=other.powerLinks.get(p.port);if(back!=null&&back.pos==h.getBlockPos().asLong()&&back.port==port){other.powerLinks.remove(p.port);other.changed();}}h.changed();}
    public static void disconnectAll(DeviceEntity h){for(int port:new ArrayList<>(h.powerLinks.keySet()))disconnect(h,port);}
    public static boolean removeSupply(DeviceEntity h){if(!h.rackSupply)return false;for(int port:new ArrayList<>(h.powerLinks.keySet()))if(port>=310&&port<328)disconnect(h,port);h.rackSupply=false;h.rackPowered=false;h.rackSupplyId="";h.changed();return true;}
    public static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,NetLab.DEVICE.get(),(h,side)->Set.of("energy","energy_source").contains(h.device.kind)||LaptopBattery.laptop(h.device)?storage(h):null);}
    private static IEnergyStorage storage(DeviceEntity h){return new IEnergyStorage(){
        public int receiveEnergy(int amount,boolean simulate){if(!canReceive()||h.getLevel()==null||h.getLevel().isClientSide)return 0;long now=h.getLevel().getGameTime();if(h.energyReceivedTick!=now){h.energyReceivedTick=now;h.energyReceived=0;}boolean laptop=LaptopBattery.laptop(h.device);int value=laptop?LaptopBattery.receive(h.device,amount,LaptopBattery.CHARGE_RATE-h.energyReceived,true):PowerModel.receive(h.energy,amount,PowerModel.INPUT_RATE-h.energyReceived);if(!simulate&&value>0){if(laptop)LaptopBattery.receive(h.device,value,LaptopBattery.CHARGE_RATE-h.energyReceived,false);else h.energy+=value;h.energyReceived+=value;h.setChanged();}return value;}
        public int extractEnergy(int amount,boolean simulate){return canExtract()?Math.max(0,amount):0;}
        public int getEnergyStored(){return canExtract()?Integer.MAX_VALUE:LaptopBattery.laptop(h.device)?LaptopBattery.stored(h.device):h.energy;}public int getMaxEnergyStored(){return canExtract()?Integer.MAX_VALUE:LaptopBattery.laptop(h.device)?LaptopBattery.CAPACITY:PowerModel.CAPACITY;}
        public boolean canExtract(){return !h.isRemoved()&&h.device.kind.equals("energy_source");}public boolean canReceive(){return !h.isRemoved()&&(h.device.kind.equals("energy")||LaptopBattery.laptop(h.device));}
    };}
    private static void state(DeviceEntity host,Device d,boolean supplied){if(!PowerModel.board(d))return;var h=Hardware.ensure(d);boolean old=Hardware.powered(d);h.energyRequired=true;h.energySupplied=supplied;if(old!=Hardware.powered(d)){h.epoch++;host.changed();}}
    public static void attached(DeviceEntity host,int bay){var d=bay==0?host.device:host.upperDevice;if(d!=null)state(host,d,false);}
    public static void initialize(DeviceEntity host){for(var d:Arrays.asList(host.device,host.upperDevice))if(d!=null){prepare(d);if(host.getLevel()!=null&&!host.getLevel().isClientSide)state(host,d,false);}if(host.getLevel()!=null&&!host.getLevel().isClientSide)host.rackPowered=false;}
    public static void tick(ServerTickEvent.Pre event){for(var level:event.getServer().getAllLevels()){
        var loaded=DeviceEntity.loaded(level);
        for(var source:loaded)if(source.device.kind.equals("energy_source"))for(var side:Direction.values()){var pos=source.getBlockPos().relative(side);if(level.hasChunkAt(pos)){var cap=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,side.getOpposite());if(cap!=null&&cap.canReceive())cap.receiveEnergy(Integer.MAX_VALUE,false);}}
        var budgets=new HashMap<DeviceEntity,PowerModel.Budget>();for(var h:loaded)if(h.device.kind.equals("energy"))budgets.put(h,new PowerModel.Budget(h.energy));
        var supplies=new HashMap<DeviceEntity,DeviceEntity>();for(var h:loaded)if(h.rackSupply){var bank=peer(h,310);boolean on=bank!=null&&bank.device.kind.equals("energy")&&bank.energy>0;if(on)supplies.put(h,bank);if(h.rackPowered!=on){h.rackPowered=on;h.changed();}}
        var boards=new ArrayList<Map.Entry<DeviceEntity,Integer>>();for(var h:loaded)for(int bay=0;bay<2;bay++){var d=bay==0?h.device:h.upperDevice;if(PowerModel.board(d))boards.add(Map.entry(h,bay));}
        boards.sort(Comparator.comparingLong((Map.Entry<DeviceEntity,Integer> e)->e.getKey().getBlockPos().asLong()).thenComparingInt(Map.Entry::getValue));
        // Rotate priority to prevent a permanently starved device when available FE is insufficient.
        if(!boards.isEmpty())Collections.rotate(boards,-(int)(level.getGameTime()%boards.size()));
        for(var entry:boards){var h=entry.getKey();int bay=entry.getValue();var d=bay==0?h.device:h.upperDevice;prepare(d);var supply=peer(h,512+bay);var bank=supplies.get(supply);var budget=budgets.get(bank);boolean ok=budget!=null&&budget.supply(supply.rackSupplyId,PowerModel.demand(d));state(h,d,ok);}
        for(var h:loaded)if(LaptopBattery.laptop(h.device)){
            boolean wasPowered=Hardware.powered(h.device);var source=peer(h,PowerGeometry.BOARD_IN);var bank=source!=null&&source.device.kind.equals("energy")?source:supplies.get(source);var budget=budgets.get(bank);
            int remaining=LaptopBattery.CHARGE_RATE-(h.energyReceivedTick==level.getGameTime()?h.energyReceived:0),wanted=LaptopBattery.receive(h.device,remaining,remaining,true);
            if(budget!=null&&wanted>0){int amount=budget.draw(source.rackSupply?source.rackSupplyId:"laptop:"+h.device.identity,wanted);if(amount>0)storage(h).receiveEnergy(amount,false);}
            boolean changed=LaptopBattery.tick(h.device,h.energyReceivedTick==level.getGameTime()&&h.energyReceived>0);
            if(wasPowered!=Hardware.powered(h.device)){Hardware.ensure(h.device).epoch++;h.changed();}else if(changed)h.setChanged();
            if(level.getGameTime()%20==0)h.changed();
        }
        for(var e:budgets.entrySet()){var h=e.getKey();if(h.energy!=e.getValue().energy){h.energy=e.getValue().energy;h.setChanged();}if(level.getGameTime()%20==0)h.changed();}
    }}
    public static String status(DeviceEntity h,int bay){var d=bay==0?h.device:h.upperDevice;if(d==null)return "Пустая ячейка";if(LaptopBattery.laptop(d))return LaptopBattery.status(d);if(d.kind.equals("energy"))return "Энергошкаф: "+h.energy+" / "+PowerModel.CAPACITY+" FE; вход до "+PowerModel.INPUT_RATE+" FE/тик. Выходы сзади.";if(d.kind.equals("energy_source"))return "Творческий генератор: бесконечный запас FE. Поставь рядом с энергошкафом или совместимым энергоприёмником.";if(PowerModel.board(d))return Hardware.powered(d)?"Работает; расход "+PowerModel.demand(d)+" FE/тик":Hardware.ensure(d).powered?"Нет питания FE. Задний разъём → OUT блока питания шкафа.":"Устройство выключено кнопкой Power";return "";}
    /** Explicit demo builders call this only after checking the reserved positions are empty. */
    public static void demo(ServerLevel l,DeviceEntity rack,BlockPos cabinet,BlockPos generator,List<Map.Entry<DeviceEntity,Integer>> devices){
        l.setBlockAndUpdate(cabinet,NetLab.ENERGY_CABINET.get().defaultBlockState());l.setBlockAndUpdate(generator,NetLab.CREATIVE_SOURCE.get().defaultBlockState());var bank=(DeviceEntity)l.getBlockEntity(cabinet);bank.energy=PowerModel.CAPACITY;bank.changed();rack.rackSupply=true;rack.rackSupplyId=UUID.randomUUID().toString();rack.changed();connect(bank,300,rack,310);int port=320;for(var e:devices){connect(rack,port++,e.getKey(),512+e.getValue());attached(e.getKey(),e.getValue());}
    }
}

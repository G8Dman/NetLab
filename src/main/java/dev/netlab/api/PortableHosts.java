package dev.netlab.api;

import dev.netlab.*;
import dev.netlab.core.*;
import dev.netlab.core.HandsetConfiguration.Binding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.*;

/** Server-owned network endpoints for held equipment. Never placed or added to chunk storage. */
public final class PortableHosts {
    public interface Binder {Binding bind(ServerPlayer player,DeviceEntity host,int bay);}
    public static final Map<String,Binder> BINDERS=new java.util.concurrent.ConcurrentHashMap<>();
    /** Optional item-backed application configuration, persisted by the host's owning addon. */
    public static final List<java.util.function.Consumer<Host>> SAVERS=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final List<java.util.function.Consumer<Host>> PREPARED=new java.util.concurrent.CopyOnWriteArrayList<>();
    public static void save(Host host){for(var saver:SAVERS)saver.accept(host);}
    private static final Map<ServerLevel,Map<Long,Host>> HOSTS=new WeakHashMap<>();
    public static final class Host extends DeviceEntity {
        public final ServerPlayer owner;
        private final BooleanSupplier present,online;
        private final Predicate<String> permits;
        private final Supplier<Binding> binding;
        public Host(ServerPlayer owner,String identity,Device device,BooleanSupplier present,BooleanSupplier online,Predicate<String> permits,Supplier<Binding> binding){
            super(address(identity),NetLab.PC.get().defaultBlockState());this.owner=owner;this.device=device;this.level=owner.serverLevel();
            this.present=present;this.online=online;this.permits=permits;this.binding=binding;
            var hosts=HOSTS.computeIfAbsent(owner.serverLevel(),l->new HashMap<>());hosts.values().removeIf(h->!h.present());
            if(hosts.containsKey(slotId(0))&&hosts.get(slotId(0)).owner!=owner)throw new IllegalArgumentException("Конфликт адресов портативных устройств");
            hosts.put(slotId(0),this);
        }
        public boolean present(){return !owner.hasDisconnected()&&owner.serverLevel()==level&&present.getAsBoolean();}
        public boolean online(){return present()&&online.getAsBoolean();}
        public boolean allows(String module){return present()&&permits.test(module);}
        public Binding binding(){return binding.get();}
        public Vec3 location(){return owner.position().add(0,1,0);}
        @Override public void changed(){}
        @Override public void setChanged(){}
        @Override public Network network(){return WirelessWorld.network(owner.serverLevel());}
    }
    public static BlockPos address(String identity){var id=UUID.fromString(identity);return new BlockPos((int)(id.getMostSignificantBits()&0x1ffffff),-2048,(int)(id.getLeastSignificantBits()&0x1ffffff));}
    public static Host find(ServerLevel level,long id){var h=HOSTS.getOrDefault(level,Map.of()).get(id);return h!=null&&h.present()?h:null;}
    public static DeviceEntity resolve(ServerPlayer player,BlockPos address){
        var h=find(player.serverLevel(),address.asLong());if(h!=null)return h.owner==player?h:null;
        return player.serverLevel().hasChunkAt(address)&&player.serverLevel().getBlockEntity(address) instanceof DeviceEntity device?device:null;
    }
    public static void require(ServerPlayer player,DeviceEntity host,String module){
        if(host instanceof Host h){if(h.owner!=player||!h.online()||!h.allows(module))throw new IllegalArgumentException("Держи подключённый телефон с нужным модулем");return;}
        if(host==null||host.isRemoved()||host.getLevel()!=player.serverLevel()||player.distanceToSqr(host.getBlockPos().getCenter())>64||!host.device.kind.equals("pc")||!Hardware.powered(host.device)||module.equals("CISCO-AUTOMATION")&&!Hardware.ensure(host.device).modules.containsValue(module))throw new IllegalArgumentException("Нужен включённый ПК/ноутбук рядом с нужным модулем");
    }
    public static boolean bound(DeviceEntity host,String identity){if(!(host instanceof Host h))return false;var binding=h.binding();return binding!=null&&binding.identity().equals(identity)&&binding.dimension().equals(h.owner.serverLevel().dimension().location().toString());}
    public static void clear(){HOSTS.clear();}
}

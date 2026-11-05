package dev.netlab;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(NetLab.ID)
@PrefixGameTestTemplate(false)
public final class NetLabGameTests {
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void worldNetwork(GameTestHelper h) {
    h.setBlock(new BlockPos(1, 1, 1), NetLab.PC.get());
    h.setBlock(new BlockPos(4, 1, 1), NetLab.SWITCH.get());
    h.setBlock(new BlockPos(7, 1, 1), NetLab.RACK_ROUTER.get());
    h.setBlock(new BlockPos(10, 1, 1), NetLab.LAPTOP.get());
    var a = (DeviceEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
    var s = (DeviceEntity) h.getBlockEntity(new BlockPos(4, 1, 1));
    var r = (DeviceEntity) h.getBlockEntity(new BlockPos(7, 1, 1));
    var b = (DeviceEntity) h.getBlockEntity(new BlockPos(10, 1, 1));
    configure(a, 0, "192.168.1.2");
    a.device.gateway = "192.168.1.1";
    configure(r, 0, "192.168.1.1");
    configure(r, 1, "192.168.2.1");
    configure(b, 0, "192.168.2.2");
    b.device.gateway = "192.168.2.1";
    link(a, 0, s, 0);
    link(s, 1, r, 0);
    link(r, 1, b, 0);
    if (!a.network().ping(a.getBlockPos().asLong(), "192.168.2.2").success())
      throw new net.minecraft.gametest.framework.GameTestAssertException("World graph failed routed ping");
    if (!a.decorate(0, 0xcc2244, "Лаборатория A"))
      throw new net.minecraft.gametest.framework.GameTestAssertException("Cable decoration failed");
    if (s.links.get(0).color() != 0xcc2244 || !s.links.get(0).name().equals("Лаборатория A"))
      throw new net.minecraft.gametest.framework.GameTestAssertException("Peer metadata diverged");
    var saved = a.saveWithoutMetadata(h.getLevel().registryAccess());
    var copy = new DeviceEntity(a.getBlockPos(), a.getBlockState());
    copy.loadWithComponents(saved, h.getLevel().registryAccess());
    if (!copy.device.gateway.equals("192.168.1.1") || !copy.links.equals(a.links)
        || !copy.device.ports.getFirst().ip.equals("192.168.1.2"))
      throw new net.minecraft.gametest.framework.GameTestAssertException("NBT round trip failed");
    var legacy = saved.copy();
    for (var tag : legacy.getList("links", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
      ((net.minecraft.nbt.CompoundTag) tag).remove("name");
      ((net.minecraft.nbt.CompoundTag) tag).remove("color");
    }
    var oldCopy = new DeviceEntity(a.getBlockPos(), a.getBlockState());
    oldCopy.loadWithComponents(legacy, h.getLevel().registryAccess());
    if (oldCopy.links.get(0).color() != DeviceEntity.DEFAULT_CABLE_COLOR
        || !oldCopy.links.get(0).name().isEmpty())
      throw new net.minecraft.gametest.framework.GameTestAssertException("Legacy cable migration failed");
    if (!a.decorate(0, 0, "black") || a.links.get(0).color() != 0)
      throw new net.minecraft.gametest.framework.GameTestAssertException("Black cable color must survive");
    r.device.routes.add(new dev.netlab.core.Device.Route("0.0.0.0", "0.0.0.0", "192.168.1.2"));
    var restored = new DeviceEntity(r.getBlockPos(), r.getBlockState());
    restored.loadWithComponents(
        r.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
    if (!restored.device.routes.equals(r.device.routes))
      throw new net.minecraft.gametest.framework.GameTestAssertException("Static route persistence failed");
    h.setBlock(new BlockPos(7, 1, 1), Blocks.AIR);
    if (s.links.containsKey(1) || !b.links.isEmpty())
      throw new net.minecraft.gametest.framework.GameTestAssertException(
          "Removing rack router left connected neighbors");
    if (a.network().ping(a.getBlockPos().asLong(), "192.168.2.2").success())
      throw new net.minecraft.gametest.framework.GameTestAssertException(
          "Ping succeeded after router removal");
    h.runAfterDelay(2, () -> {
      var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
      player.getAbilities().mayBuild = true;
      var middle = CableGeometry.vector(dev.netlab.core.WallCablePath.sample(CableGeometry.route(a,0,s,0,a.links.get(0).anchors()),.5));
      player.setPos(middle.x, middle.y - player.getEyeHeight(), middle.z - 1);
      player.setYRot(0);
      player.setXRot(0);
      var hit = CableGeometry.pick(player);
      if (hit == null || hit.port() != 0 || !hit.device().getBlockPos().equals(a.getBlockPos()))
        throw new net.minecraft.gametest.framework.GameTestAssertException(
            "Ray selection missed cable span: hit=" + hit
            + ", loaded=" + DeviceEntity.loaded(h.getLevel()).size() + ", eye=" + player.getEyePosition()
            + ", mid=" + middle);
      var dye = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_DYE, 2);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dye);
      var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(
          player, net.minecraft.world.InteractionHand.MAIN_HAND);
      CableInteraction.onAir(event);
      if (!event.isCanceled() || dye.getCount() != 1
          || a.links.get(0).color()
              != (net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor() & 0xffffff))
        throw new net.minecraft.gametest.framework.GameTestAssertException("Dye interaction failed");
      var tag = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NAME_TAG, 2);
      tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
          net.minecraft.network.chat.Component.literal("Магистраль 01"));
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tag);
      CableInteraction.onAir(
          new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(
              player, net.minecraft.world.InteractionHand.MAIN_HAND));
      if (tag.getCount() != 1 || !s.links.get(0).name().equals("Магистраль 01"))
        throw new net.minecraft.gametest.framework.GameTestAssertException("Name tag interaction failed");
      if (!a.network().ping(a.getBlockPos().asLong(), "192.168.1.2").success())
        throw new net.minecraft.gametest.framework.GameTestAssertException("Decoration affected IPv4 state");
      if (!DeviceEntity.cleanName("§a\nline").equals("aline"))
        throw new net.minecraft.gametest.framework.GameTestAssertException("Name sanitizing failed");
      h.succeed();
    });
  }
  @GameTest(template = "empty", timeoutTicks = 100)
  public static void routerPersistence(GameTestHelper h) {
    h.setBlock(new BlockPos(1, 1, 1), NetLab.ROUTER.get());
    var entity = (DeviceEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
    var cli = new dev.netlab.core.Console();
    String[] commands = {"enable", "conf t", "hostname School-R1", "ip domain-name school.lab",
        "username pupil secret lab-pass", "line vty 0 4", "login local", "transport input ssh", "exit",
        "ip dhcp pool LAN", "network 192.168.1.0 255.255.255.0", "default-router 192.168.1.1", "exit",
        "ip access-list extended FILTER", "permit icmp any any", "exit", "int lo0",
        "ip add 172.16.0.1 255.255.255.255", "ipv6 address 2001:db8::1/64", "exit", "router ospf 1",
        "network 0.0.0.0 255.255.255.255 area 0", "end", "write memory"};
    for (String command : commands) {
      String result = cli.execute(entity.device, entity.network(), entity.getBlockPos().asLong(), command);
      if (result.startsWith("%"))
        throw new net.minecraft.gametest.framework.GameTestAssertException(command + ": " + result);
    }
    var restored = new DeviceEntity(entity.getBlockPos(), entity.getBlockState());
    restored.loadWithComponents(
        entity.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
    var state = restored.device.os();
    if (!state.users.containsKey("pupil") || !state.pools.containsKey("LAN")
        || !state.acls.containsKey("FILTER") || !state.routing.containsKey("ospf:1")
        || !state.line("vty 0 4").login.equals("local"))
      throw new net.minecraft.gametest.framework.GameTestAssertException("Router state lost in NBT");
    var saved = dev.netlab.core.ConfigStore.restore(state.startup);
    if (!saved.hostname.equals("School-R1") || saved.findInterface("lo0", false) < 0)
      throw new net.minecraft.gametest.framework.GameTestAssertException(
          "Startup snapshot did not survive NBT");
    h.succeed();
  }
  private static void configure(DeviceEntity d, int port, String ip) {
    var p = d.device.ports.get(port);
    p.ip = ip;
    p.up = true;
  }
  private static void link(DeviceEntity a, int ap, DeviceEntity b, int bp) {
    a.links.put(ap, new DeviceEntity.Peer(b.getBlockPos().asLong(), bp));
    b.links.put(bp, new DeviceEntity.Peer(a.getBlockPos().asLong(), ap));
  }
}

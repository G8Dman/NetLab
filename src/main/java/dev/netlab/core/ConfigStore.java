package dev.netlab.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class ConfigStore {
  private static final String TYPE7 = "dsfd;kfoA,.iyewrkldJKDHSUB";
  public static String secret(String text) {
    byte[] salt = new byte[12];
    new SecureRandom().nextBytes(salt);
    return "$netlab$" + HexFormat.of().formatHex(salt) + "$" + digest(HexFormat.of().formatHex(salt) + text);
  }
  private static String digest(String text) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
  public static boolean verify(String text, String encoded) {
    String[] a = encoded.split("\\$");
    return a.length == 4
        && MessageDigest.isEqual(
            a[3].getBytes(StandardCharsets.UTF_8), digest(a[2] + text).getBytes(StandardCharsets.UTF_8));
  }
  public static String type7(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    StringBuilder s = new StringBuilder("02");
    for (int i = 0; i < bytes.length; i++)
      s.append(String.format("%02X", (bytes[i] & 255) ^ TYPE7.charAt((i + 2) % TYPE7.length())));
    return s.toString();
  }
  public static String untype7(String value) {
    if (value.isEmpty())
      return "";
    int seed = Integer.parseInt(value.substring(0, 2));
    byte[] data = HexFormat.of().parseHex(value.substring(2));
    for (int i = 0; i < data.length; i++) data[i] ^= (byte) TYPE7.charAt((i + seed) % TYPE7.length());
    return new String(data, StandardCharsets.UTF_8);
  }
  private static String encode(Device d) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
        out.writeObject(d);
      }
      return Base64.getEncoder().encodeToString(bytes.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
  public static String snapshot(Device d) {
    return snapshot(d, true);
  }
  public static String snapshot(Device d, boolean includeKeys) {
    String startup = d.os().startup, raw;
    String memory=d.extensions.remove("netlab.memory");
    var flash = d.os().flash;
    d.os().startup = "";
    d.os().flash = new LinkedHashMap<>();
    try {
      raw = encode(d);
    } finally {
      d.os().startup = startup;
      d.os().flash = flash;
      if(memory!=null)d.extensions.put("netlab.memory",memory);
    }
    Device copy = restore(raw);
    var os = copy.os();
    os.flash.clear();
    os.translations.clear();
    os.arp.clear();
    os.neighbors6.clear();
    os.leases.clear();
    os.conflicts.clear();
    os.learnedMacs.clear();os.secureLearned.clear();os.securityViolations.clear();os.snoopingBindings.clear();os.switchLog.clear();
    os.dhcpWindows.clear();
    if(copy.wireless!=null)copy.wireless.associated=Long.MIN_VALUE;
    os.natHits = 0;
    os.natMisses = 0;
    os.bootMillis = 0;
    if (!includeKeys) {
      os.rsaPublic = "";
      os.rsaPrivate = "";
    }
    for (var p : copy.ports) {
      p.received = 0;
      p.sent = 0;
      p.drops = 0;
      p.errDisabled=false;
      if (p.dhcp)
        p.ip = "";
      if (p.autoconfig)
        p.ipv6 = "";
    }
    return encode(copy);
  }
  public static Device restore(String encoded) {
    try (ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
      in.setObjectInputFilter(info -> {
        if (info.depth() > 40 || info.references() > 50000 || info.streamBytes() > 2_000_000)
          return ObjectInputFilter.Status.REJECTED;
        Class<?> c = info.serialClass();
        if (c == null)
          return ObjectInputFilter.Status.UNDECIDED;
        while (c.isArray()) c = c.getComponentType();
        String n = c.getName();
        return c.isPrimitive() || n.startsWith("dev.netlab.core.") || n.startsWith("java.util.")
                || n.startsWith("java.lang.")
            ? ObjectInputFilter.Status.ALLOWED
            : ObjectInputFilter.Status.REJECTED;
      });
      Device restored=(Device)in.readObject();restored.normalize();restored.os();return restored;
    } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid configuration snapshot", e);
    }
  }
  public static void replace(Device target, Device saved) {
    if (!target.kind.equals(saved.kind))
      throw new IllegalArgumentException("Configuration belongs to another device type");
    String startup = target.os().startup;
    var flash = target.os().flash;
    // Physical slot IDs are referenced by saved cables and must survive reload.
    var slots = new ArrayList<Device.Port>();
    var names = new ArrayList<String>();
    for (int i = 0; i < target.ports.size(); i++) {
      var old = target.ports.get(i);
      var empty = new Device.Port(target.kind.equals("switch")||old.console||old.l2||old.medium.equals("wireless"));
      empty.name = target.portName(i);
      empty.virtual = old.virtual;
      empty.parent = old.parent;
      empty.hidden = old.hidden||old.virtual;
      empty.console=old.console;empty.medium=old.medium;empty.maxMbps=old.maxMbps;empty.l2=old.l2;empty.hardwareSlot=old.hardwareSlot;
      empty.bandwidth=old.maxMbps>0?old.maxMbps*1000:64;
      slots.add(empty);
      names.add(empty.name);
    }
    for (int i = 0; i < saved.ports.size(); i++) {
      var port = saved.ports.get(i);
      if (port.hidden)
        continue;
      String name = saved.portName(i);
      int index = names.indexOf(name);
      if (index < 0) {
        if(target.hardware!=null&&!port.virtual)continue;
        index = slots.size();
        slots.add(port);
        names.add(name);
      } else {
        var physical=target.ports.get(index);
        if(target.hardware!=null&&!port.virtual){
          if(physical.hidden)continue;
          port.medium=physical.medium;port.maxMbps=physical.maxMbps;port.hardwareSlot=physical.hardwareSlot;port.l2=physical.l2;port.console=physical.console;
        }
        slots.set(index, port);
      }
    }
    for (int i = 0; i < saved.ports.size(); i++) {
      var port = saved.ports.get(i);
      if (port.hidden || port.parent < 0)
        continue;
      String parentName = saved.portName(port.parent);
      port.parent = names.indexOf(parentName);
    }
    target.hostname = saved.hostname;
    target.ports = slots;
    target.routes = saved.routes;
    target.gateway = saved.gateway;
    target.router = saved.os();
    target.router.startup = startup;
    target.router.flash = flash;
    Hardware.normalizePortNames(target);
  }
}

package dev.netlab.core;

import java.math.BigInteger;
import java.net.*;
import java.util.*;

public final class Addressing {
  public static BigInteger ipv6(String value) {
    if (!value.contains(":") || !value.matches("[0-9A-Fa-f:.]+"))
      throw new IllegalArgumentException("Invalid numeric IPv6 address");
    try {
      byte[] b = InetAddress.getByName(value).getAddress();
      if (b.length != 16)
        throw new IllegalArgumentException("IPv6 address required");
      return new BigInteger(1, b);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid IPv6 address");
    }
  }
  public static int prefix6(String prefix) {
    String[] a = prefix.split("/", -1);
    if (a.length != 2)
      throw new IllegalArgumentException("IPv6 prefix length required");
    ipv6(a[0]);
    return number(a[1], 0, 128);
  }
  public static boolean same6(String address, String prefix) {
    int p = prefix6(prefix);
    return ipv6(address).shiftRight(128 - p).equals(ipv6(prefix.split("/")[0]).shiftRight(128 - p));
  }
  public static int number(String value, int min, int max) {
    try {
      int n = Integer.parseInt(value);
      if (n < min || n > max)
        throw new IllegalArgumentException();
      return n;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Expected integer " + min + ".." + max);
    }
  }
  public static String ip(long n) {
    return ((n >>> 24) & 255) + "." + ((n >>> 16) & 255) + "." + ((n >>> 8) & 255) + "." + (n & 255);
  }
  public static String wildcard(String mask) {
    return ip(Device.mask(mask) ^ 0xffffffffL);
  }
  public static boolean matches(String address, String network, String wildcard) {
    long mask = Device.ip(wildcard) ^ 0xffffffffL;
    return (Device.ip(address) & mask) == (Device.ip(network) & mask);
  }
  public static String classMask(String address) {
    long first = Device.ip(address) >>> 24;
    return first < 128 ? "255.0.0.0" : first < 192 ? "255.255.0.0" : "255.255.255.0";
  }
  public static long identity(long id, int port) {
    long hash = id ^ ((long) port * 0x9e3779b97f4a7c15L);
    hash = (hash ^ (hash >>> 33)) * 0xff51afd7ed558ccdL;
    return (hash ^ (hash >>> 33)) * 0xc4ceb9fe1a85ec53L;
  }
  public static String mac(long id, int port) {
    long hash = identity(id, port);
    return String.format("02%02x.%04x.%04x", (hash >>> 32) & 255, (hash >>> 16) & 65535, hash & 65535);
  }
}

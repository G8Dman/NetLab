package dev.netlab.sputnik.core;
import java.net.*;
import java.util.*;
/** Pin DNS resolution once; public HTTP(S) only, including checks for mapped/alternative IP forms. */
public final class InternetTarget {
    public static void validate(String host,int port){if(port!=80&&port!=443)throw new IllegalArgumentException("Разрешены веб-порты 80 и 443");if(host==null||host.isBlank()||host.length()>253||!host.matches("[A-Za-z0-9.:-]+"))throw new IllegalArgumentException("Некорректное имя сайта");}
    public static boolean publicAddress(InetAddress a){if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;byte[] b=a.getAddress();int x=b[0]&255;if(b.length==16)return (x&0xe0)==0x20&&!(x==0x20&&(b[1]&255)==1&&(b[2]&255)==0x0d&&(b[3]&255)==0xb8);int y=b[1]&255,z=b[2]&255;return x!=0&&x!=127&&x<224&&!(x==100&&y>=64&&y<=127)&&!(x==169&&y==254)&&!(x==192&&(y==168||y==0&&(z==0||z==2)))&&!(x==198&&(y==18||y==19||y==51&&z==100))&&!(x==203&&y==0&&z==113);}
    public static InetSocketAddress resolve(String host,int port)throws UnknownHostException{validate(host,port);var addresses=InetAddress.getAllByName(host);if(addresses.length==0||Arrays.stream(addresses).anyMatch(a->!publicAddress(a)))throw new IllegalArgumentException("Интернет-шлюз принимает только публичные адреса сайтов");return new InetSocketAddress(addresses[0],port);}
}

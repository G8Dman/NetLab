package dev.netlab.core;

/** Router-boundary transform: LAN payload is clear, WAN UDP/4500 is authenticated ciphertext. */
public final class RouterTunnel implements java.util.function.BiFunction<Long,Network.Packet,Network.Packet> {
    private final VpnPeers.Tunnel tunnel;
    private Network.Packet original;
    private byte[] envelope;
    private boolean decrypted;
    public RouterTunnel(VpnPeers.Tunnel tunnel){this.tunnel=tunnel;}
    public byte[] envelope(){return envelope==null?new byte[0]:envelope.clone();}
    public boolean completed(){return decrypted;}
    public boolean protectedPayload(){return original!=null&&!decrypted;}
    @Override public Network.Packet apply(Long id,Network.Packet packet){
        if(id==tunnel.from()&&original==null){original=packet;envelope=tunnel.channel().seal(packet.payload());return new Network.Packet(packet.source(),packet.destination(),"udp",packet.sourcePort(),4500,envelope);}
        if(id==tunnel.to()&&original!=null&&!decrypted){byte[] clear=tunnel.channel().open(packet.payload());decrypted=true;return new Network.Packet(original.source(),original.destination(),original.protocol(),original.sourcePort(),original.destinationPort(),clear);}
        return packet;
    }
}

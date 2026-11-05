package dev.netlab.core;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** AES-GCM envelope and 64-packet anti-replay window. Keys never belong to client block state. */
public final class CryptoChannel {
    private final SecretKeySpec key;private final SecureRandom random=new SecureRandom();
    private long outgoing,highest,window;
    public CryptoChannel(byte[] key){if(key.length!=32)throw new IllegalArgumentException("AES-256 requires 32 bytes");this.key=new SecretKeySpec(key.clone(),"AES");}
    public static byte[] derive(String secret,byte[] salt){try{var digest=MessageDigest.getInstance("SHA-256");digest.update(salt);return digest.digest(secret.getBytes(StandardCharsets.UTF_8));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    public synchronized byte[] seal(byte[] plaintext){
        try{long sequence=++outgoing;byte[] nonce=new byte[12];random.nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(ByteBuffer.allocate(8).putLong(sequence).array());byte[] body=cipher.doFinal(plaintext);return ByteBuffer.allocate(20+body.length).putLong(sequence).put(nonce).put(body).array();}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public synchronized byte[] open(byte[] envelope){
        if(envelope.length<36)throw new IllegalArgumentException("Truncated encrypted packet");
        try{var buffer=ByteBuffer.wrap(envelope);long sequence=buffer.getLong();if(sequence<=0||sequence<=highest-64||sequence<=highest&&(window&(1L<<(highest-sequence)))!=0)throw new IllegalArgumentException("VPN replay rejected");
            byte[] nonce=new byte[12],body=new byte[buffer.remaining()-12];buffer.get(nonce);buffer.get(body);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(ByteBuffer.allocate(8).putLong(sequence).array());byte[] plaintext=cipher.doFinal(body);
            if(sequence>highest){long shift=sequence-highest;window=shift>=64?1:(window<<shift)|1;highest=sequence;}else window|=1L<<(highest-sequence);return plaintext;
        }catch(GeneralSecurityException e){throw new IllegalArgumentException("VPN authentication failed");}
    }
}

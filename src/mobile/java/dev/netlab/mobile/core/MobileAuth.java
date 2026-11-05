package dev.netlab.mobile.core;

import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Salted operator credential and nonce-bound challenge responses; no key goes in simulated packets. */
public final class MobileAuth {
    private static final SecureRandom RANDOM=new SecureRandom();
    public static String secret(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
    public static String credential(String password){if(password.length()<8||password.length()>64)throw new IllegalArgumentException("Пароль: 8–64 символа");String salt=secret();return salt+":"+derive(password,salt);}
    public static String salt(String verifier){return verifier.split(":",2)[0];}
    public static String verifierKey(String verifier){String[] v=verifier.split(":",2);if(v.length!=2)throw new IllegalArgumentException("Сначала mobile password ПАРОЛЬ");return v[1];}
    public static String derive(String password,String salt){try{var spec=new PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),60000,256);try{return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}finally{spec.clearPassword();}}catch(GeneralSecurityException|IllegalArgumentException e){throw new IllegalArgumentException("Неверный формат ключа оператора",e);}}
    public static String proof(String key,String nonce,String body){try{var h=Mac.getInstance("HmacSHA256");h.init(new SecretKeySpec(Base64.getDecoder().decode(key),"HmacSHA256"));return Base64.getEncoder().encodeToString(h.doFinal((nonce+"\n"+body).getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException|IllegalArgumentException e){throw new IllegalArgumentException("Неверный ключ SIM/станка",e);}}
    public static boolean verify(String key,String nonce,String body,String proof){try{return MessageDigest.isEqual(Base64.getDecoder().decode(proof(key,nonce,body)),Base64.getDecoder().decode(proof));}catch(IllegalArgumentException e){return false;}}
}

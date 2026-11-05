package dev.netlab.automation.core;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public final class ServerPassword {
    private static byte[] key(String text,byte[] salt){try{var spec=new PBEKeySpec(text.toCharArray(),salt,60000,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    public static String create(String password){if(password.length()<8||password.length()>64)throw new IllegalArgumentException("Пароль: 8..64 символа");byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(key(password,salt));}
    public static boolean verify(String password,String stored){if(password.length()>64||stored==null||stored.length()>150)return false;try{var p=stored.split(":");if(p.length!=2)return false;var salt=Base64.getDecoder().decode(p[0]);return salt.length==16&&MessageDigest.isEqual(Base64.getDecoder().decode(p[1]),key(password,salt));}catch(IllegalArgumentException e){return false;}}
}

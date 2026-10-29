package de.local.tapoviewer;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Portable password encryption; independent of the receiver's non-exportable Keystore. */
final class BackupCodec {
    private static final byte[] MAGIC={'C','S','B','1'};
    static byte[] encrypt(String json,char[] password)throws Exception{
        if(password.length<10)throw new IllegalArgumentException("Mindestens 10 Zeichen als Sicherungspasswort verwenden");
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));cipher.updateAAD(MAGIC);
        byte[] encrypted=cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));return ByteBuffer.allocate(32+encrypted.length).put(MAGIC).put(salt).put(iv).put(encrypted).array();
    }
    static String decrypt(byte[] bytes,char[] password)throws Exception{
        if(bytes.length<48||bytes.length>4*1024*1024)throw new IllegalArgumentException("Ungültige Sicherungsdatei");ByteBuffer in=ByteBuffer.wrap(bytes);byte[] magic=new byte[4],salt=new byte[16],iv=new byte[12],encrypted=new byte[bytes.length-32];in.get(magic).get(salt).get(iv).get(encrypted);if(!java.util.Arrays.equals(magic,MAGIC))throw new IllegalArgumentException("Unbekanntes Sicherungsformat");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));cipher.updateAAD(MAGIC);return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
    }
    private static SecretKey key(char[] password,byte[] salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(password,salt,210000,256);try{return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),"AES");}finally{spec.clearPassword();}}
}

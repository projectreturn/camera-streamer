package de.local.tapoviewer;
import org.junit.Test;
import static org.junit.Assert.*;
public class BackupCodecTest {
    @Test public void encryptedBackupRoundTripsAndUsesRandomSalt()throws Exception{char[] password="a long test password".toCharArray();byte[] first=BackupCodec.encrypt("{\"secret\":\"private\"}",password),second=BackupCodec.encrypt("{\"secret\":\"private\"}",password);assertFalse(java.util.Arrays.equals(first,second));assertEquals("{\"secret\":\"private\"}",BackupCodec.decrypt(first,password));assertFalse(new String(first,java.nio.charset.StandardCharsets.UTF_8).contains("private"));}
    @Test public void rejectsWrongPasswordAndModifiedCiphertext()throws Exception{byte[] bytes=BackupCodec.encrypt("test","correct-password".toCharArray());try{BackupCodec.decrypt(bytes,"wrong-password".toCharArray());fail();}catch(javax.crypto.AEADBadTagException expected){}bytes[bytes.length-1]^=1;try{BackupCodec.decrypt(bytes,"correct-password".toCharArray());fail();}catch(javax.crypto.AEADBadTagException expected){}}
    @Test public void retryBackoffIsBounded(){assertEquals(2000,StreamHealth.retryDelay(0));assertEquals(4000,StreamHealth.retryDelay(1));assertEquals(60000,StreamHealth.retryDelay(100));}
}

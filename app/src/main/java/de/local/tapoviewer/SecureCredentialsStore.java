package de.local.tapoviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores camera credentials encrypted by a non-exportable Android Keystore key. */
public final class SecureCredentialsStore {
    private static final String KEY_ALIAS = "tapo_tv_camera_credentials_v1";
    private static final String PREFS = "secure_camera_settings";
    private final SharedPreferences prefs;

    public SecureCredentialsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(String ip, String username, String password) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        String encryptedUser = encode(cipher.getIV(), cipher.doFinal(username.getBytes(StandardCharsets.UTF_8)));

        cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        String encryptedPassword = encode(cipher.getIV(), cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        prefs.edit()
            .putString(key(ip, "user"), encryptedUser)
            .putString(key(ip, "pass"), encryptedPassword)
            .apply();
    }

    public Credentials load(String ip) {
        try {
            String user = prefs.getString(key(ip, "user"), null);
            String pass = prefs.getString(key(ip, "pass"), null);
            if (user == null || pass == null) return new Credentials("", "");
            SecretKey key = getOrCreateKey();
            return new Credentials(decode(key, user), decode(key, pass));
        } catch (Exception e) {
            prefs.edit().remove(key(ip, "user")).remove(key(ip, "pass")).apply();
            return new Credentials("", "");
        }
    }

    public boolean hasCredentials(String ip) {
        Credentials value = load(ip);
        return !value.username.isEmpty() && !value.password.isEmpty();
    }

    public void saveSecret(String name, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        prefs.edit().putString("secret_" + name, encode(cipher.getIV(), cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)))).apply();
    }

    public String loadSecret(String name) {
        try {
            String value = prefs.getString("secret_" + name, null);
            return value == null ? "" : decode(getOrCreateKey(), value);
        } catch (Exception e) { return ""; }
    }

    public void remove(String id) {
        prefs.edit().remove(key(id, "user")).remove(key(id, "pass")).remove("secret_" + id + "_url").remove("secret_" + id + "_suburl").apply();
    }

    private static String key(String ip, String field) {
        return ip.replace('.', '_') + "_" + field;
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String encode(byte[] iv, byte[] ciphertext) {
        return Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private static String decode(SecretKey key, String encoded) throws Exception {
        String[] parts = encoded.split("\\.", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid encrypted value");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    public static final class Credentials {
        public final String username;
        public final String password;
        public Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}

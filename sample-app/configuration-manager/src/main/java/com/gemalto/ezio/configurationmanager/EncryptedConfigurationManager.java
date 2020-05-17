package com.gemalto.ezio.configurationmanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class EncryptedConfigurationManager extends ConfigurationManager {

    private final static String KEY_ALIAS = PREF_PREFIX + ".encKey";
    private static final String IV_PREFIX = PREF_PREFIX + ".iv." ;
    private SecretKey encKey = null;

    @SuppressLint("NewApi")
    public EncryptedConfigurationManager(Context context, String name)
            throws GeneralSecurityException, IOException {

        super(context, name);

        // create/get key from keystore
        if ((encKey == null) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            final KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore
                    .getEntry(KEY_ALIAS, null);
            if (secretKeyEntry != null) {
                // get existing key
                encKey = secretKeyEntry.getSecretKey();
            } else {
                // no key yet, create it
                final KeyGenParameterSpec keyGenParameterSpec;
                final KeyGenerator keyGenerator = KeyGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                keyGenParameterSpec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
                keyGenerator.init(keyGenParameterSpec);
                encKey = keyGenerator.generateKey();
            }
        }
    }

    public EncryptedConfigurationManager(Context context)
            throws GeneralSecurityException, IOException {
        this(context, PREF_NAME);
    }

    @Override
    String readSetting(String key, String defaultValue) {
        String value = super.readSetting(key, defaultValue);
        String ivStr = preferences.getString(IV_PREFIX + key, "");
        if ((encKey != null) && (ivStr.length() > 0)) {
            // Decrypt value
            try {
                long time = new Date().getTime();
                byte[] iv = Base64.decode(ivStr, Base64.DEFAULT);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                final GCMParameterSpec spec = new GCMParameterSpec(128, iv);
                cipher.init(Cipher.DECRYPT_MODE, encKey, spec);

                value = new String(cipher.doFinal(Base64.decode(value, Base64.DEFAULT)), StandardCharsets.UTF_8);
                time = new Date().getTime() - time;
                Log.d(LOG_TAG, "Keystore decryption in " + time + "ms");
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to decrypt " + key, e);
                value = defaultValue;
            }
        }
        return value;
    }

    @Override
    void storeSetting(SharedPreferences.Editor editor, boolean secure, String key, String value) {
        if ((encKey != null) && secure) {
            try {
                long time = new Date().getTime();
                // Encrypt value
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, encKey);
                byte[] iv = cipher.getIV();
                editor.putString(IV_PREFIX + key, Base64.encodeToString(iv, Base64.DEFAULT));
                value = Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.DEFAULT);
                time = new Date().getTime() - time;
                Log.d(LOG_TAG, "Keystore encryption in " + time + "ms");
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to encrypt and store setting: " + key, e);
            }
        }
        super.storeSetting(editor, true, key, value);
    }

}
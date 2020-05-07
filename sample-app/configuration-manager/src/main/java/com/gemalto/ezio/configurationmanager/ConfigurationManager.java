package com.gemalto.ezio.configurationmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

public class ConfigurationManager {

    //region Defines

    private static final String PREF_NAME = "_CM$_";
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";
    private static final String LOG_TAG = "ConfigurationManager";
    private static final String PREF_PREFIX = "_CM$_";
    private static final String PREF_LOADED = PREF_PREFIX + "_CM$_.loaded";

    final Context context;
    final SharedPreferences preferences;
    private final Map<String, SecretKey> keys = new HashMap<>();

    /**
     * Creates a configuration manager with default name
     */
    public ConfigurationManager(Context context) {
        this(context, PREF_NAME);
    }

    /**
     * Creates a configuration manager
     *
     * @param name named to the SharedPreferences instance that will be used to store configuration settings
     */
    public ConfigurationManager(Context context, String name) {
        Log.d(LOG_TAG, "ConfigManager: " + name);
        this.preferences =  context.getSharedPreferences(name, Context.MODE_PRIVATE);
        this.context = context;
    }

    /**
     * To be called at application startup to detect that configuration needs to be loaded
     *
     * @return true if configuration has previously been successfully loaded
     */
    public boolean isLoaded() {
        return preferences.getBoolean(PREF_LOADED, false);
    }

    /**
     * @throws ConfigurationNotLoaded if configuration is not loaded
     */
    void checkLoaded() throws ConfigurationNotLoaded {
        if (!isLoaded()) {
            throw new ConfigurationNotLoaded();
        }
    }

    /**
     * Clears all preferences entries created by this configuration
     */
    void clear() {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key: preferences.getAll().keySet()) {
            if (key.startsWith(PREF_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.commit();
    }

    /**
     * Derives the property encryption/decryption key from the provided password and derivation parameters
     *
     * @param password the password to use for key derivation
     * @param iterations iterations for key derivation
     * @param outputKeyLength outputKeyLength for key derivation
     * @param algorithm algorithm for key derivation
     * @param salt salt for key derivation
     * @return the secret key
     * @throws NoSuchAlgorithmException if crypto error
     * @throws InvalidKeySpecException if crypto error
     */
    private SecretKey deriveKey(char[] password, int iterations, int outputKeyLength, String algorithm, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmac" + algorithm);
        KeySpec keySpec = new PBEKeySpec(password, salt, iterations, outputKeyLength);
        return secretKeyFactory.generateSecret(keySpec);
    }

    private String decryptValue(String value, char[] password) throws ConfigurationDecryptionError {
        // Encrypted format:
        //  iterations#algorithm#keySize#base64(salt)#base64(iv)#base64(ciphered)
        String res = value;
        int iterations, keySize;
        byte[] salt, iv, encryptedBytes;
        String algorithm;
        SecretKey decKey;
        long time;

        if ((value == null) || (value.length() == 0)) {
            return "";
        }

        try {
            // encrypted value
            int sep = res.lastIndexOf("#");
            encryptedBytes = Base64.decode(res.substring((sep+1)), Base64.DEFAULT);
            res = res.substring(0, sep);

            // IV
            sep = res.lastIndexOf("#");
            iv = Base64.decode(res.substring(sep+1), Base64.DEFAULT);
            res = res.substring(0, sep);

            decKey = keys.get(res);
            if (decKey == null) {
                String params = res;
                time = new Date().getTime();
                // Salt
                sep = res.lastIndexOf("#");
                salt = Base64.decode(res.substring(sep+1), Base64.DEFAULT);
                res = res.substring(0, sep);

                // Key size
                sep = res.lastIndexOf("#");
                keySize = Integer.parseInt(res.substring(sep+1));
                res = res.substring(0, sep);

                // Algorithm
                sep = res.lastIndexOf("#");
                algorithm = res.substring(sep+1);
                // use Android naming
                if ("SHA-1".equals(algorithm)) {
                    algorithm = "SHA1";
                }
                res = res.substring(0, sep);

                // Iterations
                iterations = Integer.parseInt(res);

                try {
                    // derive key
                    decKey = deriveKey(password, iterations, keySize, algorithm, salt);
                    // store key
                    keys.put(params, decKey);
                } catch (Exception err) {
                    Log.e(LOG_TAG, "Key derivation error", err);
                    throw new ConfigurationDecryptionError("Failed to derive key");
                }
                time = new Date().getTime() - time;
                Log.d(LOG_TAG, "Derived key in " + time + "ms");
            }

        } catch (ConfigurationDecryptionError err) {
            // propagate
            throw err;
        } catch (Exception err) {
            Log.e(LOG_TAG, "Decoding error", err);
            throw new ConfigurationDecryptionError("Failed to parse encrypted value");
        }

        try {
            time = new Date().getTime();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, decKey, new GCMParameterSpec(128, iv));
            res = new String(cipher.doFinal(encryptedBytes));
            time = new Date().getTime() - time;
            Log.d(LOG_TAG, "Decrypted in " + time + "ms");
        } catch (Exception err) {
            Log.e(LOG_TAG, "Decryption error", err);
            throw new ConfigurationDecryptionError("Failed to decrypt value");
        }
        return res;
    }

    String readSetting(String key, String defaultValue) {
        return preferences.getString(PREF_PREFIX + key, defaultValue);
    }

    public String getConfigurationSetting(String key, String defaultValue)
            throws ConfigurationNotLoaded  {
        checkLoaded();
        return readSetting(key, defaultValue);
    }

    void storeSetting(SharedPreferences.Editor editor, String key, String value) {
        editor.putString(PREF_PREFIX + key, value);
    }

    public void loadConfigurationFromInputStream(InputStream in, char[] password)
            throws IOException, ConfigurationDecryptionError {
        Properties properties = new Properties();
        properties.load(in);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        for (String key: properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if ((value != null) && (value.startsWith(ENC_PREFIX)) && (value.endsWith(ENC_SUFFIX))) {
                if ((password == null) || (password.length == 0)) {
                    throw new ConfigurationDecryptionError(key + ": cannot decrypt due to missing password");
                }
                try {
                    value = decryptValue(value.substring(ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length()),
                            password);
                } catch (ConfigurationDecryptionError ex) {
                    throw new ConfigurationDecryptionError(key + ": " + ex.getMessage());
                }
            }
            storeSetting(editor, key, value);
        }
        keys.clear();
        editor.putBoolean(PREF_LOADED, true);
        editor.apply();
    }

    /**
     * Load configuration from URL
     *
     * @param url the URL from which the configuration file can be downloaded. Supported schemes are:
     *            https, http, file, asset
     * @param password password to decrypt encrypted configuration values, if any. May be null
     * @param connectTimeout http connect timeout
     * @param readTimeout http read timeout
     * @throws IOException if error while reading URL content
     */
    public void loadConfigurationFromUrl(String url, char[] password, int connectTimeout, int readTimeout)
            throws IOException, ConfigurationDecryptionError {

        InputStream input = null;
        Log.i(LOG_TAG, "Loading configuration from " + url);

        if (url.startsWith("https://") || url.startsWith("http://")) {
            URL myUrl = new URL(url);
            //Create a connection
            HttpURLConnection connection = (HttpURLConnection)
                    myUrl.openConnection();
            //Set methods and timeouts
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            // get it
            connection.connect();
            input = connection.getInputStream();
        } else if (url.startsWith("file:")) {
            url = url.substring(5);
            input = new FileInputStream(url);
        } else if (url.startsWith("asset:")) {
            url = url.substring(6);
            input = context.getAssets().open(url);
        }
        if (input != null) {
            loadConfigurationFromInputStream(input, password);
        } else {
            throw new IllegalArgumentException("Unsupported URL: " + url);
        }
    }

    /**
     * Load configuration from URL using default timeout (15s)
     * @param url the URL from which the configuration file can be downloaded
     * @param password password to decrypt encrypted configuration values, if any. May be null
     * @throws IOException if error while reading URL content
     */
    public void loadConfigurationFromUrl(String url, char[] password)
            throws IOException, ConfigurationDecryptionError {
        loadConfigurationFromUrl(url, password, 15000, 15000);
    }

    public static class ConfigurationNotLoaded extends Exception {}

    public static class ConfigurationDecryptionError extends Exception {
        ConfigurationDecryptionError(String message) {
            super(message);
        }
    }

}

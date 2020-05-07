package com.gemalto.ezio.configurationmanager;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.io.InputStream;

public class EncryptedConfigurationManager extends ConfigurationManager {

    public EncryptedConfigurationManager(Context context, String name) {
        super(context, name);
        // TODO create/get key from keystore
    }

    @Override
    String readSetting(String key, String defaultValue) {
        String value = super.readSetting(key, defaultValue);
        // TODO decrypt value
        return value;
    }

    @Override
    void storeSetting(SharedPreferences.Editor editor, String key, String value) {
        // TODO encrypt value
        super.storeSetting(editor, key, value);
    }

    @Override
    public void loadConfigurationFromInputStream(InputStream in, char[] password)
            throws IOException, ConfigurationDecryptionError {
        // TODO create encryption key
        super.loadConfigurationFromInputStream(in, password);
    }
}
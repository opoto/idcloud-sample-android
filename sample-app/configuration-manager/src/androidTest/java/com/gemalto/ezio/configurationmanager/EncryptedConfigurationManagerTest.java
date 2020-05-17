package com.gemalto.ezio.configurationmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.runner.AndroidJUnit4;

import com.gemalto.ezio.configurationmanager.ConfigurationManager.ConfigurationManagerError;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class EncryptedConfigurationManagerTest extends ConfigurationManagerTest {

    @Override
    ConfigurationManager createConfigurationManager(String name) throws GeneralSecurityException, IOException {
        return new EncryptedConfigurationManager(ctx, name);
    }

    @Override
    ConfigurationManager createConfigurationManager() throws GeneralSecurityException, IOException {
        return new EncryptedConfigurationManager(ctx);
    }

    @Test
    public void basicValues() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        super.basicValues();
        SharedPreferences prefs = ctx.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE);
        assertEquals("ok2  ", prefs.getString("_CM$_test2", null));
        assertNull(prefs.getString("_CM$_.iv.test2", null));
    }

    @Test
    public void encOkValues() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        super.encOkValues();
        SharedPreferences prefs = ctx.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE);
        assertNotEquals("testing", prefs.getString("_CM$_enc2", null));
        assertNotNull(prefs.getString("_CM$_.iv.enc2", null));
    }

}

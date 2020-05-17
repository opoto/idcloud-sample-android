package com.gemalto.ezio.configurationmanager;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.gemalto.ezio.configurationmanager.ConfigurationManager.ConfigurationManagerError;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ConfigurationManagerTest {

    Context ctx;
    final static String TEST_PREFS = "testPrefs";
    private final static char[] DUMMY_PWD = "dummy".toCharArray();
    private final static char[] VALID_PWD = "p4ssw0rd".toCharArray();

    @Before
    public void initContext() {
        // Context of the app under test.
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    ConfigurationManager createConfigurationManager(String name) throws GeneralSecurityException, IOException {
        return new ConfigurationManager(ctx, name);
    }

    ConfigurationManager createConfigurationManager() throws GeneralSecurityException, IOException {
        return new ConfigurationManager(ctx);
    }

    @Test(expected = ConfigurationManagerError.class)
    public void notLoaded() throws ConfigurationManagerError, GeneralSecurityException, IOException {
        ConfigurationManager cm = createConfigurationManager( "notloaded");
        assertFalse(cm.isLoaded());
        cm.getConfigurationSetting("test1", null);
    }

    @Test(expected = IOException.class)
    public void missingFile() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager( "notloaded");
        assertFalse(cm.isLoaded());
        cm.loadConfigurationFromUrl("file:/does/not/exist.error", DUMMY_PWD);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedUrl() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager( "notloaded");
        assertFalse(cm.isLoaded());
        cm.loadConfigurationFromUrl("dummy:/does/not/exist.error", DUMMY_PWD);
    }

    @Test
    public void basicValues() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager(TEST_PREFS);
        String input = "";
        input += " test1=ok1\n";
        input += "test2 : ok2  \n";
        input += "test3=\n\n\n";
        input += "  # comment\n";
        input += "  ! comment\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), DUMMY_PWD);
        assertTrue(cm.isLoaded());
        assertEquals("ok1", cm.getConfigurationSetting("test1", null));
        assertEquals("ok2  ", cm.getConfigurationSetting("test2", null));
        assertEquals("", cm.getConfigurationSetting("test3", "ok3"));
        assertNull(cm.getConfigurationSetting("test4", null));
        assertEquals("ok4", cm.getConfigurationSetting("test4", "ok4"));
        assertNull(cm.getConfigurationSetting("test5", null));
    }

    @Test
    public void encOkValues() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager(TEST_PREFS);
        String input = "";
        input += "enc1=ENC(100000#SHA-1#256#aDg+LoyH+AbOvC8TjwS+AA==#x3ill0HJozxFIMGf#YSMdDbXF8c+V/z5ockH8lgLHApscTuU=)\n";
        input += "enc2=ENC(10#SHA-1#128#Uem9WSoK35Q5pRiKWRnS1g==#+sIpKY6j8IoE2ZC3#UHb9xT104lmD7Yo82aKTV7G6gfAQYgg=)\n";
        input += "enc3=ENC()\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), VALID_PWD);
        assertEquals("testing", cm.getConfigurationSetting("enc1", null));
        assertEquals("testing", cm.getConfigurationSetting("enc1", "default"));
        assertEquals("testing", cm.getConfigurationSetting("enc2", null));
        assertEquals("", cm.getConfigurationSetting("enc3", null));
        assertEquals("", cm.getConfigurationSetting("enc3", "default"));
    }

    @Test
    public void encEmpty() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager();
        String input = "";
        input += "enc1=ENC()\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), DUMMY_PWD);
        assertEquals("", cm.getConfigurationSetting("enc1", "default"));
    }

    @Test(expected = ConfigurationManagerError.class)
    public void encPwdKo() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager();
        String input = "";
        input += "enc1=ENC(100000#SHA-1#256#aDg+LoyH+AbOvC8TjwS+AA==#x3ill0HJozxFIMGf#YSMdDbXF8c+V/z5ockH8lgLHApscTuU=)\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), DUMMY_PWD);
    }

    @Test(expected = ConfigurationManagerError.class)
    public void encValuedKo() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager();
        String input = "";
        input += "enc1=ENC(10#SHA-1#128#Uem9WSoK35Q5pRiKWRnS1g==#+sIpKY6j8IoE2ZC3#UHb9xT104lmD7Yo82aKTV32gfAQYgg=))\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), DUMMY_PWD);
    }


    @Test
    public void emptyName() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager( "");
        String input = "";
        input += "test1=ok1\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), DUMMY_PWD);
        assertTrue(cm.isLoaded());
        assertEquals("ok1", cm.getConfigurationSetting("test1", null));
    }

    @Test
    public void nullName() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager( null);
        String input = "";
        input += "test1=ok1\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), DUMMY_PWD);
        assertTrue(cm.isLoaded());
        assertEquals("ok1", cm.getConfigurationSetting("test1", null));
    }

    @Test
    public void nullPwd() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager();
        String input = "";
        input += "test1=ok1\n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), null);
        assertTrue(cm.isLoaded());
        assertEquals("ok1", cm.getConfigurationSetting("test1", null));
    }

    @Test(expected = ConfigurationManagerError.class)
    public void clearValues() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager();
        String input = "";
        input += " test1=ok1\n";
        input += "test2 : ok2  \n";
        cm.loadConfigurationFromInputStream(new ByteArrayInputStream(input.getBytes()), DUMMY_PWD);
        assertTrue(cm.isLoaded());
        assertEquals("ok1", cm.getConfigurationSetting("test1", null));
        assertEquals("ok2  ", cm.getConfigurationSetting("test2", null));
        cm.clear();
        assertFalse(cm.isLoaded());
        cm.getConfigurationSetting("test1", null);
    }

    @Test
    public void loadUrl() throws IOException, ConfigurationManagerError, GeneralSecurityException {
        ConfigurationManager cm = createConfigurationManager();
        cm.loadConfigurationFromUrl("https://raw.githubusercontent.com/opoto/idcloud-sample-android/master/sample-app/gradle.properties", DUMMY_PWD);
        assertTrue(cm.isLoaded());
        assertNotEquals(null, cm.getConfigurationSetting("org.gradle.jvmargs", null));
    }
}

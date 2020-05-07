/*
 * MIT License
 *
 * Copyright (c) 2020 Thales DIS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * IMPORTANT: This source code is intended to serve training information purposes only.
 *            Please make sure to review our IdCloud documentation, including security guidelines.
 */

package com.gemalto.eziomobilesampleapp;

import android.net.Uri;
import android.util.Log;

import com.gemalto.ezio.configurationmanager.ConfigurationManager;
import com.gemalto.idp.mobile.core.ApplicationContextHolder;
import com.gemalto.idp.mobile.core.devicefingerprint.DeviceFingerprintSource;
import com.gemalto.idp.mobile.core.net.TlsConfiguration;
import com.gemalto.idp.mobile.msp.MspSignatureKey;
import com.gemalto.idp.mobile.oob.OobConfiguration;
import com.gemalto.idp.mobile.otp.OtpConfiguration;
import com.google.android.gms.common.util.Hex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Configuration {

    private static ConfigurationManager config = new ConfigurationManager(ApplicationContextHolder.getContext());
    private final static String LOG_TAG = "CONFIG";

    public final static void init() throws IOException, ConfigurationManager.ConfigurationDecryptionError {
        if (BuildConfig.CONFIG_RELOAD || !config.isLoaded()) {
            config.loadConfigurationFromUrl(BuildConfig.CONFIG_URL, BuildConfig.CONFIG_PASSWORD.toCharArray());
        }
    }

    private static String getConfig(String key) {
        return getConfig(key, null);
    }

    private static String getConfig(String key, String defaultValue) {
        String res = null;
        try {
            res = config.getConfigurationSetting(key, defaultValue).trim();
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Failed to get configuration " + key);
        }
        if ((res == null) || (res.length() == 0)) {
            res = defaultValue;
        }
        return res;
    }

    //region SDK


    /**
     * Activation code is used to enable OOB features.
     * It should be provided by application.
     */
    public static final byte[] getSdkActivationCode() {
        String hexa = getConfig("sdk.activationcode");
        return Hex.stringToBytes(hexa);
    }

    /**
     * Optional value with custom fingerprint data. Used as input of encryption calculation
     */
    public static final DeviceFingerprintSource getSdkDeviceFingerprintSource() {
        String sourcesListCsv = getConfig("sdk.devicefingerprint.sources", "SOFT");
        if (sourcesListCsv.length() > 0) {
            String[] sourceNames = sourcesListCsv.split(" *, *");
            DeviceFingerprintSource.Type[] sources = new DeviceFingerprintSource.Type[sourceNames.length];
            int i = 0;
            for (String sourceName: sourceNames) {
                DeviceFingerprintSource.Type source = null;
                try {
                    source = DeviceFingerprintSource.Type.valueOf(sourceName.trim());
                } catch (Exception ex) {
                    // TODO should we throw exception here?
                    Log.e(LOG_TAG, "DFP source not found: " + sourceName);
                }
                if (source != null) {
                    sources[i++] = source;
                }
            }
            if (i != sourceNames.length){
                sources = java.util.Arrays.copyOfRange(sources, 0, i);
            }
            Log.d(LOG_TAG,"DFP sources: " + Arrays.toString(sources));
            return new DeviceFingerprintSource(
                    getCustomFingerprintData(),
                    sources);
        }
        return new DeviceFingerprintSource(
                getCustomFingerprintData());
    }


    /**
     * Gets the custom fingerprint data.
     *
     * @return Custom fingerprint data.
     */
    public static byte[] getCustomFingerprintData() {
        try {
            return getConfig("sdk.devicefingerprint.customdata").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // This should not happen.
            throw new IllegalStateException(e);
        }
    }

    /**
     * For debug purposes we can weaken TLS configuration.
     * In release mode all values must be set to NO. Otherwise it will cause runtime exception.
     */
    public static final TlsConfiguration getSdkTlsConfiguration() {
        boolean tlsDebug = "true".equals(getConfig("sdk.tls.debug"));
        if (tlsDebug) {
            return new TlsConfiguration(
                TlsConfiguration.Permit.SELF_SIGNED_CERTIFICATES,
                TlsConfiguration.Permit.HOSTNAME_MISMATCH,
                TlsConfiguration.Permit.INSECURE_CONNECTIONS);
        } else {
            return new TlsConfiguration();
        }
    }

    //endregion

    //region OTP


    /**
     * Replace this domain with your OTP server domain.
     * This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final String getOtpDomain() {
        return getConfig("otp.domain");
    }

    /**
     * Define Token related behaviour on rooted devices.
     * See OtpConfiguration.TokenRootPolicy for more details.
     */
    public static final OtpConfiguration.TokenRootPolicy getOtpRootPolicy() {
        String policy = getConfig("otp.rootpolicy", "IGNORE");
        return OtpConfiguration.TokenRootPolicy.valueOf(policy);
    }

    /**
     * Replace this byte array with your own EPS key modulus unless you are using the EPS 2.X default key pair.
     * The EPS' RSA modulus. This is specific to the configuration of the bank's system.
     * Therefore other values should be used here.
     */
    public static final byte[] getOtpRsaKeyModulus() {
        String hexa = getConfig("otp.key.pub.modulus");
        return Hex.stringToBytes(hexa);
    }

    /**
     * Replace this byte array with your own EPS key exponent.
     * The EPS' RSA exponent. This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final byte[] getRsaKeyExponent() {
        String hexa = getConfig("otp.key.pub.exponent");
        return Hex.stringToBytes(hexa);
    }

    /**
     * Replace this URL with your EPS URL.
     */
    public static final String getOtpProvisionUrl() {
        return getConfig("otp.provision.url");
    }

    /**
     * Replace this string with your own EPS key ID.
     * This is specific to the configuration of the bank's system. Therefore other values should be used here.
     */
    public static final String getOtpRsaKeyId() {
        return getConfig("otp.key.id");
    }

    /**
     * Configuration of example OCRA suite used in this demo.
     */
    public static final String getOtpOcraSuite() {
        return getConfig("otp.ocrasuite");
    }

    /**
     * OTP Value lifespan used for graphical representation.
     */
    public static final int getOtLifespan() {
        return Integer.parseInt(getConfig("otp.lifespan", "30"));
    }

    //endregion

    //region OOB

    /**
     * Define OOB related behaviour on rooted devices.
     * See OobConfiguration.OobRootPolicy for more details.
     */
    public static final OobConfiguration.OobRootPolicy getOobRootPolicy() {
        String policy = getConfig("oob.rootpolicy", "IGNORE");
        return OobConfiguration.OobRootPolicy.valueOf(policy);
    }

    /**
     * Replace this byte array with your own OOB key modulus unless you are using the default key pair.
     * This is specific to the configuration of the bank's system. Therefore other values should be used here.
     */
    public static final byte[] getOobRsaKeyModulus() {
        String hexa = getConfig("oob.key.pub.modulus");
        return Hex.stringToBytes(hexa);
    };

    /**
     * OOB key exponent.
     * This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final byte[] getOobRsaKeyExponent() {
        String hexa = getConfig("oob.key.pub.exponent");
        return Hex.stringToBytes(hexa);
    }

    /**
     * OOB server URL.
     * This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final String getOobUrl() {
        return getConfig("oob.url");
    }

    /**
     * Replace this domain with your OOB server domain.
     * This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final String getOobDomain() {
        return getConfig("oob.domain");
    }

    /**
     * Replace this app id with your OOB server app id.
     * This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final String getOobAppId() {
        return getConfig("oob.appid");
    }

    /**
     * Replace this push channel with your OOB server push channel.
     * This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final String getOobChannel() {
        return getConfig("oob.channel");
    }

    /**
     * Replace this provider id with your OOB server provider id.
     * This is specific to the configuration of the bank's system.  Therefore other values should be used here.
     */
    public static final String getOobProviderId() {
        return getConfig("oob.providerid");
    }

    //endregion

    //region GEMALTO FACE ID

    /**
     * Use in order to activate Gemalto Face ID support.
     */
    public static final String getFaceIdProductKey() {
        return getConfig("faceid.productkey");
    }

    /**
     * Use in order to activate Gemalto Face ID support.
     */
    public static final String getFaceIdServerUrl() {
        return getConfig("faceid.serverurl");
    }

    //endregion

    //region MSP

    /**
     * This sample app does not use MSP encryption.
     */
    public static final List<byte[]> getMspObfuscationCode() {
        String codeCsv = getConfig("msp.obfuscation.code", "");
        String[] codesHex = codeCsv.split(" *, *");
        ArrayList<byte[]> res = new ArrayList<>();
        for (String codeHex: codesHex) {
            res.add(Hex.stringToBytes(codeHex));
        }
        return res;
    }

    /**
     * This sample app does not use MSP encryption.
     */
    public static final List<MspSignatureKey> getMspSignKeys() {
        getConfig("msp.sign.key", "");
        // TODO
        return null;
    }

    //endregion

    //region APP CONFIG

    /**
     * This value is optional. In case that URL is not null,
     * it will display privacy policy button on settings page.
     */
    public static final Uri getPrivacyPolicyUrl() {
        return Uri.parse(getConfig("app.privacy.url"));
    }

    //endregion


    //region TUTO PAGE CONFIG
    /**
     * Tuto page does require authentication.
     */
    public static final String getTutoBasicAuthUsername() {
        return getConfig("tuto.basicauth.username");
    }

    /**
     * Tuto page does require authentication.
     */
    public static final String getTutoBasicAuthPassword() {
        return getConfig("tuto.basicauth.password");
    }

    /**
     * Base totu page URL. Used for In Band cases.
     */
    private static final String getTutoUrlRoot() {
        return getConfig("tuto.url.root");
    }

    /**
     * Auth API url used for In Band cases.
     */
    public static final String getTutoUrlAuth() {
        return getTutoUrlRoot() + "api/auth";
    }

    /**
     * Transaction sign API url used for In Band cases.
     */
    public static final String getTutoUrlSign() {
        return getTutoUrlRoot() + "api/sign";
    }

    //endregion

}
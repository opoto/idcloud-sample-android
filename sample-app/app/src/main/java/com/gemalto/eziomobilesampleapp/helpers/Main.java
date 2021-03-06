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

package com.gemalto.eziomobilesampleapp.helpers;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.widget.Toast;

import com.gemalto.eziomobilesampleapp.Configuration;
import com.gemalto.eziomobilesampleapp.EzioSampleApp;
import com.gemalto.eziomobilesampleapp.MainActivity;
import com.gemalto.eziomobilesampleapp.R;
import com.gemalto.eziomobilesampleapp.helpers.app.storage.SharedPreferences;
import com.gemalto.eziomobilesampleapp.helpers.ezio.HttpManager;
import com.gemalto.eziomobilesampleapp.helpers.ezio.PushManager;
import com.gemalto.eziomobilesampleapp.helpers.ezio.QRCodeManager;
import com.gemalto.eziomobilesampleapp.helpers.ezio.TokenManager;
import com.gemalto.eziomobilesampleapp.helpers.ezio.storage.SecureStorage;
import com.gemalto.idp.mobile.authentication.IdpAuthException;
import com.gemalto.idp.mobile.authentication.mode.face.FaceAuthInitializeCallback;
import com.gemalto.idp.mobile.authentication.mode.face.FaceAuthLicense;
import com.gemalto.idp.mobile.authentication.mode.face.FaceAuthLicenseConfigurationCallback;
import com.gemalto.idp.mobile.authentication.mode.face.FaceAuthService;
import com.gemalto.idp.mobile.authentication.mode.face.ui.FaceManager;
import com.gemalto.idp.mobile.core.ApplicationContextHolder;
import com.gemalto.idp.mobile.core.IdpCore;
import com.gemalto.idp.mobile.core.IdpException;
import com.gemalto.idp.mobile.core.SecurityDetectionService;
import com.gemalto.idp.mobile.core.passwordmanager.PasswordManager;
import com.gemalto.idp.mobile.core.passwordmanager.PasswordManagerException;
import com.gemalto.idp.mobile.core.util.SecureByteArray;
import com.gemalto.idp.mobile.core.util.SecureString;
import com.gemalto.idp.mobile.msp.MspConfiguration;
import com.gemalto.idp.mobile.msp.MspSignatureKey;
import com.gemalto.idp.mobile.oob.OobConfiguration;
import com.gemalto.idp.mobile.otp.OtpConfiguration;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main app singleton. With instances of all managers.
 * It is available whole app lifetime.
 */
public class Main {

    //region Defines

    private static Main sInstance = null;

    private boolean mInited = false;
    private Protocols.StorageProtocol mStorageSecure = null;
    private Protocols.StorageProtocol mStorageFast = null;
    private PushManager mManagerPush = null;
    private TokenManager mManagerToken = null;
    private QRCodeManager mManagerQRCode = null;
    private HttpManager mManagerHttp = null;
    private IdpCore mCore = null;
    private GemaloFaceIdState mGemaloFaceIdState = GemaloFaceIdState.GemaloFaceIdStateUndefined;
    private Protocols.GenericHandler mUiHandler;

    public enum GemaloFaceIdState {
        // Face Id service was not even started.
        GemaloFaceIdStateUndefined(R.string.GEMALTO_FACE_ID_STATE_UNDEFINED, android.R.color.holo_red_dark),
        // Face id is not supported
        GemaloFaceIdStateNotSupported(R.string.GEMALTO_FACE_ID_STATE_NOT_SUPPORTED, android.R.color.holo_red_dark),
        // Failed to registered.
        GemaloFaceIdStateUnlicensed(R.string.GEMALTO_FACE_ID_STATE_UNLICENSED, android.R.color.holo_red_dark),
        // Successfully registered.
        GemaloFaceIdStateLicensed(R.string.GEMALTO_FACE_ID_STATE_LICENSED, android.R.color.holo_orange_dark),
        // Failed to init service.
        GemaloFaceIdStateInitFailed(R.string.GEMALTO_FACE_ID_STATE_INIT_FAILED, android.R.color.holo_red_dark),
        // Registered and initialised.
        GemaloFaceIdStateInited(R.string.GEMALTO_FACE_ID_STATE_INITED, android.R.color.holo_orange_dark),
        // Registered, initialised and configured with at least one user enrolled.
        GemaloFaceIdStateReadyToUse(R.string.GEMALTO_FACE_ID_STATE_READY, android.R.color.holo_green_dark);

        GemaloFaceIdState(final int valueString, final int valueColor) {
            mValueString = valueString;
            mValueColor = valueColor;
        }

        private final int mValueString;
        private final int mValueColor;

        public int getValueString() {
            return mValueString;
        }

        public int getValueColor() {
            return mValueColor;
        }
    }

    //endregion

    //region Life Cycle

    /**
     * Gets the singleton instance of {@code Main}.
     *
     * @return Singleton instance of {@code Main}.
     */
    public static synchronized Main sharedInstance() {
        if (sInstance == null) {
            sInstance = new Main();
        }

        return sInstance;
    }

    /**
     * Unregisters the UI handler.
     */
    public synchronized void unregisterUiHandler() {
        mUiHandler = null;
    }

    /**
     * Initializes the main SDK.
     *
     * @param context Android application context.
     */
    public void init(final Context context) {
        // App context used in all sdk levels.
        ApplicationContextHolder.setContext(context);

        // Initialise basic managers without all permissions yet.
        mStorageFast = new SharedPreferences();
        mManagerPush = new PushManager();
        mManagerQRCode = new QRCodeManager();
        mManagerHttp = new HttpManager();
    }

    /**
     * Initializes the SDK after all mandatory permissions have been requested.
     *
     * @param handler Callback.
     */
    public void initWithPermissions(@NonNull final Protocols.GenericHandler handler) {

        // Sync handler will all all methods in ui thread.
        mUiHandler = new Protocols.GenericHandler.Sync(handler);

        SecurityDetectionService.setDebuggerDetection(false);

        // Make sure, that we will always check isConfigured first. Multiple call of init will cause crash / run time exception.
        if (IdpCore.isConfigured()) {
            if (mUiHandler != null) {
                mUiHandler.onFinished(true, null);
            }

            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (sInstance) {
                    mCore = IdpCore.configure(Configuration.getSdkActivationCode(),
                                              getConfigurationOob(),
                                              getConfigurationOtp(),
                                              getConfigurationMsp());
                }

                final SecureString password = mCore.getSecureContainerFactory().fromString("SecureStoragePassword");

                SecurityDetectionService.setDebuggerDetection(false);
                try {
                    // Login so we can use secure storage, OOB etc..
                    final PasswordManager passwordManager = mCore.getPasswordManager();
                    if (!passwordManager.isPasswordSet()) {
                        passwordManager.setPassword(password);
                    }
                    passwordManager.login(password);


                    // This will also register and activate licence.
                    FaceManager.initInstance();
                    Main.this.updateGemaltoFaceIdStatus();

                    synchronized (sInstance) {
                        // Init rest of the managers and update basic one with new permissions.
                        mStorageSecure = new SecureStorage();
                        mManagerToken = new TokenManager();
                        mManagerPush.initWithPermissions();

                        // Mark helper as prepared. So we don't have to call this ever again.
                        mInited = true;
                    }

                    // Notify handler
                    synchronized (sInstance) {
                        if (mUiHandler != null) {
                            mUiHandler.onFinished(true, null);
                        }
                    }
                } catch (PasswordManagerException | MalformedURLException e) {
                    synchronized (sInstance) {
                        if (mUiHandler != null) {
                            mUiHandler.onFinished(true, e.getLocalizedMessage());
                        }
                    }
                }
            }
        }).start();
    }

    //endregion

    //region Static Helpers

    /**
     * Creates a {@code SecureString} from {@code String}.
     *
     * @param value {@code String} input.
     *
     * @return {@code SecureString} output.
     */
    public synchronized SecureString secureStringFromString(final String value) {
        return mCore.getSecureContainerFactory().fromString(value);
    }

    /**
     * Creates a {@code SecureByteArray} from {@code byte[]}.
     *
     * @param value {@code byte[]} input.
     *
     * @return {@code SecureByteArray} output.
     */
    public synchronized SecureByteArray secureByteArrayFromBytes(final byte[] value, final boolean wipeSource) {
        return mCore.getSecureContainerFactory().createByteArray(value, wipeSource);
    }

    /**
     * Gets a string resource.
     *
     * @param aString Name.
     *
     * @return String resource.
     */
    public static String getStringByKeyName(final String aString) {
        final Context context = ApplicationContextHolder.getContext();
        final int resId = context.getResources().getIdentifier(aString, "string", context.getPackageName());
        if (resId != 0) {
            return getString(resId);
        } else {
            return getString(R.string.message_not_found);
        }
    }

    /**
     * Gets a string resource.
     *
     * @param stringId Id.
     *
     * @return String resource.
     */
    public static String getString(@StringRes final int stringId) {
        return ApplicationContextHolder.getContext().getString(stringId);
    }

    /**
     * Checks for runtime permission.
     *
     * @param activity Calling activity.
     * @param askForThem {@code True} if missing permission should be requested, else {@code false}.
     * @param permissions List of permissions.
     *
     * @return {@code True} if permissions are present, else {@code false}.
     */
    @TargetApi(23)
    public static boolean checkPermissions(final Activity activity,
                                           final boolean askForThem,
                                           final String... permissions) {

        // Old SDK version does not have dynamic permissions.
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }

        final List<String> permissionsToCheck = new ArrayList<>();

        for (final String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PermissionChecker.PERMISSION_GRANTED) {
                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                    Toast.makeText(activity, "Requesting permission - " + permission, Toast.LENGTH_LONG).show();
                }

                permissionsToCheck.add(permission);
            }
        }

        if (!permissionsToCheck.isEmpty() && askForThem) {
            ActivityCompat
                    .requestPermissions(activity, permissionsToCheck.toArray(new String[permissionsToCheck.size()]), 0);
        }

        return permissionsToCheck.isEmpty();
    }

    //endregion

    //region Public API


    /**
     * Check whenever SDK was successfully initialised with all permisions.
     * @return True in case that SDK is ready to use.
     */
    public synchronized boolean isInited() {
        return mInited;
    }

    /**
     * @return Instance of secure storage. (Ezio SecureStorage)
     */
    public synchronized Protocols.StorageProtocol getStorageSecure() {
        return mStorageSecure;
    }

    /**
     * @return Instance of fast insecure storage. (iOS UserDefaults)
     */
    public synchronized Protocols.StorageProtocol getStorageFast() {
        return mStorageFast;
    }

    /**
     * @return Manager used for handling all push related actions.
     */
    public synchronized PushManager getManagerPush() {
        return mManagerPush;
    }

    /**
     * @return Manager used for handling all token related actions.
     */
    public synchronized TokenManager getManagerToken() {
        return mManagerToken;
    }

    /**
     * @return Manager used for handling http communication.
     */
    public synchronized HttpManager getManagerHttp() {
        return mManagerHttp;
    }

    /**
     * @return Manager used for handling QR codes.
     */
    public QRCodeManager getManagerQRCode() {
        return mManagerQRCode;
    }

    /**
     * Gemalto face id does have multiple step async activation.
     * Check this value to see current state.
     *
     * @return Current state.
     */
    public synchronized GemaloFaceIdState getFaceIdState() {
        return mGemaloFaceIdState;
    }

    /**
     * Retrieves {@code IdpCore} instance.
     *
     * @return {@code IdpCore} instance.
     */
    public synchronized IdpCore getCore() {
        return mCore;
    }

    /**
     * Forces reload of gemalto face id status.
     */
    public synchronized void updateGemaltoFaceIdStatus() {
        final FaceAuthService faceIdService = FaceManager.getInstance().getFaceAuthService();

        if (!faceIdService.isSupported()) {
            setGemaloFaceIdState(GemaloFaceIdState.GemaloFaceIdStateNotSupported);
            return;
        }

        // Support sample app even without face id.
        String faceIdProdKey = Configuration.getFaceIdProductKey();
        String faceIdSrvUrl = Configuration.getFaceIdServerUrl();
        if (faceIdProdKey == null || faceIdProdKey.isEmpty() ||
                faceIdSrvUrl == null || faceIdSrvUrl.isEmpty()) {
            setGemaloFaceIdState(GemaloFaceIdState.GemaloFaceIdStateUnlicensed);
            return;
        }

        final FaceAuthLicense license = new FaceAuthLicense.Builder()
                .setProductKey(faceIdProdKey)
                .setServerUrl(faceIdSrvUrl)
                .build();

        faceIdService.configureLicense(license, new FaceAuthLicenseConfigurationCallback() {
            @Override
            public void onLicenseConfigurationSuccess() {
                setGemaloFaceIdState(GemaloFaceIdState.GemaloFaceIdStateLicensed);

                // Already inited.
                if (faceIdService.isInitialized()) {
                    updateGemaltoFaceIdStatusConfigured(faceIdService);
                } else {
                    // With license we can activate face id service.
                    faceIdService.initialize(new FaceAuthInitializeCallback() {
                        @Override
                        public String onInitializeCamera(final String[] strings) {
                            // Select one from the given list by returning null,
                            // the SDK will pick a default camera which will be:
                            // the first in the list which contains 'front'
                            // or the first one if no 'front' is found
                            return null;
                        }

                        @Override
                        public void onInitializeSuccess() {
                            updateGemaltoFaceIdStatusConfigured(faceIdService);
                        }

                        @Override
                        public void onInitializeError(final IdpException exception) {
                            setGemaloFaceIdState(GemaloFaceIdState.GemaloFaceIdStateInitFailed);
                        }
                    });
                }
            }

            @Override
            public void onLicenseConfigurationFailure(final IdpAuthException exception) {
                setGemaloFaceIdState(GemaloFaceIdState.GemaloFaceIdStateUnlicensed);
            }
        });
    }

    /**
     * Gets the current activity.
     *
     * @return Current activity.
     */
    @Nullable
    public MainActivity getCurrentListener() {
        final Activity currentActivity = ((EzioSampleApp) ApplicationContextHolder.getContext().getApplicationContext()).getCurrentActivity();
        if (currentActivity != null && (currentActivity instanceof MainActivity)) {
            return (MainActivity) currentActivity;
        } else {
            return null;
        }
    }

    //endregion

    //region Private Helpers

    /**
     * Gets the OTP configuration.
     *
     * @return OTP configuration.
     */
    private OtpConfiguration getConfigurationOtp() {
        // OTP module is required for token management and OTP calculation.
        return new OtpConfiguration.Builder().setRootPolicy(Configuration.getOtpRootPolicy()).build();
    }

    /**
     * Gets the OOB configuration.
     *
     * @return OOB configuration.
     */
    private OobConfiguration getConfigurationOob() {
        // OOB module is required for push notifications.
        return new OobConfiguration.Builder()
                // Device fingerprint is used for security reason. This way app can add some additional input for internal encryption mechanism.
                // This value must remain the same all the time. Otherwise all provisioned tokens will not be valid any more.
                .setDeviceFingerprintSource(Configuration.getSdkDeviceFingerprintSource())
                // Jailbreak policy for OOB module. See EMOobJailbreakPolicyIgnore for more details.
                .setRootPolicy(Configuration.getOobRootPolicy())
                // For debug and ONLY debug reasons we might lower some TLS configuration.
                .setTlsConfiguration(Configuration.getSdkTlsConfiguration()).build();
    }

    /**
     * Gets the MSP configuration.
     *
     * @return MSP configuration.
     */
    private MspConfiguration getConfigurationMsp() {
        // Mobile Signing Protocol QR parsing, push messages etc..
        final MspConfiguration.Builder builder = new MspConfiguration.Builder();

        // Set obfuscation
        List<byte[]> mspObfKeys = Configuration.getMspObfuscationCode();
        if (mspObfKeys != null) {
            builder.setObfuscationKeys(mspObfKeys);
        }
        // Set signature
        List<MspSignatureKey> mspSignKeys = Configuration.getMspSignKeys();
        if (mspSignKeys != null) {
            builder.setSignatureKeys(mspSignKeys);
        }

        return builder.build();
    }

    /**
     * Updates the Gemalto Face id status.
     *
     * @param service {@code FaceAuthService}.
     */
    private void updateGemaltoFaceIdStatusConfigured(final FaceAuthService service) {
        // Configured at this point mean, that there is at least one user enrolled.
        if (service.isConfigured()) {
            setGemaloFaceIdState(GemaloFaceIdState.GemaloFaceIdStateReadyToUse);
        } else {
            setGemaloFaceIdState(GemaloFaceIdState.GemaloFaceIdStateInited);
        }
    }

    /**
     * Sets the Gemalto Face id status.
     *
     * @param state Status.
     */
    private void setGemaloFaceIdState(final GemaloFaceIdState state) {
        if (mGemaloFaceIdState.equals(state)) {
            return;
        }

        mGemaloFaceIdState = state;

        final MainActivity listener = getCurrentListener();
        if (listener != null) {
            listener.updateFaceIdSupport();
        }
    }

    //endregion

}

package com.twiliovoicemodule;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.AssertionException;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import static com.twiliovoicemodule.fcm.VoiceCallFCMService.ACTION_FCM_TOKEN_REFRESHED;
import static com.twiliovoicemodule.fcm.VoiceCallFCMService.ACTION_INCOMING_CALL;
import static com.twiliovoicemodule.fcm.VoiceCallFCMService.ACTION_INCOMING_CALL_CANCELLED;

public class TwilioVoiceModuleModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private final ReactApplicationContext reactContext;
    private String accessToken = "";
    private HashMap<String, String> twiMLParams = new HashMap<>();
    private static final String DEVICE_REGISTERED = "deviceRegistered";
    private static final String DEVICE_NOT_REGISTERED = "deviceNotRegistered";
    private static final String CONNECTION_DID_CONNECT = "connectionDidConnect";
    private static final String CONNECTION_DID_DISCONNECT = "connectionDidDisconnect";
    private static final String CONNECTION_DID_FAILED = "connectionDidFailed";
    private static final String CONNECTION_DID_RINGING = "connectionDidRinging";
    private static final String CONNECTION_DID_RECONNECTING = "connectionDidReconnecting";
    private static final String CONNECTION_DID_RECONNECT = "connectionDidReconnect";
    private static final String CALL_INCOMING = "callIncoming";
    private static final String CALL_INCOMING_CANCELLED = "callIncomingCancelled";
    private static final int MIC_PERMISSION_REQUEST_CODE = 100;
    private Call call;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private int originalAudioMode = AudioManager.MODE_NORMAL;
    private ProximityManager proximityManager;
    private TokenRefreshedBroadcastReceiver tokenReceiver;
    private VoiceCallBroadcastReceiver callReceiver;
    private boolean receiversRegistered = false;
    private CallInvite incomingCall;

    public TwilioVoiceModuleModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        proximityManager = new ProximityManager(reactContext);
        this.reactContext.addLifecycleEventListener(this);
        audioManager = (AudioManager)this.reactContext.getSystemService(Context.AUDIO_SERVICE);
        tokenReceiver = new TokenRefreshedBroadcastReceiver();
        callReceiver = new VoiceCallBroadcastReceiver();
        registerReceivers();
    }

    @Override
    public String getName() {
        return "TwilioVoiceModule";
    }

    @Override
    public void onHostResume() {
        if (getCurrentActivity() != null) {
            getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }
        registerReceivers();
    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        disconnect();
        unsetAudioFocus();
    }

    @ReactMethod
    public void initWithToken(String token, Promise promise) {
        if (token.isEmpty()) {
            promise.reject(new JSApplicationIllegalArgumentException("Invalid token provided"));
            return;
        }
        if (!checkMicrophonePermission()) {
            requestMicrophonePermission();
            promise.reject(new AssertionException("Microphone permission required"));
            return;
        }
        registerForCallInvites();
        this.accessToken = token;
        WritableMap params = Arguments.createMap();
        params.putBoolean("initialized", true);
        promise.resolve(params);
    }

    private boolean checkMicrophonePermission() {
        return ContextCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED;
    }

    @ReactMethod
    public void connect(ReadableMap params) {
        WritableMap errParams = Arguments.createMap();
        if (params == null) {
            errParams.putString("error", "Parameters should not be null");
            sendEvent(CONNECTION_DID_FAILED, errParams);
            return;
        }
        if (!params.hasKey("To")) {
            errParams.putString("error", "Callee should not be null");
            sendEvent(CONNECTION_DID_FAILED, errParams);
            return;
        }
        if (accessToken.isEmpty()) {
            errParams.putString("error", "Access token should not be null");
            sendEvent(CONNECTION_DID_FAILED, errParams);
            return;
        }

        twiMLParams.clear();
        ReadableMapKeySetIterator iterator = params.keySetIterator();
        while(iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType readableType = params.getType(key);
            switch (readableType) {
                case Null:
                    twiMLParams.put(key, "");
                    break;
                case Boolean:
                    twiMLParams.put(key, String.valueOf(params.getBoolean(key)));
                    break;
                case Number:
                    twiMLParams.put(key, String.valueOf(params.getDouble(key)));
                    break;
                case String:
                    twiMLParams.put(key, params.getString(key));
                    break;
                default:
                    errParams.putString("error", "Could not convert with key: " + key);
                    sendEvent(CONNECTION_DID_FAILED, errParams);
                    return;
            }
        }

        ConnectOptions connOptions = new ConnectOptions.Builder(accessToken).params(twiMLParams).build();
        this.call = Voice.connect(reactContext, connOptions, callListener);
    }

    @ReactMethod
    public void disconnect() {
        if (this.call != null) {
            this.call.disconnect();
        }
    }

    @ReactMethod
    public void getVersion(Callback callback) {
        callback.invoke(Voice.getVersion());
    }

    @ReactMethod
    public void getActiveCall(Promise promise) {
        if (this.call != null) {
            promise.resolve(getCallParams(this.call));
        } else {
            promise.reject("400", "There is no active call");
        }
    }

    @ReactMethod
    public void setSpeakerPhone(Boolean isOn) {
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(isOn);
        }
    }

    @ReactMethod
    public void setMuted(Boolean isMuted) {
        if (this.call != null) {
            this.call.mute(isMuted);
        }
    }

    @ReactMethod
    public void sendDigits(String digits) {
        if (this.call != null) {
            this.call.sendDigits(digits);
        }
    }

    @ReactMethod
    public void accept() {
        if (incomingCall != null) {
            incomingCall.accept(reactContext, callListener);
        }
    }

    @ReactMethod
    public void reject() {
        if (incomingCall != null) {
            incomingCall.reject(reactContext);
        }
    }

    private WritableMap getCallParams(Call call) {
        WritableMap params = Arguments.createMap();
        if (call != null) {
            params.putString("call_sid", call.getSid());
            params.putString("call_state", call.getState().name());
            params.putString("call_from", call.getFrom());
            params.putString("call_to", call.getTo());
        }
        return params;
    }

    private WritableMap getCallErrors(CallException exception) {
        WritableMap errParams = Arguments.createMap();
        if (exception != null) {
            errParams.putInt("error_code", exception.getErrorCode());
            errParams.putString("error_message", exception.getLocalizedMessage());
        }
        return errParams;
    }

    private void registerForCallInvites() {
        FirebaseApp.initializeApp(reactContext);
        FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
            @Override
            public void onSuccess(InstanceIdResult instanceIdResult) {
                String fcmToken = instanceIdResult.getToken();
                if (fcmToken != null) {
                    Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
                }
            }
        });
    }

    RegistrationListener registrationListener = new RegistrationListener() {
        @Override
        public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
            sendEvent(DEVICE_REGISTERED, null);
        }

        @Override
        public void onError(@NonNull RegistrationException registrationException, @NonNull String accessToken, @NonNull String fcmToken) {
            WritableMap params = Arguments.createMap();
            params.putString("error", registrationException.getMessage());
            sendEvent(DEVICE_NOT_REGISTERED, params);
        }
    };

    Call.Listener callListener = new Call.Listener() {
        @Override
        public void onConnectFailure(@NonNull Call call, @NonNull CallException callException) {
            unsetAudioFocus();
            proximityManager.stopProximityDetector();
            WritableMap errParams = getCallErrors(callException);
            TwilioVoiceModuleModule.this.call = null;
            sendEvent(CONNECTION_DID_FAILED, errParams);
        }

        @Override
        public void onRinging(@NonNull Call call) {
            WritableMap params = getCallParams(call);
            TwilioVoiceModuleModule.this.call = call;
            sendEvent(CONNECTION_DID_RINGING, params);
        }

        @Override
        public void onConnected(@NonNull Call call) {
            setAudioFocus();
            proximityManager.startProximityDetector();
            TwilioVoiceModuleModule.this.call = call;
            WritableMap params = getCallParams(call);
            sendEvent(CONNECTION_DID_CONNECT, params);
        }

        @Override
        public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
            TwilioVoiceModuleModule.this.call = call;
            WritableMap errParams = getCallErrors(callException);
            sendEvent(CONNECTION_DID_RECONNECTING, errParams);
        }

        @Override
        public void onReconnected(@NonNull Call call) {
            setAudioFocus();
            proximityManager.startProximityDetector();
            TwilioVoiceModuleModule.this.call = call;
            WritableMap params = getCallParams(call);
            sendEvent(CONNECTION_DID_RECONNECT, params);
        }

        @Override
        public void onDisconnected(@NonNull Call call, @android.support.annotation.Nullable CallException callException) {
            unsetAudioFocus();
            proximityManager.stopProximityDetector();
            TwilioVoiceModuleModule.this.call = null;
            WritableMap params = getCallParams(call);
            if (callException != null) {
                params.putInt("error_code", callException.getErrorCode());
                params.putString("error_message", callException.getMessage());
            }
            sendEvent(CONNECTION_DID_DISCONNECT, params);
        }
    };

    private void setAudioFocus() {
        if (audioManager == null) {
            return;
        }
        originalAudioMode = audioManager.getMode();
        if (Build.VERSION.SDK_INT >= 26) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            );
        }
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {

        }
    };

    private void unsetAudioFocus() {
        if (audioManager == null) {
            return;
        }
        audioManager.setMode(originalAudioMode);
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }

    private void requestMicrophonePermission() {
        if (getCurrentActivity() != null) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), Manifest.permission.RECORD_AUDIO)) {

            } else {
                ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void registerReceivers() {
        if (!receiversRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_INCOMING_CALL);
            LocalBroadcastManager.getInstance(reactContext).registerReceiver(callReceiver, intentFilter);
            registerTokenRefreshedReceiver();
            receiversRegistered = true;
        }
    }

    private void registerTokenRefreshedReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_FCM_TOKEN_REFRESHED);
        LocalBroadcastManager.getInstance(reactContext).registerReceiver(tokenReceiver, intentFilter);
    }

    private class TokenRefreshedBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == ACTION_FCM_TOKEN_REFRESHED) {
                String fcmToken = intent.getStringExtra("fcm_token");
                if (fcmToken != null) {
                    Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
                }
            }
        }
    }

    private class VoiceCallBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == ACTION_INCOMING_CALL) {
                WritableMap params = Arguments.createMap();
                Bundle extras = intent.getExtras();
                incomingCall = extras.getParcelable("call_invite");
                params.putString("call_sid", incomingCall.getCallSid());
                params.putString("call_from", incomingCall.getFrom());
                params.putString("call_to", incomingCall.getTo());
                Map<String, String> customParams = incomingCall.getCustomParameters();
                Iterator<String> iterator = customParams.keySet().iterator();
                while(iterator.hasNext()) {
                    String key = iterator.next();
                    params.putString(key, customParams.get(key));
                }
                sendEvent(CALL_INCOMING, params);
            } else if (intent.getAction() == ACTION_INCOMING_CALL_CANCELLED) {
                incomingCall = null;
                WritableMap params = Arguments.createMap();
                Bundle extras = intent.getExtras();
                CancelledCallInvite cancelledCallInvite = extras.getParcelable("cancelled_call_invite");
                params.putString("call_sid", cancelledCallInvite.getCallSid());
                params.putString("call_from", cancelledCallInvite.getFrom());
                params.putString("call_to", cancelledCallInvite.getTo());
                sendEvent(CALL_INCOMING_CANCELLED, params);
            }
        }
    }
}

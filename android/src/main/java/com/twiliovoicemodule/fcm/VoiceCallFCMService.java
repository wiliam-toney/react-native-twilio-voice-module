package com.twiliovoicemodule.fcm;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactContext;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

import java.util.Map;

public class VoiceCallFCMService extends FirebaseMessagingService {
    public static final String ACTION_FCM_TOKEN_REFRESHED = "fcmTokenRefreshed";
    public static final String ACTION_INCOMING_CALL = "incomingCall";
    public static final String ACTION_INCOMING_CALL_CANCELLED = "incomingCallCancelled";
    @Override
    public void onCreate() {

    }

    @Override
    public void onNewToken(String token) {
        Intent intent = new Intent(ACTION_FCM_TOKEN_REFRESHED);
        intent.putExtra("fcm_token", token);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            Voice.handleMessage(data, new MessageListener() {
                @Override
                public void onCallInvite(@NonNull CallInvite callInvite) {
                    Intent i = new Intent(ACTION_INCOMING_CALL);
                    i.putExtra("call_invite", callInvite);
                    ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                    ReactContext context = mReactInstanceManager.getCurrentReactContext();
                    LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                }

                @Override
                public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite) {
                    Intent i = new Intent(ACTION_INCOMING_CALL_CANCELLED);
                    i.putExtra("cancelled_call_invite", cancelledCallInvite);
                    ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                    ReactContext context = mReactInstanceManager.getCurrentReactContext();
                    LocalBroadcastManager.getInstance(context).sendBroadcast(i);
                }
            });
        }
    }
}

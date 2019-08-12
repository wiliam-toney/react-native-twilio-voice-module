# react-native-twilio-voice-module
This is twilio programmable voice module for react native that makes you allow to make inbound / outbound calls.

This is working with version 4.1.0 of twilio native ios / android sdks. (Version 2.x and 3.x will be deprecated at the end of 2019.)

## Getting started

`$ npm install react-native-twilio-voice-module --save`

`$ yarn add react-native-twilio-voice-module`

### Mostly automatic installation

`$ react-native link react-native-twilio-voice-module`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-twilio-voice-module` and add `TwilioVoiceModule.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libTwilioVoiceModule.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.reactlibrary.TwilioVoiceModulePackage;` to the imports at the top of the file
  - Add `new TwilioVoiceModulePackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-twilio-voice-module'
  	project(':react-native-twilio-voice-module').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-twilio-voice-module/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-twilio-voice-module')
  	```
## Permissions
1. For iOS, you have to add Microphone usage description in info.plist.

2. For Android, open AndroidManifest.xml file and add the following permissions.

`<uses-permission android:name="android.permission.RECORD_AUDIO" />`

`<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />`

`<uses-feature android:name="android.hardware.sensor.proximity" android:required="true" />`

Next under `<application>` tag, add the firebase messaging service

``` <service android:name="com.twiliovoicemodule.fcm.VoiceCallFCMService">

          <intent-filter>

              <action android:name="com.google.firebase.MESSAGING_EVENT"/>

          </intent-filter>

    </service>
```

## Usage
You will be required to follow [iOS Quickstart step 2 ~ step 9](https://github.com/twilio/voice-quickstart-objc#2-create-a-voice-api-key)
and [Android Quickstart step 2 ~ step 9](https://github.com/twilio/voice-quickstart-android#2-create-a-voice-api-key) to gain access token and
to receive call invite.

Here are supported event listeners:
1. `deviceRegistered` - Device is registered successfully to receive call incoming. It means FCM token is registered for android and device token is registered for iOS
2. `deviceNotRegistered` - Device is not registered. Maybe invalid token issue.
3. `connectionDidConnect` - Call has connected
4. `connectionDidFailed` - Call has failed to connect
5. `connectionDidRinging` - Called party is being alerted of a Call
6. `connectionDidReconnecting` - Call starts to reconnect due to network change event
7. `connectionDidReconnect` - Call is reconnected
8. `connectionDidDisconnect` - Call has disconnected
9. `callIncoming` - A call is incoming to your device
10. `callIncomingCancelled` - A incoming call is cancelled

You should add these event listeners before you intialize TwilioVoiceModule with access token.

```javascript
import TwilioVoiceModule from 'react-native-twilio-voice-module';

componentDidMount() {
  TwilioVoiceModule.addEventListener('deviceRegistered', this.deviceRegistered);
  TwilioVoiceModule.addEventListener('connectionDidConnect', this.connectionDidConnect);
  // Add more listeners

  TwilioVoiceModule.initWithToken(token).then(res => {
    // true if initialized,
    // false if something went wrong
  });  
}

componentWillUnmount() {
  TwilioVoiceModule.removeEventListener('deviceRegistered', this.deviceRegistered);
  TwilioVoiceModule.removeEventListener('connectionDidConnect', this.connectionDidConnect);
  // Remove added listeners
}

deviceRegistered = () => {
  // Now your device is allowed to receive incoming inbound calls.
}

connectionDidConnect = (call) => {
  // call.call_sid
  // call.call_state
  // call.call_from
  // call.call_to
}

```

Now you are ready to make a call.
```javascript
TwilioVoiceModule.addEventListener('callIncoming', this.callIncoming);
TwilioVoiceModule.addEventListener('callIncomingCancelled', this.callIncomingCancelled);


// outbound call example
TwilioVoiceModule.connect({
  To: '+123456789',
  From: '+987654321'
});

//inbound call example
TwilioVoiceModule.connect({
  To: 'John'
});

callIncoming = (call) => {
  // ... maybe open dialog with accept / reject button

  // Accept the call
  TwilioVoiceModule.accept();

  // Reject the call
  TwilioVoiceModule.reject();
}

callIncomingCancelled = (cancelledCall) => {

}

```

To disconnect call, just call `TwilioVoiceModule.disconnect()`

### Other APIs that you may need.
```javascript
TwilioVoiceModule.getVersion((version) => {
  // Twilio sdk version. Actually 4.1.0
});

const activeCall = await TwilioVoiceModule.getActiveCall();

TwilioVoiceModule.setMuted(true); // mute the sound

TwilioVoiceModule.setSpeakerPhone(true); // set the speaker on.

```

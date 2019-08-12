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


## Usage
You will be required to follow [iOS Quickstart step 2 ~ step 9](https://github.com/twilio/voice-quickstart-objc#2-create-a-voice-api-key)
and [Android Quickstart step 2 ~ step 9](https://github.com/twilio/voice-quickstart-android#2-create-a-voice-api-key) to gain access token and
to receive call invite.

After you have followed the above steps, you can init TwilioVoiceModule with access token to make a call.
```javascript
import TwilioVoiceModule from 'react-native-twilio-voice-module';

// TODO: What to do with the module?
TwilioVoiceModule.initWithToken(token).then(res => {
  // true if initialized,
  // false if something went wrong
});
```

# react-native-twilio-voice-module

## Getting started

`$ npm install react-native-twilio-voice-module --save`

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
```javascript
import TwilioVoiceModule from 'react-native-twilio-voice-module';

// TODO: What to do with the module?
TwilioVoiceModule;
```

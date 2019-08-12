import {
  NativeModules,
  NativeEventEmitter,
  Platform
} from 'react-native';

const { TwilioVoiceModule } = NativeModules;

const _eventEmitter = new NativeEventEmitter(TwilioVoiceModule);

const _eventHandlers = {
  callIncoming: new Map(),
  callIncomingCancelled: new Map(),
  deviceRegistered: new Map(),
  deviceNotRegistered: new Map(),
  connectionDidConnect: new Map(),
  connectionDidDisconnect: new Map(),
  connectionDidFailed: new Map(),
  connectionDidRinging: new Map(),
  connectionDidReconnecting: new Map(),
  connectionDidReconnect: new Map()
}

const Twilio = {
  initWithToken(token) {
    if (typeof token !== 'string') {
      return {
        initialized: false,
        err: 'Invalid token, token must be a string'
      }
    }
    return TwilioVoiceModule.initWithToken(token);
  },

  connect(params = {}) {
    TwilioVoiceModule.connect(params);
  },

  disconnect() {
    TwilioVoiceModule.disconnect();
  },

  setMuted(isMuted) {
    TwilioVoiceModule.setMuted(isMuted);
  },

  setSpeakerPhone(isSpeaker) {
    TwilioVoiceModule.setSpeakerPhone(isSpeaker);
  },

  getVersion(callback) {
    TwilioVoiceModule.getVersion(callback);
  },

  getActiveCall() {
    return TwilioVoiceModule.getActiveCall();
  },

  accept() {
    TwilioVoiceModule.accept();
  },

  reject() {
    TwilioVoiceModule.reject();
  },

  addEventListener(type, handler) {
    if (_eventHandlers[type].has(handler)) {
      return;
    }
    _eventHandlers[type].set(handler, _eventEmitter.addListener(type, rtn => { handler(rtn) }));
  },

  removeEventListener(type, handler) {
    if (!_eventHandlers[type].has(handler)) {
      return;
    }
    _eventHandlers[type].get(handler).remove();
    _eventHandlers[type].delete(handler);
  }
}

export default Twilio;

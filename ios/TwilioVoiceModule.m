#import "TwilioVoiceModule.h"

#import <AFNetworking/AFNetworking.h>
@import PushKit;
@import TwilioVoice;

static NSString *const kAccessTokenUrl = @"http://stepintocity-server.herokuapp.com/twilio/accessToken";
static NSString *const StatePending = @"PENDING";
static NSString *const StateConnecting = @"CONNECTING";
static NSString *const StateConnected = @"CONNECTED";
static NSString *const StateRinging = @"RINGING";
static NSString *const StateReconnecting = @"RECONNECTING";
static NSString *const StateDisconnected = @"DISCONNECTED";

API_AVAILABLE(ios(8.0))
@interface TwilioVoiceModule()<TVOCallDelegate, TVONotificationDelegate, PKPushRegistryDelegate>
@property (nonatomic, strong) TVOCall *call;
@property (nonatomic, strong) PKPushRegistry *voipRegistry;
@property (nonatomic, strong) TVOCallInvite *callInvite;
@end

@implementation TwilioVoiceModule {
    NSString* _accessToken;
    NSString* _deviceToken;
    bool _hasListeners;
}

RCT_EXPORT_MODULE()

-(NSArray<NSString *> *)supportedEvents
{
    return @[@"deviceRegistered", @"deviceNotRegistered", @"connectionDidConnect", @"connectionDidDisconnect", @"connectionDidFailed",
             @"connectionDidRinging", @"connectionDidReconnecting", @"connectionDidReconnect", @"callIncoming", @"callIncomingCancelled"];
}

// Will be called when this module's first listener is added.
-(void)startObserving {
    _hasListeners = YES;
}

// Will be called when this module's last listener is removed, or on dealloc.
-(void)stopObserving {
    _hasListeners = NO;
}

-(void)sendEvent:(NSString*)eventName eventBody:(id)body
{
    if (_hasListeners) {
        [self sendEventWithName:eventName body:body];
    }
}

-(NSString *)fetchAccessToken
{
    NSString *accessTokenUrlString = [NSString stringWithFormat:@"%@/identity=%@&platform=ios", kAccessTokenUrl, @"alice"];
    NSString *accessToken = [NSString stringWithContentsOfURL:[NSURL URLWithString:accessTokenUrlString] encoding:NSUTF8StringEncoding error:nil];
    return accessToken;
}

RCT_EXPORT_METHOD(initWithToken:(NSString *)token resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if ([token isEqualToString:@""]) {
        reject(@"400", @"Invalid token detected", nil);
        return;
    }
    
    if (![self checkRecordPermission]) {
        [self requestRecordPermission:^(BOOL granted) {
            if (!granted) {
                reject(@"400", @"Record permission is required to be initialized", nil);
            } else {
                [self registerForCallInvite];
                self->_accessToken = token;
                resolve(@{@"initialized": @true});
            }
        }];
    } else {
        [self registerForCallInvite];
        _accessToken = token;
        resolve(@{@"initialized": @true});
    }
    
}

RCT_EXPORT_METHOD(connect:(NSDictionary *)params)
{
    if (self.call && self.call.state == TVOCallStateConnected) {
        [self.call disconnect];
    } else {
        TVOConnectOptions *connectOptions = [TVOConnectOptions optionsWithAccessToken:_accessToken block:^(TVOConnectOptionsBuilder * _Nonnull builder) {
            builder.params = params;
            // builder.uuid = [NSUUID UUID];
        }];
        self.call = [TwilioVoice connectWithOptions:connectOptions delegate:self];
    }
}

RCT_EXPORT_METHOD(disconnect)
{
    if (self.call) {
        [self.call disconnect];
    }
}

RCT_EXPORT_METHOD(setMuted:(BOOL)isMuted)
{
    if (self.call) {
        self.call.muted = isMuted;
    }
}

RCT_EXPORT_METHOD(setSpeakerPhone:(BOOL)isSpeaker)
{
    NSError *error = nil;
    if (isSpeaker) {
        if (![[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker
                                                                error:&error]) {
            NSLog(@"Turn on speaker error: %@", [error localizedDescription]);
        }
    } else {
        if (![[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideNone
                                                                error:&error]) {
            NSLog(@"Turn off speaker error: %@", [error localizedDescription]);
        }
    }
}

RCT_EXPORT_METHOD(sendDigits:(NSString *)digits)
{
    if (self.call) {
        [self.call sendDigits:digits];
    }
}

RCT_EXPORT_METHOD(getVersion:(RCTResponseSenderBlock)callback)
{
    NSString *version = [TwilioVoice sdkVersion];
    callback(@[version]);
}

RCT_EXPORT_METHOD(getActiveCall:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (self.call) {
       resolve([self paramsForCall:self.call]);
    } else {
        reject(@"400", @"There is no active call", nil);
    }
}

RCT_EXPORT_METHOD(accept)
{
    if (self.callInvite) {
        [self.callInvite acceptWithDelegate:self];
    }
}

RCT_EXPORT_METHOD(reject)
{
    if (self.callInvite) {
        [self.callInvite reject];
    }
}

- (BOOL)checkRecordPermission
{
    AVAudioSessionRecordPermission permissionStatus = [[AVAudioSession sharedInstance] recordPermission];
    return permissionStatus == AVAudioSessionRecordPermissionGranted;
}

- (void)requestRecordPermission:(void(^)(BOOL))completion
{
    [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
        completion(granted);
    }];
}

- (NSMutableDictionary *)paramsForCall:(TVOCall *)call
{
    NSMutableDictionary *callParams = [[NSMutableDictionary alloc] init];
    [callParams setObject:call.sid forKey:@"call_sid"];
    if (call.state == TVOCallStateConnecting) {
        [callParams setObject:StateConnecting forKey:@"call_state"];
    } else if (call.state == TVOCallStateConnected) {
        [callParams setObject:StateConnected forKey:@"call_state"];
    } else if (call.state == TVOCallStateRinging) {
        [callParams setObject:StateRinging forKey:@"call_state"];
    } else if (call.state == TVOCallStateReconnecting) {
        [callParams setObject:StateReconnecting forKey:@"call_state"];
    } else if (call.state == TVOCallStateDisconnected) {
        [callParams setObject:StateDisconnected forKey:@"call_state"];
    }
    
    if (call.from) {
        [callParams setObject:call.from forKey:@"call_from"];
    }
    if (call.to) {
        [callParams setObject:call.to forKey:@"call_to"];
    }
    
    return callParams;
}


- (NSMutableDictionary *)paramsForError:(NSError *)error {
    NSMutableDictionary *params = [self paramsForCall:self.call];
    
    if (error) {
        NSMutableDictionary *errorParams = [[NSMutableDictionary alloc] init];
        if (error.code) {
            [errorParams setObject:[@([error code]) stringValue] forKey:@"code"];
        }
        if (error.domain) {
            [errorParams setObject:[error domain] forKey:@"domain"];
        }
        if (error.localizedDescription) {
            [errorParams setObject:[error localizedDescription] forKey:@"message"];
        }
        if (error.localizedFailureReason) {
            [errorParams setObject:[error localizedFailureReason] forKey:@"reason"];
        }
        [params setObject:errorParams forKey:@"error"];
    }
    return params;
}

- (void)registerForCallInvite
{
    dispatch_queue_t mainQueue = dispatch_get_main_queue();
    self.voipRegistry = [[PKPushRegistry alloc] initWithQueue:mainQueue];
    self.voipRegistry.delegate = self;
    self.voipRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
}

#pragma mark - TVOCallDelegate methods
- (void)call:(nonnull TVOCall *)call didDisconnectWithError:(nullable NSError *)error
{
    [UIDevice currentDevice].proximityMonitoringEnabled = NO;
    NSMutableDictionary *params = [self paramsForError:error];
    [self sendEvent:@"connectionDidDisconnect" eventBody:params];
    self.call = nil;
}

- (void)call:(nonnull TVOCall *)call didFailToConnectWithError:(nonnull NSError *)error
{
    [UIDevice currentDevice].proximityMonitoringEnabled = NO;
    NSMutableDictionary *params = [self paramsForError:error];
    [self sendEvent:@"connectionDidFailed" eventBody:params];
    self.call = nil;
}

- (void)callDidConnect:(nonnull TVOCall *)call
{
    self.call = call;
    [UIDevice currentDevice].proximityMonitoringEnabled = YES;
    if (self.callInvite) {
        self.callInvite = nil;
    }
    NSMutableDictionary *paramsForCall = [self paramsForCall:call];
    [self sendEvent:@"connectionDidConnect" eventBody:paramsForCall];
}

- (void)callDidStartRinging:(nonnull TVOCall *)call
{
    self.call = call;
    NSMutableDictionary *paramsForCall = [self paramsForCall:call];
    [self sendEvent:@"connectionDidRinging" eventBody:paramsForCall];
}

- (void)call:(nonnull TVOCall *)call isReconnectingWithError:(nonnull NSError *)error
{
    self.call = call;
    NSMutableDictionary *paramsForCall = [self paramsForError:error];
    [self sendEvent:@"connectionDidReconnecting" eventBody:paramsForCall];
}

- (void)callDidReconnect:(nonnull TVOCall *)call
{
    [UIDevice currentDevice].proximityMonitoringEnabled = YES;
    self.call = call;
    if (self.callInvite) {
        self.callInvite = nil;
    }
    NSMutableDictionary *paramsForCall = [self paramsForCall:call];
    [self sendEvent:@"connectionDidReconnect" eventBody:paramsForCall];
}

#pragma mark - PKPushRegistryDelegate
- (void)pushRegistry:(nonnull PKPushRegistry *)registry didUpdatePushCredentials:(nonnull PKPushCredentials *)pushCredentials forType:(nonnull PKPushType)type
{
    if ([type isEqualToString:PKPushTypeVoIP]) {
        _deviceToken = pushCredentials.token.description;
        if (_accessToken && _deviceToken) {
            [TwilioVoice registerWithAccessToken:_accessToken deviceToken:_deviceToken completion:^(NSError * _Nullable error) {
                if (error) {
                    NSMutableDictionary *errParams = [self paramsForError:error];
                    [self sendEvent:@"deviceNotRegistered" eventBody:errParams];
                } else {
                    [self sendEvent:@"deviceRegistered" eventBody:nil];
                }
            }];
        }
    }
}

- (void)pushRegistry:(PKPushRegistry *)registry didInvalidatePushTokenForType:(PKPushType)type
{
    if ([type isEqualToString:PKPushTypeVoIP]) {
        if (_accessToken && _deviceToken) {
            [TwilioVoice unregisterWithAccessToken:_accessToken deviceToken:_deviceToken completion:^(NSError * _Nullable error) {
                if (!error) {
                    NSMutableDictionary *errParams = [[NSMutableDictionary alloc] init];
                    [errParams setObject:@"Did invalid push token for type" forKey:@"reason"];
                    [self sendEvent:@"deviceNotRegistered" eventBody:errParams];
                }
            }];
        }
    }
}

- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(PKPushType)type withCompletionHandler:(void(^)(void))completion
{
    if ([type isEqualToString:PKPushTypeVoIP]) {
        [TwilioVoice handleNotification:payload.dictionaryPayload delegate:self];
        completion();
    }
}

// deprecated for iOS 8 ~ iOS 11
- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(PKPushType)type
{
    if ([type isEqualToString:PKPushTypeVoIP]) {
        [TwilioVoice handleNotification:payload.dictionaryPayload delegate:self];
    }
}

- (void)callInviteReceived:(nonnull TVOCallInvite *)callInvite {
    self.callInvite = callInvite;
    NSMutableDictionary * params = [[NSMutableDictionary alloc] init];
    [params setObject:callInvite.callSid forKey:@"call_sid"];
    [params setObject:callInvite.from forKey:@"call_from"];
    [params setObject:callInvite.to forKey:@"call_to"];
    NSDictionary<NSString *, NSString *> *customParams = callInvite.customParameters;
    NSArray<NSString *> *allKeys = [customParams allKeys];
    [allKeys enumerateObjectsUsingBlock:^(NSString * _Nonnull obj, NSUInteger idx, BOOL * _Nonnull stop) {
        [params setObject:[customParams objectForKey:obj] forKey:obj];
    }];
    [self sendEvent:@"callIncoming" eventBody:params];
}

- (void)cancelledCallInviteReceived:(nonnull TVOCancelledCallInvite *)cancelledCallInvite {
    self.callInvite = nil;
    NSMutableDictionary * params = [[NSMutableDictionary alloc] init];
    [params setObject:cancelledCallInvite.callSid forKey:@"call_sid"];
    [params setObject:cancelledCallInvite.from forKey:@"call_from"];
    [params setObject:cancelledCallInvite.to forKey:@"call_to"];
    [self sendEvent:@"callIncomingCancelled" eventBody:params];
}

@end

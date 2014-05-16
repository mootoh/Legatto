//
//  XNNearBy.m
//  BTWithAndroid
//
//  Created by Motohiro Takayama on 5/13/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import "XNNearBy.h"

@interface XNPeerId ()
@property (nonatomic, strong) NSUUID *uuid;
- (id) initWithIdentifier:(NSUUID *)uuid;
@end

enum {
    STATE_SUBSCRIBED = 0x01,
    STATE_IDENTIFIER_RECEIVED = 0x02
};

enum {
    HEADER_KEY_NORMAL = 0x03,
    HEADER_KEY_URL    = 0x05
};

@interface XNAdvertiser ()
@property (readonly, nonatomic, strong) CBPeripheralManager *cbPeripheralManager;
@property (nonatomic, strong) CBMutableService *service;
@property (nonatomic, strong) NSMutableDictionary *sessions;
@property (nonatomic, strong) NSMutableDictionary *peers;
@property (nonatomic, strong) NSMutableDictionary *subscribedPeers;
@property (nonatomic, strong) CBCharacteristic *controlCharacteristic;
@property int state;
@end

@interface XNAdvertiser (Private)
- (CBMutableService *) setupService;
@end

#pragma mark - XNPeerId

@implementation XNPeerId
- (id) initWithIdentifier:(NSUUID *)uuid {
    self = [super init];
    if (self) {
        self.uuid = uuid;
    }
    return self;
}

- (id)copyWithZone:(NSZone *)zone {
    id copy = [[[self class] alloc] init];
    ((XNPeerId *)copy).uuid = [self.uuid copyWithZone:zone];
    return copy;
}

@end

#pragma mark - XNSession

@interface XNSession ()
@property (nonatomic, weak) XNAdvertiser *advertiser;
@end

@implementation XNSession

- (id) initWithAdvertiser:(XNAdvertiser *)advertiser {
    self = [super init];
    if (self) {
        self.advertiser = advertiser;
    }
    return self;
}

- (void) send:(NSData *)data to:(XNPeerId *)peer as:(NSInteger)key {
    // hash data into chunks of 20 octets
    NSUInteger len = data.length;
    uint32_t ulen = (uint32_t)len;
    
    // header
    char buf[8];
    buf[0] = key;
    memcpy(buf+1, &ulen, sizeof(ulen));
    NSData *header = [NSData dataWithBytes:buf length:5];
    
    if (! [self.advertiser.cbPeripheralManager updateValue:header forCharacteristic:peer.characteristic onSubscribedCentrals:nil]) {
        NSLog(@"failed to send header data");
    }
    
    // body
    NSUInteger loc = 0;
    dispatch_time_t notifyAt = DISPATCH_TIME_NOW;
    while (len > 0) {
        notifyAt = dispatch_time(notifyAt, (int64_t)(100 * NSEC_PER_MSEC)); // tricky here, since BT LE queue can be easily fulled up.
        
        dispatch_after(notifyAt, dispatch_get_main_queue(), ^{
            NSUInteger lengthToSend = MIN(20, len);
            NSData *chunk = [data subdataWithRange:NSMakeRange(loc, lengthToSend)];
            if (! [self.advertiser.cbPeripheralManager updateValue:chunk forCharacteristic:peer.characteristic onSubscribedCentrals:nil]) {
                NSLog(@"failed to send body data %d, %d %d", loc, lengthToSend, len);
            }
        });
        loc += 20;
        len = ((NSInteger)len - 20 < 0) ? 0 : len-20;
    }
}

- (void) send:(NSData *)data to:(XNPeerId *)peer {
    [self send:data to:peer as:HEADER_KEY_NORMAL];
}

- (void) sendURL:(NSURL *)url to:(XNPeerId *)peer {
    NSData *data = [[url absoluteString] dataUsingEncoding:NSUTF8StringEncoding];
    [self send:data to:peer as:HEADER_KEY_URL];
}

@end

#pragma mark - XNAdvertiser

@implementation XNAdvertiser

- (id) init {
    self = [super init];
    if (self) {
        _cbPeripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:nil];
        self.sessions = [NSMutableDictionary dictionary];
        self.peers = [NSMutableDictionary dictionary];
        self.subscribedPeers = [NSMutableDictionary dictionary];
        self.state = 0;
    }
    return self;
}

- (void) checkReady:(XNPeerId *)peer session:(XNSession *)session {
    if (self.state & STATE_SUBSCRIBED && self.state & STATE_IDENTIFIER_RECEIVED) {
        if ([self.delegate respondsToSelector:@selector(didConnect:session:)]) {
            [self.delegate didConnect:peer session:session];
        }
    }
}

#pragma mark CBPeripheralManagerDelegate

- (void) peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral {
    NSLog(@"peripheral manager status update: %d", peripheral.state);
    if (peripheral.state == CBPeripheralManagerStatePoweredOn) {
        NSLog(@"peripheral state powered on");
        
        self.service = [self setupService];
        [self.cbPeripheralManager addService:self.service];
    }
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didAddService:(CBService *)service error:(NSError *)error {
    if (error) {
        NSLog(@"failed in adding service: %@", error);
        return;
    }
    
    NSDictionary *advertising = @{ CBAdvertisementDataLocalNameKey: k_localName, CBAdvertisementDataServiceUUIDsKey: @[self.service.UUID] };
    [self.cbPeripheralManager startAdvertising:advertising];
    NSLog(@"advertising started");
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didSubscribeToCharacteristic:(CBCharacteristic *)characteristic {
    NSLog(@"%s, subscribed from %@", __PRETTY_FUNCTION__, central.identifier);
    self.subscribedPeers[central.identifier] = @1;

    XNSession *session = [self sessionFor:central];
    XNPeerId *peer = [self peerIdFor:central];
    
    peer.characteristic = characteristic;
    
    self.state |= STATE_SUBSCRIBED;
    [self checkReady:peer session:session];

    if ([self.delegate respondsToSelector:@selector(gotReadyForSend:session:)]) {
        [self.delegate gotReadyForSend:peer session:session];
    }
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didUnsubscribeFromCharacteristic:(CBCharacteristic *)characteristic {
    NSLog(@"%s, unsubscribed %@", __PRETTY_FUNCTION__, central.identifier);

    XNSession *session = [self sessionFor:central];
    XNPeerId *peer = [self peerIdFor:central];

    if ([self.delegate respondsToSelector:@selector(didDisconnect:session:)]) {
        [self.delegate didDisconnect:peer session:session];
    }

    [self.subscribedPeers removeObjectForKey:central.identifier];
    [self.sessions removeObjectForKey:central.identifier];
    [self.peers removeObjectForKey:central.identifier];
    self.state = 0;
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveReadRequest:(CBATTRequest *)request {
    NSLog(@"%s from %@", __PRETTY_FUNCTION__, request.central.identifier);

    if (self.subscribedPeers[request.central.identifier]) {
        Byte value = {0x01};
        NSData *data = [NSData dataWithBytes:&value length:1];
        request.value = data;
    } else {
        Byte value = {0x00};
        NSData *data = [NSData dataWithBytes:&value length:1];
        request.value = data;
    }
    [peripheral respondToRequest:request withResult:CBATTErrorSuccess];
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray *)requests {
    CBCentral *central = ((CBATTRequest *)requests[0]).central;
    for (CBATTRequest *request in requests) {
        NSAssert([request.central isEqual:central], @"all requests should have the same central");
    }
    NSLog(@"%s from %@", __PRETTY_FUNCTION__, central.identifier);
    
    XNSession *session = [self sessionFor:central];
    XNPeerId *peerId = [self peerIdFor:central];
    
    CBATTRequest *firstRequest = requests[0];
    if ([firstRequest.characteristic isEqual:self.controlCharacteristic]) {
        NSUInteger len = firstRequest.value.length;
        char *buf = (char *)firstRequest.value.bytes;
        if (buf[0] & 0x05) { // set identifier
            NSString *identifier = [[NSString alloc] initWithBytes:buf+1 length:len-1 encoding:NSUTF8StringEncoding];
            NSLog(@"identifier = %@", identifier);
            peerId.identifier = identifier;

            self.state |= STATE_IDENTIFIER_RECEIVED;
            [self checkReady:peerId session:session];
        }
            
        [peripheral respondToRequest:requests[0] withResult:CBATTErrorSuccess];
        return;
    }
    
    NSMutableData *concatenated = [NSMutableData data];
    
    for (CBATTRequest *request in requests) {
        [concatenated appendData:request.value];
    }
    
    if ([session.delegate respondsToSelector:@selector(didReceive:from:)]) {
        [session.delegate didReceive:concatenated from:peerId];
    }
    
    [peripheral respondToRequest:requests[0] withResult:CBATTErrorSuccess];
}

- (XNPeerId *) peerIdFor:(CBCentral *)central {
    XNPeerId *peer = self.peers[central.identifier];
    if (! peer) {
        NSLog(@"creating new peer for %@", central.identifier);
        peer = [[XNPeerId alloc] initWithIdentifier:central.identifier];
        self.peers[central.identifier] = peer;
    }
    return peer;
}

- (XNSession *) sessionFor:(CBCentral *)central {
    XNSession *session = self.sessions[central.identifier];
    if (! session) {
        NSLog(@"creating new session for %@", central.identifier);
        session = [[XNSession alloc] initWithAdvertiser:self];
        self.sessions[central.identifier] = session;
    }
    return session;
}

- (void)peripheralManagerDidStartAdvertising:(CBPeripheralManager *)peripheral error:(NSError *)error {
    NSLog(@"%s error=%@", __PRETTY_FUNCTION__, error);
}

- (void)peripheralManagerIsReadyToUpdateSubscribers:(CBPeripheralManager *)peripheral {
    NSLog(@"%s ---------------", __PRETTY_FUNCTION__);
}

@end

@implementation XNAdvertiser (Private)

- (CBMutableCharacteristic *) characteristicForNotifier {
    CBUUID *characteristicUUID = [CBUUID UUIDWithString:k_characteristicNotifierUUIDString];
    CBCharacteristicProperties props = CBCharacteristicPropertyNotify;
    return [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:props value:nil permissions:0];
}

- (CBMutableCharacteristic *) characteristicForReceiver {
    CBUUID *characteristicUUID = [CBUUID UUIDWithString:k_characteristicReceiverUUIDStirng];
    CBCharacteristicProperties props = CBCharacteristicPropertyWrite;
    return [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:props value:nil permissions:CBAttributePermissionsWriteable];
}

- (CBMutableCharacteristic *) characteristicForController {
    CBUUID *characteristicUUID = [CBUUID UUIDWithString:k_characteristicControllerUUIDString];
    CBCharacteristicProperties props = CBCharacteristicPropertyRead | CBCharacteristicPropertyWrite;
    return [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:props value:nil permissions:CBAttributePermissionsReadable | CBAttributePermissionsWriteable];
}


- (CBMutableService *) setupService {
    CBUUID *serviceUUID = [CBUUID UUIDWithString:k_serviceUUIDStirng];
    CBMutableService *service = [[CBMutableService alloc] initWithType:serviceUUID primary:YES];
    
    self.controlCharacteristic = [self characteristicForController];
    service.characteristics = @[[self characteristicForNotifier], [self characteristicForReceiver], self.controlCharacteristic];
    return service;
}

@end
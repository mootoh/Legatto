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
@end

@interface XNAdvertiser ()
@property (readonly, nonatomic, strong) CBPeripheralManager *cbPeripheralManager;
@property (nonatomic, strong) CBMutableService *service;
@property (nonatomic, strong) NSMutableDictionary *sessions;
@property (nonatomic, strong) NSMutableDictionary *peers;
@property (nonatomic, strong) NSMutableDictionary *subscribedPeers;
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

- (void) send:(NSData *)data to:(XNPeerId *)peer {
    if (! [self.advertiser.cbPeripheralManager updateValue:data forCharacteristic:peer.characteristic onSubscribedCentrals:nil]) {
        NSLog(@"failed to send data to %@", peer.uuid);
    }
    NSLog(@"should be sent data to observers");
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
    }
    return self;
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
    /*
    XNSession *session = self.sessions[central.identifier];
    NSAssert(session, @"session should be created before subscription");
    
    XNPeerId *peer = self.peers[central.identifier];
    peer.characteristic = characteristic;
    
    
    if ([self.delegate respondsToSelector:@selector(gotReadyForSend:session:)]) {
        [self.delegate gotReadyForSend:peer session:session];
    }
     */

    [self send:characteristic];
}

- (void) send:(CBCharacteristic *)characteristic {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(3 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        
        NSLog(@"sending");
        
        NSData *data = [@"notification from iOS" dataUsingEncoding:NSUTF8StringEncoding];
        if (! [self.cbPeripheralManager updateValue:data forCharacteristic:characteristic onSubscribedCentrals:nil]) {
            NSLog(@"failed to send data to");
        }

        [self send:characteristic];
    });
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
//    Byte value = arc4random()&0xff;
    [peripheral respondToRequest:request withResult:CBATTErrorSuccess];
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray *)requests {
    NSLog(@"%s count=%d", __PRETTY_FUNCTION__, requests.count);

    CBCentral *central = ((CBATTRequest *)requests[0]).central;
    for (CBATTRequest *request in requests) {
        NSAssert([request.central isEqual:central], @"all requests should have the same central");
    }

    XNPeerId *peer = self.peers[central.identifier];
    if (! peer) {
        NSLog(@"creating new peer for %@", central.identifier);
        peer = [[XNPeerId alloc] initWithIdentifier:central.identifier];
        self.peers[central.identifier] = peer;
    }

    NSLog(@"%s from %@", __PRETTY_FUNCTION__, central.identifier);

    BOOL first = YES;
    for (CBATTRequest *request in requests) {
        XNSession *session = nil;
        NSData *data = nil;

        if (first) {
            first = NO;

            const Byte *bytes = request.value.bytes;
            if (bytes[0] & k_controlCentralConnect) { // session begin
                NSAssert(self.sessions[central.identifier] == nil, @"session should not exist before the first request");
                session = [[XNSession alloc] initWithAdvertiser:self];
                self.sessions[central.identifier] = session;

                if ([self.delegate respondsToSelector:@selector(didConnect:session:)]) {
                    [self.delegate didConnect:peer session:session];
                }
            }
            data = [request.value subdataWithRange:NSMakeRange(1, request.value.length-1)];
        } else {
            data = request.value;
        }

        if (! session) {
            session = self.sessions[central.identifier];
        }
        NSAssert(session, @"session should exist");
        
        if ([session.delegate respondsToSelector:@selector(didReceive:from:)]) {
            [session.delegate didReceive:data from:peer];
        }
        [peripheral respondToRequest:request withResult:CBATTErrorSuccess];
    }
//    [peripheral respondToRequest:requests[0] withResult:CBATTErrorSuccess];
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
    CBCharacteristicProperties props = CBCharacteristicPropertyRead;
    return [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:props value:nil permissions:CBAttributePermissionsReadable];
}


- (CBMutableService *) setupService {
    CBUUID *serviceUUID = [CBUUID UUIDWithString:k_serviceUUIDStirng];
    CBMutableService *service = [[CBMutableService alloc] initWithType:serviceUUID primary:YES];
    service.characteristics = @[[self characteristicForNotifier], [self characteristicForReceiver], [self characteristicForController]];
    return service;
}

@end
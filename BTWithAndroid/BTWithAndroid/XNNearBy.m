//
//  XNNearBy.m
//  BTWithAndroid
//
//  Created by Motohiro Takayama on 5/13/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import "XNNearBy.h"

#pragma mark - XNSession

@implementation XNSession
@end

#pragma mark - XNPeerId

@interface XNPeerId ()
@property (nonatomic, strong) NSUUID *uuid;
@end

@implementation XNPeerId
- (id) initWithIdentifier:(NSUUID *)uuid {
    self = [super init];
    if (self) {
        self.uuid = uuid;
    }
    return self;
}
@end

#pragma mark - XNAdvertiser

@interface XNAdvertiser ()
@property (nonatomic, strong) CBPeripheralManager *cbPeripheralManager;
@property (nonatomic, strong) CBMutableService *service;
@end

@implementation XNAdvertiser

- (id) init {
    self = [super init];
    if (self) {
        self.cbPeripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:nil];
    }
    return self;
}

- (void) send:(NSData *)data to:(XNPeerId *)peer {
    if (! [self.cbPeripheralManager updateValue:data forCharacteristic:peer.characteristic onSubscribedCentrals:nil]) {
        NSLog(@"failed to send data to %@", peer.uuid);
    }
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


- (CBMutableService *) setupService {
    CBUUID *serviceUUID = [CBUUID UUIDWithString:k_serviceUUIDStirng];
    CBMutableService *service = [[CBMutableService alloc] initWithType:serviceUUID primary:YES];
    service.characteristics = @[[self characteristicForNotifier], [self characteristicForReceiver]];
    return service;
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
    NSLog(@"%s, subscribed", __PRETTY_FUNCTION__);
    
    XNPeerId *peer = [[XNPeerId alloc] initWithIdentifier:central.identifier];
    peer.characteristic = characteristic;

    if ([self.delegate respondsToSelector:@selector(didConnect:)])
         [self.delegate didConnect:peer];
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveReadRequest:(CBATTRequest *)request {
    NSLog(@"%s from %@", __PRETTY_FUNCTION__, request.central.identifier);
    
    Byte value = arc4random()&0xff;
    NSData *data = [NSData dataWithBytes:&value length:1];
    request.value = data;
    [peripheral respondToRequest:request withResult:CBATTErrorSuccess];
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray *)requests {
    NSLog(@"%s from %d", __PRETTY_FUNCTION__, requests.count);
    for (CBATTRequest *request in requests) {
        NSString *requestedString = [[NSString alloc] initWithData:request.value encoding:NSUTF8StringEncoding];
        NSLog(@"requestedString: %@", requestedString);
        
        [peripheral respondToRequest:request withResult:CBATTErrorSuccess];
    }
}

- (void)peripheralManagerDidStartAdvertising:(CBPeripheralManager *)peripheral error:(NSError *)error {
    NSLog(@"%s error=%@", __PRETTY_FUNCTION__, error);
}

- (void)peripheralManagerIsReadyToUpdateSubscribers:(CBPeripheralManager *)peripheral {
    NSLog(@"%s ---------------", __PRETTY_FUNCTION__);
}

@end
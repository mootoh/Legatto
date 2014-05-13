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

@implementation XNPeerId
@end

#pragma mark - XNAdvertiser

@interface XNAdvertiser ()
@property (nonatomic) CBPeripheralManager *cbPeripheralManager;
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

- (CBMutableCharacteristic *) characteristicForNotifier {
    CBUUID *characteristicUUID = [CBUUID UUIDWithString:k_characteristicNotifierUUIDString];
    CBCharacteristicProperties props = CBCharacteristicPropertyNotify;
    return [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:props value:nil permissions:0];
}

- (CBMutableCharacteristic *) characteristicForSender {
    CBUUID *characteristicUUID = [CBUUID UUIDWithString:k_characteristicSenderUUIDString];
    CBCharacteristicProperties props = CBCharacteristicPropertyRead | CBCharacteristicPropertyWrite | CBCharacteristicPropertyNotify;
    CBMutableCharacteristic *chr = [[CBMutableCharacteristic alloc] initWithType:characteristicUUID properties:props value:nil permissions:CBAttributePermissionsReadable | CBAttributePermissionsWriteable];
    
    return chr;
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
    NSLog(@"peripheral manager status update: %ld", peripheral.state);
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
    
    [NSTimer scheduledTimerWithTimeInterval:2 target:self selector:@selector(notify:) userInfo:characteristic repeats:YES];
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveReadRequest:(CBATTRequest *)request {
    NSLog(@"%s from %@", __PRETTY_FUNCTION__, request.central.identifier);
    
    Byte value = arc4random()&0xff;
    NSData *data = [NSData dataWithBytes:&value length:1];
    request.value = data;
    [peripheral respondToRequest:request withResult:CBATTErrorSuccess];
}

- (void) notify:(NSTimer *) timer {
    NSLog(@"notifying");
    NSString *str = @"notification from iOS";
    CBCharacteristic *chr = (CBCharacteristic *)timer.userInfo;
    
    NSData *strData = [str dataUsingEncoding:NSUTF8StringEncoding];
    if (! [self.cbPeripheralManager updateValue:strData forCharacteristic:chr onSubscribedCentrals:nil]) {
        NSLog(@"failed to notify");
    }
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
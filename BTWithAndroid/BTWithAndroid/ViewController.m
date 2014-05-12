//
//  ViewController.m
//  BTWithAndroid
//
//  Created by Motohiro Takayama on 5/1/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import "ViewController.h"
#import <CoreBluetooth/CoreBluetooth.h>

#define k_serviceUUIDStirng @"688C7F90-F424-4BC0-8508-AEDE43A4288D"
#define k_characteristicNotifierUUIDString @"42015324-6E63-412D-9B7F-257024D56460"
#define k_characteristicReceiverUUIDStirng @"721AC875-945E-434A-93D8-7AD8C740A51A"
#define k_characteristicSenderUUIDString   @"9321525D-08B6-4BDC-90C7-0C2B6234C52B"
#define k_localName @"btbt"

@interface ViewController ()
@property (nonatomic) CBPeripheralManager *cbPeripheralManager;
@property (nonatomic, strong) CBMutableService *service;
@end

@implementation ViewController

- (void)viewDidLoad
{
    [super viewDidLoad];
	// Do any additional setup after loading the view, typically from a nib.
    
    [self setupBT];
}

- (void)didReceiveMemoryWarning
{
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

#pragma mark - CoreBluetooth

- (void) setupBT {
    self.cbPeripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:nil];
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
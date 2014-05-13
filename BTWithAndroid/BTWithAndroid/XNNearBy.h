//
//  XNNearBy.h
//  BTWithAndroid
//
//  Created by Motohiro Takayama on 5/13/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

@interface XNSession : NSObject
@end

@interface XNPeerId : NSObject
@property (nonatomic, strong) CBCharacteristic *characteristic;
- (id) initWithIdentifier:(NSUUID *)uuid;
@end

@protocol XNAdvertiserDelegate
- (void)didConnect:(XNPeerId *)peer;
@end

@interface XNAdvertiser : NSObject <CBPeripheralManagerDelegate>
@property (nonatomic, weak) NSObject <XNAdvertiserDelegate> *delegate;

- (void) send:(NSData *)data to:(XNPeerId *)peer;

@end
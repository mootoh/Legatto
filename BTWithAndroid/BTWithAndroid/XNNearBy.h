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
@end

@interface XNAdvertiser : NSObject <CBPeripheralManagerDelegate>
@end
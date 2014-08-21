//
//  Legatto.h
//  Legatto
//
//  Created by Motohiro Takayama on 5/13/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

@interface XNPeer : NSObject <NSCopying>
@property (nonatomic, strong) CBMutableCharacteristic *characteristic;
@property (nonatomic, strong) NSString *name;
@property (nonatomic) uint8_t identifier;
@end

@protocol XNSessionDelegate
- (void) didReceive:(NSData *)data from:(XNPeer *)peer;
@end

@interface XNSession : NSObject
@property (nonatomic, weak) NSObject <XNSessionDelegate> *delegate;
- (void) send:(NSData *)data from:(XNPeer *)fromPeer to:(XNPeer *)toPeer;
- (void) sendURL:(NSURL *)url to:(XNPeer *)peer;
@end

@protocol XNAdvertiserDelegate
- (void) didConnect:(XNPeer *)peer session:(XNSession *)session;
- (void) didDisconnect:(XNPeer *)peer session:(XNSession *)session;
- (void) gotReadyForSend:(XNPeer *)peer session:(XNSession *)session;
@end

@interface XNAdvertiser : NSObject <CBPeripheralManagerDelegate>
@property (nonatomic, weak) NSObject <XNAdvertiserDelegate> *delegate;
@end
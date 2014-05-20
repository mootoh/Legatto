//
//  Legatto.h
//  Legatto
//
//  Created by Motohiro Takayama on 5/13/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

@interface XNPeerId : NSObject <NSCopying>
@property (nonatomic, strong) CBMutableCharacteristic *characteristic;
@property (nonatomic, strong) NSString *identifier;
@end

@protocol XNSessionDelegate
- (void) didReceive:(NSData *)data from:(XNPeerId *)peer;
@end

@interface XNSession : NSObject
@property (nonatomic, weak) NSObject <XNSessionDelegate> *delegate;
- (void) send:(NSData *)data to:(XNPeerId *)peer;
- (void) sendURL:(NSURL *)url to:(XNPeerId *)peer;
@end

@protocol XNAdvertiserDelegate
- (void) didConnect:(XNPeerId *)peer session:(XNSession *)session;
- (void) didDisconnect:(XNPeerId *)peer session:(XNSession *)session;
- (void) gotReadyForSend:(XNPeerId *)peer session:(XNSession *)session;
@end

@interface XNAdvertiser : NSObject <CBPeripheralManagerDelegate>
@property (nonatomic, weak) NSObject <XNAdvertiserDelegate> *delegate;
@end
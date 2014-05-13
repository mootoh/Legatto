//
//  ViewController.m
//  BTWithAndroid
//
//  Created by Motohiro Takayama on 5/1/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import "ViewController.h"
#import <CoreBluetooth/CoreBluetooth.h>
#import "XNNearBy.h"

@interface XNNearByWrap : NSObject <XNAdvertiserDelegate, XNSessionDelegate>
@property (nonatomic) XNAdvertiser *advertiser;
@end

@implementation XNNearByWrap

- (id) init {
    self = [super init];
    if (self) {
        self.advertiser = [[XNAdvertiser alloc] init];
        self.advertiser.delegate = self;
    }
    return self;
}

- (void) didConnect:(XNPeerId *)peer session:(XNSession *)session {
    session.delegate = self;
    NSLog(@"connected to peer");
}

- (void) gotReadyForSend:(XNPeerId *)peer session:(XNSession *)session {
    [self notify:peer session:session];
}

- (void) notify:(XNPeerId *)peer session:(XNSession *)session {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(3 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        NSLog(@"sending");
        
        NSData *data = [@"notification from iOS" dataUsingEncoding:NSUTF8StringEncoding];
        [session send:data to:peer];
        [self notify:peer session:session];
    });
}

- (void) didReceive:(NSData *)data from:(XNPeerId *)peer {
    NSString *received = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    NSLog(@"received: %@", received);
}

@end

@interface ViewController ()
@property (nonatomic) XNNearByWrap *xn;
@end

@implementation ViewController

- (void)viewDidLoad
{
    [super viewDidLoad];
    self.xn = [[XNNearByWrap alloc] init];
}

@end
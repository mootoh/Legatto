//
//  LegattoTest.m
//  LegattoTests
//
//  Created by Motohiro Takayama on 5/1/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import <XCTest/XCTest.h>
#import "XNNearBy.h"

enum {
    STATE_INITIAL,
    STATE_CONNECTED
};

@interface XNNearByWrap : NSObject <XNAdvertiserDelegate, XNSessionDelegate>
@property (nonatomic) XNAdvertiser *advertiser;
@property int state;
@end

@implementation XNNearByWrap

- (id) init {
    self = [super init];
    if (self) {
        self.advertiser = [[XNAdvertiser alloc] init];
        self.advertiser.delegate = self;
        self.state = STATE_INITIAL;
    }
    return self;
}

- (void) didConnect:(XNPeerId *)peer session:(XNSession *)session {
    session.delegate = self;
    self.state = STATE_CONNECTED;
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


@interface LegattoTests : XCTestCase
@property XNNearByWrap *xn;
@end

@implementation LegattoTests

- (void)setUp
{
    [super setUp];
    // Put setup code here. This method is called before the invocation of each test method in the class.
    self.xn = [[XNNearByWrap alloc] init];
}

- (void)tearDown
{
    // Put teardown code here. This method is called after the invocation of each test method in the class.
    [super tearDown];
}

- (void) checkConnection {
    if (self.xn.state != STATE_CONNECTED) {
        [NSTimer scheduledTimerWithTimeInterval:100 target:self selector:@selector(checkConnection) userInfo:nil repeats:YES];
    }
}

- (void)testConnection
{
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    
    void (^checker)(void) = ^() {
        if (self.xn.state == STATE_CONNECTED) {
            NSLog(@"hit the condition");
            dispatch_semaphore_signal(sem);
            return;
        }
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1LL * NSEC_PER_SEC)), dispatch_get_main_queue(), checker);
    };

    dispatch_async(dispatch_get_main_queue(), checker);
    dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, 20LL * NSEC_PER_SEC));

    XCTAssert(self.xn.state == STATE_CONNECTED, @"should be connected");
}

@end

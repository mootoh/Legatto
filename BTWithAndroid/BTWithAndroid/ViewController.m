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

@interface XNNearByWrap : NSObject <XNAdvertiserDelegate>
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

- (void) didConnect:(XNPeerId *)peer {
    [NSTimer scheduledTimerWithTimeInterval:2 target:self selector:@selector(sendMessage:) userInfo:peer repeats:YES];
}

- (void) sendMessage:(NSTimer *) timer {
    NSLog(@"notifying");

    XNPeerId *peer = (XNPeerId *)timer.userInfo;
    NSData *data = [@"notification from iOS" dataUsingEncoding:NSUTF8StringEncoding];

    [self.advertiser send:data to:peer];
}

@end

@interface ViewController ()
@property (nonatomic) XNNearByWrap *xn;
@end

@implementation ViewController

- (void)viewDidLoad
{
    [super viewDidLoad];
	// Do any additional setup after loading the view, typically from a nib.
    self.xn = [[XNNearByWrap alloc] init];
}

- (void)didReceiveMemoryWarning
{
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

@end
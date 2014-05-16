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
@property XNSession *session;
@property XNPeerId *peerId;
@property (weak) ViewController *viewController;

@end

@implementation XNNearByWrap

- (id) initWithViewController:(ViewController *)vc {
    self = [super init];
    if (self) {
        self.advertiser = [[XNAdvertiser alloc] init];
        self.advertiser.delegate = self;
        self.viewController = vc;
    }
    return self;
}

- (void) didConnect:(XNPeerId *)peer session:(XNSession *)session {
    session.delegate = self;
    NSLog(@"connected to peer");
    /*
    NSMutableAttributedString *as = [[NSMutableAttributedString alloc] initWithString:[NSString stringWithFormat:@"   %@ connected", peer.identifier]];
    [as addAttribute:NSForegroundColorAttributeName value:[UIColor grayColor] range:NSMakeRange(0, as.length)];
    [self.viewController appendAttributedTextToLog:as];
     */
    [self.viewController appendTextToLog:[NSString stringWithFormat:@"   %@ connected", peer.identifier]];
}

- (void) didDisconnect:(XNPeerId *)peer session:(XNSession *)session {
    self.session = nil;
    self.peerId = nil;

    [self.viewController appendTextToLog:[NSString stringWithFormat:@"   %@ disconnected", peer.identifier]];
}

- (void) gotReadyForSend:(XNPeerId *)peer session:(XNSession *)session {
    self.session = session;
    self.peerId = peer;
}

- (void) didReceive:(NSData *)data from:(XNPeerId *)peer {
    if (! [self isReady])
        return;
    NSString *received = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    NSLog(@"received from %@: %@", peer.identifier, received);
    [self.viewController appendTextToLog:[NSString stringWithFormat:@"%@: %@", peer.identifier, received]];
}

- (BOOL) isReady {
    return self.session != nil && self.peerId != nil;
}

@end

@interface ViewController ()
@property (nonatomic) XNNearByWrap *xn;
@end

@implementation ViewController

- (void)viewDidLoad
{
    [super viewDidLoad];
    self.xn = [[XNNearByWrap alloc] initWithViewController:self];
    [self.inputTextField becomeFirstResponder];
}

- (void) scrollLogToBottom {
    NSRange bottom = NSMakeRange(self.logTextView.text.length-1, 1);
    [self.logTextView scrollRangeToVisible:bottom];
}

- (void) appendTextToLog:(NSString *)text {
    self.logTextView.text = [self.logTextView.text stringByAppendingFormat:@"\n%@", text];
    [self scrollLogToBottom];
}

- (void) appendAttributedTextToLog:(NSAttributedString *)attributedString {
    self.logTextView.attributedText = attributedString;
}

- (IBAction) sendText {
    NSString *text = self.inputTextField.text;
    NSLog(@"sending text: %@", text);

    if ([text hasPrefix:@"http"] && [text hasSuffix:@"jpg"]) {
        NSURL *url = [NSURL URLWithString:text];
        if ([self.xn isReady]) {
            [self.xn.session sendURL:url to:self.xn.peerId];
        }
    } else {
        if ([self.xn isReady]) {
            [self.xn.session send:[text dataUsingEncoding:NSUTF8StringEncoding] to:self.xn.peerId];
        }
        [self appendTextToLog:[NSString stringWithFormat:@"me: %@", text]];
    }
    self.inputTextField.text = @"";
}

#pragma mark - UITextFieldDelegate

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [self sendText];
    return YES;
}
@end
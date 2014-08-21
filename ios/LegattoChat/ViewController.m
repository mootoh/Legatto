//
//  ViewController.m
//  Legatto
//
//  Created by Motohiro Takayama on 5/1/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import "ViewController.h"
#import <CoreBluetooth/CoreBluetooth.h>
#import "AFNetworking/AFNetworking.h"
#import "Legatto.h"

@interface XNNearByWrap : NSObject <XNAdvertiserDelegate, XNSessionDelegate>
@property (nonatomic) XNAdvertiser *advertiser;
@property XNSession *session;
@property XNPeer *peerId;
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

- (void) didConnect:(XNPeer *)peer session:(XNSession *)session {
    session.delegate = self;
    NSLog(@"connected to peer: %u", peer.identifier);
    /*
    NSMutableAttributedString *as = [[NSMutableAttributedString alloc] initWithString:[NSString stringWithFormat:@"   %@ connected", peer.identifier]];
    [as addAttribute:NSForegroundColorAttributeName value:[UIColor grayColor] range:NSMakeRange(0, as.length)];
    [self.viewController appendAttributedTextToLog:as];
     */
    [self.viewController appendTextToLog:[NSString stringWithFormat:@"   %u connected", peer.identifier]];
}

- (void) didDisconnect:(XNPeer *)peer session:(XNSession *)session {
    self.session = nil;
    self.peerId = nil;

    [self.viewController appendTextToLog:[NSString stringWithFormat:@"   %u disconnected", peer.identifier]];
}

- (void) gotReadyForSend:(XNPeer *)peer session:(XNSession *)session {
    self.session = session;
    self.peerId = peer;
}

- (void) didReceive:(NSData *)data from:(XNPeer *)peer {
    if (! [self isReady])
        return;
    NSString *received = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    NSLog(@"received from %u: %@", peer.identifier, received);
    [self.viewController appendTextToLog:[NSString stringWithFormat:@"%u: %@", peer.identifier, received]];
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
            [self.xn.session send:[text dataUsingEncoding:NSUTF8StringEncoding] from:nil to:self.xn.peerId];
        }
        [self appendTextToLog:[NSString stringWithFormat:@"me: %@", text]];
    }
    self.inputTextField.text = @"";
}
- (IBAction)sendPhoto:(id)sender {
    UIImagePickerController *ipc = [[UIImagePickerController alloc] init];
    /*
    ipc.mediaTypes = [UIImagePickerController availableMediaTypesForSourceType:UIImagePickerControllerSourceTypeCamera];
    ipc.sourceType = UIImagePickerControllerSourceTypeCamera;
    ipc.allowsEditing = NO;
     */
    ipc.delegate = self;
    [self presentViewController:ipc animated:YES completion:nil];
}

#pragma mark - UIImagePickerController

// http://stackoverflow.com/questions/2658738/the-simplest-way-to-resize-an-uiimage
+ (UIImage *)imageWithImage:(UIImage *)image scaledToSize:(CGSize)newSize {
    //UIGraphicsBeginImageContext(newSize);
    // In next line, pass 0.0 to use the current device's pixel scaling factor (and thus account for Retina resolution).
    // Pass 1.0 to force exact pixel size.
    UIGraphicsBeginImageContextWithOptions(newSize, NO, 0.0);
    [image drawInRect:CGRectMake(0, 0, newSize.width, newSize.height)];
    UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return newImage;
}

- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary *)info {
    UIImage *image = (UIImage *)[info objectForKey:UIImagePickerControllerOriginalImage];
    UIImage *smallerImage = [ViewController imageWithImage:image scaledToSize:CGSizeMake(240, 240)];
    NSData *imageData = UIImageJPEGRepresentation(smallerImage, 1.0);
    
    NSString *cacheDir = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES)[0];
    NSString *path = [cacheDir stringByAppendingPathComponent:@"tmp.jpg"];

    NSFileManager *fm = [NSFileManager defaultManager];
    if (! [fm createFileAtPath:path contents:imageData attributes:nil]) {
        NSLog(@"failed to save a temporally file");
        return;
    }

    AFHTTPRequestOperationManager *manager = [AFHTTPRequestOperationManager manager];
    [manager POST:[NSString stringWithFormat:@"http://%@/upload", k_mediatorHostAndPort] parameters:nil constructingBodyWithBlock:^(id<AFMultipartFormData> formData) {
        [formData appendPartWithFileData:imageData name:@"image" fileName:path mimeType:@"image/jpeg"];
    } success:^(AFHTTPRequestOperation *operation, id responseObject) {
        NSLog(@"Success: %@", responseObject);
        NSString *urlString = ((NSDictionary *)responseObject)[@"url"];

        NSURL *url = [NSURL URLWithString:urlString];
        if ([self.xn isReady]) {
            [self.xn.session sendURL:url to:self.xn.peerId];
        }
    } failure:^(AFHTTPRequestOperation *operation, NSError *error) {
        NSLog(@"Error: %@", error);
    }];

    [picker dismissViewControllerAnimated:YES completion:nil];
}

#pragma mark - UITextFieldDelegate

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [self sendText];
    return YES;
}
@end
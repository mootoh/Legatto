//
//  ViewController.h
//  Legatto
//
//  Created by Motohiro Takayama on 5/1/14.
//  Copyright (c) 2014 mootoh.net. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface ViewController : UIViewController <UITextFieldDelegate, UIImagePickerControllerDelegate>
@property (weak, nonatomic) IBOutlet UITextView *logTextView;
@property (weak, nonatomic) IBOutlet UITextField *inputTextField;

- (IBAction) sendText;
- (void) appendTextToLog:(NSString *)text;
- (void) appendAttributedTextToLog:(NSAttributedString *)attributedString;

@end

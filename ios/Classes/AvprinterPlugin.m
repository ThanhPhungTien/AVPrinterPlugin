#import "AvprinterPlugin.h"
#if __has_include(<avprinter/avprinter-Swift.h>)
#import <avprinter/avprinter-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "avprinter-Swift.h"
#endif

@implementation AvprinterPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftAvprinterPlugin registerWithRegistrar:registrar];
}
@end

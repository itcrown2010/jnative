/* Author: 张晓达
 * Finder Sync: contextual menu 「JNative」→ 压缩 / 解压 …
 * Extension only enqueues JN1 jobs and activates the host app — no zip work here.
 */
#import "JNativeFinderSync.h"
#import <pwd.h>
#import <sys/stat.h>
#import <unistd.h>

/** Real user home — NSHomeDirectory()/NSSearchPath may point into the appex container. */
static NSString *JNRealHome(void) {
    struct passwd *pw = getpwuid(getuid());
    if (pw != NULL && pw->pw_dir != NULL) {
        return [NSString stringWithUTF8String:pw->pw_dir];
    }
    NSString *env = [[NSProcessInfo processInfo] environment][@"HOME"];
    if (env.length > 0 && [env rangeOfString:@"/Containers/"].location == NSNotFound) {
        return env;
    }
    return NSHomeDirectory();
}

static NSString *const kHostBundleId = @"io.jnative.examples.archive";

typedef NS_ENUM(NSInteger, JNSelKind) {
    JNSelEmpty = 0,
    JNSelFilesOrFolders,
    JNSelSingleZip,
    JNSelMultiZip,
    JNSelMixedWithZip,
};

@implementation JNativeFinderSync {
    NSSet<NSURL *> *_baseDirectories;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        [self jn_refreshDirectoryURLs];
        [[[NSWorkspace sharedWorkspace] notificationCenter]
            addObserver:self
               selector:@selector(jn_volumesChanged:)
                   name:NSWorkspaceDidMountNotification
                 object:nil];
        [[[NSWorkspace sharedWorkspace] notificationCenter]
            addObserver:self
               selector:@selector(jn_volumesChanged:)
                   name:NSWorkspaceDidUnmountNotification
                 object:nil];
    }
    return self;
}

- (void)dealloc {
    [[[NSWorkspace sharedWorkspace] notificationCenter] removeObserver:self];
}

#pragma mark - Directory scope

- (void)jn_volumesChanged:(NSNotification *)note {
    (void)note;
    [self jn_refreshDirectoryURLs];
}

- (void)jn_log:(NSString *)fmt, ... NS_FORMAT_FUNCTION(1, 2) {
    va_list ap;
    va_start(ap, fmt);
    NSString *msg = [[NSString alloc] initWithFormat:fmt arguments:ap];
    va_end(ap);
    NSLog(@"[JNativeFinderSync] %@", msg);
    /* Sandbox-safe: always writable. Also mirror into host inbox when allowed. */
    NSString *line = [NSString stringWithFormat:@"%@ %@\n", [NSDate date], msg];
    NSString *tmp = [NSTemporaryDirectory() stringByAppendingPathComponent:@"jnative-findersync.log"];
    NSFileHandle *fh = [NSFileHandle fileHandleForWritingAtPath:tmp];
    if (fh == nil) {
        [line writeToFile:tmp atomically:YES encoding:NSUTF8StringEncoding error:nil];
    } else {
        @try {
            [fh seekToEndOfFile];
            [fh writeData:[line dataUsingEncoding:NSUTF8StringEncoding]];
        } @finally {
            [fh closeFile];
        }
    }
    NSString *mirrorDir = [NSHomeDirectory()
        stringByAppendingPathComponent:@"Library/Application Support/JNativeArchive"];
    NSString *mirror = [mirrorDir stringByAppendingPathComponent:@"findersync.log"];
    if ([[NSFileManager defaultManager] createDirectoryAtPath:mirrorDir
                                  withIntermediateDirectories:YES
                                                   attributes:nil
                                                        error:nil]) {
        NSFileHandle *mh = [NSFileHandle fileHandleForWritingAtPath:mirror];
        if (mh == nil) {
            [line writeToFile:mirror atomically:YES encoding:NSUTF8StringEncoding error:nil];
        } else {
            @try {
                [mh seekToEndOfFile];
                [mh writeData:[line dataUsingEncoding:NSUTF8StringEncoding]];
            } @finally {
                [mh closeFile];
            }
        }
    }
}

- (void)jn_refreshDirectoryURLs {
    NSFileManager *fm = NSFileManager.defaultManager;
    NSMutableSet<NSURL *> *urls = [NSMutableSet set];
    NSString *homePath = JNRealHome();

    /*
     * Do NOT register $HOME or "/". Do NOT use NSSearchPath (sandbox redirects into
     * Container/Data/Desktop etc.). Build real paths from getpwuid home.
     */
    for (NSString *name in @[
             @"Desktop",
             @"Documents",
             @"Downloads",
             @"Movies",
             @"Music",
             @"Pictures",
             @"Public",
             @"JNativeFinderMenuTest",
             @"Projects",
             @"Developer",
             @"src",
             @"work",
             @"code",
             @"jnative",
         ]) {
        NSString *p = [homePath stringByAppendingPathComponent:name];
        BOOL isDir = NO;
        if ([fm fileExistsAtPath:p isDirectory:&isDir] && isDir) {
            [urls addObject:[NSURL fileURLWithPath:p isDirectory:YES]];
        }
    }
    /* Desktop test folder used in acceptance. */
    NSString *deskTest =
        [[homePath stringByAppendingPathComponent:@"Desktop"]
            stringByAppendingPathComponent:@"JNativeFinderSyncTest"];
    BOOL deskTestDir = NO;
    if ([fm fileExistsAtPath:deskTest isDirectory:&deskTestDir] && deskTestDir) {
        [urls addObject:[NSURL fileURLWithPath:deskTest isDirectory:YES]];
    }

    /* Optional extra roots (host-written). Try real home then container mirror. */
    NSArray<NSString *> *rootsCandidates = @[
        [homePath stringByAppendingPathComponent:
            @"Library/Application Support/JNativeArchive/finder-sync-roots.txt"],
        [NSHomeDirectory() stringByAppendingPathComponent:
            @"Library/Application Support/JNativeArchive/finder-sync-roots.txt"],
    ];
    for (NSString *rootsFile in rootsCandidates) {
        NSString *rootsText = [NSString stringWithContentsOfFile:rootsFile
                                                        encoding:NSUTF8StringEncoding
                                                           error:nil];
        if (rootsText.length == 0) {
            continue;
        }
        for (NSString *raw in [rootsText componentsSeparatedByCharactersInSet:
                                   [NSCharacterSet newlineCharacterSet]]) {
            NSString *p =
                [raw stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
            if (p.length == 0 || [p hasPrefix:@"#"]) {
                continue;
            }
            BOOL isDir = NO;
            if ([fm fileExistsAtPath:p isDirectory:&isDir] && isDir) {
                [urls addObject:[NSURL fileURLWithPath:p isDirectory:YES]];
            }
        }
    }

    /* External / removable volumes — not the boot volume root. */
    NSArray<NSURL *> *vols =
        [fm mountedVolumeURLsIncludingResourceValuesForKeys:@[ NSURLVolumeIsRootFileSystemKey ]
                                                    options:NSVolumeEnumerationSkipHiddenVolumes];
    for (NSURL *vol in vols) {
        NSNumber *isRoot = nil;
        [vol getResourceValue:&isRoot forKey:NSURLVolumeIsRootFileSystemKey error:nil];
        if (isRoot != nil && isRoot.boolValue) {
            continue;
        }
        if (vol.path.length > 1) {
            [urls addObject:vol];
        }
    }

    _baseDirectories = [urls copy];
    FIFinderSyncController.defaultController.directoryURLs = _baseDirectories;
    NSMutableArray<NSString *> *paths = [NSMutableArray array];
    for (NSURL *u in _baseDirectories) {
        if (u.path != nil) {
            [paths addObject:u.path];
        }
    }
    [self jn_log:@"directoryURLs count=%lu realHome=%@ paths=%@",
                 (unsigned long)_baseDirectories.count, homePath,
                 [paths componentsJoinedByString:@","]];
}

#pragma mark - Menu

- (NSString *)toolbarItemName {
    return @"JNative";
}

- (NSString *)toolbarItemToolTip {
    return @"JNative 压缩 / 解压";
}

- (NSImage *)toolbarItemImage {
    if (@available(macOS 11.0, *)) {
        NSImage *img = [NSImage imageWithSystemSymbolName:@"archivebox"
                                 accessibilityDescription:@"JNative"];
        if (img != nil) {
            return img;
        }
    }
    return [NSImage imageNamed:NSImageNameFolder];
}

- (NSMenu *)menuForMenuKind:(FIMenuKind)menuKind {
    /*
     * macOS 26+ does not auto-wrap Finder Sync items under CFBundleDisplayName.
     * Return one top-level 「JNative」 item with an NSMenu submenu of actions.
     */
    NSArray<NSURL *> *selected = FIFinderSyncController.defaultController.selectedItemURLs;
    NSURL *targeted = FIFinderSyncController.defaultController.targetedURL;
    [self jn_log:@"menuKind=%ld selected=%lu targeted=%@",
                 (long)menuKind,
                 (unsigned long)(selected != nil ? selected.count : 0),
                 targeted.path ?: @"(nil)"];

    if (menuKind != FIMenuKindContextualMenuForItems
        && menuKind != FIMenuKindContextualMenuForContainer
        && menuKind != FIMenuKindToolbarItemMenu) {
        return nil;
    }

    NSMenu *sub = [[NSMenu alloc] initWithTitle:@"JNative"];

    if (selected == nil || selected.count == 0) {
        if (menuKind == FIMenuKindToolbarItemMenu) {
            NSMenuItem *hint = [[NSMenuItem alloc] initWithTitle:@"在 Finder 中选择文件后右键使用"
                                                          action:nil
                                                   keyEquivalent:@""];
            hint.enabled = NO;
            [sub addItem:hint];
            return [self jn_rootMenuWithSubmenu:sub];
        }
        return nil;
    }

    JNSelKind kind = [self jn_classify:selected];

    /* Quick actions run in background; 「压缩选项…」 opens the full create form. */
    [self jn_addItem:sub title:@"压缩为 ZIP" action:@selector(jn_compress:) tag:1];
    [self jn_addItem:sub title:@"加密压缩…" action:@selector(jn_compressPassword:) tag:2];
    [self jn_addItem:sub title:@"压缩选项…" action:@selector(jn_compressOptions:) tag:7];

    BOOL allZip = (kind == JNSelSingleZip || kind == JNSelMultiZip);
    if (allZip) {
        [sub addItem:[NSMenuItem separatorItem]];
        if (kind == JNSelSingleZip) {
            [self jn_addItem:sub title:@"打开压缩包" action:@selector(jn_open:) tag:3];
        }
        [self jn_addItem:sub title:@"解压到当前位置" action:@selector(jn_extractHere:) tag:4];
        [self jn_addItem:sub title:@"解压到同名文件夹" action:@selector(jn_extractSibling:) tag:5];
        [self jn_addItem:sub title:@"解压到…" action:@selector(jn_extractTo:) tag:6];
    }

    return [self jn_rootMenuWithSubmenu:sub];
}

- (NSMenu *)jn_rootMenuWithSubmenu:(NSMenu *)sub {
    NSMenu *root = [[NSMenu alloc] initWithTitle:@""];
    NSMenuItem *parent = [[NSMenuItem alloc] initWithTitle:@"JNative"
                                                    action:nil
                                             keyEquivalent:@""];
    parent.submenu = sub;
    [root addItem:parent];
    return root;
}

- (void)jn_addItem:(NSMenu *)menu title:(NSString *)title action:(SEL)sel tag:(NSInteger)tag {
    NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:title action:sel keyEquivalent:@""];
    item.target = self;
    item.tag = tag;
    item.enabled = YES;
    [menu addItem:item];
}

- (BOOL)validateMenuItem:(NSMenuItem *)menuItem {
    (void)menuItem;
    return YES;
}

#pragma mark - Selection helpers

- (JNSelKind)jn_classify:(NSArray<NSURL *> *)urls {
    if (urls.count == 0) {
        return JNSelEmpty;
    }
    NSUInteger zipCount = 0;
    for (NSURL *u in urls) {
        if ([self jn_isZipURL:u]) {
            zipCount++;
        }
    }
    if (zipCount == 0) {
        return JNSelFilesOrFolders;
    }
    if (zipCount == urls.count) {
        return (urls.count == 1) ? JNSelSingleZip : JNSelMultiZip;
    }
    return JNSelMixedWithZip;
}

- (BOOL)jn_isZipURL:(NSURL *)url {
    if (url == nil || url.path.length == 0) {
        return NO;
    }
    NSString *ext = url.pathExtension.lowercaseString;
    if ([ext isEqualToString:@"zip"]) {
        return YES;
    }
    NSString *typeId = nil;
    NSError *err = nil;
    if ([url getResourceValue:&typeId forKey:NSURLTypeIdentifierKey error:&err] && typeId != nil) {
        if ([typeId isEqualToString:@"public.zip-archive"]
            || [typeId isEqualToString:@"com.pkware.zip-archive"]
            || [typeId hasSuffix:@".zip-archive"]) {
            return YES;
        }
    }
    return NO;
}

#pragma mark - Actions

- (void)jn_compress:(id)sender {
    (void)sender;
    [self jn_enqueueAction:@"compress"];
}

- (void)jn_compressPassword:(id)sender {
    (void)sender;
    [self jn_enqueueAction:@"compress-password"];
}

- (void)jn_compressOptions:(id)sender {
    (void)sender;
    [self jn_enqueueAction:@"compress-options"];
}

- (void)jn_open:(id)sender {
    (void)sender;
    [self jn_enqueueAction:@"open"];
}

- (void)jn_extractHere:(id)sender {
    (void)sender;
    [self jn_enqueueAction:@"extract-here"];
}

- (void)jn_extractSibling:(id)sender {
    (void)sender;
    [self jn_enqueueAction:@"extract-sibling"];
}

- (void)jn_extractTo:(id)sender {
    (void)sender;
    [self jn_enqueueAction:@"extract-to"];
}

#pragma mark - Host IPC (JN1 inbox — same as shell launcher)

- (void)jn_enqueueAction:(NSString *)action {
    NSArray<NSURL *> *selected = FIFinderSyncController.defaultController.selectedItemURLs;
    if (selected == nil || selected.count == 0 || action.length == 0) {
        return;
    }
    NSMutableArray<NSString *> *paths = [NSMutableArray arrayWithCapacity:selected.count];
    for (NSURL *u in selected) {
        if (u.path.length > 0) {
            [paths addObject:u.path];
        }
    }
    if (paths.count == 0) {
        return;
    }
    if (![self jn_writeJN1Job:action paths:paths]) {
        NSLog(@"[JNativeFinderSync] failed to write job action=%@", action);
        return;
    }
    BOOL needsFocus = [action isEqualToString:@"open"]
        || [action isEqualToString:@"compress-options"]
        || [action isEqualToString:@"extract-to"];
    [self jn_activateHostApp:needsFocus];
}

- (BOOL)jn_writeJN1Job:(NSString *)action paths:(NSArray<NSString *> *)paths {
    /*
     * Sandbox redirects ~/Library/Application Support into the appex container.
     * Host (unsandboxed) also polls that container inbox — see AgShell.finderSyncInboxDirs.
     * Prefer writing there (always allowed). Also try real-home inbox via temporary-exception.
     */
    NSMutableArray<NSString *> *candidates = [NSMutableArray array];
    [candidates addObject:[NSHomeDirectory() stringByAppendingPathComponent:
                               @"Library/Application Support/JNativeArchive/shell-inbox"]];
    [candidates addObject:[JNRealHome() stringByAppendingPathComponent:
                               @"Library/Application Support/JNativeArchive/shell-inbox"]];
    /* Shared drop box — absolute-path temporary exception. */
    [candidates addObject:@"/Users/Shared/JNativeArchive/shell-inbox"];

    NSMutableString *body = [NSMutableString stringWithString:@"JN1\n"];
    [body appendFormat:@"%@\n", action];
    [body appendFormat:@"%lu\n", (unsigned long)paths.count];
    for (NSString *p in paths) {
        [body appendFormat:@"%lu\n%@\n", (unsigned long)p.length, p];
    }
    NSData *data = [body dataUsingEncoding:NSUTF8StringEncoding];
    if (data == nil) {
        return NO;
    }

    NSString *name = [NSString stringWithFormat:@"job-%.0f-%d.job",
                                                 [[NSDate date] timeIntervalSince1970] * 1000.0,
                                                 (int)getpid()];
    for (NSString *support in candidates) {
        NSError *err = nil;
        if (![[NSFileManager defaultManager] createDirectoryAtPath:support
                                       withIntermediateDirectories:YES
                                                        attributes:@{ NSFilePosixPermissions: @0777 }
                                                             error:&err]) {
            continue;
        }
        NSString *jobPath = [support stringByAppendingPathComponent:name];
        NSString *tmpPath = [jobPath stringByAppendingString:@".tmp"];
        if (![data writeToFile:tmpPath options:NSDataWritingAtomic error:&err]) {
            continue;
        }
        chmod(tmpPath.fileSystemRepresentation, 0666);
        if ([[NSFileManager defaultManager] fileExistsAtPath:jobPath]) {
            [[NSFileManager defaultManager] removeItemAtPath:jobPath error:nil];
        }
        if ([[NSFileManager defaultManager] moveItemAtPath:tmpPath toPath:jobPath error:&err]) {
            [self jn_log:@"wrote job %@ → %@", action, jobPath];
            return YES;
        }
    }
    NSLog(@"[JNativeFinderSync] write job failed for all inbox candidates");
    return NO;
}

- (void)jn_activateHostApp:(BOOL)activates {
    NSWorkspace *ws = NSWorkspace.sharedWorkspace;
    NSURL *appURL = [ws URLForApplicationWithBundleIdentifier:kHostBundleId];
    if (appURL == nil) {
        /* Fallback: sibling of PlugIns/….appex → Contents → .app */
        NSURL *appex = [NSBundle mainBundle].bundleURL;
        NSURL *plugins = appex.URLByDeletingLastPathComponent;
        NSURL *contents = plugins.URLByDeletingLastPathComponent;
        NSURL *host = contents.URLByDeletingLastPathComponent;
        if ([[NSFileManager defaultManager] fileExistsAtPath:host.path]) {
            appURL = host;
        }
    }
    if (appURL == nil) {
        NSLog(@"[JNativeFinderSync] host app not found");
        return;
    }
    if (@available(macOS 11.0, *)) {
        NSWorkspaceOpenConfiguration *cfg = [NSWorkspaceOpenConfiguration configuration];
        /* Quick compress/extract: launch/wake without stealing Finder focus. */
        cfg.activates = activates;
        [ws openApplicationAtURL:appURL
                   configuration:cfg
               completionHandler:^(NSRunningApplication *app, NSError *error) {
                   if (error != nil) {
                       NSLog(@"[JNativeFinderSync] open host: %@", error);
                   }
                   (void)app;
               }];
    } else {
        [[NSWorkspace sharedWorkspace] openURL:appURL];
    }
}

@end

#import <Cocoa/Cocoa.h>
#include "jnative_ui.h"

#include <stdlib.h>
#include <string.h>

#ifndef JN_UI_MAX
#define JN_UI_MAX 4096
#endif

static NSApplication *g_app = nil;
static id g_peers[JN_UI_MAX];
static int g_cocoa_active = 0;

@interface JnUiAppDelegate : NSObject <NSApplicationDelegate>
@end
@implementation JnUiAppDelegate
- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)sender {
    (void)sender;
    return NO;
}
@end

@interface JnUiMenuTarget : NSObject
@end
@implementation JnUiMenuTarget
- (void)jnMenuAction:(id)sender {
    NSMenuItem *item = (NSMenuItem *)sender;
    int32_t hid = (int32_t)item.tag;
    if (hid > 0) {
        jnative_ui_emit_event(hid, JN_UI_EVENT_ACTION);
    }
}
@end

static JnUiMenuTarget *g_menu_target = nil;

@interface JnUiWindow : NSWindow <NSWindowDelegate>
@property(nonatomic, assign) int32_t jnHandleId;
@end
@implementation JnUiWindow
- (BOOL)windowShouldClose:(NSWindow *)sender {
    (void)sender;
    if (self.jnHandleId > 0) {
        jnative_ui_emit_event(self.jnHandleId, JN_UI_EVENT_CLOSE);
    }
    /* Managed side decides; call jnative_ui_close_window to actually close. */
    return NO;
}
@end

@interface JnUiButton : NSButton
@property(nonatomic, assign) int32_t jnHandleId;
@end
@implementation JnUiButton
- (void)jnClicked:(id)sender {
    (void)sender;
    jnative_ui_emit_event(self.jnHandleId, JN_UI_EVENT_CLICK);
}
@end

@interface JnCanvasView : NSView
@property(nonatomic, assign) int32_t jnHandleId;
@property(nonatomic, strong) NSBitmapImageRep *bitmap;
@property(nonatomic, assign) int32_t dragging;
@end

/** Map macOS keyCode / characters to the VK-like codes CodeEditor expects. */
static int32_t jn_cocoa_key_code(NSEvent *event) {
    unsigned short kc = event.keyCode;
    switch (kc) {
        case 123: return 37; /* left */
        case 124: return 39; /* right */
        case 125: return 40; /* down */
        case 126: return 38; /* up */
        case 51:  return 8;  /* backspace */
        case 117: return 46; /* delete */
        case 36:  return 13; /* return */
        case 76:  return 13; /* keypad enter */
        case 48:  return 9;  /* tab */
        case 115: return 36; /* home */
        case 119: return 35; /* end */
        case 116: return 33; /* page up */
        case 121: return 34; /* page down */
        case 53:  return 27; /* escape */
        default: break;
    }
    NSString *chars = event.charactersIgnoringModifiers;
    if (chars != nil && chars.length > 0) {
        unichar c = [chars characterAtIndex:0];
        if (c >= 32 && c < 127) {
            return (int32_t)c;
        }
    }
    return (int32_t)kc;
}

static int32_t jn_cocoa_key_mods(NSEvent *event) {
    NSEventModifierFlags f = event.modifierFlags;
    int32_t mods = 0;
    if (f & NSEventModifierFlagShift) {
        mods += 1;
    }
    if (f & NSEventModifierFlagControl) {
        mods += 2;
    }
    if (f & NSEventModifierFlagOption) {
        mods += 4;
    }
    if (f & NSEventModifierFlagCommand) {
        mods += 8;
    }
    return mods;
}

@implementation JnCanvasView
- (BOOL)isFlipped {
    return YES;
}
- (BOOL)acceptsFirstResponder {
    return YES;
}
- (BOOL)canBecomeKeyView {
    return YES;
}
- (void)drawRect:(NSRect)dirtyRect {
    (void)dirtyRect;
    [[NSColor whiteColor] setFill];
    NSRectFill(self.bounds);
    if (self.bitmap != nil) {
        [self.bitmap drawInRect:self.bounds
                       fromRect:NSZeroRect
                      operation:NSCompositingOperationSourceOver
                       fraction:1.0
                 respectFlipped:YES
                          hints:nil];
    }
}
- (void)mouseDown:(NSEvent *)event {
    [[self window] makeFirstResponder:self];
    NSPoint p = [self convertPoint:event.locationInWindow fromView:nil];
    self.dragging = 1;
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_MOUSE, JN_UI_MOUSE_DOWN,
                             (int32_t)p.x, (int32_t)p.y, 1);
}
- (void)mouseDragged:(NSEvent *)event {
    NSPoint p = [self convertPoint:event.locationInWindow fromView:nil];
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_MOUSE, JN_UI_MOUSE_MOVE,
                             (int32_t)p.x, (int32_t)p.y, 1);
}
- (void)mouseUp:(NSEvent *)event {
    NSPoint p = [self convertPoint:event.locationInWindow fromView:nil];
    self.dragging = 0;
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_MOUSE, JN_UI_MOUSE_UP,
                             (int32_t)p.x, (int32_t)p.y, 1);
}
- (void)keyDown:(NSEvent *)event {
    int32_t code = jn_cocoa_key_code(event);
    int32_t mods = jn_cocoa_key_mods(event);
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_KEY, JN_UI_KEY_DOWN,
                             code, mods, 0);
}
- (void)keyUp:(NSEvent *)event {
    int32_t code = jn_cocoa_key_code(event);
    int32_t mods = jn_cocoa_key_mods(event);
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_KEY, JN_UI_KEY_UP,
                             code, mods, 0);
}
- (void)flagsChanged:(NSEvent *)event {
    (void)event;
}
- (void)jnPresentRgba:(const uint8_t *)rgba width:(int32_t)w height:(int32_t)h {
    if (rgba == NULL || w <= 0 || h <= 0) {
        return;
    }
    /* Copy RGBA8888 into an NSBitmapImageRep (non-premultiplied). */
    size_t bytes = (size_t)w * (size_t)h * 4;
    uint8_t *copy = (uint8_t *)malloc(bytes);
    if (copy == NULL) {
        return;
    }
    memcpy(copy, rgba, bytes);
    NSBitmapImageRep *rep = [[NSBitmapImageRep alloc]
        initWithBitmapDataPlanes:&copy
                      pixelsWide:w
                      pixelsHigh:h
                   bitsPerSample:8
                 samplesPerPixel:4
                        hasAlpha:YES
                        isPlanar:NO
                  colorSpaceName:NSCalibratedRGBColorSpace
                    bitmapFormat:NSBitmapFormatAlphaNonpremultiplied
                     bytesPerRow:(w * 4)
                    bitsPerPixel:32];
    /* NSBitmapImageRep takes ownership of plane when freeWhenDone semantics via custom;
       keep copy alive by attaching via associated storage on rep via bitmapData. */
    if (rep == nil) {
        free(copy);
        return;
    }
    /* Re-init with copied plane that NSBitmap owns: use non-plane API. */
    free(copy);
    rep = [[NSBitmapImageRep alloc]
        initWithBitmapDataPlanes:NULL
                      pixelsWide:w
                      pixelsHigh:h
                   bitsPerSample:8
                 samplesPerPixel:4
                        hasAlpha:YES
                        isPlanar:NO
                  colorSpaceName:NSCalibratedRGBColorSpace
                    bitmapFormat:NSBitmapFormatAlphaNonpremultiplied
                     bytesPerRow:(w * 4)
                    bitsPerPixel:32];
    if (rep == nil) {
        return;
    }
    memcpy([rep bitmapData], rgba, bytes);
    self.bitmap = rep;
    [self setNeedsDisplay:YES];
}
@end

static NSMutableDictionary<NSNumber *, NSTimer *> *g_cocoa_timers;

static NSString *jn_nsstring(const char *utf8) {
    if (utf8 == NULL) {
        return @"";
    }
    return [NSString stringWithUTF8String:utf8];
}

int32_t jnative_ui_platform_available(void) {
    return g_cocoa_active ? 1 : 0;
}

void jnative_ui_platform_init(int32_t headless) {
    if (headless) {
        g_cocoa_active = 0;
        return;
    }
    if (g_app == nil) {
        g_app = [NSApplication sharedApplication];
        [g_app setActivationPolicy:NSApplicationActivationPolicyRegular];
        JnUiAppDelegate *del = [JnUiAppDelegate new];
        [g_app setDelegate:del];
    }
    g_cocoa_active = 1;
}

void jnative_ui_platform_run_loop(void) {
    if (!g_cocoa_active) {
        return;
    }
    [g_app run];
}

void jnative_ui_platform_quit(void) {
    if (!g_cocoa_active) {
        return;
    }
    [g_app stop:nil];
    /* Unblock run loop */
    NSEvent *ev = [NSEvent otherEventWithType:NSEventTypeApplicationDefined
                                     location:NSMakePoint(0, 0)
                                modifierFlags:0
                                    timestamp:0
                                 windowNumber:0
                                      context:nil
                                      subtype:0
                                        data1:0
                                        data2:0];
    [g_app postEvent:ev atStart:YES];
}

void jnative_ui_platform_wakeup(void) {
    if (!g_cocoa_active) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        jnative_ui_emit_event(0, JN_UI_EVENT_TASK);
    });
}

int32_t jnative_ui_platform_create_window(int32_t peerId, const char *title, int32_t w, int32_t h) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return 0;
    }
    NSRect rect = NSMakeRect(100, 100, w > 0 ? w : 800, h > 0 ? h : 600);
    JnUiWindow *win = [[JnUiWindow alloc]
        initWithContentRect:rect
                  styleMask:(NSWindowStyleMaskTitled | NSWindowStyleMaskClosable
                             | NSWindowStyleMaskMiniaturizable | NSWindowStyleMaskResizable)
                    backing:NSBackingStoreBuffered
                      defer:NO];
    win.jnHandleId = peerId;
    win.delegate = win;
    [win setTitle:jn_nsstring(title)];
    g_peers[peerId] = win;
    return 1;
}

int32_t jnative_ui_platform_create_control(int32_t peerId, int32_t kind, int32_t parentId, const char *text) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return 0;
    }
    id parentObj = nil;
    if (parentId > 0 && parentId < JN_UI_MAX) {
        parentObj = g_peers[parentId];
    }
    NSView *parentView = nil;
    if ([parentObj isKindOfClass:[NSWindow class]]) {
        parentView = [(NSWindow *)parentObj contentView];
    } else if ([parentObj isKindOfClass:[NSView class]]) {
        parentView = (NSView *)parentObj;
    }
    if (kind == JN_UI_KIND_BUTTON) {
        JnUiButton *btn = [JnUiButton buttonWithTitle:jn_nsstring(text)
                                               target:nil
                                               action:NULL];
        btn.jnHandleId = peerId;
        [btn setTarget:btn];
        [btn setAction:@selector(jnClicked:)];
        [btn setBordered:YES];
        [btn setButtonType:NSButtonTypeMomentaryPushIn];
        if (@available(macOS 14.0, *)) {
            [btn setBezelStyle:NSBezelStyleFlexiblePush];
        } else {
            [btn setBezelStyle:NSBezelStyleRounded];
        }
        [btn setControlSize:NSControlSizeRegular];
        [btn setAlignment:NSTextAlignmentCenter];
        [btn sizeToFit];
        NSRect f = btn.frame;
        f.origin = NSMakePoint(20, 20);
        [btn setFrame:f];
        if (parentView != nil) {
            [parentView addSubview:btn];
        }
        g_peers[peerId] = btn;
        return 1;
    }
    if (kind == JN_UI_KIND_LABEL) {
        NSTextField *lab = [NSTextField labelWithString:jn_nsstring(text)];
        [lab setFrame:NSMakeRect(20, 60, 200, 24)];
        if (parentView != nil) {
            [parentView addSubview:lab];
        }
        g_peers[peerId] = lab;
        return 1;
    }
    if (kind == JN_UI_KIND_TEXTFIELD) {
        NSTextField *tf = [[NSTextField alloc] initWithFrame:NSMakeRect(20, 100, 200, 24)];
        [tf setStringValue:jn_nsstring(text)];
        if (parentView != nil) {
            [parentView addSubview:tf];
        }
        g_peers[peerId] = tf;
        return 1;
    }
    if (kind == JN_UI_KIND_CANVAS) {
        JnCanvasView *view = [[JnCanvasView alloc] initWithFrame:NSMakeRect(0, 0, 400, 300)];
        view.jnHandleId = peerId;
        view.dragging = 0;
        if (parentView != nil) {
            [parentView addSubview:view];
        }
        g_peers[peerId] = view;
        return 1;
    }
    if (kind == JN_UI_KIND_PANEL || kind == JN_UI_KIND_SCROLL || kind == JN_UI_KIND_SPLIT
            || kind == JN_UI_KIND_TABPANE) {
        NSView *view = [[NSView alloc] initWithFrame:NSMakeRect(0, 0, 400, 300)];
        if (parentView != nil) {
            [parentView addSubview:view];
        }
        g_peers[peerId] = view;
        return 1;
    }
    if (kind == JN_UI_KIND_CHECKBOX) {
        NSButton *btn = [NSButton checkboxWithTitle:jn_nsstring(text) target:nil action:NULL];
        [btn setFrame:NSMakeRect(20, 20, 160, 28)];
        if (parentView != nil) {
            [parentView addSubview:btn];
        }
        g_peers[peerId] = btn;
        return 1;
    }
    if (kind == JN_UI_KIND_RADIO) {
        NSButton *btn = [NSButton radioButtonWithTitle:jn_nsstring(text) target:nil action:NULL];
        [btn setFrame:NSMakeRect(20, 20, 160, 28)];
        if (parentView != nil) {
            [parentView addSubview:btn];
        }
        g_peers[peerId] = btn;
        return 1;
    }
    if (kind == JN_UI_KIND_COMBOBOX) {
        NSComboBox *box = [[NSComboBox alloc] initWithFrame:NSMakeRect(20, 20, 180, 26)];
        if (parentView != nil) {
            [parentView addSubview:box];
        }
        g_peers[peerId] = box;
        return 1;
    }
    if (kind == JN_UI_KIND_TEXTAREA) {
        NSScrollView *scroll = [[NSScrollView alloc] initWithFrame:NSMakeRect(20, 20, 300, 120)];
        NSTextView *tv = [[NSTextView alloc] initWithFrame:scroll.bounds];
        [scroll setDocumentView:tv];
        [scroll setHasVerticalScroller:YES];
        if (parentView != nil) {
            [parentView addSubview:scroll];
        }
        g_peers[peerId] = scroll;
        return 1;
    }
    if (kind == JN_UI_KIND_PROGRESS) {
        NSProgressIndicator *bar = [[NSProgressIndicator alloc] initWithFrame:NSMakeRect(20, 20, 200, 20)];
        [bar setIndeterminate:NO];
        [bar setMinValue:0.0];
        [bar setMaxValue:1.0];
        if (parentView != nil) {
            [parentView addSubview:bar];
        }
        g_peers[peerId] = bar;
        return 1;
    }
    if (kind == JN_UI_KIND_LISTVIEW || kind == JN_UI_KIND_TABLEVIEW) {
        NSScrollView *scroll = [[NSScrollView alloc] initWithFrame:NSMakeRect(20, 20, 240, 200)];
        NSTableView *table = [[NSTableView alloc] initWithFrame:scroll.bounds];
        NSTableColumn *col = [[NSTableColumn alloc] initWithIdentifier:@"c0"];
        [col setTitle:@"Item"];
        [table addTableColumn:col];
        [scroll setDocumentView:table];
        [scroll setHasVerticalScroller:YES];
        if (parentView != nil) {
            [parentView addSubview:scroll];
        }
        g_peers[peerId] = scroll;
        return 1;
    }
    if (kind == JN_UI_KIND_TREEVIEW) {
        NSScrollView *scroll = [[NSScrollView alloc] initWithFrame:NSMakeRect(20, 20, 240, 200)];
        NSOutlineView *outline = [[NSOutlineView alloc] initWithFrame:scroll.bounds];
        NSTableColumn *col = [[NSTableColumn alloc] initWithIdentifier:@"c0"];
        [col setTitle:@"Node"];
        [outline addTableColumn:col];
        [outline setOutlineTableColumn:col];
        [scroll setDocumentView:outline];
        [scroll setHasVerticalScroller:YES];
        if (parentView != nil) {
            [parentView addSubview:scroll];
        }
        g_peers[peerId] = scroll;
        return 1;
    }
    if (kind == JN_UI_KIND_MENUITEM || kind == JN_UI_KIND_SEPARATOR) {
        /* Menu peers are bookkeeping-only on Cocoa for 0.18; MenuBar wiring is managed. */
        g_peers[peerId] = [NSObject new];
        return 1;
    }
    return 0;
}

int32_t jnative_ui_platform_dialog_message(int32_t windowId, int32_t type,
                                           const char *title, const char *message) {
    if (!g_cocoa_active) {
        return -1;
    }
    (void)windowId;
    NSAlert *alert = [[NSAlert alloc] init];
    [alert setMessageText:jn_nsstring(title)];
    [alert setInformativeText:jn_nsstring(message)];
    if (type == 1) {
        [alert addButtonWithTitle:@"Yes"];
        [alert addButtonWithTitle:@"No"];
    } else if (type == 3) {
        [alert addButtonWithTitle:@"Save"];
        [alert addButtonWithTitle:@"Don't Save"];
        [alert addButtonWithTitle:@"Cancel"];
    } else {
        [alert addButtonWithTitle:@"OK"];
    }
    if (type == 2) {
        [alert setAlertStyle:NSAlertStyleCritical];
    }
    NSModalResponse r = [alert runModal];
    if (type == 3) {
        if (r == NSAlertFirstButtonReturn) return 1;      /* Save */
        if (r == NSAlertSecondButtonReturn) return 2;     /* Don't Save */
        return 0;                                        /* Cancel */
    }
    return (r == NSAlertFirstButtonReturn) ? 1 : 0;
}

char *jnative_ui_platform_dialog_file(int32_t windowId, int32_t save, const char *filter) {
    if (!g_cocoa_active) {
        return NULL;
    }
    (void)windowId;
    (void)filter;
    if (save) {
        NSSavePanel *panel = [NSSavePanel savePanel];
        if ([panel runModal] != NSModalResponseOK) {
            return NULL;
        }
        const char *utf8 = panel.URL.path.UTF8String;
        return utf8 ? strdup(utf8) : NULL;
    }
    NSOpenPanel *panel = [NSOpenPanel openPanel];
    if (save == 2) {
        [panel setCanChooseFiles:NO];
        [panel setCanChooseDirectories:YES];
        [panel setAllowsMultipleSelection:NO];
    } else {
        [panel setCanChooseFiles:YES];
        [panel setCanChooseDirectories:NO];
    }
    if ([panel runModal] != NSModalResponseOK) {
        return NULL;
    }
    const char *utf8 = panel.URL.path.UTF8String;
    return utf8 ? strdup(utf8) : NULL;
}

void jnative_ui_platform_destroy(int32_t peerId) {
    if (peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    g_peers[peerId] = nil;
    if ([obj isKindOfClass:[NSWindow class]]) {
        [(NSWindow *)obj close];
    } else if ([obj isKindOfClass:[NSView class]]) {
        [(NSView *)obj removeFromSuperview];
    }
}

void jnative_ui_platform_show(int32_t handleId) {
    id obj = (handleId > 0 && handleId < JN_UI_MAX) ? g_peers[handleId] : nil;
    if ([obj isKindOfClass:[NSWindow class]]) {
        [(NSWindow *)obj makeKeyAndOrderFront:nil];
        [g_app activateIgnoringOtherApps:YES];
    } else if ([obj isKindOfClass:[NSView class]]) {
        [(NSView *)obj setHidden:NO];
    }
}

void jnative_ui_platform_hide(int32_t handleId) {
    id obj = (handleId > 0 && handleId < JN_UI_MAX) ? g_peers[handleId] : nil;
    if ([obj isKindOfClass:[NSWindow class]]) {
        [(NSWindow *)obj orderOut:nil];
    } else if ([obj isKindOfClass:[NSView class]]) {
        [(NSView *)obj setHidden:YES];
    }
}

void jnative_ui_platform_set_title(int32_t handleId, const char *title) {
    id obj = (handleId > 0 && handleId < JN_UI_MAX) ? g_peers[handleId] : nil;
    if ([obj isKindOfClass:[NSWindow class]]) {
        [(NSWindow *)obj setTitle:jn_nsstring(title)];
    }
}

void jnative_ui_platform_set_text(int32_t handleId, const char *text) {
    id obj = (handleId > 0 && handleId < JN_UI_MAX) ? g_peers[handleId] : nil;
    if ([obj isKindOfClass:[NSButton class]]) {
        [(NSButton *)obj setTitle:jn_nsstring(text)];
    } else if ([obj isKindOfClass:[NSTextField class]]) {
        [(NSTextField *)obj setStringValue:jn_nsstring(text)];
    }
}

void jnative_ui_platform_set_bounds(int32_t handleId, int32_t x, int32_t y, int32_t w, int32_t h) {
    id obj = (handleId > 0 && handleId < JN_UI_MAX) ? g_peers[handleId] : nil;
    if ([obj isKindOfClass:[NSView class]]) {
        NSView *view = (NSView *)obj;
        NSView *superView = view.superview;
        CGFloat useW = w;
        CGFloat useH = h;
        CGFloat useX = x;
        CGFloat useY = y;
        /* Keep intrinsic button height so the title stays vertically centered.
           Width stays on the layout slot so neighbors do not overlap. */
        if ([view isKindOfClass:[JnUiButton class]]) {
            NSButton *btn = (NSButton *)view;
            NSSize fit = [btn fittingSize];
            if (fit.height > 1.0) {
                if (useH > fit.height) {
                    useY = y + (CGFloat)((int)((useH - fit.height) * 0.5));
                }
                useH = fit.height;
            }
        }
        /* JNative layout uses top-left origin; AppKit uses bottom-left. */
        CGFloat yCocoa = useY;
        if (superView != nil) {
            yCocoa = superView.bounds.size.height - useY - useH;
        }
        [view setFrame:NSMakeRect(useX, yCocoa, useW, useH)];
    } else if ([obj isKindOfClass:[NSWindow class]]) {
        NSRect f = [(NSWindow *)obj frame];
        f.size.width = w;
        f.size.height = h;
        [(NSWindow *)obj setFrame:f display:YES];
    }
}

void jnative_ui_platform_set_visible(int32_t handleId, int32_t visible) {
    if (visible) {
        jnative_ui_platform_show(handleId);
    } else {
        jnative_ui_platform_hide(handleId);
    }
}

void jnative_ui_platform_set_enabled(int32_t handleId, int32_t enabled) {
    id obj = (handleId > 0 && handleId < JN_UI_MAX) ? g_peers[handleId] : nil;
    if ([obj respondsToSelector:@selector(setEnabled:)]) {
        [obj setEnabled:enabled != 0];
    }
}

void jnative_ui_platform_set_content(int32_t windowId, int32_t childId) {
    id win = (windowId > 0 && windowId < JN_UI_MAX) ? g_peers[windowId] : nil;
    id child = (childId > 0 && childId < JN_UI_MAX) ? g_peers[childId] : nil;
    if ([win isKindOfClass:[NSWindow class]] && [child isKindOfClass:[NSView class]]) {
        NSView *cv = [(NSWindow *)win contentView];
        if ([(NSView *)child superview] != cv) {
            [cv addSubview:(NSView *)child];
        }
        NSRect bounds = [cv bounds];
        [(NSView *)child setFrame:bounds];
        [(NSView *)child setAutoresizingMask:(NSViewWidthSizable | NSViewHeightSizable)];
    }
}

void jnative_ui_platform_close_window(int32_t windowId) {
    id win = (windowId > 0 && windowId < JN_UI_MAX) ? g_peers[windowId] : nil;
    if ([win isKindOfClass:[NSWindow class]]) {
        [(NSWindow *)win setDelegate:nil];
        [(NSWindow *)win close];
        g_peers[windowId] = nil;
    }
}

void jnative_ui_platform_focus(int32_t peerId) {
    id obj = (peerId > 0 && peerId < JN_UI_MAX) ? g_peers[peerId] : nil;
    if ([obj isKindOfClass:[NSView class]]) {
        NSView *view = (NSView *)obj;
        NSWindow *win = view.window;
        if (win != nil) {
            [win makeKeyAndOrderFront:nil];
            [win makeFirstResponder:view];
        }
    }
}

void jnative_ui_platform_set_menubar(int32_t windowId, int32_t menuBarId) {
    (void)windowId;
    if (!g_cocoa_active || menuBarId <= 0) {
        return;
    }
    if (g_menu_target == nil) {
        g_menu_target = [JnUiMenuTarget new];
    }
    NSMenu *mainMenu = [[NSMenu alloc] initWithTitle:@"MainMenu"];
    /* Standard app menu so Cmd+Q etc. remain available. */
    NSMenuItem *appItem = [[NSMenuItem alloc] init];
    NSMenu *appMenu = [[NSMenu alloc] initWithTitle:@"JNative"];
    [appMenu addItemWithTitle:@"About JNative Notepad"
                       action:@selector(orderFrontStandardAboutPanel:)
                keyEquivalent:@""];
    [appMenu addItem:[NSMenuItem separatorItem]];
    [appMenu addItemWithTitle:@"Quit"
                       action:@selector(terminate:)
                keyEquivalent:@"q"];
    [appItem setSubmenu:appMenu];
    [mainMenu addItem:appItem];

    int32_t menuCount = jnative_ui_menu_child_count(menuBarId);
    for (int32_t mi = 0; mi < menuCount; mi++) {
        int32_t menuId = jnative_ui_menu_child_at(menuBarId, mi);
        if (menuId <= 0) continue;
        const char *mtitle = jnative_ui_peer_text(menuId);
        NSString *menuTitle = jn_nsstring(mtitle);
        NSMenuItem *top = [[NSMenuItem alloc] init];
        NSMenu *submenu = [[NSMenu alloc] initWithTitle:menuTitle];
        int32_t itemCount = jnative_ui_menu_child_count(menuId);
        for (int32_t ii = 0; ii < itemCount; ii++) {
            int32_t itemId = jnative_ui_menu_child_at(menuId, ii);
            if (itemId <= 0) continue;
            int32_t kind = jnative_ui_peer_kind(itemId);
            const char *itext = jnative_ui_peer_text(itemId);
            if (kind == JN_UI_KIND_SEPARATOR || (itext && strcmp(itext, "-") == 0)) {
                [submenu addItem:[NSMenuItem separatorItem]];
                continue;
            }
            NSMenuItem *item = [[NSMenuItem alloc]
                initWithTitle:jn_nsstring(itext)
                       action:@selector(jnMenuAction:)
                keyEquivalent:@""];
            [item setTarget:g_menu_target];
            [item setTag:(NSInteger)itemId];
            [submenu addItem:item];
        }
        [top setSubmenu:submenu];
        [top setTitle:menuTitle];
        [mainMenu addItem:top];
    }
    [g_app setMainMenu:mainMenu];
}

void jnative_ui_platform_reparent(int32_t childId, int32_t parentId) {
    id child = (childId > 0 && childId < JN_UI_MAX) ? g_peers[childId] : nil;
    id parent = (parentId > 0 && parentId < JN_UI_MAX) ? g_peers[parentId] : nil;
    if (![child isKindOfClass:[NSView class]]) {
        return;
    }
    NSView *parentView = nil;
    if ([parent isKindOfClass:[NSWindow class]]) {
        parentView = [(NSWindow *)parent contentView];
    } else if ([parent isKindOfClass:[NSView class]]) {
        parentView = (NSView *)parent;
    }
    if (parentView == nil) {
        return;
    }
    NSView *childView = (NSView *)child;
    if ([childView superview] != parentView) {
        [parentView addSubview:childView];
    }
}

void jnative_ui_platform_canvas_present(int32_t peerId, const uint8_t *rgba, int32_t w, int32_t h) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[JnCanvasView class]]) {
        [(JnCanvasView *)obj jnPresentRgba:rgba width:w height:h];
    }
}

void jnative_ui_platform_canvas_invalidate(int32_t peerId) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[NSView class]]) {
        /* Ask managed side to paint, then present will refresh bitmap. */
        jnative_ui_emit_event(peerId, JN_UI_EVENT_PAINT);
        [(NSView *)obj setNeedsDisplay:YES];
    }
}

void jnative_ui_platform_timer_arm(int32_t timerId, int32_t intervalMs, int32_t handleId) {
    if (!g_cocoa_active) {
        return;
    }
    if (g_cocoa_timers == nil) {
        g_cocoa_timers = [NSMutableDictionary dictionary];
    }
    NSNumber *key = @(timerId);
    NSTimer *old = g_cocoa_timers[key];
    if (old != nil) {
        [old invalidate];
    }
    NSTimeInterval sec = intervalMs / 1000.0;
    if (sec < 0.001) {
        sec = 0.001;
    }
    int32_t hid = handleId;
    NSTimer *timer = [NSTimer scheduledTimerWithTimeInterval:sec
                                                     repeats:YES
                                                       block:^(__unused NSTimer *t) {
        jnative_ui_emit_event(hid, JN_UI_EVENT_TIMER);
    }];
    g_cocoa_timers[key] = timer;
}

void jnative_ui_platform_timer_disarm(int32_t timerId) {
    if (g_cocoa_timers == nil) {
        return;
    }
    NSNumber *key = @(timerId);
    NSTimer *old = g_cocoa_timers[key];
    if (old != nil) {
        [old invalidate];
        [g_cocoa_timers removeObjectForKey:key];
    }
}

int32_t jnative_ui_platform_clipboard_set(const char *text) {
    if (!g_cocoa_active) {
        return 0;
    }
    NSPasteboard *pb = [NSPasteboard generalPasteboard];
    [pb clearContents];
    NSString *s = jn_nsstring(text);
    return [pb setString:s forType:NSPasteboardTypeString] ? 1 : 0;
}

char *jnative_ui_platform_clipboard_get(void) {
    if (!g_cocoa_active) {
        return NULL;
    }
    NSPasteboard *pb = [NSPasteboard generalPasteboard];
    NSString *s = [pb stringForType:NSPasteboardTypeString];
    if (s == nil) {
        return NULL;
    }
    const char *utf8 = s.UTF8String;
    return utf8 ? strdup(utf8) : NULL;
}

/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

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

static int32_t jn_cocoa_key_code(NSEvent *event);
static int32_t jn_cocoa_key_mods(NSEvent *event);

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
- (void)jnAppQuit:(id)sender {
    (void)sender;
    /* Managed quit: Java Application.requestQuit → hooks → quitNative. */
    jnative_ui_request_quit();
}
@end

static JnUiMenuTarget *g_menu_target = nil;

static void jn_apply_menu_shortcut(NSMenuItem *item, int32_t itemId) {
    int32_t key = jnative_ui_menu_get_shortcut_key(itemId);
    int32_t mods = jnative_ui_menu_get_shortcut_mods(itemId);
    if (key <= 0 || (mods & 16) != 0) { /* bit 16 = display-only label */
        [item setKeyEquivalent:@""];
        return;
    }
    unichar ch = (unichar)key;
    BOOL fkey = (key >= 112 && key <= 123);
    if (fkey) {
        ch = (unichar)(NSF1FunctionKey + (key - 112));
    } else if (ch >= 'A' && ch <= 'Z') {
        ch = (unichar)(ch - 'A' + 'a');
    }
    NSString *ke = [NSString stringWithCharacters:&ch length:1];
    [item setKeyEquivalent:ke];
    NSUInteger mask = fkey ? 0 : NSEventModifierFlagCommand;
    if ((mods & 4) != 0) {
        mask = NSEventModifierFlagControl;
    }
    if ((mods & 8) != 0) {
        mask = NSEventModifierFlagCommand;
    }
    if ((mods & 1) != 0) {
        mask |= NSEventModifierFlagShift;
    }
    if ((mods & 2) != 0) {
        mask |= NSEventModifierFlagOption;
    }
    [item setKeyEquivalentModifierMask:mask];
}

/* Live menu state (Enabled / checkmark) after the menu bar was built. */
static NSMenuItem *jn_find_menu_item(NSMenu *menu, int32_t itemId) {
    for (NSMenuItem *mi in menu.itemArray) {
        if (mi.tag == (NSInteger)itemId && !mi.hasSubmenu) {
            return mi;
        }
        if (mi.hasSubmenu) {
            NSMenuItem *r = jn_find_menu_item(mi.submenu, itemId);
            if (r != nil) {
                return r;
            }
        }
    }
    return nil;
}

static void jn_menu_item_refresh(int32_t itemId) {
    NSMenu *mm = [NSApp mainMenu];
    if (mm == nil) {
        return;
    }
    NSMenuItem *mi = jn_find_menu_item(mm, itemId);
    if (mi == nil) {
        return;
    }
    [mi setEnabled:jnative_ui_get_enabled(itemId) != 0];
    [mi setState:jnative_ui_get_selected(itemId) != 0 ? NSControlStateValueOn
                                                       : NSControlStateValueOff];
}

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
- (void)windowDidResize:(NSNotification *)notification {
    (void)notification;
    if (self.jnHandleId <= 0) {
        return;
    }
    NSSize sz = self.contentView.bounds.size;
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_RESIZE,
                             (int32_t)(sz.width + 0.5), (int32_t)(sz.height + 0.5), 0, 0);
}
/* Cmd+X/C/V/A/Z in native text inputs. Apps built here rarely have a standard Edit menu, and
 * without one AppKit swallows these shortcuts, so route them to the focused text view ourselves.
 * Canvas-based editors (not NSTextView) are untouched and still receive the key event. */
- (BOOL)performKeyEquivalent:(NSEvent *)event {
    NSResponder *fr = [self firstResponder];
    NSEventModifierFlags f = event.modifierFlags & NSEventModifierFlagDeviceIndependentFlagsMask;
    if ([fr isKindOfClass:[NSTextView class]] && event.type == NSEventTypeKeyDown) {
        NSString *ch = [event charactersIgnoringModifiers];
        BOOL cmd = (f & (NSEventModifierFlagCommand | NSEventModifierFlagControl |
                         NSEventModifierFlagOption)) == NSEventModifierFlagCommand;
        BOOL cmdShift = (f & (NSEventModifierFlagCommand | NSEventModifierFlagShift)) ==
                        (NSEventModifierFlagCommand | NSEventModifierFlagShift);
        if (ch.length == 1 && (cmd || cmdShift)) {
            NSTextView *tv = (NSTextView *)fr;
            unichar c = [[ch lowercaseString] characterAtIndex:0];
            if (cmd && c == 'x') {
                [tv cut:nil];
                return YES;
            } else if (cmd && c == 'c') {
                [tv copy:nil];
                return YES;
            } else if (cmd && c == 'v') {
                [tv paste:nil];
                return YES;
            } else if (cmd && c == 'a') {
                [tv selectAll:nil];
                return YES;
            } else if (c == 'z') {
                if (cmdShift) {
                    [[tv undoManager] redo];
                } else {
                    [[tv undoManager] undo];
                }
                return YES;
            }
        }
    }
    return [super performKeyEquivalent:event];
}
- (void)keyDown:(NSEvent *)event {
    NSResponder *fr = [self firstResponder];
    /* Let text editing keep native Return/Tab/edit keys; still notify managed for Escape. */
    int32_t code = jn_cocoa_key_code(event);
    int32_t mods = jn_cocoa_key_mods(event);
    int inText = 0;
    if ([fr isKindOfClass:[NSTextView class]] || [fr isKindOfClass:[NSTextField class]]) {
        inText = 1;
    }
    int32_t typed = 0;
    if ((mods & (2 | 8)) == 0) {
        NSString *tc = event.characters;
        if (tc != nil && tc.length > 0) {
            unichar c0 = [tc characterAtIndex:0];
            if (c0 >= 32 && c0 < 127) {
                typed = (int32_t)c0;
            }
        }
    }
    if (inText == 0 || code == 27) {
        if (self.jnHandleId > 0) {
            jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_KEY, JN_UI_KEY_DOWN,
                                     code, mods, typed);
        }
    }
    /* Printable keys outside text inputs have no native meaning here: swallow them so
     * app-level shortcuts (calculator keypad) do not beep. */
    if (inText == 0 && typed != 0 && (mods & (2 | 8)) == 0) {
        return;
    }
    [super keyDown:event];
}
@end

@interface JnUiButton : NSButton
@property(nonatomic, assign) int32_t jnHandleId;
@property(nonatomic, assign) int32_t jnFilled;
@property(nonatomic, assign) int32_t jnBgRgba;
@property(nonatomic, assign) int32_t jnFgRgba;
@property(nonatomic, assign) int32_t jnRadius;
@property(nonatomic, assign) int32_t jnHover;
@property(nonatomic, assign) int32_t jnPressed;
- (void)jnApplyFilledVisual;
@end
@implementation JnUiButton
- (BOOL)becomeFirstResponder {
    BOOL ok = [super becomeFirstResponder];
    if (ok && self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 1, 0, 0, 0);
    }
    return ok;
}
- (BOOL)resignFirstResponder {
    if (self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 2, 0, 0, 0);
    }
    return [super resignFirstResponder];
}
- (void)jnClicked:(id)sender {
    (void)sender;
    jnative_ui_emit_event(self.jnHandleId, JN_UI_EVENT_CLICK);
}
- (void)updateTrackingAreas {
    [super updateTrackingAreas];
    for (NSTrackingArea *a in [self.trackingAreas copy]) {
        [self removeTrackingArea:a];
    }
    if (self.jnFilled != 0) {
        NSTrackingAreaOptions opts = NSTrackingMouseEnteredAndExited | NSTrackingActiveInKeyWindow | NSTrackingInVisibleRect;
        NSTrackingArea *area = [[NSTrackingArea alloc] initWithRect:self.bounds
                                                            options:opts
                                                              owner:self
                                                           userInfo:nil];
        [self addTrackingArea:area];
    }
}
- (void)mouseEntered:(NSEvent *)event {
    (void)event;
    self.jnHover = 1;
    [self jnApplyFilledVisual];
}
- (void)mouseExited:(NSEvent *)event {
    (void)event;
    self.jnHover = 0;
    self.jnPressed = 0;
    [self jnApplyFilledVisual];
}
- (void)mouseDown:(NSEvent *)event {
    if (self.jnFilled != 0) {
        self.jnPressed = 1;
        [self jnApplyFilledVisual];
    }
    [super mouseDown:event];
    if (self.jnFilled != 0) {
        self.jnPressed = 0;
        [self jnApplyFilledVisual];
    }
}
static NSColor *jn_color_from_rgba(int32_t rgba) {
    CGFloat a = ((rgba >> 24) & 0xFF) / 255.0;
    CGFloat r = ((rgba >> 16) & 0xFF) / 255.0;
    CGFloat g = ((rgba >> 8) & 0xFF) / 255.0;
    CGFloat b = (rgba & 0xFF) / 255.0;
    return [NSColor colorWithCalibratedRed:r green:g blue:b alpha:a];
}
static int32_t jn_lighten_rgba(int32_t rgba, int delta) {
    int r = ((rgba >> 16) & 0xFF) + delta;
    int g = ((rgba >> 8) & 0xFF) + delta;
    int b = (rgba & 0xFF) + delta;
    int a = (rgba >> 24) & 0xFF;
    if (r > 255) r = 255;
    if (g > 255) g = 255;
    if (b > 255) b = 255;
    if (r < 0) r = 0;
    if (g < 0) g = 0;
    if (b < 0) b = 0;
    return (a << 24) | (r << 16) | (g << 8) | b;
}
- (void)jnApplyFilledVisual {
    if (self.jnFilled == 0) {
        return;
    }
    self.wantsLayer = YES;
    [self setBordered:NO];
    [self setButtonType:NSButtonTypeMomentaryChange];
    int32_t bg = self.jnBgRgba;
    if (self.jnPressed != 0) {
        bg = jn_lighten_rgba(bg, -28);
    } else if (self.jnHover != 0) {
        bg = jn_lighten_rgba(bg, 18);
    }
    self.layer.backgroundColor = jn_color_from_rgba(bg).CGColor;
    self.layer.cornerRadius = (CGFloat)(self.jnRadius > 0 ? self.jnRadius : 14);
    self.layer.masksToBounds = YES;
    NSMutableDictionary *attrs = [NSMutableDictionary dictionary];
    attrs[NSForegroundColorAttributeName] = jn_color_from_rgba(self.jnFgRgba);
    NSFont *font = self.font;
    if (font == nil) {
        font = [NSFont systemFontOfSize:22.0 weight:NSFontWeightMedium];
    }
    attrs[NSFontAttributeName] = font;
    NSAttributedString *title = [[NSAttributedString alloc] initWithString:self.title ?: @""
                                                               attributes:attrs];
    [self setAttributedTitle:title];
    [self setAlignment:NSTextAlignmentCenter];
}
@end

@interface JnUiSegmented : NSSegmentedControl
@property(nonatomic, assign) int32_t jnHandleId;
- (void)jnChanged:(id)sender;
@end
@implementation JnUiSegmented
- (void)jnChanged:(id)sender {
    (void)sender;
    int32_t idx = (int32_t)[self selectedSegment];
    jnative_ui_set_selected_index(self.jnHandleId, idx);
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_SELECT, idx, 0, 0, 0);
}
@end

@interface JnUiCheck : NSButton
@property(nonatomic, assign) int32_t jnHandleId;
- (void)jnToggled:(id)sender;
@end
@implementation JnUiCheck
- (BOOL)becomeFirstResponder {
    BOOL ok = [super becomeFirstResponder];
    if (ok && self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 1, 0, 0, 0);
    }
    return ok;
}
- (BOOL)resignFirstResponder {
    if (self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 2, 0, 0, 0);
    }
    return [super resignFirstResponder];
}
- (void)jnToggled:(id)sender {
    (void)sender;
    int32_t sel = ([self state] == NSControlStateValueOn) ? 1 : 0;
    jnative_ui_set_selected(self.jnHandleId, sel);
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_TOGGLE, sel, 0, 0, 0);
}
@end

@interface JnUiTextField : NSTextField <NSTextFieldDelegate>
@property(nonatomic, assign) int32_t jnHandleId;
@end
@implementation JnUiTextField
- (BOOL)becomeFirstResponder {
    BOOL ok = [super becomeFirstResponder];
    if (ok && self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 1, 0, 0, 0);
    }
    return ok;
}
- (BOOL)resignFirstResponder {
    if (self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 2, 0, 0, 0);
    }
    return [super resignFirstResponder];
}
- (void)controlTextDidChange:(NSNotification *)obj {
    (void)obj;
    if (self.jnHandleId <= 0) {
        return;
    }
    NSString *s = [self stringValue];
    const char *utf8 = s.UTF8String;
    /* Store only — avoid set_text→platform rewrite that can move the caret. */
    jnative_ui_store_text(self.jnHandleId, utf8 ? utf8 : "");
    jnative_ui_emit_event(self.jnHandleId, JN_UI_EVENT_CHANGE);
}
@end

@interface JnUiSecureTextField : NSSecureTextField <NSTextFieldDelegate>
@property(nonatomic, assign) int32_t jnHandleId;
@end
@implementation JnUiSecureTextField
- (BOOL)becomeFirstResponder {
    BOOL ok = [super becomeFirstResponder];
    if (ok && self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 1, 0, 0, 0);
    }
    return ok;
}
- (BOOL)resignFirstResponder {
    if (self.jnHandleId > 0) {
        jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_FOCUS, 2, 0, 0, 0);
    }
    return [super resignFirstResponder];
}
- (void)controlTextDidChange:(NSNotification *)obj {
    (void)obj;
    if (self.jnHandleId <= 0) {
        return;
    }
    NSString *s = [self stringValue];
    const char *utf8 = s.UTF8String;
    jnative_ui_store_text(self.jnHandleId, utf8 ? utf8 : "");
    jnative_ui_emit_event(self.jnHandleId, JN_UI_EVENT_CHANGE);
}
@end

/* Plain-text multi-line editor (Notepad-class TextArea). */
@interface JnTextArea : NSTextView
@property(nonatomic, assign) int32_t jnHandleId;
@end
@implementation JnTextArea
- (void)didChangeText {
    [super didChangeText];
    if (self.jnHandleId > 0) {
        const char *u = self.string.UTF8String;
        jnative_ui_store_text(self.jnHandleId, u ? u : "");
        jnative_ui_emit_event(self.jnHandleId, JN_UI_EVENT_CHANGE);
    }
}
@end


@interface JnUiCombo : NSComboBox
@property(nonatomic, assign) int32_t jnHandleId;
@end
@implementation JnUiCombo
- (void)jnSelectionChanged:(NSNotification *)note {
    (void)note;
    if (self.jnHandleId <= 0) {
        return;
    }
    int32_t idx = (int32_t)[self indexOfSelectedItem];
    jnative_ui_set_selected_index(self.jnHandleId, idx);
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_SELECT, idx, 0, 0, 0);
    jnative_ui_emit_event(self.jnHandleId, JN_UI_EVENT_CHANGE);
}
@end

@interface JnTrendView : NSView
@property(nonatomic, assign) int32_t jnHandleId;
@property(nonatomic, strong) NSMutableArray<NSNumber *> *samples;
@property(nonatomic, assign) double yMin;
@property(nonatomic, assign) double yMax;
@property(nonatomic, assign) NSInteger capacity;
- (void)jnSetCsv:(NSString *)csv;
@end
@implementation JnTrendView
- (instancetype)initWithFrame:(NSRect)frameRect {
    self = [super initWithFrame:frameRect];
    if (self) {
        _samples = [NSMutableArray array];
        _yMin = 0.0;
        _yMax = 100.0;
        _capacity = 64;
        self.wantsLayer = YES;
    }
    return self;
}
- (BOOL)isFlipped { return YES; }
- (void)jnSetCsv:(NSString *)csv {
    [self.samples removeAllObjects];
    if (csv == nil || csv.length == 0) {
        [self setNeedsDisplay:YES];
        return;
    }
    /* Optional header: ymin,ymax,cap|v0,v1,...  or plain v0,v1,... */
    NSString *body = csv;
    NSRange bar = [csv rangeOfString:@"|"];
    if (bar.location != NSNotFound) {
        NSString *meta = [csv substringToIndex:bar.location];
        body = [csv substringFromIndex:bar.location + 1];
        NSArray *mp = [meta componentsSeparatedByString:@","];
        if (mp.count >= 2) {
            self.yMin = [mp[0] doubleValue];
            self.yMax = [mp[1] doubleValue];
        }
        if (mp.count >= 3) {
            NSInteger c = [mp[2] integerValue];
            if (c > 0 && c <= 4096) {
                self.capacity = c;
            }
        }
    }
    NSArray *parts = [body componentsSeparatedByString:@","];
    for (NSString *p in parts) {
        if (p.length == 0) continue;
        [self.samples addObject:@([p doubleValue])];
        while ((NSInteger)self.samples.count > self.capacity) {
            [self.samples removeObjectAtIndex:0];
        }
    }
    [self setNeedsDisplay:YES];
}
- (void)drawRect:(NSRect)dirtyRect {
    (void)dirtyRect;
    NSRect b = self.bounds;
    NSColor *bg = [NSColor controlBackgroundColor];
    NSColor *grid = [NSColor separatorColor];
    NSColor *line = [NSColor systemBlueColor];
    NSColor *empty = [NSColor secondaryLabelColor];
    [bg setFill];
    NSRectFill(b);
    [grid setStroke];
    NSBezierPath *border = [NSBezierPath bezierPathWithRoundedRect:NSInsetRect(b, 0.5, 0.5)
                                                           xRadius:4 yRadius:4];
    [border setLineWidth:1.0];
    [border stroke];
    if (self.samples.count < 2) {
        NSString *msg = @"无数据";
        NSDictionary *attrs = @{
            NSFontAttributeName: [NSFont systemFontOfSize:11.0],
            NSForegroundColorAttributeName: empty
        };
        NSSize sz = [msg sizeWithAttributes:attrs];
        NSPoint pt = NSMakePoint((b.size.width - sz.width) * 0.5,
                                 (b.size.height - sz.height) * 0.5);
        [msg drawAtPoint:pt withAttributes:attrs];
        return;
    }
    double ymin = self.yMin;
    double ymax = self.yMax;
    if (ymax <= ymin) {
        ymax = ymin + 1.0;
    }
    NSBezierPath *path = [NSBezierPath bezierPath];
    [path setLineWidth:1.5];
    [path setLineJoinStyle:NSLineJoinStyleRound];
    NSInteger n = (NSInteger)self.samples.count;
    for (NSInteger i = 0; i < n; i++) {
        double v = [self.samples[i] doubleValue];
        double t = (v - ymin) / (ymax - ymin);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        double x = 4.0 + (b.size.width - 8.0) * ((double)i / (double)(n - 1));
        double y = (b.size.height - 4.0) - t * (b.size.height - 8.0);
        if (i == 0) {
            [path moveToPoint:NSMakePoint(x, y)];
        } else {
            [path lineToPoint:NSMakePoint(x, y)];
        }
    }
    [line setStroke];
    [path stroke];
}
@end


/**
 * Table that paints zebra only for real rows — the empty clip region below
 * data stays solid (system usesAlternatingRowBackgroundColors stripes the
 * whole clip view even when row count is small).
 */
@interface JnTableView : NSTableView
@end
@implementation JnTableView
- (void)drawBackgroundInClipRect:(NSRect)clipRect {
    NSColor *base = [NSColor textBackgroundColor];
    NSColor *alt = [NSColor alternatingContentBackgroundColors].count > 1
                       ? [NSColor alternatingContentBackgroundColors][1]
                       : [NSColor controlBackgroundColor];
    [base setFill];
    NSRectFill(clipRect);
    NSInteger rows = self.numberOfRows;
    if (rows <= 0) {
        return;
    }
    CGFloat rowH = self.rowHeight + self.intercellSpacing.height;
    if (rowH < 1.0) {
        rowH = 22.0;
    }
    NSInteger first = (NSInteger)floor(NSMinY(clipRect) / rowH);
    if (first < 0) {
        first = 0;
    }
    NSInteger last = (NSInteger)ceil(NSMaxY(clipRect) / rowH);
    if (last > rows) {
        last = rows;
    }
    NSInteger r;
    for (r = first; r < last; r++) {
        if ((r % 2) != 0) {
            NSRect rowRect = NSMakeRect(NSMinX(clipRect), (CGFloat)r * rowH,
                                       NSWidth(clipRect), rowH);
            rowRect = NSIntersectionRect(rowRect, clipRect);
            if (!NSIsEmptyRect(rowRect)) {
                [alt setFill];
                NSRectFill(rowRect);
            }
        }
    }
}
@end

/** Bridges C peer item arrays into AppKit table/outline views. */
@interface JnTableBridge : NSObject <NSTableViewDataSource, NSTableViewDelegate,
                                     NSOutlineViewDataSource, NSOutlineViewDelegate>
@property(nonatomic, assign) int32_t peerId;
@end

static JnTableBridge *g_table_bridges[JN_UI_MAX];

static NSString *jn_ns_from_utf8(const char *text) {
    if (text == NULL) {
        return @"";
    }
    NSString *s = [NSString stringWithUTF8String:text];
    return s != nil ? s : @"";
}

static NSString *jn_tsv_field(const char *row, NSInteger col) {
    NSString *s = jn_ns_from_utf8(row);
    if (col <= 0) {
        NSRange tab = [s rangeOfString:@"\t"];
        if (tab.location == NSNotFound) {
            return s;
        }
        return [s substringToIndex:tab.location];
    }
    NSArray<NSString *> *parts = [s componentsSeparatedByString:@"\t"];
    if (col >= (NSInteger)parts.count) {
        return @"";
    }
    return parts[(NSUInteger)col];
}

static NSTableView *jn_table_view(int32_t peerId) {
    if (peerId <= 0 || peerId >= JN_UI_MAX) {
        return nil;
    }
    id obj = g_peers[peerId];
    if (![obj isKindOfClass:[NSScrollView class]]) {
        return nil;
    }
    id doc = [(NSScrollView *)obj documentView];
    if ([doc isKindOfClass:[NSTableView class]]) {
        return (NSTableView *)doc;
    }
    return nil;
}

static void jn_size_scroll_document(NSScrollView *scroll) {
    if (scroll == nil) {
        return;
    }
    NSView *doc = [scroll documentView];
    if (doc == nil) {
        return;
    }
    NSSize content = [scroll contentSize];
    if (content.width < 1.0) {
        content.width = scroll.bounds.size.width;
    }
    if ([doc isKindOfClass:[NSTableView class]]) {
        NSTableView *tv = (NSTableView *)doc;
        /* Width follows the clip view; height must stay row-intrinsic.
           Stretching the table frame vertically desyncs text from zebra stripes. */
        CGFloat rowPitch = tv.rowHeight + tv.intercellSpacing.height;
        if (rowPitch < 1.0) {
            rowPitch = 32.0;
        }
        CGFloat headerH = 0.0;
        if (tv.headerView != nil) {
            headerH = NSHeight(tv.headerView.frame);
        }
        CGFloat rowsH = (CGFloat)tv.numberOfRows * rowPitch;
        /* Keep document height row-intrinsic so empty clip stays solid. */
        if (rowsH < 1.0) {
            rowsH = 0.0;
        }
        [tv setUsesAlternatingRowBackgroundColors:NO];
        [tv setFrame:NSMakeRect(0, 0, content.width, rowsH + headerH)];
        if (tv.tableColumns.count > 0) {
            if (tv.columnAutoresizingStyle != NSTableViewFirstColumnOnlyAutoresizingStyle) {
                [tv setColumnAutoresizingStyle:NSTableViewFirstColumnOnlyAutoresizingStyle];
            }
        }
    } else {
        CGFloat h = content.height;
        if (h < 1.0) {
            h = scroll.bounds.size.height;
        }
        [doc setFrame:NSMakeRect(0, 0, content.width, h)];
    }
}

static NSImage *jn_symbol(NSString *name, NSString *fallback) {
    if (@available(macOS 11.0, *)) {
        NSImage *img = [NSImage imageWithSystemSymbolName:name accessibilityDescription:nil];
        if (img != nil) {
            NSImageSymbolConfiguration *cfg =
                [NSImageSymbolConfiguration configurationWithPointSize:14.0
                                                                weight:NSFontWeightMedium
                                                                 scale:NSImageSymbolScaleMedium];
            NSImage *configured = [img imageWithSymbolConfiguration:cfg];
            if (configured != nil) {
                img = configured;
            }
            [img setTemplate:YES];
            return img;
        }
    }
    if (fallback != nil) {
        NSImage *named = [NSImage imageNamed:fallback];
        if (named != nil) {
            return named;
        }
    }
    return [NSImage imageNamed:NSImageNameFolder];
}

static NSImage *jn_icon_for_kind(NSString *kind, BOOL isGroup) {
    if (isGroup) {
        return jn_symbol(@"internaldrive", NSImageNameComputer);
    }
    NSString *k = kind == nil ? @"" : [kind lowercaseString];
    if ([k isEqualToString:@"folder"] || [k hasPrefix:@"folder"]) {
        return jn_symbol(@"folder.fill", NSImageNameFolder);
    }
    if ([k containsString:@"image"] || [k containsString:@"png"] || [k containsString:@"jpg"]
            || [k containsString:@"jpeg"] || [k containsString:@"gif"] || [k containsString:@"heic"]) {
        return jn_symbol(@"photo", NSImageNameFolder);
    }
    if ([k containsString:@"pdf"]) {
        return jn_symbol(@"doc.richtext", NSImageNameFolder);
    }
    if ([k containsString:@"zip"] || [k containsString:@"archive"]) {
        return jn_symbol(@"doc.zipper", NSImageNameFolder);
    }
    if ([k containsString:@"text"] || [k containsString:@"code"] || [k hasSuffix:@" source"]) {
        return jn_symbol(@"doc.text", NSImageNameFolder);
    }
    /* Activity Monitor: second TSV field is often PID (digits) — use process glyph. */
    if (k.length > 0) {
        BOOL digits = YES;
        NSUInteger i;
        for (i = 0; i < k.length; i++) {
            unichar ch = [k characterAtIndex:i];
            if (ch < '0' || ch > '9') {
                digits = NO;
                break;
            }
        }
        if (digits) {
            return jn_symbol(@"gearshape", NSImageNameApplicationIcon);
        }
    }
    if ([k containsString:@"process"] || [k containsString:@"app"]) {
        return jn_symbol(@"app.dashed", NSImageNameApplicationIcon);
    }
    return jn_symbol(@"gearshape", NSImageNameApplicationIcon);
}

/** Prefer app-bundle icons; generic processes use a quiet SF Symbol (not doc/exec). */
static BOOL jn_path_is_app_bundle(NSString *path) {
    if (path == nil || path.length == 0) {
        return NO;
    }
    if ([path hasSuffix:@".app"]) {
        return YES;
    }
    NSRange r = [path rangeOfString:@".app/" options:0];
    return r.location != NSNotFound;
}

static NSImage *jn_icon_for_row(NSString *path, NSString *kind, NSSize logical) {
    if (jn_path_is_app_bundle(path)) {
        NSString *appPath = path;
        NSRange r = [path rangeOfString:@".app/" options:0];
        if (r.location != NSNotFound) {
            appPath = [path substringToIndex:(r.location + 4)];
        }
        NSImage *img = [[NSWorkspace sharedWorkspace] iconForFile:appPath];
        if (img != nil) {
            [img setSize:logical];
            return img;
        }
    }
    /* Quiet generic process glyph — avoid black exec / blank document icons. */
    NSImage *fallback = jn_symbol(@"gearshape", NSImageNameApplicationIcon);
    if (fallback == nil) {
        fallback = jn_icon_for_kind(kind, NO);
    }
    if (fallback != nil) {
        [fallback setSize:logical];
    }
    return fallback;
}

static NSInteger jn_tsv_field_count(NSString *raw) {
    if (raw == nil || raw.length == 0) {
        return 0;
    }
    NSInteger n = 1;
    NSUInteger i;
    for (i = 0; i < raw.length; i++) {
        if ([raw characterAtIndex:i] == '\t') {
            n++;
        }
    }
    return n;
}

static NSTableCellView *jn_make_cell(NSString *ident, BOOL withIcon) {
    NSTableCellView *cell = [[NSTableCellView alloc] initWithFrame:NSMakeRect(0, 0, 280, 32)];
    cell.identifier = ident;
    NSTextField *tf = [NSTextField labelWithString:@""];
    [tf setDrawsBackground:NO];
    [tf setBordered:NO];
    [tf setEditable:NO];
    [tf setLineBreakMode:NSLineBreakByTruncatingTail];
    [tf setFont:[NSFont systemFontOfSize:13.0]];
    [tf setBackgroundColor:[NSColor clearColor]];
    if (withIcon) {
        NSImageView *iv = [[NSImageView alloc] initWithFrame:NSMakeRect(4, 6, 20, 20)];
        [iv setImageScaling:NSImageScaleProportionallyUpOrDown];
        [iv setContentTintColor:[NSColor secondaryLabelColor]];
        [iv setAutoresizingMask:NSViewMaxXMargin | NSViewMinYMargin | NSViewMaxYMargin];
        [tf setFrame:NSMakeRect(30, 5, 240, 22)];
        [tf setAutoresizingMask:NSViewWidthSizable | NSViewMinYMargin | NSViewMaxYMargin];
        cell.imageView = iv;
        [cell addSubview:iv];
    } else {
        [tf setFrame:NSMakeRect(6, 5, 240, 22)];
        [tf setAutoresizingMask:NSViewWidthSizable | NSViewMinYMargin | NSViewMaxYMargin];
        [tf setTextColor:[NSColor secondaryLabelColor]];
    }
    cell.textField = tf;
    [cell addSubview:tf];
    return cell;
}

@implementation JnTableBridge
- (NSInteger)numberOfRowsInTableView:(NSTableView *)tableView {
    (void)tableView;
    return (NSInteger)jnative_ui_get_item_count(self.peerId);
}

- (id)tableView:(NSTableView *)tableView
        objectValueForTableColumn:(NSTableColumn *)tableColumn
                              row:(NSInteger)row {
    (void)tableView;
    const char *text = jnative_ui_item_text(self.peerId, (int32_t)row);
    NSInteger col = 0;
    if (tableColumn != nil && tableColumn.identifier != nil) {
        col = [tableColumn.identifier integerValue];
    }
    return jn_tsv_field(text, col);
}

- (NSView *)tableView:(NSTableView *)tableView
    viewForTableColumn:(NSTableColumn *)tableColumn
                  row:(NSInteger)row {
    NSInteger col = 0;
    if (tableColumn != nil && tableColumn.identifier != nil) {
        col = [tableColumn.identifier integerValue];
    }
    const char *raw = jnative_ui_item_text(self.peerId, (int32_t)row);
    NSString *rawS = jn_ns_from_utf8(raw);
    NSString *value = jn_tsv_field(raw, col);
    NSString *kind = jn_tsv_field(raw, 1);
    /* Optional trailing path field after visible columns for NSWorkspace icons. */
    NSString *iconPath = nil;
    NSInteger fields = jn_tsv_field_count(rawS);
    NSInteger colCount = (NSInteger)[[tableView tableColumns] count];
    if (fields > colCount) {
        iconPath = jn_tsv_field(raw, (int32_t)(fields - 1));
    }
    NSString *ident = (col == 0) ? @"jn.name" : @"jn.meta";
    NSTableCellView *cell = [tableView makeViewWithIdentifier:ident owner:nil];
    if (cell == nil) {
        cell = jn_make_cell(ident, col == 0);
    }
        cell.textField.stringValue = value;
    if (col == 0) {
        if (iconPath != nil && iconPath.length > 0) {
            cell.imageView.image = jn_icon_for_row(iconPath, kind, NSMakeSize(16, 16));
        } else {
            cell.imageView.image = nil;
        }
        [cell.imageView setContentTintColor:nil];
        /* labelColor tracks selection / key-window focus via cell view. */
        cell.textField.textColor = [NSColor labelColor];
        cell.textField.font = [NSFont systemFontOfSize:[NSFont systemFontSize]];
        cell.textField.lineBreakMode = NSLineBreakByTruncatingTail;
        [cell.textField setAlignment:NSTextAlignmentLeft];
        [cell.textField setToolTip:value];
        cell.toolTip = value;
    } else {
        cell.imageView.image = nil;
        cell.textField.textColor = [NSColor secondaryLabelColor];
        cell.textField.font = [NSFont monospacedDigitSystemFontOfSize:12.0
                                                               weight:NSFontWeightRegular];
        NSString *ctitle = tableColumn.title ?: @"";
        BOOL right = [ctitle containsString:@"%"]
                || [ctitle containsString:@"PID"]
                || [ctitle containsString:@"线程"]
                || [ctitle containsString:@"Threads"]
                || [ctitle containsString:@"内存"]
                || [ctitle containsString:@"Memory"]
                || [ctitle containsString:@"RSS"]
                || [ctitle containsString:@"占用"]
                || [ctitle containsString:@"CPU"];
        [cell.textField setAlignment:right ? NSTextAlignmentRight : NSTextAlignmentLeft];
        [cell.textField setToolTip:value];
    }
    return cell;
}

- (void)tableView:(NSTableView *)tableView didClickTableColumn:(NSTableColumn *)tableColumn {
    (void)tableView;
    if (tableColumn == nil || tableColumn.identifier == nil) {
        return;
    }
    int32_t col = (int32_t)[tableColumn.identifier integerValue];
    int32_t peerId = self.peerId;
    dispatch_async(dispatch_get_main_queue(), ^{
        jnative_ui_emit_event_ex(peerId, JN_UI_EVENT_HEADER, col, 0, 0, 0);
    });
}

- (void)tableViewSelectionDidChange:(NSNotification *)notification {
    NSTableView *tv = notification.object;
    if (![tv isKindOfClass:[NSTableView class]]) {
        return;
    }
    /* Outline views use outlineViewSelectionDidChange: (model index). */
    if ([tv isKindOfClass:[NSOutlineView class]]) {
        return;
    }
    NSInteger row = tv.selectedRow;
    if (row < 0) {
        return;
    }
    int32_t peerId = self.peerId;
    int32_t idx = (int32_t)row;
    /* Defer out of AppKit selection posting — sync navigate/reload can SEGV. */
    dispatch_async(dispatch_get_main_queue(), ^{
        jnative_ui_set_selected_index(peerId, idx);
        jnative_ui_emit_event_ex(peerId, JN_UI_EVENT_SELECT, idx, 0, 0, 0);
    });
}

/** Activate row (Finder-style double-click). Defer ACTION so reload/open
    is not nested inside AppKit's double-click delivery (avoids crash). */
- (void)jnDoubleActivate:(id)sender {
    if (![sender isKindOfClass:[NSTableView class]]) {
        return;
    }
    NSTableView *tv = (NSTableView *)sender;
    NSInteger row = tv.clickedRow;
    if (row < 0) {
        row = tv.selectedRow;
    }
    if (row < 0) {
        return;
    }
    int32_t peerId = self.peerId;
    int32_t idx = (int32_t)row;
    if ([tv isKindOfClass:[NSOutlineView class]]) {
        id item = [(NSOutlineView *)tv itemAtRow:row];
        if (item == nil) {
            return;
        }
        idx = (int32_t)[item intValue];
    }
    /* Defer SELECT+ACTION together — sync SELECT nested in AppKit double-click
       can SEGV the same way as outlineViewSelectionDidChange. */
    dispatch_async(dispatch_get_main_queue(), ^{
        jnative_ui_set_selected_index(peerId, idx);
        jnative_ui_emit_event_ex(peerId, JN_UI_EVENT_SELECT, idx, 0, 0, 0);
        jnative_ui_emit_event(peerId, JN_UI_EVENT_ACTION);
    });
}

- (NSInteger)outlineView:(NSOutlineView *)outlineView numberOfChildrenOfItem:(id)item {
    (void)outlineView;
    int32_t parent = (item == nil) ? -1 : (int32_t)[item intValue];
    int32_t n = jnative_ui_get_item_count(self.peerId);
    NSInteger count = 0;
    for (int32_t i = 0; i < n; i++) {
        if (jnative_ui_item_parent(self.peerId, i) == parent) {
            count++;
        }
    }
    return count;
}

- (id)outlineView:(NSOutlineView *)outlineView child:(NSInteger)index ofItem:(id)item {
    (void)outlineView;
    int32_t parent = (item == nil) ? -1 : (int32_t)[item intValue];
    int32_t n = jnative_ui_get_item_count(self.peerId);
    NSInteger seen = 0;
    for (int32_t i = 0; i < n; i++) {
        if (jnative_ui_item_parent(self.peerId, i) == parent) {
            if (seen == index) {
                return @(i);
            }
            seen++;
        }
    }
    return nil;
}

- (BOOL)outlineView:(NSOutlineView *)outlineView isItemExpandable:(id)item {
    return [self outlineView:outlineView numberOfChildrenOfItem:item] > 0;
}

- (id)outlineView:(NSOutlineView *)outlineView
        objectValueForTableColumn:(NSTableColumn *)tableColumn
                           byItem:(id)item {
    (void)outlineView;
    (void)tableColumn;
    if (item == nil) {
        return @"";
    }
    int32_t idx = (int32_t)[item intValue];
    return jn_ns_from_utf8(jnative_ui_item_text(self.peerId, idx));
}

- (NSView *)outlineView:(NSOutlineView *)outlineView
     viewForTableColumn:(NSTableColumn *)tableColumn
                   item:(id)item {
    (void)tableColumn;
    if (item == nil) {
        return nil;
    }
    int32_t idx = (int32_t)[item intValue];
    NSString *label = jn_ns_from_utf8(jnative_ui_item_text(self.peerId, idx));
    BOOL group = [self outlineView:outlineView isItemExpandable:item];
    NSString *ident = group ? @"jn.nav.group" : @"jn.nav.leaf";
    NSTableCellView *cell = [outlineView makeViewWithIdentifier:ident owner:nil];
    if (cell == nil) {
        cell = jn_make_cell(ident, YES);
    }
    cell.textField.stringValue = label;
    cell.textField.textColor = [NSColor labelColor];
    if (group) {
        cell.textField.font = [NSFont boldSystemFontOfSize:12.0];
        cell.imageView.image = jn_symbol(@"folder.fill", NSImageNameFolder);
        NSString *lower = [label lowercaseString];
        if ([lower containsString:@"computer"] || [lower containsString:@"this pc"]) {
            cell.imageView.image = jn_symbol(@"desktopcomputer", NSImageNameComputer);
        } else if ([lower containsString:@"quick"]) {
            cell.imageView.image = jn_symbol(@"star.fill", NSImageNameFolder);
        }
    } else {
        cell.textField.font = [NSFont systemFontOfSize:13.0];
        cell.imageView.image = jn_symbol(@"folder", NSImageNameFolder);
        NSString *lower = [label lowercaseString];
        if ([lower containsString:@"desktop"]) {
            cell.imageView.image = jn_symbol(@"menubar.dock.rectangle", NSImageNameFolder);
        } else if ([lower containsString:@"download"]) {
            cell.imageView.image = jn_symbol(@"arrow.down.circle", NSImageNameFolder);
        } else if ([lower containsString:@"document"]) {
            cell.imageView.image = jn_symbol(@"doc.text", NSImageNameFolder);
        } else if ([lower containsString:@"picture"] || [lower containsString:@"photo"]) {
            cell.imageView.image = jn_symbol(@"photo", NSImageNameFolder);
        } else if ([lower containsString:@"home"]) {
            cell.imageView.image = jn_symbol(@"house", NSImageNameFolder);
        }
    }
    return cell;
}

- (void)outlineViewSelectionDidChange:(NSNotification *)notification {
    NSOutlineView *ov = notification.object;
    if (![ov isKindOfClass:[NSOutlineView class]]) {
        return;
    }
    NSInteger row = ov.selectedRow;
    if (row < 0) {
        return;
    }
    id item = [ov itemAtRow:row];
    if (item == nil) {
        return;
    }
    int32_t peerId = self.peerId;
    int32_t idx = (int32_t)[item intValue];
    /* Defer out of AppKit selection posting (same as double-click ACTION).
       Explorer navigateTo/reopenWatcher nested here caused SIGSEGV on Home. */
    dispatch_async(dispatch_get_main_queue(), ^{
        jnative_ui_set_selected_index(peerId, idx);
        jnative_ui_emit_event_ex(peerId, JN_UI_EVENT_SELECT, idx, 0, 0, 0);
    });
}
@end

static JnTableBridge *jn_bridge_for(int32_t peerId) {
    if (peerId <= 0 || peerId >= JN_UI_MAX) {
        return nil;
    }
    JnTableBridge *b = g_table_bridges[peerId];
    if (b == nil) {
        b = [JnTableBridge new];
        b.peerId = peerId;
        g_table_bridges[peerId] = b;
    }
    return b;
}

@interface JnCanvasView : NSView
@property(nonatomic, assign) int32_t jnHandleId;
@property(nonatomic, strong) NSBitmapImageRep *bitmap;
@property(nonatomic, assign) int32_t dragging;
@property(nonatomic, assign) int32_t transparent;
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

@interface JnPanelView : NSView
@property(nonatomic) int32_t jnHandleId;
@property(nonatomic) int32_t jnWheel;
@end

@implementation JnPanelView
- (void)scrollWheel:(NSEvent *)event {
    if (self.jnWheel == 0) {
        [super scrollWheel:event];
        return;
    }
    double dy = event.scrollingDeltaY;
    int32_t d;
    if (event.hasPreciseScrollingDeltas) {
        d = (int32_t)(dy / 8.0);
    } else {
        d = (int32_t)(dy > 0 ? ceil(dy) : floor(dy));
    }
    if (d == 0) {
        return;
    }
    if (d > 100) {
        d = 100;
    } else if (d < -100) {
        d = -100;
    }
    NSPoint p = [self convertPoint:event.locationInWindow fromView:nil];
    int32_t packed = ((jn_cocoa_key_mods(event) & 15) << 4) | ((d + 512) << 8);
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_MOUSE, JN_UI_MOUSE_WHEEL, (int32_t)p.x, (int32_t)p.y, packed);
}
@end

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
- (BOOL)isOpaque {
    return self.transparent ? NO : YES;
}
- (void)drawRect:(NSRect)dirtyRect {
    (void)dirtyRect;
    if (!self.transparent) {
        [[NSColor whiteColor] setFill];
        NSRectFill(self.bounds);
    }
    if (self.bitmap != nil) {
        [self.bitmap drawInRect:self.bounds
                       fromRect:NSZeroRect
                      operation:NSCompositingOperationSourceOver
                       fraction:1.0
                 respectFlipped:YES
                          hints:nil];
    }
}
- (void)viewDidMoveToWindow {
    [super viewDidMoveToWindow];
    if (self.window != nil) {
        self.window.acceptsMouseMovedEvents = YES;
    }
}
- (void)updateTrackingAreas {
    [super updateTrackingAreas];
    for (NSTrackingArea *a in [self.trackingAreas copy]) {
        [self removeTrackingArea:a];
    }
    NSTrackingAreaOptions opts = NSTrackingMouseMoved | NSTrackingMouseEnteredAndExited |
                                 NSTrackingActiveInKeyWindow | NSTrackingInVisibleRect;
    NSTrackingArea *area = [[NSTrackingArea alloc] initWithRect:self.bounds
                                                        options:opts
                                                          owner:self
                                                       userInfo:nil];
    [self addTrackingArea:area];
}
/* arg3 packs: bits 0-3 button (1 left, 2 middle, 3 right, 0 none), bits 4-7 modifiers
 * (1 shift, 2 ctrl, 4 alt, 8 meta), bits 8+ wheel delta + 512 (wheel events only). */
- (void)jnEmitMouse:(NSEvent *)event sub:(int32_t)sub button:(int32_t)button delta:(int32_t)delta {
    NSPoint p = [self convertPoint:event.locationInWindow fromView:nil];
    int32_t packed = (button & 15) | ((jn_cocoa_key_mods(event) & 15) << 4) | ((delta + 512) << 8);
    jnative_ui_emit_event_ex(self.jnHandleId, JN_UI_EVENT_MOUSE, sub, (int32_t)p.x, (int32_t)p.y, packed);
}
- (void)mouseDown:(NSEvent *)event {
    [[self window] makeFirstResponder:self];
    self.dragging = 1;
    [self jnEmitMouse:event sub:JN_UI_MOUSE_DOWN button:1 delta:0];
}
- (void)mouseDragged:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_MOVE button:1 delta:0];
}
- (void)mouseUp:(NSEvent *)event {
    self.dragging = 0;
    [self jnEmitMouse:event sub:JN_UI_MOUSE_UP button:1 delta:0];
}
- (void)rightMouseDown:(NSEvent *)event {
    [[self window] makeFirstResponder:self];
    [self jnEmitMouse:event sub:JN_UI_MOUSE_DOWN button:3 delta:0];
}
- (void)rightMouseDragged:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_MOVE button:3 delta:0];
}
- (void)rightMouseUp:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_UP button:3 delta:0];
}
- (void)otherMouseDown:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_DOWN button:2 delta:0];
}
- (void)otherMouseDragged:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_MOVE button:2 delta:0];
}
- (void)otherMouseUp:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_UP button:2 delta:0];
}
- (void)mouseMoved:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_MOVE button:0 delta:0];
}
- (void)mouseEntered:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_ENTER button:0 delta:0];
}
- (void)mouseExited:(NSEvent *)event {
    [self jnEmitMouse:event sub:JN_UI_MOUSE_EXIT button:0 delta:0];
}
- (void)scrollWheel:(NSEvent *)event {
    double dy = event.scrollingDeltaY;
    int32_t d;
    if (event.hasPreciseScrollingDeltas) {
        d = (int32_t)(dy / 8.0);
    } else {
        d = (int32_t)(dy > 0 ? ceil(dy) : floor(dy));
    }
    if (d == 0) {
        return;
    }
    if (d > 100) {
        d = 100;
    } else if (d < -100) {
        d = -100;
    }
    [self jnEmitMouse:event sub:JN_UI_MOUSE_WHEEL button:0 delta:d];
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

void jnative_ui_platform_set_appearance_mode(int32_t mode) {
    if (!g_cocoa_active) {
        return;
    }
    /* 0=system, 1=light, 2=dark — forces NSApp appearance for screenshots/tests. */
    if (mode == 1) {
        [NSApp setAppearance:[NSAppearance appearanceNamed:NSAppearanceNameAqua]];
    } else if (mode == 2) {
        [NSApp setAppearance:[NSAppearance appearanceNamed:NSAppearanceNameDarkAqua]];
    } else {
        [NSApp setAppearance:nil];
    }
}

int32_t jnative_ui_platform_create_window(int32_t peerId, const char *title, int32_t w, int32_t h) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return 0;
    }
    NSRect rect = NSMakeRect(0, 0, w > 0 ? w : 800, h > 0 ? h : 600);
    JnUiWindow *win = [[JnUiWindow alloc]
        initWithContentRect:rect
                  styleMask:(NSWindowStyleMaskTitled | NSWindowStyleMaskClosable
                             | NSWindowStyleMaskMiniaturizable | NSWindowStyleMaskResizable)
                    backing:NSBackingStoreBuffered
                      defer:NO];
    win.jnHandleId = peerId;
    win.delegate = win;
    [win setTitle:jn_nsstring(title)];
    /* Screen-center by default — avoids stacking every dialog at a fixed corner. */
    [win center];
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
        JnUiTextField *tf = [[JnUiTextField alloc] initWithFrame:NSMakeRect(20, 100, 200, 24)];
        tf.jnHandleId = peerId;
        [tf setStringValue:jn_nsstring(text)];
        [tf setDelegate:tf];
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
        NSView *view = nil;
        if (kind == JN_UI_KIND_PANEL) {
            JnPanelView *pv = [[JnPanelView alloc] initWithFrame:NSMakeRect(0, 0, 400, 300)];
            pv.jnHandleId = peerId;
            view = pv;
        } else {
            view = [[NSView alloc] initWithFrame:NSMakeRect(0, 0, 400, 300)];
        }
        if (parentView != nil) {
            [parentView addSubview:view];
        }
        g_peers[peerId] = view;
        return 1;
    }
    if (kind == JN_UI_KIND_CHECKBOX) {
        JnUiCheck *btn = [JnUiCheck checkboxWithTitle:jn_nsstring(text) target:nil action:NULL];
        btn.jnHandleId = peerId;
        [btn setTarget:btn];
        [btn setAction:@selector(jnToggled:)];
        [btn setFrame:NSMakeRect(20, 20, 160, 28)];
        if (parentView != nil) {
            [parentView addSubview:btn];
        }
        g_peers[peerId] = btn;
        return 1;
    }
    if (kind == JN_UI_KIND_RADIO) {
        JnUiCheck *btn = [JnUiCheck radioButtonWithTitle:jn_nsstring(text) target:nil action:NULL];
        btn.jnHandleId = peerId;
        [btn setTarget:btn];
        [btn setAction:@selector(jnToggled:)];
        [btn setFrame:NSMakeRect(20, 20, 160, 28)];
        if (parentView != nil) {
            [parentView addSubview:btn];
        }
        g_peers[peerId] = btn;
        return 1;
    }
    if (kind == JN_UI_KIND_COMBOBOX) {
        JnUiCombo *box = [[JnUiCombo alloc] initWithFrame:NSMakeRect(20, 20, 180, 26)];
        box.jnHandleId = peerId;
        [[NSNotificationCenter defaultCenter] addObserver:box
                                                 selector:@selector(jnSelectionChanged:)
                                                     name:NSComboBoxSelectionDidChangeNotification
                                                   object:box];
        if (parentView != nil) {
            [parentView addSubview:box];
        }
        g_peers[peerId] = box;
        return 1;
    }
    if (kind == JN_UI_KIND_TEXTAREA) {
        NSScrollView *scroll = [[NSScrollView alloc] initWithFrame:NSMakeRect(20, 20, 300, 120)];
        JnTextArea *tv = [[JnTextArea alloc] initWithFrame:scroll.bounds];
        tv.jnHandleId = peerId;
        [tv setRichText:NO];
        [tv setAllowsUndo:YES];
        [tv setAutomaticQuoteSubstitutionEnabled:NO];
        [tv setAutomaticDashSubstitutionEnabled:NO];
        [tv setAutomaticTextReplacementEnabled:NO];
        [tv setAutomaticSpellingCorrectionEnabled:NO];
        [tv setAutomaticLinkDetectionEnabled:NO];
        [tv setContinuousSpellCheckingEnabled:NO];
        [tv setUsesFindBar:NO];
        [tv setMinSize:NSMakeSize(0, 0)];
        [tv setMaxSize:NSMakeSize(1e7, 1e7)];
        [tv setVerticallyResizable:YES];
        [tv setHorizontallyResizable:NO];
        [tv setAutoresizingMask:NSViewWidthSizable];
        [[tv textContainer] setWidthTracksTextView:YES];
        [[tv textContainer] setContainerSize:NSMakeSize(scroll.bounds.size.width, 1e7)];
        [tv setFont:[NSFont userFixedPitchFontOfSize:14.0]];
        [scroll setDocumentView:tv];
        [scroll setHasVerticalScroller:YES];
        [scroll setBorderType:NSNoBorder];
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
    if (kind == JN_UI_KIND_SEGMENTED) {
        JnUiSegmented *seg = [[JnUiSegmented alloc] initWithFrame:NSMakeRect(0, 0, 160, 28)];
        seg.jnHandleId = peerId;
        [seg setSegmentStyle:NSSegmentStyleRounded];
        [seg setTrackingMode:NSSegmentSwitchTrackingSelectOne];
        [seg setTarget:seg];
        [seg setAction:@selector(jnChanged:)];
        NSArray<NSString *> *parts = [jn_nsstring(text) componentsSeparatedByString:@"\t"];
        if (parts.count == 0) {
            parts = @[ @"A", @"B" ];
        }
        [seg setSegmentCount:(NSInteger)parts.count];
        NSUInteger si;
        for (si = 0; si < parts.count; si++) {
            [seg setLabel:parts[si] forSegment:(NSInteger)si];
            [seg setWidth:0 forSegment:(NSInteger)si];
        }
        [seg setSelectedSegment:0];
        [seg sizeToFit];
        if (parentView != nil) {
            [parentView addSubview:seg];
        }
        g_peers[peerId] = seg;
        return 1;
    }
    if (kind == JN_UI_KIND_TRENDCHART) {
        JnTrendView *tv = [[JnTrendView alloc] initWithFrame:NSMakeRect(0, 0, 160, 36)];
        tv.jnHandleId = peerId;
        [tv jnSetCsv:jn_nsstring(text)];
        if (parentView != nil) {
            [parentView addSubview:tv];
        }
        g_peers[peerId] = tv;
        return 1;
    }
    if (kind == JN_UI_KIND_LISTVIEW || kind == JN_UI_KIND_TABLEVIEW) {
        NSScrollView *scroll = [[NSScrollView alloc] initWithFrame:NSMakeRect(0, 0, 240, 200)];
        [scroll setHasVerticalScroller:YES];
        [scroll setHasHorizontalScroller:NO];
        [scroll setAutohidesScrollers:YES];
        [scroll setBorderType:NSNoBorder];
        [scroll setDrawsBackground:YES];
        [scroll setBackgroundColor:[NSColor textBackgroundColor]];
        JnTableView *table = [[JnTableView alloc] initWithFrame:scroll.bounds];
        [table setHeaderView:[[NSTableHeaderView alloc] init]];
        /* Custom drawBackgroundInClipRect — do not use system full-clip zebra. */
        [table setUsesAlternatingRowBackgroundColors:NO];
        /* Compact system row height (~22–24pt). */
        [table setRowHeight:22.0];
        [table setIntercellSpacing:NSMakeSize(4.0, 1.0)];
        [table setGridStyleMask:NSTableViewGridNone];
        /* Name (first) column absorbs leftover width — not the last (Threads) column. */
        [table setColumnAutoresizingStyle:NSTableViewFirstColumnOnlyAutoresizingStyle];
        if (@available(macOS 11.0, *)) {
            [table setStyle:NSTableViewStylePlain];
        }
        NSTableColumn *col = [[NSTableColumn alloc] initWithIdentifier:@"0"];
        [col setTitle:(kind == JN_UI_KIND_TABLEVIEW) ? @"Name" : @"Item"];
        [col setWidth:280];
        [col setMinWidth:120];
        [table addTableColumn:col];
        JnTableBridge *bridge = jn_bridge_for(peerId);
        [table setDataSource:bridge];
        [table setDelegate:bridge];
        [table setTarget:bridge];
        [table setDoubleAction:@selector(jnDoubleActivate:)];
        [table setAllowsMultipleSelection:YES];
        [scroll setDocumentView:table];
        if (parentView != nil) {
            [parentView addSubview:scroll];
        }
        g_peers[peerId] = scroll;
        return 1;
    }
    if (kind == JN_UI_KIND_TREEVIEW) {
        NSScrollView *scroll = [[NSScrollView alloc] initWithFrame:NSMakeRect(0, 0, 240, 200)];
        [scroll setHasVerticalScroller:YES];
        [scroll setAutohidesScrollers:YES];
        [scroll setBorderType:NSNoBorder];
        [scroll setDrawsBackground:YES];
        [scroll setBackgroundColor:[NSColor controlBackgroundColor]];
        NSOutlineView *outline = [[NSOutlineView alloc] initWithFrame:scroll.bounds];
        [outline setHeaderView:nil];
        [outline setUsesAlternatingRowBackgroundColors:NO];
        [outline setRowHeight:30.0];
        [outline setIndentationPerLevel:15.0];
        [outline setIntercellSpacing:NSMakeSize(6.0, 4.0)];
        [outline setSelectionHighlightStyle:NSTableViewSelectionHighlightStyleSourceList];
        if (@available(macOS 11.0, *)) {
            [outline setStyle:NSTableViewStyleSourceList];
        }
        NSTableColumn *col = [[NSTableColumn alloc] initWithIdentifier:@"0"];
        [col setTitle:@""];
        [col setWidth:220];
        [outline addTableColumn:col];
        [outline setOutlineTableColumn:col];
        JnTableBridge *bridge = jn_bridge_for(peerId);
        [outline setDataSource:bridge];
        [outline setDelegate:bridge];
        [outline setTarget:bridge];
        [outline setDoubleAction:@selector(jnDoubleActivate:)];
        [scroll setDocumentView:outline];
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
    } else if (type == 4) {
        /* Terminate choose: 1=SIGTERM, 2=SIGKILL, 0=cancel */
        [alert addButtonWithTitle:@"结束进程"];
        [alert addButtonWithTitle:@"强制结束"];
        [alert addButtonWithTitle:@"取消"];
        [alert setAlertStyle:NSAlertStyleWarning];
    } else if (type == 5) {
        [alert addButtonWithTitle:@"Yes"];
        [alert addButtonWithTitle:@"No"];
        [alert setAlertStyle:NSAlertStyleWarning];
    } else if (type == 7) {
        [alert addButtonWithTitle:@"OK"];
        [alert setAlertStyle:NSAlertStyleWarning];
    } else {
        [alert addButtonWithTitle:@"OK"];
    }
    if (type == 2) {
        [alert setAlertStyle:NSAlertStyleCritical];
    }
    NSModalResponse r = [alert runModal];
    if (type == 3 || type == 4) {
        if (r == NSAlertFirstButtonReturn) return 1;
        if (r == NSAlertSecondButtonReturn) return 2;
        return 0;
    }
    return (r == NSAlertFirstButtonReturn) ? 1 : 0;
}

static NSArray<NSString *> *jn_parse_filter_exts(const char *filter) {
    /* filter forms: "json" | "json,txt" | "JSON files|json;txt" — take extension tokens. */
    if (filter == NULL || filter[0] == '\0') {
        return nil;
    }
    NSMutableArray<NSString *> *exts = [NSMutableArray array];
    NSString *raw = jn_nsstring(filter);
    NSCharacterSet *seps = [NSCharacterSet characterSetWithCharactersInString:@",;| "];
    NSArray<NSString *> *parts = [raw componentsSeparatedByCharactersInSet:seps];
    for (NSString *p in parts) {
        NSString *t = [[p stringByTrimmingCharactersInSet:
            [NSCharacterSet whitespaceAndNewlineCharacterSet]] lowercaseString];
        if (t.length == 0) {
            continue;
        }
        if ([t containsString:@"."]) {
            t = [t pathExtension];
        }
        if ([t isEqualToString:@"files"] || [t isEqualToString:@"file"]
                || [t isEqualToString:@"all"]) {
            continue;
        }
        if (t.length > 0 && t.length <= 8) {
            [exts addObject:t];
        }
    }
    if (exts.count == 0) {
        return nil;
    }
    return exts;
}

char *jnative_ui_platform_dialog_file(int32_t windowId, int32_t save, const char *filter) {
    if (!g_cocoa_active) {
        return NULL;
    }
    (void)windowId;
    NSArray<NSString *> *exts = jn_parse_filter_exts(filter);
    if (save) {
        NSSavePanel *panel = [NSSavePanel savePanel];
        [panel setCanCreateDirectories:YES];
        /* macOS Save panel confirms overwrite by default. */
        if (exts != nil) {
            [panel setAllowedFileTypes:exts];
            [panel setAllowsOtherFileTypes:NO];
        }
        if ([panel runModal] != NSModalResponseOK) {
            return NULL; /* cancel → not an error */
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
        if (exts != nil) {
            [panel setAllowedFileTypes:exts];
        }
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
    g_table_bridges[peerId] = nil;
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
    if ([obj isKindOfClass:[JnTrendView class]]) {
        [(JnTrendView *)obj jnSetCsv:jn_nsstring(text)];
        return;
    }
    if ([obj isKindOfClass:[JnUiButton class]]) {
        JnUiButton *btn = (JnUiButton *)obj;
        [btn setTitle:jn_nsstring(text)];
        if (btn.jnFilled != 0) {
            [btn jnApplyFilledVisual];
        }
    } else if ([obj isKindOfClass:[NSButton class]]) {
        [(NSButton *)obj setTitle:jn_nsstring(text)];
    } else if ([obj isKindOfClass:[NSTextField class]]) {
        [(NSTextField *)obj setStringValue:jn_nsstring(text)];
    } else if ([obj isKindOfClass:[NSScrollView class]]
               && [[(NSScrollView *)obj documentView] isKindOfClass:[JnTextArea class]]) {
        JnTextArea *tv = (JnTextArea *)[(NSScrollView *)obj documentView];
        [tv setString:jn_nsstring(text)];
        [[tv undoManager] removeAllActions];
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
        /* Default push buttons keep intrinsic height centered in the slot.
           Filled (styled) buttons honor the full layout cell for calculator keys. */
        if ([view isKindOfClass:[JnUiButton class]]) {
            JnUiButton *btn = (JnUiButton *)view;
            if (btn.jnFilled == 0) {
                NSSize fit = [btn fittingSize];
                if (fit.height > 1.0) {
                    if (useH > fit.height) {
                        useY = y + (CGFloat)((int)((useH - fit.height) * 0.5));
                    }
                    useH = fit.height;
                }
            }
        }
        /* JNative layout uses top-left origin; AppKit uses bottom-left. */
        CGFloat yCocoa = useY;
        if (superView != nil) {
            yCocoa = superView.bounds.size.height - useY - useH;
        }
        [view setFrame:NSMakeRect(useX, yCocoa, useW, useH)];
        if ([view isKindOfClass:[NSScrollView class]]) {
            jn_size_scroll_document((NSScrollView *)view);
        }
    } else if ([obj isKindOfClass:[NSWindow class]]) {
        /* JNative sizes are content-area sizes, not full frame (title bar). */
        NSWindow *win = (NSWindow *)obj;
        [win setContentSize:NSMakeSize((CGFloat)w, (CGFloat)h)];
    }
}

void jnative_ui_platform_window_center_on_parent(int32_t childId, int32_t parentId) {
    id childObj = (childId > 0 && childId < JN_UI_MAX) ? g_peers[childId] : nil;
    if (![childObj isKindOfClass:[NSWindow class]]) {
        return;
    }
    NSWindow *child = (NSWindow *)childObj;
    id parentObj = (parentId > 0 && parentId < JN_UI_MAX) ? g_peers[parentId] : nil;
    if ([parentObj isKindOfClass:[NSWindow class]]) {
        NSWindow *parent = (NSWindow *)parentObj;
        /* Center over owner only. Do NOT setParentWindow — that can clip the
           dialog to the parent frame and hide footer buttons. */
        NSRect pf = [parent frame];
        NSRect cf = [child frame];
        CGFloat nx = NSMidX(pf) - cf.size.width * (CGFloat)0.5;
        CGFloat ny = NSMidY(pf) - cf.size.height * (CGFloat)0.45;
        [child setFrameOrigin:NSMakePoint(nx, ny)];
        [child setLevel:[parent level]];
    } else {
        [child center];
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
    } else if (obj == nil) {
        jn_menu_item_refresh(handleId);
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
        /* Width-only autoresize. HeightSizable stretches the root view when the
           window realizes without re-running JNative layout, so children keep
           Y-flipped frames from the old superview height and leave a white gap
           under the toolbar/breadcrumb. Height is always applied via setBounds. */
        [(NSView *)child setAutoresizingMask:NSViewWidthSizable];
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
    NSString *appName = jn_nsstring(jnative_ui_get_app_name());
    NSMenu *mainMenu = [[NSMenu alloc] initWithTitle:@"MainMenu"];
    /* Application menu: About / Quit via managed requestQuit (not NSApp terminate). */
    NSMenuItem *appItem = [[NSMenuItem alloc] init];
    NSMenu *appMenu = [[NSMenu alloc] initWithTitle:appName];
    NSString *aboutTitle = [NSString stringWithFormat:@"About %@", appName];
    [appMenu addItemWithTitle:aboutTitle
                       action:@selector(orderFrontStandardAboutPanel:)
                keyEquivalent:@""];
    [appMenu addItem:[NSMenuItem separatorItem]];
    NSString *quitTitle = [NSString stringWithFormat:@"Quit %@", appName];
    NSMenuItem *quitItem = [[NSMenuItem alloc] initWithTitle:quitTitle
                                                      action:@selector(jnAppQuit:)
                                               keyEquivalent:@"q"];
    [quitItem setTarget:g_menu_target];
    [appMenu addItem:quitItem];
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
        [submenu setAutoenablesItems:NO];
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
            jn_apply_menu_shortcut(item, itemId);
            int32_t en = jnative_ui_get_enabled(itemId);
            [item setEnabled:(en != 0)];
            int32_t sel = jnative_ui_get_selected(itemId);
            [item setState:(sel != 0) ? NSControlStateValueOn : NSControlStateValueOff];
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

/* 0 arrow, 1 hand, 2 resize left/right, 3 I-beam, 4 resize up/down, 5 move, 6 crosshair */
void jnative_ui_platform_set_cursor(int32_t kind) {
    NSCursor *c = [NSCursor arrowCursor];
    switch (kind) {
        case 1: c = [NSCursor pointingHandCursor]; break;
        case 2: c = [NSCursor resizeLeftRightCursor]; break;
        case 3: c = [NSCursor IBeamCursor]; break;
        case 4: c = [NSCursor resizeUpDownCursor]; break;
        case 5: c = [NSCursor openHandCursor]; break;
        case 6: c = [NSCursor crosshairCursor]; break;
        default: break;
    }
    [c set];
}

/* Renders the window's content view into a PNG file (works while the screen is locked). */
int32_t jnative_ui_platform_window_snapshot(int32_t peerId, const char *path) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX || path == NULL) {
        return 0;
    }
    id obj = g_peers[peerId];
    if (![obj isKindOfClass:[NSWindow class]]) {
        return 0;
    }
    NSView *v = [(NSWindow *)obj contentView];
    if (v == nil) {
        return 0;
    }
    NSBitmapImageRep *rep = [v bitmapImageRepForCachingDisplayInRect:v.bounds];
    if (rep == nil) {
        return 0;
    }
    [v cacheDisplayInRect:v.bounds toBitmapImageRep:rep];
    NSData *png = [rep representationUsingType:NSBitmapImageFileTypePNG properties:@{}];
    if (png == nil) {
        return 0;
    }
    return [png writeToFile:jn_nsstring(path) atomically:YES] ? 1 : 0;
}

/* flags: bit0 clip children to bounds, bit1 deliver mouse-wheel events to the panel handler. */
void jnative_ui_platform_panel_configure(int32_t peerId, int32_t flags) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[JnPanelView class]]) {
        JnPanelView *pv = (JnPanelView *)obj;
        pv.jnWheel = (flags & 2) != 0 ? 1 : 0;
        if ((flags & 1) != 0) {
            pv.wantsLayer = YES;
            pv.layer.masksToBounds = YES;
        } else if (pv.wantsLayer) {
            pv.layer.masksToBounds = NO;
        }
    }
}

void jnative_ui_platform_canvas_set_transparent(int32_t peerId, int32_t on) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[JnCanvasView class]]) {
        ((JnCanvasView *)obj).transparent = on;
        [(NSView *)obj setNeedsDisplay:YES];
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
        g_cocoa_timers = [[NSMutableDictionary alloc] init]; /* manual refcounting: must outlive autorelease pools */
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

void jnative_ui_platform_table_set_columns(int32_t peerId, const char *titles_tsv) {
    if (!g_cocoa_active) {
        return;
    }
    NSTableView *tv = jn_table_view(peerId);
    if (tv == nil || [tv isKindOfClass:[NSOutlineView class]]) {
        return;
    }
    NSString *titles = jn_ns_from_utf8(titles_tsv);
    NSArray<NSString *> *parts = [titles componentsSeparatedByString:@"\t"];
    if (parts.count == 0) {
        return;
    }
    while (tv.tableColumns.count > 0) {
        [tv removeTableColumn:tv.tableColumns[0]];
    }
    BOOL anyFlex = NO;
    for (NSUInteger i = 0; i < parts.count; i++) {
        NSString *raw = parts[i];
        NSString *title = raw;
        CGFloat width = -1;
        CGFloat minW = -1;
        CGFloat maxW = -1;
        int align = 0; /* 0=L 1=C 2=R */
        int flex = 0;
        NSArray *meta = [raw componentsSeparatedByString:@"##"];
        if (meta.count >= 1) {
            title = meta[0];
        }
        if (meta.count >= 2) {
            width = [meta[1] doubleValue];
        }
        if (meta.count >= 3) {
            NSString *a = meta[2];
            if ([a isEqualToString:@"R"] || [a isEqualToString:@"2"]) align = 2;
            else if ([a isEqualToString:@"C"] || [a isEqualToString:@"1"]) align = 1;
            else align = 0;
        }
        if (meta.count >= 4) {
            flex = [meta[3] intValue];
        }
        if (meta.count >= 5) {
            CGFloat parsedMax = [meta[4] doubleValue];
            if (parsedMax > 0.0) {
                maxW = parsedMax;
            }
        }
        if (title.length == 0) {
            title = [NSString stringWithFormat:@"Col%lu", (unsigned long)i];
        }
        NSTableColumn *col = [[NSTableColumn alloc]
            initWithIdentifier:[NSString stringWithFormat:@"%lu", (unsigned long)i]];
        [col setTitle:title];
        BOOL right = (align == 2);
        if (width > 0) {
            [col setWidth:width];
            if (minW < 0) {
                minW = flex ? 160.0 : width * 0.75;
            }
            if (maxW < 0) {
                maxW = flex ? 100000.0 : width * 1.25;
            }
            [col setMinWidth:minW];
            [col setMaxWidth:maxW];
            if (flex) anyFlex = YES;
        } else if (flex) {
            [col setWidth:360.0];
            [col setMinWidth:120.0];
            [col setMaxWidth:100000.0];
            anyFlex = YES;
        } else if (i == 0) {
            [col setWidth:360.0];
            [col setMinWidth:180.0];
            [col setMaxWidth:100000.0];
            anyFlex = YES;
        } else if ([title containsString:@"PID"] || i == 1) {
            [col setWidth:88.0];
            [col setMinWidth:72.0];
            [col setMaxWidth:110.0];
            right = YES;
        } else if ([title containsString:@"用户"] || [title containsString:@"User"] || i == 2) {
            [col setWidth:128.0];
            [col setMinWidth:88.0];
            [col setMaxWidth:240.0];
        } else if ([title containsString:@"CPU"] || [title containsString:@"%"]) {
            [col setWidth:84.0];
            [col setMinWidth:72.0];
            [col setMaxWidth:110.0];
            right = YES;
        } else if ([title containsString:@"线程"] || [title containsString:@"Threads"]) {
            [col setWidth:64.0];
            [col setMinWidth:56.0];
            [col setMaxWidth:80.0];
            right = YES;
        } else if ([title containsString:@"内存"] || [title containsString:@"Memory"]
                || [title containsString:@"RSS"] || [title containsString:@"占用"]) {
            [col setWidth:100.0];
            [col setMinWidth:84.0];
            [col setMaxWidth:140.0];
            right = YES;
        } else {
            [col setWidth:96.0];
            [col setMinWidth:72.0];
            [col setMaxWidth:160.0];
        }
        if (right) {
            [[col headerCell] setAlignment:NSTextAlignmentRight];
        } else if (align == 1) {
            [[col headerCell] setAlignment:NSTextAlignmentCenter];
        } else {
            [[col headerCell] setAlignment:NSTextAlignmentLeft];
        }
        [col setResizingMask:NSTableColumnUserResizingMask];
        [tv addTableColumn:col];
    }
    (void)anyFlex;
    /* Re-assert: leftover width goes to name (col 0), never Threads. */
    [tv setColumnAutoresizingStyle:NSTableViewFirstColumnOnlyAutoresizingStyle];
    /* Table may still be ~240pt at first set_columns; use a floor so numeric
       columns are not crushed before the real window layout runs. */
    if (tv.tableColumns.count > 1) {
        CGFloat fixed = 0;
        NSUInteger ci;
        for (ci = 1; ci < tv.tableColumns.count; ci++) {
            fixed += [tv.tableColumns[ci] width];
        }
        CGFloat tableW = tv.bounds.size.width;
        if (tableW < 900.0) {
            tableW = 1000.0;
        }
        CGFloat nameW = tableW - fixed - 16.0;
        if (nameW < 180.0) {
            nameW = 180.0;
        }
        [tv.tableColumns[0] setWidth:nameW];
    }
}

void jnative_ui_platform_items_changed(int32_t peerId) {
    if (!g_cocoa_active) {
        return;
    }
    id obj = (peerId > 0 && peerId < JN_UI_MAX) ? g_peers[peerId] : nil;
    if ([obj isKindOfClass:[NSComboBox class]]) {
        NSComboBox *box = (NSComboBox *)obj;
        [box removeAllItems];
        int32_t n = jnative_ui_get_item_count(peerId);
        for (int32_t i = 0; i < n; i++) {
            [box addItemWithObjectValue:jn_ns_from_utf8(jnative_ui_item_text(peerId, i))];
        }
        return;
    }
    NSTableView *tv = jn_table_view(peerId);
    if (tv == nil) {
        return;
    }
    if ([tv isKindOfClass:[NSOutlineView class]]) {
        NSOutlineView *ov = (NSOutlineView *)tv;
        [ov reloadData];
        int32_t n = jnative_ui_get_item_count(peerId);
        for (int32_t i = 0; i < n; i++) {
            if (jnative_ui_tree_is_expanded(peerId, i)) {
                id item = @(i);
                if ([ov isExpandable:item]) {
                    [ov expandItem:item expandChildren:NO];
                }
            }
        }
    } else {
        NSScrollView *sv = [tv enclosingScrollView];
        CGFloat savedY = 0;
        int hasScroll = 0;
        if (sv != nil) {
            savedY = [[sv contentView] documentVisibleRect].origin.y;
            hasScroll = 1;
        }
        [tv reloadData];
        if (hasScroll != 0 && sv != nil) {
            [[sv documentView] scrollPoint:NSMakePoint(0, savedY)];
        }
    }
    id scroll = g_peers[peerId];
    if ([scroll isKindOfClass:[NSScrollView class]]) {
        jn_size_scroll_document((NSScrollView *)scroll);
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

int32_t jnative_ui_platform_family(void) {
    return 1;
}

int32_t jnative_ui_platform_get_scale_percent(int32_t windowId) {
    if (!g_cocoa_active) {
        return 100;
    }
    id obj = nil;
    if (windowId > 0 && windowId < JN_UI_MAX) {
        obj = g_peers[windowId];
    }
    NSWindow *win = nil;
    if ([obj isKindOfClass:[NSWindow class]]) {
        win = (NSWindow *)obj;
    } else if ([obj isKindOfClass:[NSView class]]) {
        win = [(NSView *)obj window];
    }
    if (win == nil) {
        win = [NSApp keyWindow];
    }
    if (win == nil) {
        return 100;
    }
    CGFloat s = win.backingScaleFactor;
    if (s < 0.5) {
        s = 1.0;
    }
    return (int32_t)(s * 100.0 + 0.5);
}

int32_t jnative_ui_platform_system_dark(void) {
    if (!g_cocoa_active) {
        return -1;
    }
    if (@available(macOS 10.14, *)) {
        NSAppearance *a = NSApp.effectiveAppearance;
        NSAppearanceName name =
            [a bestMatchFromAppearancesWithNames:@[NSAppearanceNameAqua,
                                                   NSAppearanceNameDarkAqua]];
        if ([name isEqualToString:NSAppearanceNameDarkAqua]) {
            return 1;
        }
        return 0;
    }
    return 0;
}

int32_t jnative_ui_platform_context_menu_show(int32_t menuId, int32_t screenX, int32_t screenY) {
    if (!g_cocoa_active || menuId <= 0 || menuId >= JN_UI_MAX) {
        return 0;
    }
    if (g_menu_target == nil) {
        g_menu_target = [JnUiMenuTarget new];
    }
    NSMenu *menu = [[NSMenu alloc] initWithTitle:@""];
    [menu setAutoenablesItems:NO];
    int32_t n = jnative_ui_menu_child_count(menuId);
    int32_t i;
    for (i = 0; i < n; i++) {
        int32_t itemId = jnative_ui_menu_child_at(menuId, i);
        int32_t kind = jnative_ui_peer_kind(itemId);
        const char *itext = jnative_ui_peer_text(itemId);
        if (kind == JN_UI_KIND_SEPARATOR || (itext && strcmp(itext, "-") == 0)) {
            [menu addItem:[NSMenuItem separatorItem]];
            continue;
        }
        NSString *title = jn_ns_from_utf8(itext);
        NSMenuItem *mi = [[NSMenuItem alloc] initWithTitle:title
                                                    action:@selector(jnMenuAction:)
                                             keyEquivalent:@""];
        mi.target = g_menu_target;
        mi.tag = itemId;
        [menu addItem:mi];
    }
    NSPoint pt;
    if (screenX == 0 && screenY == 0) {
        pt = [NSEvent mouseLocation];
    } else {
        pt = NSMakePoint((CGFloat)screenX, (CGFloat)screenY);
        NSScreen *screen = [NSScreen mainScreen];
        if (screen != nil) {
            CGFloat h = NSHeight(screen.frame);
            pt.y = h - (CGFloat)screenY;
        }
    }
    [menu popUpMenuPositioningItem:nil atLocation:pt inView:nil];
    return 1;
}

void jnative_ui_platform_set_control_style(int32_t peerId, int32_t style) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[NSButton class]]) {
        NSButton *btn = (NSButton *)obj;
        if (style == 1) {
            /* Toolbar glyph: compact borderless. */
            [btn setBordered:NO];
            [btn setButtonType:NSButtonTypeMomentaryLight];
            [btn setControlSize:NSControlSizeRegular];
            [btn setFont:[NSFont systemFontOfSize:13.0 weight:NSFontWeightMedium]];
            [btn setContentTintColor:[NSColor labelColor]];
            [btn sizeToFit];
            NSRect f = btn.frame;
            if (f.size.width < 28.0) {
                f.size.width = 28.0;
            }
            if (f.size.height < 28.0) {
                f.size.height = 28.0;
            }
            [btn setFrame:f];
        } else if (style == 2) {
            /* Breadcrumb segment. */
            [btn setBordered:NO];
            [btn setButtonType:NSButtonTypeMomentaryLight];
            [btn setFont:[NSFont systemFontOfSize:12.0 weight:NSFontWeightRegular]];
            [btn setContentTintColor:[NSColor labelColor]];
            [btn sizeToFit];
        } else if (style == 3) {
            /* Toolbar push — visible bezel + optional SF Symbol. */
            [btn setBordered:YES];
            [btn setButtonType:NSButtonTypeMomentaryPushIn];
            [btn setBezelStyle:NSBezelStyleRounded];
            [btn setControlSize:NSControlSizeRegular];
            [btn setFont:[NSFont systemFontOfSize:13.0 weight:NSFontWeightRegular]];
            [btn setContentTintColor:[NSColor controlAccentColor]];
            [btn sizeToFit];
            NSRect f = btn.frame;
            if (f.size.height < 28.0) {
                f.size.height = 28.0;
            }
            [btn setFrame:f];
        } else {
            [btn setBordered:YES];
            [btn setButtonType:NSButtonTypeMomentaryPushIn];
            if (@available(macOS 14.0, *)) {
                [btn setBezelStyle:NSBezelStyleFlexiblePush];
            } else {
                [btn setBezelStyle:NSBezelStyleRounded];
            }
        }
    } else if ([obj isKindOfClass:[NSTextField class]]
            || [obj isKindOfClass:[NSSecureTextField class]]) {
        NSTextField *tf = (NSTextField *)obj;
        if (style == 5) {
            /* Password / secure entry — replace with NSSecureTextField once. */
            if ([obj isKindOfClass:[NSSecureTextField class]]) {
                return;
            }
            NSView *parent = [tf superview];
            NSRect frame = tf.frame;
            NSString *cur = [tf stringValue];
            JnUiSecureTextField *sec = [[JnUiSecureTextField alloc] initWithFrame:frame];
            sec.jnHandleId = peerId;
            [sec setStringValue:cur ? cur : @""];
            [sec setDelegate:sec];
            [sec setBezeled:YES];
            [sec setDrawsBackground:YES];
            [sec setFont:[NSFont systemFontOfSize:13.0]];
            if (parent != nil) {
                [parent addSubview:sec];
                [tf removeFromSuperview];
            }
            g_peers[peerId] = sec;
            return;
        }
        if (style == 2) {
            [tf setFont:[NSFont systemFontOfSize:12.0]];
            [tf setTextColor:[NSColor secondaryLabelColor]];
        } else if (style == 4) {
            /* Search-field look. */
            [tf setBezeled:YES];
            [tf setBezelStyle:NSTextFieldRoundedBezel];
            [tf setDrawsBackground:YES];
            [tf setFont:[NSFont systemFontOfSize:13.0]];
        }
    }
}

void jnative_ui_platform_set_tooltip(int32_t peerId, const char *tip) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    NSString *s = jn_ns_from_utf8(tip);
    if ([obj isKindOfClass:[NSView class]]) {
        [(NSView *)obj setToolTip:s];
    }
    if ([obj respondsToSelector:@selector(setAccessibilityLabel:)]) {
        /* Tooltip doubles as name when no dedicated accessible name is set. */
        NSString *existing = nil;
        if ([obj respondsToSelector:@selector(accessibilityLabel)]) {
            existing = [obj accessibilityLabel];
        }
        if (existing == nil || existing.length == 0) {
            [obj setAccessibilityLabel:s];
        }
    }
}

void jnative_ui_platform_set_accessible(int32_t peerId, const char *name, const char *description) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if (obj == nil) {
        return;
    }
    if (name != NULL && [obj respondsToSelector:@selector(setAccessibilityLabel:)]) {
        [obj setAccessibilityLabel:jn_ns_from_utf8(name)];
    }
    if (description != NULL && [obj respondsToSelector:@selector(setAccessibilityHelp:)]) {
        [obj setAccessibilityHelp:jn_ns_from_utf8(description)];
    }
}

void jnative_ui_platform_set_accessible_title_ui(int32_t fieldId, int32_t labelId) {
    if (!g_cocoa_active || fieldId <= 0 || fieldId >= JN_UI_MAX
            || labelId <= 0 || labelId >= JN_UI_MAX) {
        return;
    }
    id field = g_peers[fieldId];
    id label = g_peers[labelId];
    if ([field isKindOfClass:[NSView class]] && [label isKindOfClass:[NSView class]]) {
        if ([field respondsToSelector:@selector(setAccessibilityTitleUIElement:)]) {
            [field setAccessibilityTitleUIElement:label];
        }
    }
}

void jnative_ui_platform_set_next_key_view(int32_t fromId, int32_t toId) {
    if (!g_cocoa_active || fromId <= 0 || fromId >= JN_UI_MAX
            || toId <= 0 || toId >= JN_UI_MAX) {
        return;
    }
    id from = g_peers[fromId];
    id to = g_peers[toId];
    if ([from isKindOfClass:[NSView class]] && [to isKindOfClass:[NSView class]]) {
        [(NSView *)from setNextKeyView:(NSView *)to];
    }
}

void jnative_ui_platform_window_set_default_button(int32_t windowId, int32_t buttonId) {
    if (!g_cocoa_active || windowId <= 0 || windowId >= JN_UI_MAX) {
        return;
    }
    id winObj = g_peers[windowId];
    if (![winObj isKindOfClass:[NSWindow class]]) {
        return;
    }
    NSWindow *win = (NSWindow *)winObj;
    if (buttonId <= 0 || buttonId >= JN_UI_MAX) {
        [win setDefaultButtonCell:nil];
        return;
    }
    id btn = g_peers[buttonId];
    if ([btn isKindOfClass:[NSButton class]]) {
        [win setDefaultButtonCell:[(NSButton *)btn cell]];
    }
}

void jnative_ui_platform_window_set_cancel_button(int32_t windowId, int32_t buttonId) {
    /* Escape is handled in managed Window keyDown → cancel button click. Peer id stored via value_i32 on window. */
    (void)windowId;
    (void)buttonId;
}

int32_t jnative_ui_platform_prefers_reduced_motion(void) {
    if (!g_cocoa_active) {
        return -1;
    }
    if (@available(macOS 10.12, *)) {
        return [[NSWorkspace sharedWorkspace] accessibilityDisplayShouldReduceMotion] ? 1 : 0;
    }
    return -1;
}

int32_t jnative_ui_platform_prefers_increased_contrast(void) {
    if (!g_cocoa_active) {
        return -1;
    }
    if (@available(macOS 10.10, *)) {
        return [[NSWorkspace sharedWorkspace] accessibilityDisplayShouldIncreaseContrast] ? 1 : 0;
    }
    return -1;
}

void jnative_ui_platform_set_button_symbol(int32_t peerId, const char *symbolName) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX || symbolName == NULL) {
        return;
    }
    id obj = g_peers[peerId];
    if (![obj isKindOfClass:[NSButton class]]) {
        return;
    }
    NSButton *btn = (NSButton *)obj;
    NSImage *img = jn_symbol(jn_ns_from_utf8(symbolName), nil);
    if (img != nil) {
        [btn setImage:img];
        [btn setImagePosition:NSImageLeft];
        [btn sizeToFit];
    }
}

void jnative_ui_platform_set_selected(int32_t peerId, int32_t selected) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[NSButton class]]) {
        NSButton *btn = (NSButton *)obj;
        [btn setState:(selected != 0) ? NSControlStateValueOn : NSControlStateValueOff];
    } else if (obj == nil) {
        jn_menu_item_refresh(peerId);
    }
}

void jnative_ui_platform_set_selected_index(int32_t peerId, int32_t index) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[NSSegmentedControl class]]) {
        NSSegmentedControl *seg = (NSSegmentedControl *)obj;
        if (index >= 0 && index < [seg segmentCount]) {
            [seg setSelectedSegment:index];
        }
        return;
    }
    if ([obj isKindOfClass:[NSComboBox class]]) {
        NSComboBox *box = (NSComboBox *)obj;
        if (index >= 0 && index < [box numberOfItems]) {
            [box selectItemAtIndex:index];
            id val = [box itemObjectValueAtIndex:index];
            if (val != nil) {
                [box setObjectValue:val];
            }
        } else if (index < 0) {
            [box setObjectValue:@""];
            [box deselectItemAtIndex:[box indexOfSelectedItem]];
        }
        return;
    }
    NSTableView *tv = jn_table_view(peerId);
    if (tv != nil) {
        if (index < 0) {
            [tv deselectAll:nil];
        } else if (index < [tv numberOfRows]) {
            [tv selectRowIndexes:[NSIndexSet indexSetWithIndex:(NSUInteger)index]
           byExtendingSelection:NO];
            [tv scrollRowToVisible:index];
        }
    }
}

void jnative_ui_platform_set_visual(int32_t peerId, int32_t bg_rgba, int32_t fg_rgba,
                                    int32_t corner_radius, int32_t font_size, int32_t text_align) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    /* VISUAL_KEEP = 1 — must not collide with opaque white (0xFFFFFFFF). */
    const int32_t KEEP = 1;
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[JnUiButton class]]) {
        JnUiButton *btn = (JnUiButton *)obj;
        btn.jnFilled = 1;
        if (bg_rgba != KEEP) {
            btn.jnBgRgba = bg_rgba;
        }
        if (fg_rgba != KEEP) {
            btn.jnFgRgba = fg_rgba;
        } else if (btn.jnFgRgba == 0) {
            btn.jnFgRgba = (int32_t)0xFFFFFFFF;
        }
        if (corner_radius >= 0) {
            btn.jnRadius = corner_radius;
        }
        if (font_size > 0) {
            [btn setFont:[NSFont systemFontOfSize:(CGFloat)font_size weight:NSFontWeightMedium]];
        }
        if (text_align == 0) {
            [btn setAlignment:NSTextAlignmentLeft];
        } else if (text_align == 2) {
            [btn setAlignment:NSTextAlignmentRight];
        } else if (text_align == 1) {
            [btn setAlignment:NSTextAlignmentCenter];
        }
        [btn jnApplyFilledVisual];
        [btn updateTrackingAreas];
        return;
    }
    if ([obj isKindOfClass:[NSTextField class]]) {
        NSTextField *lab = (NSTextField *)obj;
        if (fg_rgba != KEEP) {
            [lab setTextColor:jn_color_from_rgba(fg_rgba)];
        }
        if (font_size > 0) {
            CGFloat wt = NSFontWeightRegular;
            if (font_size >= 40) {
                wt = NSFontWeightMedium;
            }
            [lab setFont:[NSFont monospacedDigitSystemFontOfSize:(CGFloat)font_size
                                                          weight:wt]];
        }
        if (text_align == 0) {
            [lab setAlignment:NSTextAlignmentLeft];
        } else if (text_align == 1) {
            [lab setAlignment:NSTextAlignmentCenter];
        } else if (text_align == 2) {
            [lab setAlignment:NSTextAlignmentRight];
        }
        if (bg_rgba != KEEP) {
            lab.wantsLayer = YES;
            lab.drawsBackground = NO;
            lab.layer.backgroundColor = jn_color_from_rgba(bg_rgba).CGColor;
            if (corner_radius >= 0) {
                lab.layer.cornerRadius = (CGFloat)corner_radius;
            }
        }
        return;
    }
    if ([obj isKindOfClass:[NSView class]]) {
        NSView *view = (NSView *)obj;
        if (bg_rgba != KEEP) {
            view.wantsLayer = YES;
            view.layer.backgroundColor = jn_color_from_rgba(bg_rgba).CGColor;
            if (corner_radius >= 0) {
                view.layer.cornerRadius = (CGFloat)corner_radius;
            }
        }
    }
}

void jnative_ui_platform_window_set_min_size(int32_t windowId, int32_t w, int32_t h) {
    if (!g_cocoa_active || windowId <= 0 || windowId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[windowId];
    if ([obj isKindOfClass:[NSWindow class]]) {
        NSWindow *win = (NSWindow *)obj;
        [win setContentMinSize:NSMakeSize((CGFloat)w, (CGFloat)h)];
    }
}

void jnative_ui_platform_button_flash(int32_t peerId, int32_t pressed) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return;
    }
    id obj = g_peers[peerId];
    if ([obj isKindOfClass:[JnUiButton class]]) {
        JnUiButton *btn = (JnUiButton *)obj;
        if (btn.jnFilled != 0) {
            btn.jnPressed = pressed != 0 ? 1 : 0;
            [btn jnApplyFilledVisual];
        }
    }
}

int32_t jnative_ui_platform_features(void) {
    if (!g_cocoa_active) {
        return 0;
    }
    return JN_UI_FEAT_SEGMENTED | JN_UI_FEAT_SEARCH_FIELD | JN_UI_FEAT_TABLE
        | JN_UI_FEAT_TRENDCHART | JN_UI_FEAT_DIALOGS | JN_UI_FEAT_TOOLTIP
        | JN_UI_FEAT_SF_SYMBOL | JN_UI_FEAT_OVERFLOW_MENU;
    /* JN_UI_FEAT_NATIVE_TOOLBAR intentionally unset: Toolbar is HBox chrome
       composed of native buttons / segmented / search, not NSToolbar. */
}

int32_t jnative_ui_platform_table_get_scroll_y(int32_t peerId) {
    if (!g_cocoa_active) {
        return -1;
    }
    NSTableView *tv = jn_table_view(peerId);
    if (tv == nil) {
        return -1;
    }
    NSScrollView *sv = [tv enclosingScrollView];
    if (sv == nil) {
        return -1;
    }
    NSRect vis = [[sv contentView] documentVisibleRect];
    return (int32_t)(vis.origin.y + 0.5);
}

void jnative_ui_platform_table_set_scroll_y(int32_t peerId, int32_t y) {
    if (!g_cocoa_active || y < 0) {
        return;
    }
    NSTableView *tv = jn_table_view(peerId);
    if (tv == nil) {
        return;
    }
    NSScrollView *sv = [tv enclosingScrollView];
    if (sv == nil) {
        return;
    }
    NSPoint pt = NSMakePoint(0, (CGFloat)y);
    [[sv documentView] scrollPoint:pt];
}


/* ===== Notepad-class editor natives (Cocoa proxy for the Win32 backend) ===== */

static JnTextArea *jn_ta(int32_t peerId) {
    if (!g_cocoa_active || peerId <= 0 || peerId >= JN_UI_MAX) {
        return nil;
    }
    id obj = g_peers[peerId];
    if (![obj isKindOfClass:[NSScrollView class]]) {
        return nil;
    }
    id doc = [(NSScrollView *)obj documentView];
    return [doc isKindOfClass:[JnTextArea class]] ? (JnTextArea *)doc : nil;
}

int32_t jnative_ui_ta_get_sel_start(int32_t id) {
    JnTextArea *tv = jn_ta(id);
    return tv == nil ? 0 : (int32_t)tv.selectedRange.location;
}

int32_t jnative_ui_ta_get_sel_end(int32_t id) {
    JnTextArea *tv = jn_ta(id);
    return tv == nil ? 0 : (int32_t)NSMaxRange(tv.selectedRange);
}

void jnative_ui_ta_set_sel(int32_t id, int32_t start, int32_t end) {
    JnTextArea *tv = jn_ta(id);
    if (tv == nil) {
        return;
    }
    NSInteger len = (NSInteger)tv.string.length;
    if (end < 0 || end > len) end = (int32_t)len;
    if (start < 0) start = 0;
    if (start > end) {
        int32_t t = start; start = end; end = t;
    }
    [tv setSelectedRange:NSMakeRange((NSUInteger)start, (NSUInteger)(end - start))];
}

void jnative_ui_ta_replace_sel(int32_t id, const char *text) {
    JnTextArea *tv = jn_ta(id);
    if (tv == nil) {
        return;
    }
    NSString *s = jn_nsstring(text);
    NSRange r = tv.selectedRange;
    if ([tv shouldChangeTextInRange:r replacementString:s]) {
        [tv replaceCharactersInRange:r withString:s];
        [tv setSelectedRange:NSMakeRange(r.location + s.length, 0)];
        [tv didChangeText];
    }
}

void jnative_ui_ta_command(int32_t peerId, int32_t cmd) {
    JnTextArea *tv = jn_ta(peerId);
    if (tv == nil) {
        id fld = (peerId > 0 && peerId < JN_UI_MAX) ? g_peers[peerId] : nil;
        if (cmd == 7 && [fld isKindOfClass:[NSTextField class]]) {
            [(NSTextField *)fld selectText:nil];
        }
        return;
    }
    switch (cmd) {
    case 1: [[tv undoManager] undo]; break;
    case 3: [tv cut:nil]; break;
    case 4: [tv copy:nil]; break;
    case 5: [tv pasteAsPlainText:nil]; break;
    case 6: [tv delete:nil]; break;
    case 7: [tv selectAll:nil]; break;
    case 8: [[tv undoManager] removeAllActions]; break;
    case 9: [tv scrollRangeToVisible:tv.selectedRange]; break;
    default: break;
    }
}

int32_t jnative_ui_ta_can_undo(int32_t id) {
    JnTextArea *tv = jn_ta(id);
    return (tv != nil && [[tv undoManager] canUndo]) ? 1 : 0;
}

/* Logical (hard-break) lines; CRLF counts as one break. */
static NSUInteger jn_line_start_after(NSString *s, NSUInteger i) {
    NSUInteger n = s.length;
    while (i < n) {
        unichar c = [s characterAtIndex:i];
        if (c == '\n') return i + 1;
        if (c == '\r') {
            if (i + 1 < n && [s characterAtIndex:i + 1] == '\n') return i + 2;
            return i + 1;
        }
        i++;
    }
    return n + 1; /* no further line */
}

int32_t jnative_ui_ta_line_count(int32_t id) {
    JnTextArea *tv = jn_ta(id);
    if (tv == nil) {
        return 1;
    }
    NSString *s = tv.string;
    int32_t lines = 1;
    NSUInteger p = 0;
    for (;;) {
        NSUInteger nx = jn_line_start_after(s, p);
        if (nx > s.length) break;
        lines++;
        p = nx;
    }
    return lines;
}

int32_t jnative_ui_ta_line_from_char(int32_t id, int32_t ch) {
    JnTextArea *tv = jn_ta(id);
    if (tv == nil) {
        return 0;
    }
    NSString *s = tv.string;
    int32_t line = 0;
    NSUInteger p = 0;
    for (;;) {
        NSUInteger nx = jn_line_start_after(s, p);
        if (nx > s.length || (NSInteger)nx > (NSInteger)ch) break;
        line++;
        p = nx;
    }
    return line;
}

int32_t jnative_ui_ta_line_index(int32_t id, int32_t line) {
    JnTextArea *tv = jn_ta(id);
    if (tv == nil || line < 0) {
        return -1;
    }
    NSString *s = tv.string;
    NSUInteger p = 0;
    for (int32_t i = 0; i < line; i++) {
        NSUInteger nx = jn_line_start_after(s, p);
        if (nx > s.length) return -1;
        p = nx;
    }
    return (int32_t)p;
}

int32_t jnative_ui_ta_length(int32_t id) {
    JnTextArea *tv = jn_ta(id);
    return tv == nil ? 0 : (int32_t)tv.string.length;
}

void jnative_ui_ta_set_font(int32_t id, const char *family, int32_t size10, int32_t weight,
                            int32_t italic) {
    JnTextArea *tv = jn_ta(id);
    if (tv == nil) {
        return;
    }
    CGFloat pt = (CGFloat)size10 / 10.0 * (96.0 / 72.0) * 0.75 + (CGFloat)size10 / 10.0 * 0.25;
    NSFont *f = [NSFont fontWithName:jn_nsstring(family) size:pt];
    if (f == nil) {
        f = [NSFont userFixedPitchFontOfSize:pt];
    }
    NSFontManager *fm = [NSFontManager sharedFontManager];
    if (weight >= 600) {
        f = [fm convertFont:f toHaveTrait:NSBoldFontMask];
    }
    if (italic != 0) {
        f = [fm convertFont:f toHaveTrait:NSItalicFontMask];
    }
    [tv setFont:f];
}

void jnative_ui_ta_set_wrap(int32_t id, int32_t wrap) {
    JnTextArea *tv = jn_ta(id);
    if (tv == nil) {
        return;
    }
    NSScrollView *sv = [tv enclosingScrollView];
    NSTextContainer *tc = [tv textContainer];
    if (wrap) {
        [sv setHasHorizontalScroller:NO];
        [tv setHorizontallyResizable:NO];
        [tv setAutoresizingMask:NSViewWidthSizable];
        [tc setWidthTracksTextView:YES];
        [tc setContainerSize:NSMakeSize(sv.contentSize.width, 1e7)];
        [tv setFrameSize:NSMakeSize(sv.contentSize.width, tv.frame.size.height)];
    } else {
        [sv setHasHorizontalScroller:YES];
        [tv setHorizontallyResizable:YES];
        [tv setAutoresizingMask:NSViewNotSizable];
        [tc setWidthTracksTextView:NO];
        [tc setContainerSize:NSMakeSize(1e7, 1e7)];
    }
    [tv setNeedsDisplay:YES];
}

int32_t jnative_ui_clipboard_has_text(void) {
    if (!g_cocoa_active) {
        return 0;
    }
    return [[NSPasteboard generalPasteboard] canReadObjectForClasses:@[ [NSString class] ]
                                                             options:nil] ? 1 : 0;
}

int32_t jnative_ui_system_ansi_codepage(void) { return 1252; }

char *jnative_ui_codepage_decode(const char *byteStr, int32_t codepage) {
    NSString *src = jn_nsstring(byteStr);
    NSMutableData *raw = [NSMutableData dataWithCapacity:src.length];
    for (NSUInteger i = 0; i < src.length; i++) {
        unichar c = [src characterAtIndex:i];
        uint8_t b = (uint8_t)(c & 0xFF);
        [raw appendBytes:&b length:1];
    }
    CFStringEncoding enc = CFStringConvertWindowsCodepageToEncoding((UInt32)codepage);
    if (enc == kCFStringEncodingInvalidId) {
        enc = kCFStringEncodingWindowsLatin1;
    }
    NSString *out = [[NSString alloc] initWithData:raw
                                          encoding:CFStringConvertEncodingToNSStringEncoding(enc)];
    const char *u = (out != nil ? out : @"").UTF8String;
    return strdup(u ? u : "");
}

char *jnative_ui_codepage_encode(const char *utf8, int32_t codepage) {
    NSString *src = jn_nsstring(utf8);
    CFStringEncoding enc = CFStringConvertWindowsCodepageToEncoding((UInt32)codepage);
    if (enc == kCFStringEncodingInvalidId) {
        enc = kCFStringEncodingWindowsLatin1;
    }
    NSData *d = [src dataUsingEncoding:CFStringConvertEncodingToNSStringEncoding(enc)
                  allowLossyConversion:YES];
    NSMutableString *out = [NSMutableString stringWithCapacity:d.length];
    const uint8_t *b = (const uint8_t *)d.bytes;
    for (NSUInteger i = 0; i < d.length; i++) {
        if (b[i] == 0) continue; /* NUL not representable in C strings */
        unichar c = b[i];
        [out appendString:[NSString stringWithCharacters:&c length:1]];
    }
    const char *u = out.UTF8String;
    return strdup(u ? u : "");
}

static int32_t g_dlg_enc = 0;

int32_t jnative_ui_dialog_last_enc(void) { return g_dlg_enc; }

/* filterSpec: "Desc|*.txt;*.log|Desc2|*.*" */
char *jnative_ui_dialog_file_ex(int32_t windowId, int32_t save, const char *filterSpec,
                                const char *defaultName, const char *encLabels, int32_t encIn) {
    (void)windowId; (void)encLabels;
    g_dlg_enc = encIn;
    if (!g_cocoa_active) {
        return NULL;
    }
    NSMutableArray<NSString *> *exts = [NSMutableArray array];
    NSArray<NSString *> *parts = [jn_nsstring(filterSpec) componentsSeparatedByString:@"|"];
    for (NSUInteger i = 1; i < parts.count && exts.count == 0; i += 2) {
        for (NSString *pat in [parts[i] componentsSeparatedByString:@";"]) {
            NSString *t = [pat stringByTrimmingCharactersInSet:
                [NSCharacterSet whitespaceCharacterSet]];
            if ([t hasPrefix:@"*."] && ![t isEqualToString:@"*.*"]) {
                [exts addObject:[[t substringFromIndex:2] lowercaseString]];
            }
        }
    }
    NSSavePanel *panel = save ? [NSSavePanel savePanel] : [NSOpenPanel openPanel];
    [panel setCanCreateDirectories:YES];
    if (save) {
        if (exts.count > 0) {
            [panel setAllowedFileTypes:exts];
            [panel setAllowsOtherFileTypes:YES];
        }
        if (defaultName != NULL && defaultName[0] != '\0') {
            [panel setNameFieldStringValue:jn_nsstring(defaultName)];
        }
    } else {
        [(NSOpenPanel *)panel setCanChooseFiles:YES];
        [(NSOpenPanel *)panel setCanChooseDirectories:NO];
    }
    if ([panel runModal] != NSModalResponseOK) {
        return NULL;
    }
    const char *u = panel.URL.path.UTF8String;
    return u ? strdup(u) : NULL;
}

char *jnative_ui_dialog_font(int32_t windowId, const char *family, int32_t size10,
                             int32_t weight, int32_t italic) {
    (void)windowId; (void)family; (void)size10; (void)weight; (void)italic;
    return NULL; /* font chooser is Windows-only in this proxy */
}

void jnative_ui_window_set_dialog(int32_t childId, int32_t ownerId) {
    (void)ownerId;
    id obj = (childId > 0 && childId < JN_UI_MAX) ? g_peers[childId] : nil;
    if ([obj isKindOfClass:[NSWindow class]]) {
        NSWindow *w = (NSWindow *)obj;
        [w setStyleMask:NSWindowStyleMaskTitled | NSWindowStyleMaskClosable];
        [w setLevel:NSFloatingWindowLevel];
    }
}

void jnative_ui_window_activate(int32_t winId) {
    id obj = (winId > 0 && winId < JN_UI_MAX) ? g_peers[winId] : nil;
    if ([obj isKindOfClass:[NSWindow class]]) {
        [(NSWindow *)obj makeKeyAndOrderFront:nil];
        [NSApp activateIgnoringOtherApps:YES];
    }
}

/* ------------------------------------------------------------------------------------------
 * Native text for the software Graphics context (fonts whose family starts with "native:").
 * Renders through CoreText into an 8-bit coverage mask which the C rasteriser blends.
 * ------------------------------------------------------------------------------------------ */
#import <CoreText/CoreText.h>

@interface JnTextEntry : NSObject
@property(nonatomic) int32_t w;
@property(nonatomic) int32_t h;
@property(nonatomic) int32_t ascent;
@property(nonatomic) double advance;
@property(nonatomic) double descent;
@property(nonatomic) double line;
@property(nonatomic, strong) NSData *mask;
@end
@implementation JnTextEntry
@end

static NSMutableDictionary<NSString *, JnTextEntry *> *g_text_cache;
static NSMutableArray<NSString *> *g_text_cache_order;
static NSMutableDictionary<NSString *, NSFont *> *g_font_cache;

static NSFont *jn_native_font(const char *family, int32_t size, int32_t style) {
    if (size < 1) {
        size = 12;
    }
    NSString *fam = (family && family[0]) ? [NSString stringWithUTF8String:family] : @"system";
    NSString *key = [NSString stringWithFormat:@"%@|%d|%d", fam, size, style];
    if (g_font_cache == nil) {
        g_font_cache = [[NSMutableDictionary alloc] init];
    }
    NSFont *f = g_font_cache[key];
    if (f != nil) {
        return f;
    }
    BOOL bold = (style & 1) != 0;
    BOOL italic = (style & 2) != 0;
    if ([fam isEqualToString:@"system"]) {
        f = [NSFont systemFontOfSize:size weight:bold ? NSFontWeightSemibold : NSFontWeightRegular];
    } else if ([fam isEqualToString:@"mono"]) {
        f = [NSFont monospacedSystemFontOfSize:size weight:bold ? NSFontWeightSemibold : NSFontWeightRegular];
    } else {
        f = [NSFont fontWithName:fam size:size];
        if (f == nil) {
            f = [NSFont systemFontOfSize:size];
        }
        if (bold) {
            f = [[NSFontManager sharedFontManager] convertFont:f toHaveTrait:NSBoldFontMask];
        }
    }
    if (italic) {
        f = [[NSFontManager sharedFontManager] convertFont:f toHaveTrait:NSItalicFontMask];
    }
    g_font_cache[key] = f;
    return f;
}

static JnTextEntry *jn_text_entry(const char *utf8, const char *family, int32_t size, int32_t style, BOOL withMask) {
    NSString *s = utf8 ? [NSString stringWithUTF8String:utf8] : @"";
    if (s == nil) {
        s = @"";
    }
    NSString *key = [NSString stringWithFormat:@"%d|%d|%s|%@", size, style, family ? family : "", s];
    if (g_text_cache == nil) {
        g_text_cache = [[NSMutableDictionary alloc] init];
        g_text_cache_order = [[NSMutableArray alloc] init];
    }
    JnTextEntry *e = g_text_cache[key];
    if (e != nil && (!withMask || e.mask != nil || e.w == 0)) {
        return e;
    }
    NSFont *font = jn_native_font(family, size, style);
    NSDictionary *attrs = @{NSFontAttributeName : font, NSForegroundColorAttributeName : [NSColor whiteColor]};
    NSAttributedString *as = [[NSAttributedString alloc] initWithString:(s.length ? s : @" ") attributes:attrs];
    CTLineRef line = CTLineCreateWithAttributedString((__bridge CFAttributedStringRef)as);
    CGFloat asc = 0, desc = 0, lead = 0;
    double adv = CTLineGetTypographicBounds(line, &asc, &desc, &lead);
    if (s.length == 0) {
        adv = 0;
    }
    e = [JnTextEntry new];
    e.advance = adv;
    e.descent = ceil(desc);
    e.ascent = (int32_t)ceil(asc);
    e.line = ceil(asc) + ceil(desc);
    int32_t w = (int32_t)ceil(adv) + 2;
    int32_t h = (int32_t)(ceil(asc) + ceil(desc)) + 1;
    e.w = s.length ? w : 0;
    e.h = h;
    if (withMask && s.length > 0 && w > 0 && h > 0) {
        NSMutableData *buf = [NSMutableData dataWithLength:(NSUInteger)w * (NSUInteger)h];
        CGColorSpaceRef cs = CGColorSpaceCreateDeviceGray();
        CGContextRef ctx = CGBitmapContextCreate(buf.mutableBytes, (size_t)w, (size_t)h, 8, (size_t)w, cs,
                                                 (CGBitmapInfo)kCGImageAlphaNone);
        CGColorSpaceRelease(cs);
        if (ctx != NULL) {
            CGContextSetGrayFillColor(ctx, 1.0, 1.0);
            CGContextSetShouldAntialias(ctx, true);
            CGContextSetShouldSmoothFonts(ctx, false);
            CGContextSetTextPosition(ctx, 1.0, ceil(desc) + 0.5);
            CTLineDraw(line, ctx);
            CGContextRelease(ctx);
        }
        e.mask = buf;
    }
    CFRelease(line);
    g_text_cache[key] = e;
    [g_text_cache_order addObject:key];
    if (g_text_cache_order.count > 2048) {
        for (int i = 0; i < 512; i++) {
            [g_text_cache removeObjectForKey:g_text_cache_order[0]];
            [g_text_cache_order removeObjectAtIndex:0];
        }
    }
    return e;
}

int32_t jnative_gfx_platform_text_mask(const char *utf8, const char *family, int32_t size, int32_t style,
                                       int32_t *outW, int32_t *outH, int32_t *outAscent,
                                       const uint8_t **outMask) {
    @autoreleasepool {
        JnTextEntry *e = jn_text_entry(utf8, family, size, style, YES);
        if (e == nil || e.mask == nil) {
            *outW = 0;
            *outH = 0;
            *outAscent = 0;
            *outMask = NULL;
            return 1;
        }
        *outW = e.w;
        *outH = e.h;
        *outAscent = e.ascent;
        /* Entry (and its NSData) is retained by the cache until eviction on a later call. */
        *outMask = (const uint8_t *)e.mask.bytes;
        return 1;
    }
}

int32_t jnative_gfx_platform_text_metrics(const char *utf8, const char *family, int32_t size, int32_t style,
                                          double *outW, double *outAscent, double *outDescent,
                                          double *outLine) {
    @autoreleasepool {
        JnTextEntry *e = jn_text_entry(utf8, family, size, style, NO);
        if (e == nil) {
            return 0;
        }
        *outW = e.advance;
        *outAscent = e.ascent;
        *outDescent = e.descent;
        *outLine = e.line;
        return 1;
    }
}

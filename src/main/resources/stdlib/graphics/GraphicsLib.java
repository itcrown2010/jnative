/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Graphics stdlib (0.19) — library/runtime, NOT compiler intrinsics.
// Public names match jnative.graphics.* (imports flattened).

/**
 * @author 张晓达
 */
class GraphicsException extends RuntimeException {
    GraphicsException(String message) {
        super(message);
    }
}

class ImageDecodeException extends GraphicsException {
    ImageDecodeException(String message) {
        super(message);
    }
}

class InvalidGraphicsStateException extends GraphicsException {
    InvalidGraphicsStateException(String message) {
        super(message);
    }
}

class DisposedGraphicsResourceException extends GraphicsException {
    DisposedGraphicsResourceException(String message) {
        super(message);
    }
}

class GfxNative {
    @Native(symbol = "jnative_gfx_surface_create")
    static native int surfaceCreate(int width, int height);

    @Native(symbol = "jnative_gfx_surface_destroy")
    static native void surfaceDestroy(int id);

    @Native(symbol = "jnative_gfx_surface_width")
    static native int surfaceWidth(int id);

    @Native(symbol = "jnative_gfx_surface_height")
    static native int surfaceHeight(int id);

    @Native(symbol = "jnative_gfx_begin")
    static native int begin(int surfaceId);

    @Native(symbol = "jnative_gfx_end")
    static native void end(int gfxId);

    @Native(symbol = "jnative_gfx_is_valid")
    static native int isValid(int gfxId);

    @Native(symbol = "jnative_gfx_set_color")
    static native void setColor(int gfxId, int rgba);

    @Native(symbol = "jnative_gfx_set_color_components")
    static native void setColorComponents(int gfxId, int r, int g, int b, int a);

    @Native(symbol = "jnative_gfx_pack_rgba")
    static native int packRgba(int r, int g, int b, int a);

    @Native(symbol = "jnative_gfx_unpack_r")
    static native int unpackR(int rgba);

    @Native(symbol = "jnative_gfx_unpack_g")
    static native int unpackG(int rgba);

    @Native(symbol = "jnative_gfx_unpack_b")
    static native int unpackB(int rgba);

    @Native(symbol = "jnative_gfx_unpack_a")
    static native int unpackA(int rgba);

    @Native(symbol = "jnative_gfx_set_stroke_width")
    static native void setStrokeWidth(int gfxId, double width);

    @Native(symbol = "jnative_gfx_set_font")
    static native void setFont(int gfxId, String family, int size, int style);

    @Native(symbol = "jnative_gfx_set_antialias")
    static native void setAntialias(int gfxId, int on);

    @Native(symbol = "jnative_gfx_clear")
    static native void clear(int gfxId, int rgba);

    @Native(symbol = "jnative_gfx_draw_line")
    static native void drawLine(int gfxId, double x1, double y1, double x2, double y2);

    @Native(symbol = "jnative_gfx_draw_rect")
    static native void drawRect(int gfxId, double x, double y, double w, double h);

    @Native(symbol = "jnative_gfx_fill_rect")
    static native void fillRect(int gfxId, double x, double y, double w, double h);

    @Native(symbol = "jnative_gfx_draw_round_rect")
    static native void drawRoundRect(int gfxId, double x, double y, double w, double h, double r);

    @Native(symbol = "jnative_gfx_fill_round_rect")
    static native void fillRoundRect(int gfxId, double x, double y, double w, double h, double r);

    @Native(symbol = "jnative_gfx_draw_ellipse")
    static native void drawEllipse(int gfxId, double x, double y, double w, double h);

    @Native(symbol = "jnative_gfx_fill_ellipse")
    static native void fillEllipse(int gfxId, double x, double y, double w, double h);

    @Native(symbol = "jnative_gfx_draw_text")
    static native void drawText(int gfxId, String utf8, double x, double y);

    @Native(symbol = "jnative_gfx_measure_text_w")
    static native int measureTextW(int gfxId, String utf8);

    @Native(symbol = "jnative_gfx_measure_text_h")
    static native int measureTextH(int gfxId, String utf8);

    @Native(symbol = "jnative_gfx_font_ascent")
    static native int fontAscent(int gfxId);

    @Native(symbol = "jnative_gfx_font_descent")
    static native int fontDescent(int gfxId);

    @Native(symbol = "jnative_gfx_font_line_height")
    static native int fontLineHeight(int gfxId);

    @Native(symbol = "jnative_gfx_draw_image")
    static native void drawImage(int gfxId, int imageId, double x, double y);

    @Native(symbol = "jnative_gfx_draw_image_sized")
    static native void drawImageSized(int gfxId, int imageId, double x, double y, double w, double h);

    @Native(symbol = "jnative_gfx_set_clip")
    static native void setClip(int gfxId, double x, double y, double w, double h);

    @Native(symbol = "jnative_gfx_clear_clip")
    static native void clearClip(int gfxId);

    @Native(symbol = "jnative_gfx_save")
    static native void save(int gfxId);

    @Native(symbol = "jnative_gfx_restore")
    static native void restore(int gfxId);

    @Native(symbol = "jnative_gfx_translate")
    static native void translate(int gfxId, double dx, double dy);

    @Native(symbol = "jnative_gfx_scale")
    static native void scale(int gfxId, double sx, double sy);

    @Native(symbol = "jnative_gfx_rotate")
    static native void rotate(int gfxId, double radians);

    @Native(symbol = "jnative_gfx_path_create")
    static native int pathCreate();

    @Native(symbol = "jnative_gfx_path_destroy")
    static native void pathDestroy(int pathId);

    @Native(symbol = "jnative_gfx_path_clear")
    static native void pathClear(int pathId);

    @Native(symbol = "jnative_gfx_path_move_to")
    static native void pathMoveTo(int pathId, double x, double y);

    @Native(symbol = "jnative_gfx_path_line_to")
    static native void pathLineTo(int pathId, double x, double y);

    @Native(symbol = "jnative_gfx_path_quad_to")
    static native void pathQuadTo(int pathId, double cx, double cy, double x, double y);

    @Native(symbol = "jnative_gfx_path_cubic_to")
    static native void pathCubicTo(int pathId, double c1x, double c1y, double c2x, double c2y, double x, double y);

    @Native(symbol = "jnative_gfx_path_close")
    static native void pathClose(int pathId);

    @Native(symbol = "jnative_gfx_draw_path")
    static native void drawPath(int gfxId, int pathId);

    @Native(symbol = "jnative_gfx_fill_path")
    static native void fillPath(int gfxId, int pathId);

    @Native(symbol = "jnative_gfx_image_load_file")
    static native int imageLoadFile(String path);

    @Native(symbol = "jnative_gfx_image_save_png")
    static native int imageSavePng(int surfaceId, String path);

    @Native(symbol = "jnative_gfx_get_pixel")
    static native int getPixel(int surfaceId, int x, int y);

    @Native(symbol = "jnative_gfx_set_pixel")
    static native void setPixel(int surfaceId, int x, int y, int rgba);

    @Native(symbol = "jnative_gfx_draw_image_region")
    static native void drawImageRegion(int gfxId, int imageId, int sx, int sy, int sw, int sh, double x, double y,
            double w, double h);

    @Native(symbol = "jnative_gfx_flood_fill")
    static native int floodFill(int surfaceId, int x, int y, int rgba, int tol);

    @Native(symbol = "jnative_gfx_live_surfaces")
    static native int liveSurfaces();

    @Native(symbol = "jnative_gfx_live_fonts")
    static native int liveFonts();

    @Native(symbol = "jnative_gfx_live_paths")
    static native int livePaths();

    @Native(symbol = "jnative_gfx_live_contexts")
    static native int liveContexts();
}

class Color {
    int r;
    int g;
    int b;
    int a;

    Color(int red, int green, int blue) {
        r = red;
        g = green;
        b = blue;
        a = 255;
    }

    Color(int red, int green, int blue, int alpha) {
        r = red;
        g = green;
        b = blue;
        a = alpha;
    }

    static Color fromRgba(int packed) {
        return new Color(
                GfxNative.unpackR(packed),
                GfxNative.unpackG(packed),
                GfxNative.unpackB(packed),
                GfxNative.unpackA(packed));
    }

    int getR() {
        return r;
    }

    int getG() {
        return g;
    }

    int getB() {
        return b;
    }

    int getA() {
        return a;
    }

    int toRgba() {
        return GfxNative.packRgba(r, g, b, a);
    }

    static Color BLACK = new Color(0, 0, 0);
    static Color WHITE = new Color(255, 255, 255);
    static Color RED = new Color(255, 0, 0);
    static Color GREEN = new Color(0, 255, 0);
    static Color BLUE = new Color(0, 0, 255);
    static Color GRAY = new Color(128, 128, 128);
    static Color TRANSPARENT = new Color(0, 0, 0, 0);
}

/* Integer geometry types Point, Size and Rect live in the UI module; floating-point variants are PointF, SizeF, RectF. */

class Stroke {
    double width;

    Stroke(double w) {
        width = w;
    }

    double getWidth() {
        return width;
    }
}

class FontStyle {
    static int NORMAL = 0;
    static int BOLD = 1;
    static int ITALIC = 2;
    static int BOLD_ITALIC = 3;
}

class Font {
    String family;
    int size;
    int style;

    Font(String fam, int sz) {
        family = fam;
        size = sz;
        style = FontStyle.NORMAL;
    }

    Font(String fam, int sz, int st) {
        family = fam;
        size = sz;
        style = st;
    }

    static Font monospace(int sz) {
        return new Font("monospace", sz, FontStyle.NORMAL);
    }

    /** Platform UI font rendered by the OS text engine (anti-aliased, full Unicode / CJK). */
    static Font system(int sz) {
        return new Font("native:system", sz, FontStyle.NORMAL);
    }

    static Font systemBold(int sz) {
        return new Font("native:system", sz, FontStyle.BOLD);
    }

    /** Platform monospaced font (code). */
    static Font mono(int sz) {
        return new Font("native:mono", sz, FontStyle.NORMAL);
    }

    /** Named OS font family, e.g. "Menlo" or "PingFang SC". */
    static Font named(String family, int sz, int style) {
        return new Font("native:" + family, sz, style);
    }

    String getFamily() {
        return family;
    }

    int getSize() {
        return size;
    }

    int getStyle() {
        return style;
    }
}

class FontMetrics {
    int ascent;
    int descent;
    int lineHeight;

    FontMetrics(int a, int d, int lh) {
        ascent = a;
        descent = d;
        lineHeight = lh;
    }

    int getAscent() {
        return ascent;
    }

    int getDescent() {
        return descent;
    }

    int getLineHeight() {
        return lineHeight;
    }
}

class Path {
    int nativeId;
    int disposed;

    Path() {
        nativeId = GfxNative.pathCreate();
        disposed = 0;
    }

    void ensure() {
        if (disposed != 0 || nativeId <= 0) {
            throw new DisposedGraphicsResourceException("Path disposed");
        }
    }

    void moveTo(double x, double y) {
        ensure();
        GfxNative.pathMoveTo(nativeId, x, y);
    }

    void lineTo(double x, double y) {
        ensure();
        GfxNative.pathLineTo(nativeId, x, y);
    }

    void quadTo(double cx, double cy, double x, double y) {
        ensure();
        GfxNative.pathQuadTo(nativeId, cx, cy, x, y);
    }

    void cubicTo(double c1x, double c1y, double c2x, double c2y, double x, double y) {
        ensure();
        GfxNative.pathCubicTo(nativeId, c1x, c1y, c2x, c2y, x, y);
    }

    void close() {
        ensure();
        GfxNative.pathClose(nativeId);
    }

    void clear() {
        ensure();
        GfxNative.pathClear(nativeId);
    }

    void dispose() {
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        if (nativeId > 0) {
            GfxNative.pathDestroy(nativeId);
            nativeId = 0;
        }
    }
}

class Image {
    int surfaceId;
    int disposed;

    Image(int sid) {
        surfaceId = sid;
        disposed = 0;
    }

    static Image create(int width, int height) {
        int id = GfxNative.surfaceCreate(width, height);
        if (id <= 0) {
            throw new GraphicsException("failed to create image surface");
        }
        return new Image(id);
    }

    static Image load(String path) {
        int id = GfxNative.imageLoadFile(path);
        if (id <= 0) {
            throw new ImageDecodeException("failed to load image: " + path);
        }
        return new Image(id);
    }

    static Image loadBytes(byte[] encoded) {
        if (encoded == null) {
            throw new ImageDecodeException("null image bytes");
        }
        String path = "/tmp/jnative-img-load.bin";
        File.writeBytes(path, encoded);
        Image img = Image.load(path);
        File.delete(path);
        return img;
    }

    int getWidth() {
        ensure();
        return GfxNative.surfaceWidth(surfaceId);
    }

    int getHeight() {
        ensure();
        return GfxNative.surfaceHeight(surfaceId);
    }

    void ensure() {
        if (disposed != 0 || surfaceId <= 0) {
            throw new DisposedGraphicsResourceException("Image disposed");
        }
    }

    Graphics beginGraphics() {
        ensure();
        int gid = GfxNative.begin(surfaceId);
        if (gid <= 0) {
            throw new GraphicsException("beginGraphics failed");
        }
        return new Graphics(gid, this);
    }

    void save(String path) {
        ensure();
        int ok = GfxNative.imageSavePng(surfaceId, path);
        if (ok == 0) {
            throw new GraphicsException("failed to save PNG: " + path);
        }
    }

    int getPixel(int x, int y) {
        ensure();
        return GfxNative.getPixel(surfaceId, x, y);
    }

    void setPixel(int x, int y, int rgba) {
        ensure();
        GfxNative.setPixel(surfaceId, x, y, rgba);
    }

    /** Flood fill from (x, y) with the packed colour; tol is the per-channel tolerance. Returns pixels changed. */
    int floodFill(int x, int y, int rgba, int tol) {
        ensure();
        return GfxNative.floodFill(surfaceId, x, y, rgba, tol);
    }

    void dispose() {
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        if (surfaceId > 0) {
            GfxNative.surfaceDestroy(surfaceId);
            surfaceId = 0;
        }
    }
}

interface PaintHandler {
    void paint(Graphics g);
}

interface FrameHandler {
    void onFrame(double timeSeconds);
}

class Graphics {
    int gfxId;
    Image owner;
    int ended;

    Graphics(int id, Image img) {
        gfxId = id;
        owner = img;
        ended = 0;
    }

    void ensureValid() {
        if (ended != 0 || GfxNative.isValid(gfxId) == 0) {
            throw new InvalidGraphicsStateException("Graphics context no longer valid");
        }
    }

    void setColor(Color c) {
        ensureValid();
        GfxNative.setColorComponents(gfxId, c.getR(), c.getG(), c.getB(), c.getA());
    }

    void setStroke(Stroke s) {
        ensureValid();
        GfxNative.setStrokeWidth(gfxId, s.getWidth());
    }

    void setFont(Font f) {
        ensureValid();
        GfxNative.setFont(gfxId, f.getFamily(), f.getSize(), f.getStyle());
    }

    /** Anti-aliased lines, round rects and ellipses (off by default to keep pixel-art exact). */
    void setAntialias(boolean on) {
        ensureValid();
        GfxNative.setAntialias(gfxId, on ? 1 : 0);
    }

    void setLineWidth(double w) {
        ensureValid();
        GfxNative.setStrokeWidth(gfxId, w);
    }

    void setColorRgba(int r, int g, int b, int a) {
        ensureValid();
        GfxNative.setColorComponents(gfxId, r, g, b, a);
    }

    void clear(Color c) {
        ensureValid();
        GfxNative.clear(gfxId, GfxNative.packRgba(c.getR(), c.getG(), c.getB(), c.getA()));
    }

    void drawLine(double x1, double y1, double x2, double y2) {
        ensureValid();
        GfxNative.drawLine(gfxId, x1, y1, x2, y2);
    }

    void drawRect(double x, double y, double w, double h) {
        ensureValid();
        GfxNative.drawRect(gfxId, x, y, w, h);
    }

    void fillRect(double x, double y, double w, double h) {
        ensureValid();
        GfxNative.fillRect(gfxId, x, y, w, h);
    }

    void drawRoundRect(double x, double y, double w, double h, double r) {
        ensureValid();
        GfxNative.drawRoundRect(gfxId, x, y, w, h, r);
    }

    void fillRoundRect(double x, double y, double w, double h, double r) {
        ensureValid();
        GfxNative.fillRoundRect(gfxId, x, y, w, h, r);
    }

    void drawEllipse(double x, double y, double w, double h) {
        ensureValid();
        GfxNative.drawEllipse(gfxId, x, y, w, h);
    }

    void fillEllipse(double x, double y, double w, double h) {
        ensureValid();
        GfxNative.fillEllipse(gfxId, x, y, w, h);
    }

    /** Draw text. y is the top of the glyph cell (not baseline). */
    void drawText(String text, double x, double y) {
        ensureValid();
        GfxNative.drawText(gfxId, text, x, y);
    }

    Size measureText(String text, Font font) {
        ensureValid();
        if (font != null) {
            setFont(font);
        }
        int w = GfxNative.measureTextW(gfxId, text);
        int h = GfxNative.measureTextH(gfxId, text);
        return new Size(w, h);
    }

    FontMetrics getFontMetrics(Font font) {
        ensureValid();
        if (font != null) {
            setFont(font);
        }
        return new FontMetrics(
                GfxNative.fontAscent(gfxId),
                GfxNative.fontDescent(gfxId),
                GfxNative.fontLineHeight(gfxId));
    }

    void drawImage(Image image, double x, double y) {
        ensureValid();
        image.ensure();
        GfxNative.drawImage(gfxId, image.surfaceId, x, y);
    }

    void drawImageScaled(Image image, double x, double y, double w, double h) {
        ensureValid();
        image.ensure();
        GfxNative.drawImageSized(gfxId, image.surfaceId, x, y, w, h);
    }

    /** Nearest-neighbour blit of the source rectangle (sx, sy, sw, sh) scaled into (x, y, w, h). */
    void drawImageRegion(Image image, int sx, int sy, int sw, int sh, double x, double y, double w, double h) {
        ensureValid();
        image.ensure();
        GfxNative.drawImageRegion(gfxId, image.surfaceId, sx, sy, sw, sh, x, y, w, h);
    }

    void drawPath(Path path) {
        ensureValid();
        path.ensure();
        GfxNative.drawPath(gfxId, path.nativeId);
    }

    void fillPath(Path path) {
        ensureValid();
        path.ensure();
        GfxNative.fillPath(gfxId, path.nativeId);
    }

    void setClip(double x, double y, double w, double h) {
        ensureValid();
        GfxNative.setClip(gfxId, x, y, w, h);
    }

    void clearClip() {
        ensureValid();
        GfxNative.clearClip(gfxId);
    }

    void save() {
        ensureValid();
        GfxNative.save(gfxId);
    }

    void restore() {
        ensureValid();
        GfxNative.restore(gfxId);
    }

    void translate(double dx, double dy) {
        ensureValid();
        GfxNative.translate(gfxId, dx, dy);
    }

    void scale(double sx, double sy) {
        ensureValid();
        GfxNative.scale(gfxId, sx, sy);
    }

    void rotate(double radians) {
        ensureValid();
        GfxNative.rotate(gfxId, radians);
    }

    void end() {
        if (ended != 0) {
            return;
        }
        ended = 1;
        GfxNative.end(gfxId);
        gfxId = 0;
    }
}

class GfxMetrics {
    static int liveImages() {
        return GfxNative.liveSurfaces();
    }

    static int liveFonts() {
        return GfxNative.liveFonts();
    }

    static int livePaths() {
        return GfxNative.livePaths();
    }

    static int liveContexts() {
        return GfxNative.liveContexts();
    }

    static int liveRenderTargets() {
        return GfxNative.liveSurfaces();
    }
}

class PointF {
    double x;
    double y;

    PointF(double px, double py) {
        x = px;
        y = py;
    }
}

class SizeF {
    double width;
    double height;

    SizeF(double w, double h) {
        width = w;
        height = h;
    }
}

class RectF {
    double x;
    double y;
    double width;
    double height;

    RectF(double rx, double ry, double rw, double rh) {
        x = rx;
        y = ry;
        width = rw;
        height = rh;
    }
}

class AffineTransform {
    double m00;
    double m01;
    double m10;
    double m11;
    double m02;
    double m12;

    AffineTransform() {
        m00 = 1.0;
        m01 = 0.0;
        m10 = 0.0;
        m11 = 1.0;
        m02 = 0.0;
        m12 = 0.0;
    }

    void translate(double dx, double dy) {
        m02 = m02 + dx;
        m12 = m12 + dy;
    }

    void scale(double sx, double sy) {
        m00 = m00 * sx;
        m11 = m11 * sy;
    }

    void rotate(double radians) {
        double c = 0.0;
        double s = 0.0;
        /* cos/sin via small-angle + identity fallback; Graphics.rotate uses native. */
        c = 1.0 - (radians * radians) / 2.0;
        s = radians;
        double n00 = m00 * c + m01 * s;
        double n01 = m00 * (0.0 - s) + m01 * c;
        double n10 = m10 * c + m11 * s;
        double n11 = m10 * (0.0 - s) + m11 * c;
        m00 = n00;
        m01 = n01;
        m10 = n10;
        m11 = n11;
    }
}

class CanvasPaintHook implements UiHook {
    Canvas canvas;

    CanvasPaintHook(Canvas c) {
        canvas = c;
    }

    void run() {
        canvas.fireCanvasPaint();
    }
}

class CanvasTimerHook implements UiHook {
    Canvas canvas;

    CanvasTimerHook(Canvas c) {
        canvas = c;
    }

    void run() {
        canvas.fireCanvasFrame();
    }
}

class UiTimerHook implements UiHook {
    UiTimer timer;

    UiTimerHook(UiTimer t) {
        timer = t;
    }

    void run() {
        timer.fireTimerTick();
    }
}

class Canvas extends Component {
    PaintHandler paintHandler;
    Image surface;
    int paintPending;
    int transparent;
    FrameHandler frameHandler;
    int lastSurfaceW;
    int lastSurfaceH;
    int hiDpi;
    int surfaceScale = 1;

    Canvas() {
        nativeId = UiNative.createControl(19, 0, "");
        UiHandleRegistry.put(nativeId, this);
        isCanvasPeer = 1;
        paintHook = new CanvasPaintHook(this);
        timerHook = new CanvasTimerHook(this);
        setPrefSize(400, 300);
        width = 400;
        height = 300;
        paintPending = 0;
        lastSurfaceW = 0;
        lastSurfaceH = 0;
        surface = null;
    }

    void onPaint(PaintHandler handler) {
        ensureAlive("Canvas.onPaint");
        paintHandler = handler;
    }

    /** Transparent canvases do not paint a white background, so sibling controls beneath show through. */
    void setTransparent(boolean on) {
        ensureAlive("Canvas.setTransparent");
        transparent = on ? 1 : 0;
        if (nativeId > 0) {
            UiNative.canvasSetTransparent(nativeId, transparent);
        }
        repaint();
    }

    /**
     * Back the canvas with a device-resolution surface (2x on Retina). Drawing code keeps using
     * logical coordinates; text and AA primitives become crisp.
     */
    void setHiDpi(boolean on) {
        ensureAlive("Canvas.setHiDpi");
        hiDpi = on ? 1 : 0;
        repaint();
    }

    void refreshSurfaceScale() {
        int sc = 1;
        if (hiDpi != 0 && nativeId > 0) {
            int pct = UiNative.getScalePercent(nativeId);
            if (pct >= 150) {
                sc = 2;
            }
        }
        if (sc != surfaceScale) {
            surfaceScale = sc;
            if (surface != null) {
                surface.dispose();
                surface = null;
            }
            lastSurfaceW = 0;
            lastSurfaceH = 0;
        }
    }

    void onFrame(FrameHandler handler) {
        ensureAlive("Canvas.onFrame");
        frameHandler = handler;
    }

    void repaint() {
        ensureAlive("Canvas.repaint");
        if (paintPending != 0) {
            return;
        }
        paintPending = 1;
        if (nativeId > 0) {
            UiNative.canvasInvalidate(nativeId);
        } else {
            paintPending = 0;
            doPaint();
        }
    }

    void fireCanvasPaint() {
        paintPending = 0;
        doPaint();
    }

    void fireCanvasFrame() {
        if (frameHandler != null) {
            frameHandler.onFrame(0.016);
        }
    }

    void setBounds(int nx, int ny, int nw, int nh) {
        ensureAlive("Canvas.setBounds");
        x = nx;
        y = ny;
        width = nw;
        height = nh;
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
        if (nw * surfaceScale != lastSurfaceW || nh * surfaceScale != lastSurfaceH) {
            if (surface != null) {
                surface.dispose();
                surface = null;
            }
            lastSurfaceW = 0;
            lastSurfaceH = 0;
            repaint();
        }
    }

    void doPaint() {
        if (disposed != 0) {
            return;
        }
        refreshSurfaceScale();
        int w = width * surfaceScale;
        int h = height * surfaceScale;
        if (w < 1) {
            w = 1;
        }
        if (h < 1) {
            h = 1;
        }
        if (surface == null || lastSurfaceW != w || lastSurfaceH != h) {
            if (surface != null) {
                surface.dispose();
            }
            surface = Image.create(w, h);
            lastSurfaceW = w;
            lastSurfaceH = h;
        }
        Graphics g = surface.beginGraphics();
        if (transparent != 0) {
            g.clear(new Color(0, 0, 0, 0));
        } else {
            g.clear(Color.WHITE);
        }
        if (surfaceScale != 1) {
            g.scale(surfaceScale, surfaceScale);
        }
        if (paintHandler != null) {
            paintHandler.paint(g);
        }
        g.end();
        if (nativeId > 0) {
            UiNative.canvasPresent(nativeId, surface.surfaceId);
        }
    }

    Image snapshot() {
        if (surface == null) {
            doPaint();
        }
        return surface;
    }

    void dispose() {
        if (surface != null) {
            surface.dispose();
            surface = null;
        }
        paintHandler = null;
        mouseDownHandler = null;
        mouseUpHandler = null;
        mouseMoveHandler = null;
        mouseDragHandler = null;
        frameHandler = null;
        Application.checkUiThread("Canvas.dispose");
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        if (nativeId > 0) {
            UiHandleRegistry.remove(nativeId);
            UiNative.destroy(nativeId);
            nativeId = 0;
        }
    }
}

class UiTimer extends Component {
    Runnable tickHandler;
    int timerId;
    int intervalMs;

    UiTimer(int ms, Runnable handler) {
        intervalMs = ms;
        tickHandler = handler;
        nativeId = UiNative.createControl(5, 0, "timer");
        UiHandleRegistry.put(nativeId, this);
        timerHook = new UiTimerHook(this);
        timerId = UiNative.timerStart(intervalMs, nativeId);
    }

    void fireTimerTick() {
        if (tickHandler != null) {
            tickHandler.run();
        }
    }

    void stop() {
        if (timerId > 0) {
            UiNative.timerStop(timerId);
            timerId = 0;
        }
    }

    void dispose() {
        stop();
        Application.checkUiThread("UiTimer.dispose");
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        tickHandler = null;
        if (nativeId > 0) {
            UiHandleRegistry.remove(nativeId);
            UiNative.destroy(nativeId);
            nativeId = 0;
        }
    }
}

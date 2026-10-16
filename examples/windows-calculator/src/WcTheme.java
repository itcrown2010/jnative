/**
 * Colour tokens for the Windows 11 Calculator look (light / dark).
 * Values follow the in-box app: Mica-like window, lighter number keys, accent "=".
 *
 * @author 张晓达
 */
class WcTheme {
    int dark;
    int bg;
    int panel;
    int panelHover;
    int keyNum;
    int keyFunc;
    int keyAccent;
    int keyToggleOn;
    int text;
    int textSec;
    int textOnAccent;
    int accent;
    int sep;

    WcTheme() {
        apply(0);
    }

    static int rgb(int r, int g, int b) {
        return Component.rgba(r, g, b, 255);
    }

    void apply(int isDark) {
        dark = isDark;
        if (isDark != 0) {
            bg = rgb(32, 32, 32);
            panel = rgb(43, 43, 43);
            panelHover = rgb(58, 58, 58);
            keyNum = rgb(59, 59, 59);
            keyFunc = rgb(50, 50, 50);
            keyAccent = rgb(76, 194, 255);
            keyToggleOn = rgb(76, 194, 255);
            text = rgb(255, 255, 255);
            textSec = rgb(197, 197, 197);
            textOnAccent = rgb(0, 0, 0);
            accent = rgb(76, 194, 255);
            sep = rgb(64, 64, 64);
        } else {
            bg = rgb(243, 243, 243);
            panel = rgb(249, 249, 249);
            panelHover = rgb(235, 235, 235);
            keyNum = rgb(255, 255, 255);
            keyFunc = rgb(246, 246, 246);
            keyAccent = rgb(0, 103, 192);
            keyToggleOn = rgb(0, 103, 192);
            text = rgb(26, 26, 26);
            textSec = rgb(91, 91, 91);
            textOnAccent = rgb(255, 255, 255);
            accent = rgb(0, 103, 192);
            sep = rgb(224, 224, 224);
        }
    }
}

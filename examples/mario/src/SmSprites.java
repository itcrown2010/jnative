import jnative.graphics.*;

/**
 * Original pixel art for the game, authored as palette strings and baked into scaled images
 * once at start-up. Every sprite is drawn 1:1 on the canvas afterwards, so the result is crisp
 * (no resampling) at any integer scale.
 *
 * @author 张晓达
 */
public class SmSprites {
    static int SCALE = 3;
    static int[] palette = new int[128];

    // small hero (facing right) 0 stand 1 walk-a 2 walk-b 3 jump 4 skid 5 dead
    static Image[] heroR = new Image[6];
    static Image[] heroL = new Image[6];
    // big hero (facing right) 0 stand 1 walk-a 2 walk-b 3 jump 4 skid 5 crouch
    static Image[] bigR = new Image[6];
    static Image[] bigL = new Image[6];

    static Image[] goomba = new Image[2];
    static Image goombaFlat;
    static Image goombaDead;
    static Image koopaDead;
    static Image[] koopaL = new Image[2];
    static Image[] koopaR = new Image[2];
    static Image shell;
    static Image mushroom;
    static Image[] coin = new Image[4];

    static Image ground;
    static Image brick;
    static Image[] question = new Image[3];
    static Image usedBlock;
    static Image solid;
    static Image pipeTL;
    static Image pipeTR;
    static Image pipeBL;
    static Image pipeBR;
    static Image poleRod;
    static Image poleBall;
    static Image flagCloth;
    static Image cloud;
    static Image bush;
    static Image hill;
    static Image castle;
    static Image debris;
    static Image title;

    // fire hero (big only), flower, fireball, spark, underground tiles
    static Image[] bigFireR = new Image[6];
    static Image[] bigFireL = new Image[6];
    static Image[] flower = new Image[2];
    static Image[] fireball = new Image[4];
    static Image spark;
    static Image[] dust = new Image[2];
    static Image starItem;
    static Image[][] starSmallR = new Image[2][6];
    static Image[][] starSmallL = new Image[2][6];
    static Image[][] starBigR = new Image[2][6];
    static Image[][] starBigL = new Image[2][6];
    static Image groundU;
    static Image brickU;
    static Image solidU;

    static void setP(char c, int r, int g, int b) {
        palette[c] = GfxNative.packRgba(r, g, b, 255);
    }

    static void initPalette() {
        int i = 0;
        while (i < 128) {
            palette[i] = 0;
            i = i + 1;
        }
        setP('k', 24, 16, 16);      // outline / hair
        setP('t', 0, 168, 152);     // teal cap + shirt
        setP('T', 0, 112, 104);     // dark teal
        setP('s', 252, 188, 120);   // skin
        setP('S', 200, 132, 72);    // skin shade
        setP('o', 232, 120, 24);    // orange overalls
        setP('O', 168, 72, 8);      // dark orange
        setP('y', 252, 224, 56);    // yellow
        setP('Y', 200, 160, 0);     // dark yellow
        setP('n', 112, 56, 16);     // brown
        setP('N', 72, 32, 8);       // dark brown
        setP('c', 252, 220, 168);   // cream
        setP('w', 255, 255, 255);   // white
        setP('r', 220, 40, 40);     // red
        setP('R', 140, 16, 16);     // dark red
        setP('g', 56, 184, 56);     // green
        setP('G', 16, 104, 24);     // dark green
        setP('l', 188, 188, 200);   // light grey
        setP('d', 100, 100, 112);   // dark grey
        setP('b', 92, 148, 252);    // sky blue
        setP('p', 255, 150, 190);   // pink
        setP('e', 232, 160, 80);    // light brick edge
        setP('m', 160, 80, 40);     // brick mortar dark
        setP('a', 252, 252, 200);   // pale yellow
        setP('u', 24, 88, 200);     // underground blue
        setP('U', 8, 40, 120);      // dark blue
        setP('v', 96, 160, 248);    // light blue
    }

    static Image make(String[] rows, int flip) {
        int h = rows.length;
        int w = rows[0].length();
        Image img = Image.create(w * SCALE, h * SCALE);
        int sid = img.surfaceId;
        int y = 0;
        while (y < h) {
            int srcY = y;
            if ((flip & 2) != 0) {
                srcY = h - 1 - y;
            }
            String row = rows[srcY];
            int x = 0;
            while (x < w) {
                int sx = x;
                if ((flip & 1) != 0) {
                    sx = w - 1 - x;
                }
                char ch = row.charAt(sx);
                int rgba = 0;
                if (ch < 128) {
                    rgba = palette[ch];
                }
                if (rgba != 0) {
                    int dy = 0;
                    while (dy < SCALE) {
                        int dx = 0;
                        while (dx < SCALE) {
                            GfxNative.setPixel(sid, x * SCALE + dx, y * SCALE + dy, rgba);
                            dx = dx + 1;
                        }
                        dy = dy + 1;
                    }
                }
                x = x + 1;
            }
            y = y + 1;
        }
        return img;
    }

    /** Replace every occurrence of a palette character (for the question-block shimmer). */
    static String[] recolor(String[] rows, char from, char to) {
        String[] out = new String[rows.length];
        int i = 0;
        while (i < rows.length) {
            out[i] = rows[i].replace(String.valueOf(from), String.valueOf(to));
            i = i + 1;
        }
        return out;
    }

    /** Stretch a 16x16 hero into a 16x32 big hero: taller head/face, torso x3, legs x2. */
    static String[] stretchBig(String[] s) {
        String[] out = new String[32];
        int n = 0;
        int i = 0;
        while (i < 7) {
            out[n] = s[i];
            n = n + 1;
            if (i == 3 || i == 4) {
                out[n] = s[i];
                n = n + 1;
            }
            i = i + 1;
        }
        i = 7;
        while (i < 12) {
            int k = 0;
            while (k < 3) {
                out[n] = s[i];
                n = n + 1;
                k = k + 1;
            }
            i = i + 1;
        }
        i = 12;
        while (i < 16) {
            int k = 0;
            while (k < 2) {
                if (n < 32) {
                    out[n] = s[i];
                    n = n + 1;
                }
                k = k + 1;
            }
            i = i + 1;
        }
        while (n < 32) {
            out[n] = s[15];
            n = n + 1;
        }
        return out;
    }

    static String[] heroFrame(String[] top, String[] legs) {
        String[] out = new String[16];
        int i = 0;
        while (i < 12) {
            out[i] = top[i];
            i = i + 1;
        }
        while (i < 16) {
            out[i] = legs[i - 12];
            i = i + 1;
        }
        return out;
    }

    static void buildHero() {
        String[] top = new String[]{
            "................",
            "....tttttt......",
            "...tttttttttt...",
            "...kkkssksss....",
            "..kskssskssss...",
            "..kskkssskssss..",
            "..kkssssskkkk...",
            "....sssssss.....",
            "...tttotttt.....",
            "..ttttottotttt..",
            ".sstttoyyotttss.",
            ".ss..oooooo.ss.."
        };
        String[] legsStand = new String[]{
            "....oooooooo....",
            "...ooo....ooo...",
            "..nnn......nnn..",
            ".nnnn......nnnn."
        };
        String[] legsA = new String[]{
            "....oooooooo....",
            "...ooo..ooo.....",
            "..nnn....nnn....",
            ".nnnn.....nnn..."
        };
        String[] legsB = new String[]{
            "....oooooo......",
            ".....oooooo.....",
            "....nnnnnn......",
            "....nnnnnn......"
        };
        String[] topJump = new String[]{
            "................",
            "....tttttt..ss..",
            "...tttttttttss..",
            "...kkkssksss.s..",
            "..kskssskssss...",
            "..kskkssskssss..",
            "..kkssssskkkk...",
            "....sssssss.....",
            "..sttttottt.....",
            ".ssttttottotttt.",
            "..ss.toyyotttt..",
            "......oooooo...."
        };
        String[] legsJump = new String[]{
            "...ooooooooo....",
            "..nnnooo.ooonn..",
            ".nnnn.....nnnn..",
            "................"
        };
        String[] smalls = null;
        String[][] frames = new String[6][];
        frames[0] = heroFrame(top, legsStand);
        frames[1] = heroFrame(top, legsA);
        frames[2] = heroFrame(top, legsB);
        frames[3] = heroFrame(topJump, legsJump);
        frames[4] = heroFrame(top, legsB);
        frames[5] = heroFrame(topJump, legsJump);
        int i = 0;
        while (i < 6) {
            heroR[i] = make(frames[i], 0);
            heroL[i] = make(frames[i], 1);
            String[] big = stretchBig(frames[i]);
            if (i == 5) {
                // crouch: squat sprite = lower 16 rows of the big stand frame
                String[] bs = stretchBig(frames[0]);
                String[] crouch = new String[32];
                int r = 0;
                while (r < 32) {
                    if (r < 14) {
                        crouch[r] = "................";
                    } else {
                        crouch[r] = bs[r - 14 + 4];
                    }
                    r = r + 1;
                }
                big = crouch;
            }
            bigR[i] = make(big, 0);
            bigL[i] = make(big, 1);
            String[] fire = recolor(recolor(big, 't', 'w'), 'o', 'r');
            bigFireR[i] = make(fire, 0);
            bigFireL[i] = make(fire, 1);
            int v = 0;
            while (v < 2) {
                char ct = 'y';
                char co = 'g';
                if (v == 1) {
                    ct = 'r';
                    co = 'y';
                }
                String[] sm = recolor(recolor(frames[i], 't', ct), 'o', co);
                String[] bg = recolor(recolor(big, 't', ct), 'o', co);
                starSmallR[v][i] = make(sm, 0);
                starSmallL[v][i] = make(sm, 1);
                starBigR[v][i] = make(bg, 0);
                starBigL[v][i] = make(bg, 1);
                v = v + 1;
            }
            i = i + 1;
        }
    }

    static void buildEnemies() {
        String[] g0 = new String[]{
            "................",
            "......nnnn......",
            "....nnnnnnnn....",
            "...nnnnnnnnnn...",
            "..nnnnnnnnnnnn..",
            "..nwwnnnnnnwwn..",
            ".nnwkwnnnnwkwnn.",
            ".nnwwwnnnnwwwnn.",
            ".nnnnnnnnnnnnnn.",
            "..nnnnccccnnnn..",
            "...nnccccccnn...",
            "....cccccccc....",
            "....cccccccc....",
            "...kkkk..kkkk...",
            "..kkkkk..kkkkk..",
            "................"
        };
        String[] g1 = new String[]{
            "................",
            "......nnnn......",
            "....nnnnnnnn....",
            "...nnnnnnnnnn...",
            "..nnnnnnnnnnnn..",
            "..nwwnnnnnnwwn..",
            ".nnwkwnnnnwkwnn.",
            ".nnwwwnnnnwwwnn.",
            ".nnnnnnnnnnnnnn.",
            "..nnnnccccnnnn..",
            "...nnccccccnn...",
            "....cccccccc....",
            "....cccccccc....",
            "..kkkkk..kkkk...",
            "..kkkk..kkkkk...",
            "................"
        };
        goomba[0] = make(g0, 0);
        goombaDead = make(g0, 2);
        goomba[1] = make(g1, 0);
        goombaFlat = make(new String[]{
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "....nnnnnnnn....",
            "..nnnwwnnwwnnn..",
            ".nnnnkwnnwknnnn.",
            ".nnnnnnnnnnnnnn.",
            "..kkkkkkkkkkkk..",
            "................"
        }, 0);
        String[] k0 = new String[]{
            ".......gggg.....",
            "......gggggg....",
            "......gwkgggg...",
            "......gwwgggg...",
            ".......ggyyy....",
            "......GGGGyy....",
            ".....GgggGGy....",
            "....GgyygggG....",
            "....GgyyyggG....",
            "....GggyygGG....",
            ".....GGGGGG.....",
            "....yyyyyyyy....",
            "...yyy....yyy...",
            "...yy......yy...",
            "..kkk......kkk..",
            "..kkkk....kkkk.."
        };
        String[] k1 = new String[]{
            ".......gggg.....",
            "......gggggg....",
            "......gwkgggg...",
            "......gwwgggg...",
            ".......ggyyy....",
            "......GGGGyy....",
            ".....GgggGGy....",
            "....GgyygggG....",
            "....GgyyyggG....",
            "....GggyygGG....",
            ".....GGGGGG.....",
            ".....yyyyyy.....",
            "......yyyy......",
            "......yyyy......",
            "......kkkk......",
            ".....kkkkkk....."
        };
        koopaDead = make(k0, 2);
        koopaR[0] = make(k0, 0);
        koopaR[1] = make(k1, 0);
        koopaL[0] = make(k0, 1);
        koopaL[1] = make(k1, 1);
        shell = make(new String[]{
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            ".....GGGGGG.....",
            "....GgggggGG....",
            "...GggyyyyggG...",
            "...GgyyyyyygG...",
            "...GggyyyyggG...",
            "...yyGGGGGGyy...",
            "....yyyyyyyy....",
            "................"
        }, 0);
        mushroom = make(new String[]{
            "................",
            "................",
            ".....rrrrrr.....",
            "...rrwwrrrrrr...",
            "..rrwwwwrrwwrr..",
            "..rrwwwwrrwwrr..",
            ".rrrrwwrrrrrrrr.",
            ".rrrrrrrrrwwrrr.",
            ".rrrrrrrrrwwrrr.",
            "..rrrrrrrrrrrr..",
            "....ccccccc.....",
            "....cckccckc....",
            "....ccccccc.....",
            "....ccccccc.....",
            ".....ccccc......",
            "................"
        }, 0);
    }

    static void buildExtras() {
        String[] f = new String[]{
            "................",
            ".....rr..rr.....",
            "....rrrrrrrr....",
            "....rryyyyrr....",
            "...rryyyyyyrr...",
            "....rryyyyrr....",
            "....rrrrrrrr....",
            ".....rr..rr.....",
            "........g.......",
            "...g....g...g...",
            "....g...g..g....",
            ".....gg.g.gg....",
            "......ggggg.....",
            ".......gg.......",
            ".......gg.......",
            ".......gg......."
        };
        flower[0] = make(f, 0);
        flower[1] = make(recolor(recolor(f, 'r', 'o'), 'y', 'w'), 0);
        String[] fb = new String[]{
            "..oooo..",
            ".oyyyyo.",
            "oyywwyyo",
            "oywwwwyo",
            "oywwwwyo",
            "oyywwyyo",
            ".oyyyyo.",
            "..oooo.."
        };
        fireball[0] = make(fb, 0);
        fireball[1] = make(recolor(fb, 'o', 'r'), 0);
        fireball[2] = make(recolor(recolor(fb, 'y', 'o'), 'o', 'r'), 1);
        fireball[3] = make(recolor(fb, 'y', 'a'), 2);
        starItem = make(new String[]{
            "................",
            ".......kk.......",
            "......kyyk......",
            "......kyyk......",
            ".....kyyyyk.....",
            "kkkkkkyyyykkkkkk",
            "kyyyyyyyyyyyyyyk",
            ".kyyyyyyyyyyyyk.",
            "..kyykyyyykyyk..",
            "...kyyyyyyyyk...",
            "...kyyyyyyyyk...",
            "..kyyyykkyyyyk..",
            "..kyyykkkkyyyk..",
            ".kyyyk....kyyyk.",
            ".kyyk......kyyk.",
            "..kk........kk.."
        }, 0);
        dust[0] = make(new String[]{
            "..llll..",
            ".lwwwwl.",
            "lwwwwwwl",
            "lwwwwwwl",
            "lwwwwwwl",
            "lwwwwwwl",
            ".lwwwwl.",
            "..llll.."
        }, 0);
        dust[1] = make(new String[]{
            "........",
            "........",
            "..llll..",
            ".lwwwwl.",
            ".lwwwwl.",
            "..llll..",
            "........",
            "........"
        }, 0);
        spark = make(new String[]{
            "y..yy..y",
            ".y.oo.y.",
            "..yooy..",
            "yoo..ooy",
            "yoo..ooy",
            "..yooy..",
            ".y.oo.y.",
            "y..yy..y"
        }, 0);
        groundU = make(recolor(recolor(recolor(new String[]{
            "nnnnnnnnnnnnnnnn",
            "neeeeeeeeeeeeeeN",
            "neNnnnnnnnnnnnNN",
            "neNnnnnNnnnnnnNN",
            "nnNnnnnNnnnnnnNn",
            "nnNNNNNNnnnnnnNn",
            "nnnnnnnnnnnnnnnn",
            "NNNNNNNNNNNNNNNN",
            "nnnnnnnNnnnnnnnn",
            "nnnnnnnNnnnnnnnn",
            "nnnnnnnNnnnnnnnn",
            "NNNNNNNNNNNNNNNN",
            "nnnnnnnnnnnnnnnn",
            "nnnnnnnnnnnnnnnn",
            "nNnnnnnnnnnnNnnn",
            "NNNNNNNNNNNNNNNN"
        }, 'n', 'u'), 'N', 'U'), 'e', 'v'), 0);
        brickU = make(recolor(recolor(recolor(new String[]{
            "eeeeeeeeeeeeeeem",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "mmmmmmmmmmmmmmmm",
            "ooomoooooooomooo",
            "ooomoooooooomooo",
            "ooomoooooooomooo",
            "mmmmmmmmmmmmmmmm",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "mmmmmmmmmmmmmmmm",
            "ooomoooooooomooo",
            "ooomoooooooomooo",
            "mmmmmmmmmmmmmmmm"
        }, 'o', 'u'), 'm', 'U'), 'e', 'v'), 0);
        solidU = make(recolor(recolor(new String[]{
            "llllllllllllllld",
            "lllllllllllllldd",
            "llddddddddddlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddddddddddlldd",
            "lllllllllllllldd",
            "llllllllllllllld",
            "dddddddddddddddd",
            "dddddddddddddddd"
        }, 'l', 'v'), 'd', 'U'), 0);
    }

    static void buildCoins() {
        coin[0] = make(new String[]{
            "................",
            "......yyyy......",
            ".....yyaayy.....",
            ".....yaaYyy.....",
            "....yyaYYyyy....",
            "....yyaYYyyy....",
            "....yyaYYyyy....",
            "....yyaYYyyy....",
            "....yyaYYyyy....",
            "....yyaYYyyy....",
            ".....yyaYyy.....",
            ".....yyYYyy.....",
            "......yyyy......",
            "................",
            "................",
            "................"
        }, 0);
        coin[1] = make(new String[]{
            "................",
            "......yyyy......",
            ".....yyaayy.....",
            ".....yaaYyy.....",
            ".....yaYYyy.....",
            ".....yaYYyy.....",
            ".....yaYYyy.....",
            ".....yaYYyy.....",
            ".....yaYYyy.....",
            ".....yaYYyy.....",
            ".....yyaYyy.....",
            ".....yyYYyy.....",
            "......yyyy......",
            "................",
            "................",
            "................"
        }, 0);
        coin[2] = make(new String[]{
            "................",
            "......yyyy......",
            "......yaYy......",
            "......yaYy......",
            "......yaYy......",
            "......yaYy......",
            "......yaYy......",
            "......yaYy......",
            "......yaYy......",
            "......yaYy......",
            "......yaYy......",
            "......yYYy......",
            "......yyyy......",
            "................",
            "................",
            "................"
        }, 0);
        coin[3] = coin[1];
    }

    static void buildTiles() {
        ground = make(new String[]{
            "nnnnnnnnnnnnnnnn",
            "neeeeeeeeeeeeeeN",
            "neNnnnnnnnnnnnNN",
            "neNnnnnNnnnnnnNN",
            "nnNnnnnNnnnnnnNn",
            "nnNNNNNNnnnnnnNn",
            "nnnnnnnnnnnnnnnn",
            "NNNNNNNNNNNNNNNN",
            "nnnnnnnNnnnnnnnn",
            "nnnnnnnNnnnnnnnn",
            "nnnnnnnNnnnnnnnn",
            "NNNNNNNNNNNNNNNN",
            "nnnnnnnnnnnnnnnn",
            "nnnnnnnnnnnnnnnn",
            "nNnnnnnnnnnnNnnn",
            "NNNNNNNNNNNNNNNN"
        }, 0);
        String[] br = new String[]{
            "eeeeeeeeeeeeeeem",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "mmmmmmmmmmmmmmmm",
            "ooomoooooooomooo",
            "ooomoooooooomooo",
            "ooomoooooooomooo",
            "mmmmmmmmmmmmmmmm",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "ooooooomooooooom",
            "mmmmmmmmmmmmmmmm",
            "ooomoooooooomooo",
            "ooomoooooooomooo",
            "mmmmmmmmmmmmmmmm"
        };
        brick = make(br, 0);
        String[] q = new String[]{
            "kkkkkkkkkkkkkkkk",
            "kyyyyyyyyyyyyyYk",
            "kyaaaaaaaaaaaaYk",
            "kyaYkkkkkkkYaaYk",
            "kyaYkyyyyykYaaYk",
            "kyaYYYYYYykYaaYk",
            "kyaaaaaYykYYaaYk",
            "kyaaaaYykYaaaaYk",
            "kyaaaaYykYaaaaYk",
            "kyaaaaaYYYaaaaYk",
            "kyaaaaYykYaaaaYk",
            "kyaaaaYykYaaaaYk",
            "kyaaaaaaaaaaaaYk",
            "kyaaaaaaaaaaaaYk",
            "kYYYYYYYYYYYYYYk",
            "kkkkkkkkkkkkkkkk"
        };
        question[0] = make(q, 0);
        question[1] = make(recolor(q, 'a', 'y'), 0);
        question[2] = make(recolor(q, 'a', 'Y'), 0);
        usedBlock = make(new String[]{
            "kkkkkkkkkkkkkkkk",
            "knnnnnnnnnnnnnNk",
            "knNnnnnnnnnnnNNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knnnnnnnnnnnnnNk",
            "knNNNNNNNNNNNNNk",
            "knNNNNNNNNNNNNNk",
            "kkkkkkkkkkkkkkkk"
        }, 0);
        solid = make(new String[]{
            "llllllllllllllld",
            "lllllllllllllldd",
            "llddddddddddlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddllllllldlldd",
            "llddddddddddlldd",
            "lllllllllllllldd",
            "llllllllllllllld",
            "dddddddddddddddd",
            "dddddddddddddddd"
        }, 0);
        pipeTL = make(new String[]{
            "kkkkkkkkkkkkkkkk",
            "kggwwggggggGGGGk",
            "kggwwgggggggGGGk",
            "kggwwgggggggGGGk",
            "kggwwgggggggGGGk",
            "kggwwgggggggGGGk",
            "kggwwgggggggGGGk",
            "kkkkkkkkkkkkkkkk",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk."
        }, 0);
        pipeTR = make(new String[]{
            "kkkkkkkkkkkkkkkk",
            "kggggggggggGGGGk",
            "kggggggggggGGGGk",
            "kggggggggggGGGGk",
            "kggggggggggGGGGk",
            "kggggggggggGGGGk",
            "kggggggggggGGGGk",
            "kkkkkkkkkkkkkkkk",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk."
        }, 0);
        pipeBL = make(new String[]{
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk.",
            "..kggwwgggggGGk."
        }, 0);
        pipeBR = make(new String[]{
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk.",
            ".kggggggggggGGk."
        }, 0);
        poleRod = make(new String[]{
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG......."
        }, 0);
        poleBall = make(new String[]{
            "................",
            "................",
            ".....gggg.......",
            "....gggggg......",
            "....gaggGg......",
            "....gggggg......",
            ".....gggG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG.......",
            "......gGG......."
        }, 0);
        flagCloth = make(new String[]{
            "................",
            "wwwwwwwwwwww....",
            ".wwwwwwwwwwww...",
            "..wwwtwwwwwwww..",
            "...wwtttwwwwwww.",
            "....wtttttwwww..",
            ".....wttttwww...",
            "......wttwww....",
            ".......wwww.....",
            "........ww......",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................"
        }, 0);
        debris = make(new String[]{
            "oooooooo",
            "oeeooemo",
            "ooooommo",
            "mmmmmmmm",
            "oooooooo",
            "oeeooemo",
            "ooooommo",
            "mmmmmmmm"
        }, 0);
    }

    static void buildScenery() {
        cloud = make(new String[]{
            "......wwwwww..........................",
            "....wwwwwwwwww.....wwwwww.............",
            "...wwwwwwwwwwww..wwwwwwwwww...........",
            "..wwwwwwwwwwwwwwwwwwwwwwwwww....wwww..",
            "..wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww",
            "..wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww",
            "...wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww.",
            "....wwwwwwwwwwwwwwwwwwwwwwwwwwwwwww...",
            ".....bbbwwwwwwwwwwwwwwwwwwwwwwwwbbb..."
        }, 0);
        String[] hill0 = new String[]{
            "..............GGGGGG..............",
            "............GGggggggGG............",
            "..........GGggggggggggGG..........",
            "........GGggggkggggkggggGG........",
            "......GGgggggkggggggkgggggGG......",
            "....GGggggggggggggggggggggggGG....",
            "..GGgggggggggggggggggggggggggggGG.",
            "GGgggggggggggggggggggggggggggggggGG"
        };
        hill = make(hill0, 0);
        bush = make(new String[]{
            "......GGGG....GGGGG.........",
            "....GGggggGGGGggggGG........",
            "...GggggggggggggggggG.......",
            "..GggggggggggggggggggGG.....",
            ".GgggggggggggggggggggggGG...",
            "GGggggggggggggggggggggggggGG"
        }, 0);
        castle = make(new String[]{
            "......k.k.k.k.......",
            "......kkkkkkk.......",
            "..k.k.kllllllk.k.k..",
            "..kkkkkllllllkkkkk..",
            "..kllllllllllllllk..",
            "..klddllllllllddlk..",
            "..klddllllllllddlk..",
            "..kllllllllllllllk..",
            "..kllllkkkkllllllk..",
            "..kllllkkkkllllllk..",
            "..kllllkkkkllllllk..",
            "..kllllkkkkllllllk.."
        }, 0);
    }

    static void init() {
        initPalette();
        buildHero();
        buildEnemies();
        buildCoins();
        buildExtras();
        buildTiles();
        buildScenery();
    }
}

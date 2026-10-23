/**
 * Tile map plus the level builder. Tile characters:
 * '.' air  '#' ground  'B' brick  '?' coin block  'M' power-up block  'S' starman block  'X' hard block
 * 'u' used block  'a' pipe top-left  'b' pipe top-right  'c' pipe body-left  'd' pipe body-right
 * 'F' flag pole  'f' pole top  'o' floating coin
 *
 * @author 张晓达
 */
public class SmLevel {
    static int TILE = 16;
    static int ROWS = 15;
    int cols;
    char[] tiles;
    /** Per-tile bump animation timers (frames left) for blocks hit from below. */
    int[] bump;
    int flagX;
    int endX;
    int startX;
    int startY;
    /** 0 overworld, 1 underground. */
    int theme;
    /** First column of the bonus room (-1 when the level has none). */
    int bonusStart = -1;
    /** Warp pipes: standing on pipe column warpSrc[i] and pressing down leads to warpDst[i]. */
    int[] warpSrc = new int[4];
    int[] warpDst = new int[4];
    int warpCount;

    // enemy spawns (tile coordinates, kind 0 goomba 1 koopa)
    int[] spawnX = new int[64];
    int[] spawnY = new int[64];
    int[] spawnKind = new int[64];
    int spawnCount;

    SmLevel(int columns) {
        cols = columns;
        tiles = new char[cols * ROWS];
        bump = new int[cols * ROWS];
        int i = 0;
        while (i < tiles.length) {
            tiles[i] = '.';
            i = i + 1;
        }
        spawnCount = 0;
    }

    char get(int tx, int ty) {
        if (tx < 0 || tx >= cols) {
            return 'X';
        }
        if (ty < 0 || ty >= ROWS) {
            return '.';
        }
        return tiles[ty * cols + tx];
    }

    void set(int tx, int ty, char c) {
        if (tx >= 0 && tx < cols && ty >= 0 && ty < ROWS) {
            tiles[ty * cols + tx] = c;
        }
    }

    static int isSolid(char c) {
        if (c == '.' || c == 'o' || c == 'F' || c == 'f') {
            return 0;
        }
        return 1;
    }

    int solidAt(int tx, int ty) {
        return isSolid(get(tx, ty));
    }

    void ground(int x0, int x1) {
        int x = x0;
        while (x <= x1) {
            set(x, 13, '#');
            set(x, 14, '#');
            x = x + 1;
        }
    }

    void row(int x0, int count, int y, char c) {
        int i = 0;
        while (i < count) {
            set(x0 + i, y, c);
            i = i + 1;
        }
    }

    /** Pipe whose top sits at row 13 - height. */
    void pipe(int x, int height) {
        int top = 13 - height;
        set(x, top, 'a');
        set(x + 1, top, 'b');
        int y = top + 1;
        while (y < 13) {
            set(x, y, 'c');
            set(x + 1, y, 'd');
            y = y + 1;
        }
    }

    /** Ascending staircase of hard blocks, n steps tall, rising to the right. */
    void stairsUp(int x, int n) {
        int i = 0;
        while (i < n) {
            int h = 0;
            while (h <= i) {
                set(x + i, 12 - h, 'X');
                h = h + 1;
            }
            i = i + 1;
        }
    }

    void stairsDown(int x, int n) {
        int i = 0;
        while (i < n) {
            int h = 0;
            while (h < n - i) {
                set(x + i, 12 - h, 'X');
                h = h + 1;
            }
            i = i + 1;
        }
    }

    void warp(int src, int dst) {
        if (warpCount < 4) {
            warpSrc[warpCount] = src;
            warpDst[warpCount] = dst;
            warpCount = warpCount + 1;
        }
    }

    /** Row of the top-left pipe tile in a column, or -1 when there is no pipe. */
    int pipeTop(int tx) {
        int y = 0;
        while (y < 13) {
            if (get(tx, y) == 'a') {
                return y;
            }
            y = y + 1;
        }
        return -1;
    }

    /** Coin room: 16 columns wide with its own exit pipe 12 columns in. */
    void bonusRoom(int r) {
        bonusStart = r;
        ground(r, r + 15);
        row(r, 16, 0, 'B');
        row(r, 16, 1, 'B');
        int y = 2;
        while (y < 13) {
            set(r, y, 'X');
            set(r + 15, y, 'X');
            y = y + 1;
        }
        row(r + 2, 8, 10, 'o');
        row(r + 2, 8, 8, 'o');
        row(r + 3, 6, 6, 'o');
        pipe(r + 12, 2);
    }

    void enemy(int tx, int ty, int kind) {
        if (spawnCount < 64) {
            spawnX[spawnCount] = tx;
            spawnY[spawnCount] = ty;
            spawnKind[spawnCount] = kind;
            spawnCount = spawnCount + 1;
        }
    }

    /** Level 1-1: original layout, classic pacing (pits, pipes, block clusters, stairs, flag). */
    static SmLevel level1() {
        SmLevel lv = new SmLevel(236);
        lv.startX = 3;
        lv.startY = 12;
        lv.ground(0, 68);
        lv.ground(71, 85);
        lv.ground(89, 152);
        lv.ground(155, 219);
        lv.bonusRoom(220);
        lv.warp(38, 222);
        lv.warp(232, 57);

        // opening blocks
        lv.set(16, 9, '?');
        lv.set(20, 9, 'B');
        lv.set(21, 9, 'M');
        lv.set(22, 9, 'B');
        lv.set(23, 9, '?');
        lv.set(24, 9, 'B');
        lv.set(22, 5, '?');

        lv.pipe(28, 2);
        lv.pipe(38, 3);
        lv.pipe(46, 4);
        lv.pipe(57, 4);

        lv.enemy(22, 12, 0);
        lv.enemy(41, 12, 0);
        lv.enemy(52, 12, 0);
        lv.enemy(53, 12, 0);

        // hidden coin stash row and the first big jump
        lv.row(64, 2, 9, 'B');
        lv.set(66, 9, 'M');
        lv.row(67, 2, 9, 'B');
        lv.row(70, 3, 5, 'B');

        lv.enemy(80, 12, 0);
        lv.enemy(82, 12, 0);

        // middle section
        lv.set(94, 9, '?');
        lv.row(100, 3, 9, 'B');
        lv.row(106, 1, 9, '?');
        lv.row(109, 1, 9, '?');
        lv.row(112, 1, 9, '?');
        lv.set(109, 5, 'M');
        lv.row(118, 1, 9, 'B');
        lv.row(121, 3, 5, 'B');
        lv.set(122, 5, 'S');
        lv.row(128, 2, 5, 'B');
        lv.set(128, 9, '?');
        lv.set(129, 9, '?');
        lv.row(130, 1, 9, 'B');
        lv.row(131, 1, 9, 'B');

        lv.enemy(96, 12, 0);
        lv.enemy(104, 12, 0);
        lv.enemy(111, 12, 1);
        lv.enemy(124, 12, 0);
        lv.enemy(126, 12, 0);
        lv.enemy(133, 12, 1);

        lv.stairsUp(134, 4);
        lv.stairsDown(140, 4);
        lv.stairsUp(148, 4);
        lv.stairsDown(155, 4);
        lv.row(150, 0, 0, '.');

        lv.pipe(163, 2);
        lv.pipe(179, 2);
        lv.row(168, 1, 9, 'B');
        lv.row(169, 3, 9, '?');
        lv.row(172, 1, 9, 'B');
        lv.enemy(166, 12, 0);
        lv.enemy(172, 12, 0);
        lv.enemy(174, 12, 0);
        lv.enemy(185, 12, 1);

        // coins above the last stretch
        lv.row(186, 5, 8, 'o');
        lv.row(10, 3, 6, 'o');
        lv.row(76, 4, 8, 'o');

        // final staircase and flag
        lv.stairsUp(190, 8);
        lv.set(198, 12, 'X');
        lv.flagX = 202;
        lv.set(202, 12, 'X');
        int y = 2;
        while (y < 12) {
            lv.set(202, y, 'F');
            y = y + 1;
        }
        lv.set(202, 1, 'f');
        lv.endX = 207 * 16;
        return lv;
    }

    static int LEVEL_COUNT = 2;

    static SmLevel forIndex(int index) {
        if (index == 1) {
            return level2();
        }
        return level1();
    }

    void finish(int x, int stepsBefore) {
        stairsUp(x - stepsBefore - 4, stepsBefore);
        set(x, 12, 'X');
        flagX = x;
        int y = 2;
        while (y < 12) {
            set(x, y, 'F');
            y = y + 1;
        }
        set(x, 1, 'f');
        endX = (x + 5) * 16;
    }

    /** Level 1-2: underground cave with a low ceiling, brick tunnel, fire flower and a long finale. */
    static SmLevel level2() {
        SmLevel lv = new SmLevel(224);
        lv.theme = 1;
        lv.startX = 3;
        lv.startY = 12;
        lv.ground(0, 60);
        lv.ground(64, 96);
        lv.ground(100, 207);
        lv.bonusRoom(208);
        lv.warp(70, 210);
        lv.warp(220, 78);
        // ceiling
        lv.row(0, 200, 0, 'B');
        lv.row(0, 200, 1, 'B');
        lv.row(0, 1, 2, 'B');

        // first block cluster, with the power-up
        lv.row(12, 4, 9, 'B');
        lv.set(13, 9, '?');
        lv.set(14, 9, 'M');
        lv.row(20, 8, 10, 'o');
        lv.enemy(22, 12, 0);
        lv.enemy(26, 12, 0);
        lv.enemy(31, 12, 0);
        lv.enemy(32, 12, 0);
        lv.enemy(37, 12, 1);

        // low brick tunnel
        lv.row(40, 12, 9, 'B');
        lv.row(40, 12, 8, 'B');
        lv.set(45, 9, '?');
        lv.set(49, 9, 'M');
        lv.enemy(43, 12, 0);
        lv.enemy(47, 12, 0);
        lv.enemy(50, 12, 0);
        lv.row(41, 9, 11, 'o');

        // first pit and a pipe pair
        lv.pipe(70, 2);
        lv.pipe(78, 3);
        lv.enemy(74, 12, 0);
        lv.enemy(75, 12, 0);
        lv.enemy(84, 12, 1);
        lv.stairsUp(88, 4);

        // a floating bridge of blocks above the second pit
        lv.row(96, 4, 9, 'B');
        lv.set(97, 9, '?');
        lv.set(98, 9, 'S');
        lv.row(94, 5, 7, 'o');

        // goomba stampede
        lv.enemy(104, 12, 0);
        lv.enemy(106, 12, 0);
        lv.enemy(108, 12, 0);
        lv.enemy(110, 12, 0);
        lv.enemy(115, 12, 1);
        lv.enemy(120, 12, 1);
        lv.row(112, 5, 9, 'B');
        lv.set(114, 9, '?');
        lv.set(114, 5, 'M');
        lv.row(126, 6, 6, 'B');
        lv.row(127, 4, 5, 'o');

        // wall maze: staggered pillars
        lv.row(134, 1, 12, 'X');
        lv.row(134, 1, 11, 'X');
        lv.row(138, 1, 12, 'X');
        lv.row(138, 1, 11, 'X');
        lv.row(138, 1, 10, 'X');
        lv.row(142, 1, 12, 'X');
        lv.row(142, 1, 11, 'X');
        lv.row(142, 1, 10, 'X');
        lv.row(142, 1, 9, 'X');
        lv.enemy(136, 12, 0);
        lv.enemy(140, 12, 0);
        lv.enemy(146, 12, 0);
        lv.enemy(147, 12, 1);
        lv.pipe(152, 2);
        lv.pipe(158, 3);
        lv.row(160, 5, 8, 'o');
        lv.enemy(163, 12, 0);
        lv.enemy(168, 12, 0);
        lv.finish(186, 8);
        return lv;
    }

    void startBump(int tx, int ty) {
        if (tx >= 0 && tx < cols && ty >= 0 && ty < ROWS) {
            bump[ty * cols + tx] = 8;
        }
    }

    void tickBumps() {
        int i = 0;
        while (i < bump.length) {
            if (bump[i] > 0) {
                bump[i] = bump[i] - 1;
            }
            i = i + 1;
        }
    }
}

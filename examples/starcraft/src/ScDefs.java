/**
 * Static game data: unit, building and resource types. Everything is table driven so new races
 * and units only need new rows.
 *
 * @author 张晓达
 */
public class ScDefs {
    // type ids
    static int WORKER = 0;
    static int RIFLEMAN = 1;
    static int HUB = 2;
    static int DEPOT = 3;
    static int BARRACKS = 4;
    static int REFINERY = 5;
    static int TURRET = 6;
    static int MINERAL = 7;
    static int GEYSER = 8;
    static int TYPES = 9;

    static String[] name = new String[16];
    static int[] hp = new int[16];
    static int[] armor = new int[16];
    static int[] cost = new int[16];
    static int[] gasCost = new int[16];
    static int[] supply = new int[16];
    static int[] provides = new int[16];
    static int[] buildTime = new int[16];
    static double[] speed = new double[16];
    static int[] range = new int[16];
    static int[] damage = new int[16];
    static int[] cooldown = new int[16];
    static int[] sight = new int[16];
    static int[] tw = new int[16];
    static int[] th = new int[16];
    static int[] radius = new int[16];
    static int[] building = new int[16];
    static int[] worker = new int[16];
    static int[] dropoff = new int[16];
    static int[] requires = new int[16];
    static int[] producer = new int[16];
    static char[] hotkey = new char[16];
    static int[] color = new int[16];

    static void unit(int id, String n, int h, int a, int c, int s, int bt, double sp, int rg, int dm, int cd, int sg, int rad, char hk) {
        name[id] = n;
        hp[id] = h;
        armor[id] = a;
        cost[id] = c;
        supply[id] = s;
        buildTime[id] = bt;
        speed[id] = sp;
        range[id] = rg;
        damage[id] = dm;
        cooldown[id] = cd;
        sight[id] = sg;
        radius[id] = rad;
        hotkey[id] = hk;
        building[id] = 0;
        requires[id] = -1;
    }

    static void structure(int id, String n, int h, int a, int c, int bt, int w, int hh, int sg, char hk) {
        name[id] = n;
        hp[id] = h;
        armor[id] = a;
        cost[id] = c;
        buildTime[id] = bt;
        tw[id] = w;
        th[id] = hh;
        sight[id] = sg;
        hotkey[id] = hk;
        building[id] = 1;
        requires[id] = -1;
    }

    static void init() {
        // Colonists (Terran-like) -- worker and infantry
        unit(0, "Worker", 60, 0, 50, 1, 288, 2.8, 20, 5, 22, 7, 7, 'W');
        worker[0] = 1;
        unit(1, "Rifleman", 40, 0, 50, 1, 360, 2.6, 128, 6, 15, 8, 7, 'R');
        producer[0] = 2;
        producer[1] = 4;
        // structures
        structure(2, "Command Hub", 1500, 1, 400, 1700, 4, 3, 10, 'C');
        provides[2] = 10;
        dropoff[2] = 1;
        structure(3, "Supply Depot", 400, 1, 100, 600, 3, 2, 8, 'D');
        provides[3] = 8;
        structure(4, "Barracks", 1000, 1, 150, 1200, 4, 3, 9, 'B');
        requires[4] = 2;
        structure(5, "Refinery", 750, 1, 75, 600, 3, 2, 8, 'R');
        structure(6, "Gun Turret", 200, 0, 100, 450, 2, 2, 9, 'T');
        requires[6] = 3;
        range[6] = 224;
        damage[6] = 12;
        cooldown[6] = 15;
        // resources
        name[7] = "Mineral Field";
        hp[7] = 1;
        tw[7] = 2;
        th[7] = 1;
        building[7] = 2;
        sight[7] = 0;
        name[8] = "Vespene Geyser";
        hp[8] = 1;
        tw[8] = 3;
        th[8] = 2;
        building[8] = 2;
        sight[8] = 0;
    }

    static int isCombat(int type) {
        if (damage[type] > 0 && building[type] != 2) {
            return 1;
        }
        return 0;
    }
}

/**
 * Per-player economy and statistics.
 *
 * @author 张晓达
 */
public class ScPlayer {
    int id;
    String name;
    int race;
    int minerals = 50;
    int gas;
    int supplyUsed;
    int supplyCap;
    int alive = 1;
    int isAI;
    // statistics
    int unitsBuilt;
    int unitsLost;
    int kills;
    int buildingsBuilt;
    int mined;
    int gasMined;
    String message = "";
    long messageTick = -1000;

    ScPlayer(int i, String n) {
        id = i;
        name = n;
    }
}

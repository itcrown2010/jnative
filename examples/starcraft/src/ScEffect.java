/**
 * Short lived visual effect (shot tracer, explosion). Not part of the simulation state.
 *
 * @author 张晓达
 */
public class ScEffect {
    int kind;
    double x0;
    double y0;
    double x1;
    double y1;
    int ttl;
    int maxTtl;
    int owner;

    ScEffect(int k, double ax, double ay, double bx, double by, int life, int o) {
        kind = k;
        x0 = ax;
        y0 = ay;
        x1 = bx;
        y1 = by;
        ttl = life;
        maxTtl = life;
        owner = o;
    }
}

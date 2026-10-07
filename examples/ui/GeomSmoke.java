import jnative.ui.*;

public class GeomSmoke {
    public static void main(String[] args) {
        Application.setHeadless(1);
        Application app = new Application();
        Point p = new Point(1, 2);
        Rect r = new Rect(0, 0, 10, 10);
        System.out.println(r.contains(1, 1));
        System.out.println(Dpi.toDevice(100, 200));
        System.out.println(p.x);
    }
}

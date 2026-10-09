import jnative.ui.*;
/**
 * @author 张晓达
 */
public class PointOnly {
  public static void main(String[] args) {
    Application.setHeadless(1);
    Application app = new Application();
    Point p = new Point(1, 2);
    System.out.println(p.x);
  }
}

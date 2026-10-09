/**
 * @author 张晓达
 */
class Box {
    int value;
}

public class BoxIdentity {
    public static void main(String[] args) {
        Box a = new Box();
        Box b = a;
        b.value = 100;
        System.out.println(a.value);
        if (a == b) {
            System.out.println(1);
        }
    }
}

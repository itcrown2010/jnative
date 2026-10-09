/**
 * @author 张晓达
 */
class Data {
    int number;
    boolean active;
}

public class DataZeroInit {
    public static void main(String[] args) {
        Data d = new Data();
        System.out.println(d.number);
        System.out.println(d.active);
    }
}

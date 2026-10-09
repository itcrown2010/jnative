/**
 * App depends on acme/stats via jnative.toml — does not copy ItemStats.java.
 * @author 张晓达
 */
public class Main {
    public static void main(String[] args) {
        ItemStats stats = new ItemStats();
        stats.addValue(10);
        stats.addValue(20);
        stats.addValue(30);
        stats.countItem("苹果");
        stats.countItem("香蕉");
        stats.countItem("苹果");
        System.out.println(stats.valueSum());
        System.out.println(stats.valueCount());
        System.out.println(stats.itemCount("苹果"));
        System.out.println(stats.distinctItems());
    }
}

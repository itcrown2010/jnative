public class TSup {
    public static void main(String[] args) {
        try {
            throw new RuntimeException("x");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}

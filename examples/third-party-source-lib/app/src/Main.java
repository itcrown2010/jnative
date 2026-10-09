/**
 * @author 张晓达
 */
class Main {
    public static void main(String[] args) {
        System.out.println(MathUtils.clamp(15, 0, 10));
        System.out.println(MathUtils.clamp(-3, 0, 10));
        System.out.println(MathUtils.clamp(7, 0, 10));
        System.out.println(MathUtils.gcd(48, 18));
    }
}

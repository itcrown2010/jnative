/**
 * Critical async/await case: await 10, then add 5 → method Future must yield 15
 * (not the awaited Future's 10).
 */
class Box {
    int n;

    Box(int n) {
        this.n = n;
    }
}

public class AwaitAddFive {
    static int sideEffects;

    async Box ten() {
        return new Box(10);
    }

    async Box addFive() {
        sideEffects = sideEffects + 1;
        Box a = await ten();
        sideEffects = sideEffects + 1;
        return new Box(a.n + 5);
    }

    public static void main(String[] args) {
        AwaitAddFive p = new AwaitAddFive();
        Future<Box> f = p.addFive();
        int spins = 0;
        while (!Future.isComplete(f) && spins < 100000) {
            spins = spins + 1;
        }
        System.out.println(Future.isComplete(f) ? 1 : 0);
        Box v = (Box) Future.getCompleted(f);
        System.out.println(v.n);
        System.out.println(sideEffects);
    }
}

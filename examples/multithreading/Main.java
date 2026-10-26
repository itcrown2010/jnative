/**
 * Two workers with Mutex-protected counter.
 * @author 张晓达
 */
class Worker extends Thread {
    private int id;
    Worker(int id) {
        this.id = id;
    }
    public void run() {
        int i = 0;
        while (i < 1000) {
            Main.lock.lock();
            Main.counter = Main.counter + 1;
            Main.lock.unlock();
            i = i + 1;
        }
        System.out.println(id);
    }
}

public class Main {
    static Mutex lock = new Mutex();
    static int counter = 0;
    public static void main(String[] args) {
        Worker a = new Worker(1);
        Worker b = new Worker(2);
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println(counter);
    }
}

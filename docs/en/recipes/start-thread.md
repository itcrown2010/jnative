# Recipe: Start a thread

```java
class Worker extends Thread {
    public void run() {
        System.out.println("hi");
    }
}
public class Main {
    public static void main(String[] args) {
        Worker w = new Worker();
        w.start();
        w.join();
    }
}
```

Example: `examples/multithreading/`. Native only.

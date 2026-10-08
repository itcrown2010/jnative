/**
 * Unhandled exception stop target. Break on jnative_debugger_on_uncaught.
 */
public class ExceptionDebugDemo {
    static void deep(int n) {
        if (n <= 0) {
            throw new RuntimeException("调试未处理异常");
        }
        deep(n - 1);
    }

    public static void main(String[] args) {
        deep(3);
        System.out.println("unreachable");
    }
}

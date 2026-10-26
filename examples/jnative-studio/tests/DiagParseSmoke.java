/**
 * Headless smoke for CoreDiagParser (compiled with studio sources via single-file concat not required —
 * run via verify script; kept as reference input for future jnative test).
 * @author Zhang Xiaoda
 */
public class DiagParseSmoke {
    public static void main(String[] args) {
        String sample = "default: /tmp/Main.jn:3:5: error: undefined method 'x'\n";
        ArrayList<CoreDiag> ds = CoreDiagParser.parse(sample);
        if (ds.size() == 1 && ds.get(0).line == 3 && ds.get(0).column == 5) {
            System.out.println("diag-ok");
        } else {
            System.out.println("diag-fail");
        }
    }
}

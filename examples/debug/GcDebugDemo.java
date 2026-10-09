/**
 * GC-safe reference check: stop before and after an explicit collection.
 * Young-gen addresses may change; field values and graphs must remain valid.
 * @author 张晓达
 */
class GcBox {
    String label;
    int[] nums;
    GcBox peer;
}

public class GcDebugDemo {
    public static void main(String[] args) {
        GcBox box = new GcBox();
        box.label = "GC前中文";
        box.nums = new int[3];
        box.nums[0] = 7;
        box.nums[1] = 8;
        box.nums[2] = 9;
        box.peer = box;

        int beforeLen = box.label.length();
        int before0 = box.nums[0];
        int markerBefore = beforeLen + before0;
        System.gc();
        int markerAfter = 0;
        int afterLen = box.label.length();
        int after0 = box.nums[0];
        markerAfter = afterLen + after0;
        int ok = 0;
        if (beforeLen == afterLen && before0 == after0 && box.peer == box) {
            ok = 1;
        }
        System.out.println(ok);
        System.out.println(markerBefore);
        System.out.println(markerAfter);
    }
}

import jnative.io.*;

/**
 * Line diff for the editor gutter: compares the text of the last commit with the text in the editor and returns, for
 * every line of the editor, 0 (unchanged), 1 (added), 2 (modified) or 3 (lines were deleted just above this line).
 *
 * @author 张晓达
 */
public class IdeDiff {
    /** Above this many cells the exact LCS table is skipped and the changed middle block is marked as modified. */
    static int MAX_CELLS = 4000000;

    static ArrayList<int> marks(ArrayList<String> base, ArrayList<String> cur) {
        int n = cur.size();
        ArrayList<int> out = new ArrayList<int>();
        int i = 0;
        while (i < n) {
            out.add(0);
            i = i + 1;
        }
        int nb = base.size();
        int pre = 0;
        while (pre < nb && pre < n && base.get(pre).equals(cur.get(pre))) {
            pre = pre + 1;
        }
        int suf = 0;
        while (suf < nb - pre && suf < n - pre && base.get(nb - 1 - suf).equals(cur.get(n - 1 - suf))) {
            suf = suf + 1;
        }
        int a = nb - pre - suf;
        int b = n - pre - suf;
        if (a == 0 && b == 0) {
            return out;
        }
        if (a == 0) {
            markRange(out, pre, pre + b, 1);
            return out;
        }
        if (b == 0) {
            markDeleted(out, pre);
            return out;
        }
        if (a * b > MAX_CELLS) {
            markRange(out, pre, pre + b, 2);
            return out;
        }
        // classic LCS table over the differing middle block
        int w = b + 1;
        ArrayList<int> t = new ArrayList<int>();
        int total = (a + 1) * w;
        i = 0;
        while (i < total) {
            t.add(0);
            i = i + 1;
        }
        i = a - 1;
        while (i >= 0) {
            int j = b - 1;
            while (j >= 0) {
                if (base.get(pre + i).equals(cur.get(pre + j))) {
                    t.set(i * w + j, t.get((i + 1) * w + j + 1) + 1);
                } else {
                    int d = t.get((i + 1) * w + j);
                    int r = t.get(i * w + j + 1);
                    t.set(i * w + j, d > r ? d : r);
                }
                j = j - 1;
            }
            i = i - 1;
        }
        // walk the table; collect the gaps between matched lines
        int x = 0;
        int y = 0;
        int gapOld = 0;
        int gapStart = 0;
        while (x < a || y < b) {
            if (x < a && y < b && base.get(pre + x).equals(cur.get(pre + y))) {
                flushGap(out, pre + gapStart, pre + y, gapOld);
                x = x + 1;
                y = y + 1;
                gapOld = 0;
                gapStart = y;
            } else if (x < a && (y >= b || t.get((x + 1) * w + y) >= t.get(x * w + y + 1))) {
                gapOld = gapOld + 1;
                x = x + 1;
            } else {
                y = y + 1;
            }
        }
        flushGap(out, pre + gapStart, pre + y, gapOld);
        return out;
    }

    /** A gap holds new lines [from, to) and {@code removed} old lines: modified, added or a deletion marker. */
    static void flushGap(ArrayList<int> out, int from, int to, int removed) {
        if (to > from) {
            markRange(out, from, to, removed > 0 ? 2 : 1);
        } else if (removed > 0) {
            markDeleted(out, from);
        }
    }

    static void markRange(ArrayList<int> out, int from, int to, int kind) {
        int i = from;
        while (i < to && i < out.size()) {
            out.set(i, kind);
            i = i + 1;
        }
    }

    /** Lines vanished in front of {@code line}: flag that line (or the last one) unless it already has a mark. */
    static void markDeleted(ArrayList<int> out, int line) {
        if (out.size() == 0) {
            return;
        }
        int at = line < out.size() ? line : out.size() - 1;
        if (out.get(at) == 0) {
            out.set(at, 3);
        }
    }
}

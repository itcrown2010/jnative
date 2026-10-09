/**
 * Pure JNative source library: ArrayList&lt;Integer&gt; + HashMap&lt;String, Integer&gt;.
 * Consumed via package manager (.jnpkg), not JVM collections.
 */
public class ItemStats {
    ArrayList<Integer> values;
    HashMap<String, Integer> counts;

    public ItemStats() {
        this.values = new ArrayList<Integer>();
        this.counts = new HashMap<String, Integer>();
    }

    public void addValue(int v) {
        this.values.add(v);
    }

    public void countItem(String name) {
        Integer cur = this.counts.get(name);
        if (cur == null) {
            this.counts.put(name, 1);
        } else {
            this.counts.put(name, cur + 1);
        }
    }

    public int valueSum() {
        int sum = 0;
        for (Integer v : this.values) {
            sum = sum + v;
        }
        return sum;
    }

    public int valueCount() {
        return this.values.size();
    }

    public int itemCount(String name) {
        Integer cur = this.counts.get(name);
        if (cur == null) {
            return 0;
        }
        return cur;
    }

    public int distinctItems() {
        return this.counts.size();
    }
}

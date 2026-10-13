/**
 * Stable-id option for SearchableSelect. Same labels may share display text;
 * identity is {@link #id}. Author: 张晓达
 */
class SelectOption<T> {
    public String id;
    public String label;
    public T value;

    SelectOption(String id, String label, T value) {
        if (id == null) {
            id = "";
        }
        if (label == null) {
            label = "";
        }
        this.id = id;
        this.label = label;
        this.value = value;
    }
}

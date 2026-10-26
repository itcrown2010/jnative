/**
 * JsonObject put/stringify/parse round-trip (string fields).
 * Note: numeric JsonNumber formatting currently has a known gap (see capability audit).
 * @author 张晓达
 */
public class JsonDemo {
    public static void main(String[] args) {
        JsonObject o = new JsonObject();
        o.putString("hello", "jnative");
        o.putString("n", "42");
        String text = Json.stringify(o);
        System.out.println(text);
        JsonValue v = Json.parse(text);
        JsonObject back = (JsonObject) v;
        System.out.println(back.has("hello"));
    }
}

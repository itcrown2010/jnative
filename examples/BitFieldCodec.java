/**
 * Bit-field pack/unpack using language cast, ternary, bitwise, and shift syntax
 * (not the BitOps library). Hex literals are not used (unsupported).
 *
 * Layout (16-bit logical field in an int):
 *   bits 15..8  typeId
 *   bits 7..1   quantity
 *   bit  0      active flag
 * @author 张晓达
 */
public class BitFieldCodec {
    public static int pack(int typeId, int quantity, boolean active) {
        int t = (typeId & 255) << 8;
        int q = (quantity & 127) << 1;
        int f = active ? 1 : 0;
        return t | q | f;
    }

    public static int typeId(int packed) {
        return (packed >>> 8) & 255;
    }

    public static int quantity(int packed) {
        return (packed >> 1) & 127;
    }

    public static boolean active(int packed) {
        return (packed & 1) != 0;
    }

    public static void main(String[] args) {
        int word = pack(42, 100, true);
        System.out.println(typeId(word));
        System.out.println(quantity(word));
        System.out.println(active(word) ? 1 : 0);

        // Narrow / widen
        long wide = 4294967296L + 42L;
        int narrow = (int) wide;
        byte low = (byte) narrow;
        System.out.println(narrow);
        System.out.println((int) low);

        // Shift distance masking and complement
        System.out.println(1 << 33);
        System.out.println(~0);
    }
}

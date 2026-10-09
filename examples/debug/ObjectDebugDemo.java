/**
 * Object / String / array debug showcase for LLDB + VS Code.
 * Stop at the println(probe) line to inspect variables.
 * @author 张晓达
 */
class Animal {
    String name;
    int age;
}

class Dog extends Animal {
    boolean trained;
}

class Node {
    int value;
    Node next;
    Node self;
}

public class ObjectDebugDemo {
    public static void main(String[] args) {
        String hello = "你好，JNative";
        String empty = "";
        String missing = null;
        // >256 UTF-8 bytes so LLDB formatter truncates (maxStringBytes=256)
        String longText =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz"
                        + "中文超长字符串用于截断测试中文超长字符串用于截断测试"
                        + "012345678901234567890123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                        + "abcdefghijklmnopqrstuvwxyz0123456789"
                        + "PADDING_PADDING_PADDING_PADDING_PADDING_PADDING_PADDING";

        int[] nums = new int[5];
        nums[0] = 10;
        nums[1] = 20;
        nums[2] = 30;
        nums[3] = 40;
        nums[4] = 50;
        int[] emptyArr = new int[0];
        int[] nullArr = null;

        Dog dog = new Dog();
        dog.name = "旺财";
        dog.age = 3;
        dog.trained = true;
        Animal animal = dog;
        Dog nullDog = null;

        Node a = new Node();
        a.value = 1;
        a.self = a;
        Node b = new Node();
        b.value = 2;
        b.self = b;
        a.next = b;
        b.next = a;

        Dog[] pack = new Dog[2];
        pack[0] = dog;
        pack[1] = null;

        int probe = dog.age + nums[0] + hello.length();
        // KEEP_ALIVE: inspect hello, empty, missing, longText, nums, emptyArr,
        // nullArr, dog, animal, nullDog, a, b, pack, probe
        System.out.println(probe);
    }
}

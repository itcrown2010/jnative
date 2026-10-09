/**
 * @author 张晓达
 */
class Animal {
    void speak() {
        System.out.println(1);
    }
}

class Dog extends Animal {
    void speak() {
        System.out.println(2);
    }
}

public class DynamicDispatch {
    public static void main(String[] args) {
        Animal a = new Dog();
        a.speak();
    }
}

/**
 * @author 张晓达
 */
class Animal {
    int age;
}

class Dog extends Animal {
    int weight;
}

public class InheritedFields {
    public static void main(String[] args) {
        Dog d = new Dog();
        d.age = 5;
        d.weight = 20;
        System.out.println(d.age);
        System.out.println(d.weight);
    }
}

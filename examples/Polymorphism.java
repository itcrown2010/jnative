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

class Cat extends Animal {
    void speak() {
        System.out.println(3);
    }
}

public class Polymorphism {
    static void makeSpeak(Animal animal) {
        animal.speak();
    }

    public static void main(String[] args) {
        Dog dog = new Dog();
        Cat cat = new Cat();
        makeSpeak(dog);
        makeSpeak(cat);
    }
}

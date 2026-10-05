class Person {
    int age;

    void birthday() {
        age = age + 1;
    }
}

public class PersonMethod {
    public static void main(String[] args) {
        Person p = new Person();
        p.age = 45;
        p.birthday();
        System.out.println(p.age);
    }
}

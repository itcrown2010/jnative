/**
 * @author 张晓达
 */
class Person {
    int age;
}

public class PersonShared {
    public static void main(String[] args) {
        Person a = new Person();
        Person b = a;
        a.age = 10;
        b.age = 99;
        System.out.println(a.age);
    }
}

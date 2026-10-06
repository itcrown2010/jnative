class User {
    int id;

    User(int id) {
        this.id = id;
    }
}

class Demo {
    async User loadUser() {
        User u = await Demo.fetchUser();
        return u;
    }

    async User fetchUser() {
        return new User(42);
    }

    public static void main(String[] args) {
        Demo d = new Demo();
        // Future<User> f = d.loadUser(); // lowered return type
    }
}

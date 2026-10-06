class UserRepository {
    ArrayList<User> users;

    UserRepository() {
        users = new ArrayList<User>();
    }

    User findById(String id) {
        int i = 0;
        while (i < users.size()) {
            User u = users.get(i);
            if (u.id.equals(id)) {
                return u;
            }
            i = i + 1;
        }
        return null;
    }

    int count() {
        return users.size();
    }

    private int calculate() {
        return 42;
    }
}

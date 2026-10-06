@Rpc(path = "/rpc/users", version = 1)
interface UserApi {
    User getUser(String id);
    int countUsers();
}

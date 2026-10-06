@Rpc(path = "/rpc/s6", version = 1)
interface Service6 {
    Type6 get(String id);
    int count();
}

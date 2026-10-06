@Rpc(path = "/rpc/s2", version = 1)
interface Service2 {
    Type2 get(String id);
    int count();
}

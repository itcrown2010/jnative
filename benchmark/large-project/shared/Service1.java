@Rpc(path = "/rpc/s1", version = 1)
interface Service1 {
    Type1 get(String id);
    int count();
}

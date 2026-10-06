@Rpc(path = "/rpc/s7", version = 1)
interface Service7 {
    Type7 get(String id);
    int count();
}

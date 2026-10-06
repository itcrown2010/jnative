@Rpc(path = "/rpc/s14", version = 1)
interface Service14 {
    Type14 get(String id);
    int count();
}

@Rpc(path = "/rpc/s15", version = 1)
interface Service15 {
    Type15 get(String id);
    int count();
}

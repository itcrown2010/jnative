@Rpc(path = "/rpc/s12", version = 1)
interface Service12 {
    Type12 get(String id);
    int count();
}

/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s19", version = 1)
interface Service19 {
    Type19 get(String id);
    int count();
}

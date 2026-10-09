/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s5", version = 1)
interface Service5 {
    Type5 get(String id);
    int count();
}

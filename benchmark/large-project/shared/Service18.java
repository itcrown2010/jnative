/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s18", version = 1)
interface Service18 {
    Type18 get(String id);
    int count();
}

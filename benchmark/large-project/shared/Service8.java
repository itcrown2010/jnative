/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s8", version = 1)
interface Service8 {
    Type8 get(String id);
    int count();
}

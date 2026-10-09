/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s16", version = 1)
interface Service16 {
    Type16 get(String id);
    int count();
}

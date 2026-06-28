
/**
 * Created by chenxun.
 * Date: 2026/6/28 11:52
 * Description:
 */
public class RpcProviderTest {
    public static void main(String[] args) throws Exception {
        // 注册服务
        ServiceRegistry.registerService(HelloService.class.getName(), new HelloServiceImpl());
        // 启动服务端
        new RpcServer().start(8080);
    }
}
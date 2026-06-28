/**
 * Created by chenxun.
 * Date: 2026/6/28 12:17
 * Description:
 */
public class RpcConsumerTest {
    public static void main(String[] args) {
        // 获取远程服务代理
        HelloService helloService = RpcClient.createProxy(HelloService.class, "127.0.0.1", 8080);
        // 调用远程方法
        String result = helloService.sayHello("RPC用户");
        System.out.println("客户端接收结果：" + result);
    }
}
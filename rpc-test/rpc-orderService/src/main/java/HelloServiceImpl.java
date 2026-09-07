/**
 * Created by chenxun.
 * Date: 2026/6/28 11:50
 * Description:
 */
// 接口实现类
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "! 来自RPC服务端";
    }
}
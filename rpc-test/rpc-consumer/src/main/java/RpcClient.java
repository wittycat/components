import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;

/**
 * Created by chenxun.
 * Date: 2026/6/28 11:48
 * Description:
 */
// RPC客户端：生成远程服务代理
public class RpcClient {
    // 创建远程服务代理对象
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> interfaceClass, String host, int port) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new RemoteInvocationHandler(host, port)
        );
    }

    // 调用处理器：发起网络请求
    private static class RemoteInvocationHandler implements InvocationHandler {
        private final String host;
        private final int port;

        public RemoteInvocationHandler(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 1. 封装RPC请求
            RpcRequest request = new RpcRequest();
            request.setClassName(method.getDeclaringClass().getName());
            request.setMethodName(method.getName());
            request.setParamTypes(method.getParameterTypes());
            request.setParams(args);

            // 2. 发送网络请求
//            try (Socket socket = new Socket(host, port);
//                 OutputStream out = socket.getOutputStream();
//                 InputStream in = socket.getInputStream()) {
//
//                // 序列化并发送请求
//                byte[] requestData = SerializationUtil.serialize(request);
//                out.write(requestData);
//                out.flush();
//
//                // 接收并反序列化响应
//                byte[] responseData = new byte[in.available()];
//                in.read(responseData);
//                RpcResponse response = (RpcResponse) SerializationUtil.deserialize(responseData);
//
//                if (response.getException() != null) {
//                    throw response.getException();
//                }
//                return response.getResult();
//            }

            try (Socket socket = new Socket(host, port);
                 DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                System.out.println("连接到服务器 " + host + "" + port);


                byte[] requestData = SerializationUtil.serialize(request);
                dos.writeInt(requestData.length);
                dos.write(requestData);
                dos.flush();

                // 接收响应
                int len = dis.readInt();
                byte[] respData = new byte[len];
                dis.readFully(respData);
                RpcResponse response = (RpcResponse) SerializationUtil.deserialize(respData);
                System.out.println("服务器响应：" + response);
                return response.getResult();
            }
        }
    }
}

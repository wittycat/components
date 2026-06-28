import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by chenxun.
 * Date: 2026/6/28 11:41
 * Description:
 * <p>
 * 服务端处理器：监听端口，处理客户端RPC请求
 */
public class RpcServer {
    public void start(int port) throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("RPC服务端启动，监听端口：" + port);

        while (true) {
            //阻塞等待客户端连接
            Socket socket = serverSocket.accept();
            System.out.printf("新链接:" + socket.hashCode());
            /**
             * InputStream 是字节流的抽象，只提供原始字节读取，且不保证一次读完；而 DataInputStream 是其装饰器，
             * 提供了读取 Java 基本类型的便捷方法，
             * 并保证 readFully 能完整读取指定长度的数据，特别适合处理 TCP 粘包/拆包问题。在网络编程中，凡是传输整型长度、自定义对象，
             * 都强烈建议使用 DataInputStream 配合 DataOutputStream，避免手动移位和循环读取的繁琐与风险。
             */
            new Thread(() -> {

//                try (InputStream in = socket.getInputStream();
//                     OutputStream out = socket.getOutputStream()) {
//
//                    // 1. 读取并反序列化请求
//                    byte[] requestData = new byte[in.available()];
//                    in.read(requestData);
//                    RpcRequest request = (RpcRequest) SerializationUtil.deserialize(requestData);
//
//                    // 2. 反射调用服务方法
//                    RpcResponse response = invokeMethod(request);
//
//                    // 3. 序列化响应并返回
//                    byte[] responseData = SerializationUtil.serialize(response);
//                    out.write(responseData);
//                    out.flush();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }


                try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                     DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                    // 1. 读取长度（如果返回 -1 表示客户端关闭）
                    int length = dis.readInt();
                    // 2. 根据长度读取字节数组
                    byte[] data = new byte[length];
                    dis.readFully(data); // 确保读取完整

                    // 3. 反序列化为 User 对象
                    RpcRequest request = (RpcRequest) SerializationUtil.deserialize(data);
                    System.out.println("收到客户端 " + socket.getRemoteSocketAddress() + " 的对象：" + request);


                    // 4. 处理业务（这里简单加个时间戳并修改消息）
                    RpcResponse response = invokeMethod(request);

                    // 5. 序列化响应并发送
                    byte[] responseData = SerializationUtil.serialize(response);
                    dos.writeInt(responseData.length);
                    dos.write(responseData);
                    dos.flush();

                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("处理客户端异常：" + e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    System.out.println("客户端 " + socket.getRemoteSocketAddress() + " 断开");
                }

            }).start();
        }
    }

    // 反射执行目标方法
    private RpcResponse invokeMethod(RpcRequest request) {
        RpcResponse response = new RpcResponse();
        try {
            // 获取服务实例
            Object service = ServiceRegistry.getService(request.getClassName());
            // 获取方法
            Method method = service.getClass().getMethod(
                    request.getMethodName(),
                    request.getParamTypes()
            );
            // 调用方法
            Object result = method.invoke(service, request.getParams());
            response.setResult(result);
        } catch (Exception e) {
            response.setException(e);
        }
        return response;
    }
}
package test02;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;

/**
 * Created by chenxun.
 * Date: 2026/6/28 16:24
 * Description:
 */
public class ObjectServer {

    private static final int PORT = 8888;

    public static void main(String[] args) {
        System.out.println("对象传输服务器启动，端口 " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("客户端连接：" + socket.getRemoteSocketAddress());
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                while (true) {
                    // 1. 读取长度（如果返回 -1 表示客户端关闭）
                    int length;
                    try {
                        length = dis.readInt();
                    } catch (EOFException e) {
                        break; // 客户端正常关闭
                    }

                    // 2. 根据长度读取字节数组
                    byte[] data = new byte[length];
                    dis.readFully(data); // 确保读取完整

                    // 3. 反序列化为 User 对象
                    User user = (User) ObjectByteUtils.toObject(data);
                    System.out.println("收到客户端 " + socket.getRemoteSocketAddress() +
                            " 的对象：" + user);

                    // 4. 处理业务（这里简单加个时间戳并修改消息）
                    String newMsg = "[Echo at " + LocalDateTime.now() + "] " + user.getMessage();
                    User responseUser = new User(user.getName(), user.getAge(), newMsg);

                    // 5. 序列化响应并发送
                    byte[] responseData = ObjectByteUtils.toBytes(responseUser);
                    dos.writeInt(responseData.length);
                    dos.write(responseData);
                    dos.flush();
                }

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("处理客户端异常：" + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("客户端 " + socket.getRemoteSocketAddress() + " 断开");
            }
        }
    }

}
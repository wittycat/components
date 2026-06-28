package test01;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;

/**
 * Created by chenxun.
 * Date: 2026/6/28 16:01
 * Description:
 */
public class EchoServer {
    private static final int PORT = 8888;

    public static void main(String[] args) {
        System.out.println("Echo Server 启动，监听端口 " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // 等待客户端连接（阻塞）
                Socket clientSocket = serverSocket.accept();
                System.out.println("新客户端连接：" + clientSocket.getRemoteSocketAddress());

                // 为每个客户端创建一个独立线程处理
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("服务器异常：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // 内部类：处理单个客户端通信
    private static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // 使用 try-with-resources 自动关闭流和 Socket
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // 收到客户端消息，加上时间戳后返回
                    String response = "[" + LocalDateTime.now() + "] " + inputLine;
                    out.println(response);
                    System.out.println("回复 " + socket.getRemoteSocketAddress() + "：" + response);
                }
                System.out.println("客户端 " + socket.getRemoteSocketAddress() + " 断开连接");

            } catch (IOException e) {
                System.err.println("处理客户端 " + socket.getRemoteSocketAddress() + " 时发生异常：" + e.getMessage());
            } finally {
                try {
                    socket.close(); // 确保 Socket 被关闭
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
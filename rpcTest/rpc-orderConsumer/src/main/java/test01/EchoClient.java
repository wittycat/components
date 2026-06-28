package test01;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Created by chenxun.
 * Date: 2026/6/28 16:01
 * Description:
 */
public class EchoClient {
    private static final String SERVER_HOST = "127.0.0.1"; // 本地测试
    private static final int SERVER_PORT = 8888;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("已连接到服务器 " + SERVER_HOST + ":" + SERVER_PORT);
            System.out.println("请输入要发送的消息（输入 'quit' 退出）：");

            while (true) {
                System.out.print("> ");
                String message = scanner.nextLine();
                if ("quit".equalsIgnoreCase(message.trim())) {
                    break;
                }

                out.println(message);          // 发送消息
                String response = in.readLine(); // 接收服务器响应
                System.out.println("服务器回应：" + response);
            }

        } catch (IOException e) {
            System.err.println("客户端异常：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
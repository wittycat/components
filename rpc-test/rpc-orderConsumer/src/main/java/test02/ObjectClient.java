package test02;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

/**
 * Created by chenxun.
 * Date: 2026/6/28 16:26
 * Description:
 */
public class ObjectClient {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8888;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("连接到服务器 " + HOST + ":" + PORT);
            System.out.println("请输入姓名 年龄 消息（用空格分隔），输入 'quit' 退出");

            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if ("quit".equalsIgnoreCase(line)) break;

                String[] parts = line.split(" ");
                if (parts.length < 3) {
                    System.out.println("格式错误，示例：张三 25 Hello");
                    continue;
                }
                String name = parts[0];
                int age;
                try {
                    age = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    System.out.println("年龄必须是数字");
                    continue;
                }
                String msg = parts[2];

                // 构造对象并序列化发送
                User user = new User(name, age, msg);
                byte[] data = ObjectByteUtils.toBytes(user);
                dos.writeInt(data.length);
                dos.write(data);
                dos.flush();

                // 接收响应
                int len = dis.readInt();
                byte[] respData = new byte[len];
                dis.readFully(respData);
                User response = (User) ObjectByteUtils.toObject(respData);
                System.out.println("服务器响应：" + response);
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
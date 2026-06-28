package com.core.components.netty.chapter03;

import com.core.components.netty.Const;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Created by chenxun.
 * Date: 2018/12/16 下午3:04
 * Description:
 */
public class NioClient {

    /**
     * 启动客户端测试
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // 获得一个Socket通道
        SocketChannel socketChannel = SocketChannel.open();
        // 设置通道为非阻塞
        socketChannel.configureBlocking(false);
        // 获得一个通道管理器
        Selector selector = Selector.open();
        // 用channel.finishConnect();才能完成连接
        // 客户端连接服务器,其实方法执行并没有实现连接，需要在listen()方法中调
        boolean connect = socketChannel.connect(new InetSocketAddress(Const.IP, Const.PRORT));
        if(connect){
            // 将通道管理器和该通道绑定，并为该通道注册SelectionKey.OP_CONNECT事件
            socketChannel.register(selector, SelectionKey.OP_READ);
        }else {
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
        }

        // 轮询访问selector
        while (true) {
            /*
             * 选择一组可以进行I/O操作的事件，放在selector中,客户端的该方法不会阻塞，
             * selector的wakeup方法被调用，方法返回，而对于客户端来说，通道一直是被选中的
             * 这里和服务端的方法不一样，查看api注释可以知道，当至少一个通道被选中时。
             */
            int select = selector.select();
            // 获得selector中选中的项的迭代器
            Iterator ite = selector.selectedKeys().iterator();
            while (ite.hasNext()) {
                SelectionKey key = (SelectionKey) ite.next();
                // 删除已选的key，以防重复处理
                ite.remove();
                // 连接事件发生
                if (key.isConnectable()) {
                    // 如果正在连接，则完成连接
                    SocketChannel channe = (SocketChannel) key.channel();
                    if (channe.isConnectionPending()) {
                        channe.finishConnect();
                    }
                    // 设置成非阻塞
                    channe.configureBlocking(false);
                    // 在这里可以给服务端发送信息哦
                    channe.write(ByteBuffer.wrap(new String("hello server").getBytes()));
                    // 在和服务端连接成功之后，为了可以接收到服务端的信息，需要给通道设置读的权限。
                    channe.register(selector, SelectionKey.OP_READ); // 获得了可读的事件
                } else if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    // 分配缓冲区
                    ByteBuffer buffer = ByteBuffer.allocate(12);
                    channel.read(buffer);
                    byte[] data = buffer.array();
                    String msg = new String(data).trim();
                    System.out.println("client receive msg from server:" + msg);
                    ByteBuffer outBuffer = ByteBuffer.wrap(msg.getBytes());
                    channel.write(outBuffer);
                }
            }
        }
    }
}
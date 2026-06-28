package com.core.components.netty.chapter03;

import com.core.components.netty.Const;
import com.core.components.netty.chapter02.server.ServerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Created by chenxun.
 * Date: 2018/12/16 下午2:40
 * Description:
 */
public class NioServer {

    static Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);


    public static void main(String[] args) {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(Const.IP, Const.PRORT));
            serverSocketChannel.configureBlocking(false);

            Selector selector = Selector.open();

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            logger.info(" msg=启动成功 ");

            // 轮询访问selector
            while (true) {
                // 当注册事件到达时，方法返回，否则该方法会一直阻塞
                selector.select();
                // 获得selector中选中的相的迭代器，选中的相为注册的事件
                Iterator ite = selector.selectedKeys().iterator();
                while (ite.hasNext()) {
                    SelectionKey key = (SelectionKey) ite.next();
                    // 删除已选的key以防重负处理
                    ite.remove();
                    // 客户端请求连接事件
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        // 获得和客户端连接的通道
                        SocketChannel channel = server.accept();
                        // 设置成非阻塞
                        channel.configureBlocking(false);
                        // 在这里可以发送消息给客户端
                        channel.write(ByteBuffer.wrap(new String("hello client").getBytes()));
                        // 在客户端连接成功之后，为了可以接收到客户端的信息，需要给通道设置读的权限
                        channel.register(selector, SelectionKey.OP_READ);
                        // 获得了可读的事件

                    } else if (key.isReadable()) {
                        // 服务器可读消息，得到事件发生的socket通道
                        SocketChannel channel = (SocketChannel) key.channel();
                        // 读取的缓冲区
                        ByteBuffer buffer = ByteBuffer.allocate(12);
                        channel.read(buffer);
                        byte[] data = buffer.array();
                        String msg = new String(data).trim();
                        System.out.println("server receive from client: " + msg);
                        ByteBuffer outBuffer = ByteBuffer.wrap(msg.getBytes());
                        channel.write(outBuffer);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(" msg=服务端启动异常 ", e);
        }
    }

 }
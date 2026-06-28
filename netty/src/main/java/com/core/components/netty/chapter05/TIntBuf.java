package com.core.components.netty.chapter05;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;

/**
 * Created by chenxun.
 * Date: 2019/1/29 上午11:46
 * Description:
 */
public class TIntBuf {

    @Test
    public void method1() {
        IntBuffer intBuffer =  IntBuffer.allocate(20);
        for (int i = 0; i < 20 ; i++) {
            intBuffer.put(new SecureRandom().nextInt(20));
        }
        intBuffer.flip();
        while (intBuffer.hasRemaining()){
            System.out.println(intBuffer.get());
        }
    }

    @Test
    public void method2() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(20000);
        FileInputStream fileInputStream = new FileInputStream("/Users/chenxun/IdeaProjects/github/components/netty/src/main/java/com/core/components/netty/chapter05/package-info.java");
        FileChannel channel = fileInputStream.getChannel();

        channel.read(byteBuffer);

        byteBuffer.flip();
        while (byteBuffer.hasRemaining()){
            System.out.print((char) byteBuffer.get());
        }
        fileInputStream.close();
    }

    @Test
    public void method3() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(20000);
        byte[] bytes = "package com.core.components.netty.chapter05;".getBytes();
        byteBuffer.put(bytes);
        byteBuffer.flip();

        FileOutputStream fileInputStream = new FileOutputStream("/Users/chenxun/IdeaProjects/github/components/netty/src/main/java/com/core/components/netty/chapter05/package-info.java.copy");
        FileChannel channel = fileInputStream.getChannel();

        channel.write(byteBuffer);

        fileInputStream.close();
    }
}
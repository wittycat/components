package test02;

import java.io.*;

/**
 * Created by chenxun.
 * Date: 2026/6/28 16:24
 * Description:
 */
public class ObjectByteUtils {

    // 对象 → byte[]
    public static byte[] toBytes(Object obj) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }

    // byte[] → 对象
    public static Object toObject(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }

}
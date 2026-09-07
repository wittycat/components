import java.io.*;

/**
 * Created by chenxun.
 * Date: 2026/6/28 11:38
 * Description:
 */
public class SerializationUtil {

    /**
     * 对象序列化为字节数组
     * @param obj
     * @return
     * @throws IOException
     */
    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.close();
        return bos.toByteArray();

//        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
//             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
//            oos.writeObject(obj);
//            oos.flush();
//            return baos.toByteArray();
//        }
    }

    /**
     * 字节数组反序列化为对象
     * @param data
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        Object obj = ois.readObject();
        ois.close();
        return obj;

//        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
//             ObjectInputStream ois = new ObjectInputStream(bais)) {
//            return ois.readObject();
//        }
    }
}
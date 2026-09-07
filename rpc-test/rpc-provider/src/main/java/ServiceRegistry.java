import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenxun.
 * Date: 2026/6/28 11:40
 * Description: 服务注册中心：存储接口与实现类的映射
 */
public class ServiceRegistry {

    /**
     * key: 接口全类名, value: 实现类实例
     */
    private static final Map<String, Object> SERVICE_MAP = new HashMap<>();

    /**
     * 注册服务
     *
     * @param interfaceName
     * @param instance
     */
    public static void registerService(String interfaceName, Object instance) {
        SERVICE_MAP.put(interfaceName, instance);
    }

    /**
     * 获取服务实例
     *
     * @param interfaceName
     * @return
     */
    public static Object getService(String interfaceName) {
        return SERVICE_MAP.get(interfaceName);
    }

}
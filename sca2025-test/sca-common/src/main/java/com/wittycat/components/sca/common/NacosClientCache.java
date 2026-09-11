package com.wittycat.components.sca.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by chenxun.
 * Date: 2026/9/11
 * Description: Nacos 客户端本地缓存目录收敛工具。
 * 客户端默认把服务发现缓存（naming/）和配置快照（config/）写到 ${user.home}/nacos，
 * 本工具通过 Nacos 官方支持的 JM.SNAPSHOT.PATH 系统属性把缓存改到工程根的 nacos-cache/ 下，
 * 家目录不再被写脏。必须在 SpringApplication.run() 之前调用（Nacos 客户端初始化时读取该属性）。
 */
public final class NacosClientCache {

    /** Nacos 客户端缓存根目录的系统属性名（nacos-client 2.x/3.x 均支持） */
    private static final String SNAPSHOT_PATH_PROPERTY = "JM.SNAPSHOT.PATH";

    /** 缓存目录名（相对工程根） */
    private static final String CACHE_DIR = "nacos-cache";

    private NacosClientCache() {
    }

    /**
     * 设置缓存根目录为工程根下的 nacos-cache/。工程根定位规则：从进程工作目录逐级向上
     * 找到包含 sca-common/pom.xml 的目录——IDE 和 mvn spring-boot:run 的工作目录都是各服务
     * 子模块，java -jar 则取决于执行位置；找不到时退化为进程工作目录，保证不影响启动
     */
    public static void init() {
        Path cacheDir = findProjectRoot().resolve(CACHE_DIR);
        System.setProperty(SNAPSHOT_PATH_PROPERTY, cacheDir.toString());
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path fallback = current;
        while (current != null) {
            if (Files.isRegularFile(current.resolve("sca-common").resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return fallback;
    }
}

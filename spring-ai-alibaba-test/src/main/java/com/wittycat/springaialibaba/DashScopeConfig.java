package com.wittycat.springaialibaba;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * DashScope(阿里云百炼/通义)配置加载:全部示例共用,约定与仓库其他模块(glm.* 等)完全一致。
 *
 * 【知识点】
 * 1. Spring AI Alibaba 对通义模型的接入走 DashScope 原生 API(而非 OpenAI 兼容端点),
 *    starter 是 spring-ai-alibaba-starter-dashscope,自动配置前缀是 spring.ai.dashscope.*。
 *    对比 spring-ai-test:那边用 spring-ai-starter-model-openai + 智谱的 OpenAI 兼容端点,
 *    连 base-url 带不带 /v1、completions-path 是什么都要手动对齐;这边端点是默认值,只有 api-key 必填。
 * 2. 配置文件约定(与仓库其他模块一致):
 *      - application.yml       放默认值(api-key 是占位符 demo),可提交 git;
 *      - application-local.yml 放真实 api-key 等敏感信息,已被 .gitignore 排除。
 *    Web 示例走 Spring 自动配置,application.yml 里用 ${dashscope.xxx} 占位符映射到
 *    spring.ai.dashscope.*,两种用法共享同一份配置。
 * 3. 同一个配置项的读取优先级(本类为纯 main() 示例手动实现,Web 示例由 Spring 完成同样的事):
 *      -D 系统属性 > 环境变量 > application-local.yml > application.yml
 */
public final class DashScopeConfig {

    /** DashScope 默认端点(百炼),正常情况从 yml 读取,一般不需要改 */
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

    /** 默认聊天模型:qwen-plus(商用主力,速度快价格低;要更强换 qwen-max) */
    public static final String DEFAULT_MODEL = "qwen-plus";

    private DashScopeConfig() {
    }

    /**
     * 解析 API Key。找不到或还是占位值 "demo" 时,给出可操作的报错提示。
     * Key 在阿里云百炼控制台(bailian.console.aliyun.com)的 "API-KEY 管理" 页面创建。
     */
    public static String resolveApiKey() {
        String key = value("api-key", "DASHSCOPE_API_KEY");
        if (key == null || key.isBlank() || "demo".equalsIgnoreCase(key.trim())) {
            throw new IllegalStateException("""
                    未找到 DashScope API Key,请任选其一配置(推荐第 1 种):
                      1. 编辑 src/main/resources/application-local.yml(该文件已被 .gitignore 排除):
                           dashscope:
                             api-key: sk-你的key
                      2. 环境变量:  export DASHSCOPE_API_KEY=sk-你的key
                      3. JVM 参数:  -Ddashscope.api-key=sk-你的key
                    Key 在阿里云百炼控制台 https://bailian.console.aliyun.com 的 "API-KEY 管理" 页创建
                    """);
        }
        return key.trim();
    }

    public static String baseUrl() {
        return value("base-url", "DASHSCOPE_BASE_URL", DEFAULT_BASE_URL);
    }

    public static String model() {
        return value("model", "DASHSCOPE_MODEL", DEFAULT_MODEL);
    }

    /**
     * 读取 dashscope.<name> 配置项,优先级:-Ddashscope.<name> > 环境变量 > application-local.yml > application.yml。
     * 找不到返回 null(由调用方决定默认值或报错)。
     */
    static String value(String name, String envName) {
        return value(name, envName, null);
    }

    /**
     * 读取 dashscope.<name> 配置项,优先级:-Ddashscope.<name> > 环境变量 > application-local.yml > application.yml。
     * 找不到返回 defaultValue(可为 null,由调用方决定是否报错)。
     */
    static String value(String name, String envName, String defaultValue) {
        String v = System.getProperty("dashscope." + name);
        if (v == null || v.isBlank()) {
            v = System.getenv(envName);
        }
        if (v == null || v.isBlank()) {
            v = yamlValue("application-local.yml", name);
        }
        if (v == null || v.isBlank()) {
            v = yamlValue("application.yml", name);
        }
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    /** 从 classpath 上的 yml 文件读取 dashscope 段下的某个 key;文件不存在或没有该 key 返回 null */
    @SuppressWarnings("unchecked")
    private static String yamlValue(String location, String name) {
        Map<String, Object> root = loadYaml(location);
        if (root == null) {
            return null;
        }
        Object dashscope = root.get("dashscope");
        if (!(dashscope instanceof Map)) {
            return null;
        }
        Object v = ((Map<String, Object>) dashscope).get(name);
        return v == null ? null : String.valueOf(v);
    }

    private static Map<String, Object> loadYaml(String location) {
        try (InputStream is = DashScopeConfig.class.getClassLoader().getResourceAsStream(location)) {
            return is == null ? null : new Yaml().load(is);
        } catch (IOException e) {
            throw new UncheckedIOException("读取配置文件失败: " + location, e);
        }
    }
}

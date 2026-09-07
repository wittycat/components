package com.wittycat.langgraph4j;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * GLM 配置加载:LLM 示例(8~10)共用,约定与 agentscope-test / ai-agent 完全一致。
 *
 * 【知识点】
 * 1. 智谱 GLM 开放平台提供 OpenAI 兼容协议(/chat/completions),所以不需要任何智谱专用 SDK,
 *    一个普通的 HTTP 客户端(见 GlmClient)就能接入;换 DeepSeek/Kimi/本地 vLLM
 *    也只是改 base-url 和 model 两个配置。
 * 2. 配置文件约定:
 *      - application.yml       放默认值(api-key 是占位符 demo),可提交 git;
 *      - application-local.yml 放真实 api-key 等敏感信息,已被 .gitignore 排除。
 * 3. 同一个配置项的读取优先级:
 *      -D 系统属性 > 环境变量 > application-local.yml > application.yml
 *    纯 main() 示例没启动 Spring,所以这里用 SnakeYAML 自己读 classpath 上的 yml,
 *    保持和 agentscope-test 同一条配置路径。
 */
public final class GlmConfig {

    /** 智谱开放平台的 OpenAI 兼容端点(兜底默认值,正常情况从 yml 读取) */
    public static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/coding/paas/v4";

    /** 默认聊天模型 */
    public static final String DEFAULT_MODEL = "glm-5.3-flash";

    private GlmConfig() {
    }

    /**
     * 解析 API Key。找不到或还是占位值 "demo" 时,给出可操作的报错提示。
     */
    public static String resolveApiKey() {
        String key = value("api-key", "GLM_API_KEY");
        if (key == null || key.isBlank() || "demo".equalsIgnoreCase(key.trim())) {
            throw new IllegalStateException("""
                    未找到智谱 API Key,请任选其一配置(推荐第 1 种):
                      1. 编辑 src/main/resources/application-local.yml(该文件已被 .gitignore 排除):
                           glm:
                             api-key: 你的key
                      2. 环境变量:  export GLM_API_KEY=你的key
                      3. JVM 参数:  -Dglm.api-key=你的key
                    """);
        }
        return key.trim();
    }

    public static String baseUrl() {
        return value("base-url", "GLM_BASE_URL", DEFAULT_BASE_URL);
    }

    public static String model() {
        return value("model", "GLM_MODEL", DEFAULT_MODEL);
    }

    /**
     * 读取 glm.<name> 配置项,优先级:-Dglm.<name> > 环境变量 > application-local.yml > application.yml。
     * 找不到返回 null(由调用方决定默认值或报错)。
     */
    static String value(String name, String envName) {
        return value(name, envName, null);
    }

    /**
     * 读取 glm.<name> 配置项,优先级:-Dglm.<name> > 环境变量 > application-local.yml > application.yml。
     * 找不到返回 defaultValue(可为 null,由调用方决定是否报错)。
     */
    static String value(String name, String envName, String defaultValue) {
        String v = System.getProperty("glm." + name);
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

    /** 从 classpath 上的 yml 文件读取 glm 段下的某个 key;文件不存在或没有该 key 返回 null */
    @SuppressWarnings("unchecked")
    private static String yamlValue(String location, String name) {
        Map<String, Object> root = loadYaml(location);
        if (root == null) {
            return null;
        }
        Object glm = root.get("glm");
        if (!(glm instanceof Map)) {
            return null;
        }
        Object v = ((Map<String, Object>) glm).get(name);
        return v == null ? null : String.valueOf(v);
    }

    private static Map<String, Object> loadYaml(String location) {
        try (InputStream is = GlmConfig.class.getClassLoader().getResourceAsStream(location)) {
            return is == null ? null : new Yaml().load(is);
        } catch (IOException e) {
            throw new UncheckedIOException("读取配置文件失败: " + location, e);
        }
    }
}

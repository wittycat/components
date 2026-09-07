package com.wittycat.agentscope;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.GLMFormatter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * 模型工厂 + 配置加载:所有示例共用。
 *
 * 【知识点 1】在 AgentScope 里,"模型"(Model) 是一个独立对象,Agent 通过 builder.model(...) 持有它。
 * 智谱 GLM 开放平台提供 OpenAI 兼容协议,所以直接用 AgentScope 的 OpenAIChatModel 接入,
 * 再配上官方为 GLM 准备的 GLMFormatter(处理 GLM 对消息格式/tool_choice 的特殊兼容),
 * 不需要任何智谱专用 SDK。
 * 如果以后想换模型,只需要换这里的实现,例如:
 *   - 通义千问(DashScope 原生协议): 引入 agentscope-extensions-model-dashscope,用 DashScopeChatModel
 *   - 其他 OpenAI 兼容服务(DeepSeek/Kimi/vLLM 本地模型): 仍是 OpenAIChatModel,改 baseUrl/modelName 即可
 *
 * 【知识点 2】配置加载(与 ai-agent 项目同一套约定):
 *   - application.yml       放默认值,可提交到 git
 *   - application-local.yml 放真实 api-key 等敏感配置,已被 .gitignore 排除,不会提交
 * 同一个配置项的读取优先级:
 *   -D 系统属性 > 环境变量 > application-local.yml > application.yml
 * 注意:纯 main() 示例(1~3)没有启动 Spring,所以这里用 SnakeYAML 自己读 classpath 上的
 * yml 文件,而不是 Spring 的 @ConfigurationProperties——这样 4 个示例走的是同一条配置路径。
 */
public final class ModelFactory {

    /** 智谱开放平台的 OpenAI 兼容端点(兜底默认值,正常情况从 yml 读取) */
    public static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/coding/paas/v4";

    /** 默认聊天模型 */
    public static final String DEFAULT_MODEL = "glm-5.3-flash";

    private ModelFactory() {
    }

    /**
     * 解析 API Key。找不到或还是占位值 "demo" 时,给出可操作的报错提示。
     */
    public static String resolveApiKey() {
        String key = glmValue("api-key", "GLM_API_KEY");
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

    /**
     * 构建 GLM 聊天模型。
     * stream(true) 表示用流式(SSE)方式接收模型响应,AgentScope 内部会聚合成事件流。
     */
    public static OpenAIChatModel createGlmChatModel() {
        return OpenAIChatModel.builder()
                .apiKey(resolveApiKey())
                .baseUrl(glmValue("base-url", "GLM_BASE_URL", DEFAULT_BASE_URL))
                .modelName(glmValue("model", "GLM_MODEL", DEFAULT_MODEL))
                .stream(true)
                .formatter(new GLMFormatter())
                .build();
    }

    /**
     * 读取 glm.<name> 配置项,优先级:-Dglm.<name> > 环境变量 > application-local.yml > application.yml。
     * 找不到返回 null(由调用方决定默认值或报错)。
     */
    static String glmValue(String name, String envName) {
        return glmValue(name, envName, null);
    }

    static String glmValue(String name, String envName, String defaultValue) {
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
        try (InputStream is = ModelFactory.class.getClassLoader().getResourceAsStream(location)) {
            return is == null ? null : new Yaml().load(is);
        } catch (IOException e) {
            throw new UncheckedIOException("读取配置文件失败: " + location, e);
        }
    }
}

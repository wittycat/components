package com.wittycat.agentscope;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;
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
 * 不需要任何智谱专用 SDK。DeepSeek 同理:OpenAIChatModel + 官方 DeepSeekFormatter。
 * 如果以后想换模型,只需要换这里的实现,例如:
 *   - 通义千问(DashScope 原生协议): 引入 agentscope-extensions-model-dashscope,用 DashScopeChatModel
 *   - 其他 OpenAI 兼容服务(Kimi/vLLM 本地模型): 仍是 OpenAIChatModel,改 baseUrl/modelName 即可
 *
 * 【知识点 2】配置加载(与 ai-agent 项目同一套约定):
 *   - application.yml                 放默认值,可提交到 git
 *   - application-local.yml           放 GLM 真实 api-key 等敏感配置(glm: 段),已被 .gitignore 排除
 *   - application-local-deepseek.yml  放 DeepSeek 真实 api-key(llm: 段),同样被 .gitignore 排除
 * 同一个配置项的读取优先级:
 *   -D 系统属性 > 环境变量 > application-local(-deepseek).yml > application.yml
 * 注意:纯 main() 示例没有启动 Spring,所以这里用 SnakeYAML 自己读 classpath 上的
 * yml 文件,而不是 Spring 的 @ConfigurationProperties——这样所有示例走的是同一条配置路径。
 */
public final class ModelFactory {

    /** 智谱开放平台的 OpenAI 兼容端点(兜底默认值,正常情况从 yml 读取) */
    public static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/coding/paas/v4";

    /** 默认聊天模型 */
    public static final String DEFAULT_MODEL = "glm-5.3-flash";

    /** DeepSeek 的 OpenAI 兼容端点(/v1 与不带 /v1 等价,Chat Completions 与 Responses 都挂在这) */
    public static final String DEEPSEEK_DEFAULT_BASE_URL = "https://api.deepseek.com/v1";

    /** DeepSeek 默认聊天模型 */
    public static final String DEEPSEEK_DEFAULT_MODEL = "deepseek-flash";

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
     * 解析 DeepSeek API Key。找不到或还是占位值 "demo" 时,给出可操作的报错提示。
     */
    public static String resolveDeepSeekApiKey() {
        String key = llmValue("api-key", "DEEPSEEK_API_KEY");
        if (key == null || key.isBlank() || "demo".equalsIgnoreCase(key.trim())) {
            throw new IllegalStateException("""
                    未找到 DeepSeek API Key,请任选其一配置(推荐第 1 种):
                      1. 编辑 src/main/resources/application-local-deepseek.yml(该文件已被 .gitignore 排除):
                           llm:
                             api-key: 你的key
                      2. 环境变量:  export DEEPSEEK_API_KEY=你的key
                      3. JVM 参数:  -Dllm.api-key=你的key
                    """);
        }
        return key.trim();
    }

    /** 构建 DeepSeek 聊天模型(推荐配置,示例 7 Part 3 使用)。 */
    public static OpenAIChatModel createDeepSeekChatModel() {
        return createDeepSeekChatModel(false);
    }

    /**
     * 带"原生结构化输出"开关的 DeepSeek 模型构建。
     *
     * 【机制核实】(2.0.2 反编译 + 实测 2026-10-28)OpenAIChatModel 的 Builder 对原生开关的
     * 默认值是 true(build() 里字段为 null 时置 true)——即结构化 call 默认会先向
     * Chat Completions 发 response_format: json_schema。但 DeepSeek 该端点只支持
     * text/json_object,实测 400 "This response_format type is unavailable now",
     * ReActAgent 打 WARN("falling back to synthetic tool path")后自动回退到
     * generate_response 数据接收工具,结果仍能拿到。
     *
     * 因此本工厂默认传 false 显式关掉原生开关:省掉每次结构化调用前那次注定失败的
     * 400 探测(白付一次请求延迟 + 一条 WARN 日志)。要复现"原生→400→回退"全过程,
     * 传 true(实验数据见 Example7_DeepSeekStructuredOutput_验证报告.md §6)。
     *
     * DeepSeekFormatter.supportsStrict() 返回 false:工具 schema 不带 strict 标记,
     * 工具参数仍是"模型生成的 JSON",不是解码层保证
     * (解码级严格 Schema 走 Responses API 的 text.format.type=json_schema,见示例 7)。
     */
    public static OpenAIChatModel createDeepSeekChatModel(boolean nativeStructuredOutputWithTools) {
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(resolveDeepSeekApiKey())
                .baseUrl(llmValue("base-url", "DEEPSEEK_BASE_URL", DEEPSEEK_DEFAULT_BASE_URL))
                .modelName(llmValue("model-name", "DEEPSEEK_MODEL", DEEPSEEK_DEFAULT_MODEL))
                .stream(true)
                .formatter(new DeepSeekFormatter());
        if (nativeStructuredOutputWithTools) {
            builder.nativeStructuredOutputWithTools(true);
        } else {
            builder.nativeStructuredOutput(false);
        }
        return builder.build();
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
            v = yamlValue("application-local.yml", "glm", name);
        }
        if (v == null || v.isBlank()) {
            v = yamlValue("application.yml", "glm", name);
        }
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    /**
     * 读取 llm.<name> 配置项(DeepSeek),优先级:
     * -Dllm.<name> > 环境变量 > application-local-deepseek.yml > application.yml。
     */
    static String llmValue(String name, String envName) {
        return llmValue(name, envName, null);
    }

    static String llmValue(String name, String envName, String defaultValue) {
        String v = System.getProperty("llm." + name);
        if (v == null || v.isBlank()) {
            v = System.getenv(envName);
        }
        if (v == null || v.isBlank()) {
            v = yamlValue("application-local-deepseek.yml", "llm", name);
        }
        if (v == null || v.isBlank()) {
            v = yamlValue("application.yml", "llm", name);
        }
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    /** 从 classpath 上 yml 文件的 section 段下读某个 key;文件不存在或没有该 key 返回 null */
    @SuppressWarnings("unchecked")
    private static String yamlValue(String location, String section, String name) {
        Map<String, Object> root = loadYaml(location);
        if (root == null) {
            return null;
        }
        Object sec = root.get(section);
        if (!(sec instanceof Map)) {
            return null;
        }
        Object v = ((Map<String, Object>) sec).get(name);
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

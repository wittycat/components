package com.wittycat.knowledgebase.tool;

import com.wittycat.knowledgebase.config.AgentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShellTool implements AgentTool {

    private final AgentConfig agentConfig;

    /** 命令执行超时时间（秒） */
    private static final int COMMAND_TIMEOUT_SECONDS = 30;

    /** 命令输出最大保留长度（字符），超出截断，防止撑爆模型上下文 */
    private static final int MAX_OUTPUT_LENGTH = 5000;

    /** 白名单：每个链式/管道段的首个命令都必须在列表内 */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "ls", "cat", "head", "tail", "wc", "find", "grep", "echo", "pwd",
            "date", "whoami", "uname", "df", "du", "ps", "env", "export",
            "sort", "uniq", "tr", "cut", "awk", "sed", "diff", "file",
            "stat", "tree", "which", "type", "basename", "dirname",
            "realpath", "readlink", "touch", "mkdir", "cp", "mv",
            "chmod", "chown", "git", "node", "npm", "python3", "python",
            "java", "javac", "mvn", "curl", "wget", "jq", "xmlstarlet",
            "tar", "gzip", "gunzip", "zip", "unzip", "base64", "sha256sum",
            "md5sum", "tee", "xargs", "nl", "true", "false"
    );

    /** 禁止的危险模式（即使命令在白名单中，匹配到这些模式也会被拒绝） */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("(?i)rm\\s+-[rfRF]+\\s+/"),
            Pattern.compile("(?i)rm\\s+-[rfRF]+\\s+\\*/"),
            Pattern.compile("(?i)mkfs"),
            Pattern.compile("(?i)dd\\s+if="),
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:\\s*&\\s*\\}\\s*;\\s*:"),
            Pattern.compile("(?i)shutdown\\b"),
            Pattern.compile("(?i)reboot\\b"),
            Pattern.compile("(?i)halt\\b"),
            Pattern.compile("(?i)init\\s+[06]"),
            Pattern.compile("(?i)>\\s*/dev/(?!null\\b)"),
            Pattern.compile("(?i)chmod\\s+-R\\s+777\\s+/"),
            Pattern.compile("(?i)\\|\\s*sudo\\b"),
            Pattern.compile("(?i)\\|\\s*bash\\b"),
            Pattern.compile("(?i)\\|\\s*sh\\b"),
            Pattern.compile("(?i);\\s*sudo\\b"),
            Pattern.compile("(?i);\\s*rm\\s+-[rfRF]+\\s+"),
            Pattern.compile("(?i)\\$\\(.*sudo"),
            Pattern.compile("(?i)`.*sudo")
    );

    @Override
    public String getName() {
        return "run_shell_command";
    }

    @Override
    public String getDescription() {
        return "在工作目录中执行安全的 Shell 命令并返回输出结果。支持常见的文件查看、搜索、Git、构建工具等命令。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "command", Map.of("type", "string", "description", "要执行的 Shell 命令（仅允许安全命令）")
        ));
        schema.put("required", List.of("command"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String command = (String) arguments.get("command");
        if (command == null || command.isBlank()) {
            return "错误：命令不能为空";
        }

        // 安全检查
        String error = validateCommand(command);
        if (error != null) {
            log.warn("[ShellTool] 命令被安全策略拒绝: {}", command);
            return "错误：" + error;
        }

        Path workspace = agentConfig.resolveWorkspace();
        java.nio.file.Files.createDirectories(workspace);

        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);
        // 限制子进程环境变量，移除 PATH 中可能指向危险目录的条目
        pb.environment().put("PATH", "/usr/local/bin:/usr/bin:/bin");

        Process process = pb.start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "错误：命令执行超时（" + COMMAND_TIMEOUT_SECONDS + "秒）";
        }

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        }

        if (output.length() > MAX_OUTPUT_LENGTH) {
            output = output.substring(0, MAX_OUTPUT_LENGTH) + "\n...(输出已截断)";
        }

        return "退出码: " + process.exitValue() + "\n" + output;
    }

    /**
     * 多层安全校验：每个链式/管道段的白名单检查 + 整条命令的危险模式检测
     */
    private String validateCommand(String command) {
        // 1. 按 &&、||、;、| 切分命令段（引号内的操作符不算边界），每段首命令都须在白名单内
        for (String segment : splitSegments(command.trim())) {
            String firstCommand = extractFirstCommand(segment);
            if (firstCommand.isEmpty()) {
                continue;
            }
            if (!ALLOWED_COMMANDS.contains(firstCommand)) {
                return "该命令 '" + firstCommand + "' 不在允许的安全命令列表中。"
                        + "允许的命令: " + String.join(", ", ALLOWED_COMMANDS);
            }
        }

        // 2. 危险模式检查（整条命令）
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return "该命令包含被禁止的危险操作模式";
            }
        }

        return null;
    }

    /**
     * 按链式/管道操作符切分命令段。引号内的 &&、||、;、| 不视作边界（如 grep "a|b"）；
     * 反斜杠转义的字符不视作边界（如 grep a\|b）
     */
    private List<String> splitSegments(String command) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false, inDouble = false, escaped = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && !inSingle) {
                current.append(c);
                escaped = true;
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(c);
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
            } else if (!inSingle && !inDouble && (c == ';' || c == '|' || c == '&')) {
                if (current.length() > 0) {
                    segments.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }

    /**
     * 提取命令段中的首个命令名：先取第一个 token，再仅对该 token 去掉路径前缀（如 /usr/bin/ls → ls）。
     * 不能对整段去路径，否则后面的文件路径（如 2>/dev/null）会被误当成命令名
     */
    private String extractFirstCommand(String command) {
        String s = command.trim();
        if (s.isEmpty()) {
            return "";
        }

        // 第一个 token：到空白或操作符为止
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '|' || c == ';' || c == '&' || c == '>') {
                end = i;
                break;
            }
        }

        String token = s.substring(0, end);
        int slashIdx = token.lastIndexOf('/');
        return slashIdx >= 0 ? token.substring(slashIdx + 1) : token;
    }
}

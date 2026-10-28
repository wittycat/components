# Example7_DeepSeekStructuredOutput 验证报告

> DeepSeek 严格结构化输出(Structured Outputs)API 行为核实与长文本压力实测。
> 测试日期:2026-10-28 | 联系上下文:[README.md](README.md) 示例 7 | 姊妹篇:[Example6_StructuredOutput_验证报告.md](Example6_StructuredOutput_验证报告.md)(GLM 工具通道的软约束实测)

## 1. 测试要求(验证目标)

1. DeepSeek Responses API `text.format.type="json_schema"`"严格结构化输出"的实际约束力如何?与 `json_object` 的差距多大?
2. **长文本压力下**(示例 6 那篇让工具通道翻过车的 1043 字符影评)硬约束是否依然成立?
3. `deepseek-flash` 默认开启思考(thinking),是否影响 json_schema 输出的正确性?

## 2. 测试环境

| 项 | 值 |
|---|---|
| 模型 | `deepseek-flash` @ DeepSeek 官方 OpenAI 兼容端点(`/v1` 与根路径均可用) |
| 端点 | Responses `POST /responses`(无状态,需重发完整历史) |
| 输入文本 | 1043 字符电影长评,与示例 6 完全同一篇 |
| 目标结构 | 片名 / 情感(POSITIVE·NEGATIVE·NEUTRAL) / 评分(0~10,未提分 -1) / 是否推荐 / 亮点(2~5 条) |
| 严格 Schema | 手写 JSON Schema:`enum` + `minItems/maxItems` + 全字段 `required` + `additionalProperties:false` |
| 校验方式 | Jackson 反序列化到 POJO(sentiment 为真正的 Java enum,非法值直接抛异常)+ 业务校验(评分范围/亮点条数) |
| API 调用 | 端点/格式探测 + 短文本预实验 + 长文本正式 2 轮 × 2,共约 12 次 |

## 3. 结论速览

| # | 结论 |
|---|---|
| 1 | DeepSeek 的严格结构化输出走 Responses API 的 `text.format.type="json_schema"`(`name`+`schema` 必填);`json_object` 只保证"合法 JSON",不保证符合 Schema |
| 2 | `json_schema` 是**解码层硬约束**:enum、`minItems/maxItems`、必填全部被严格执行;1043 字符长文本两轮 2/2 全对,无衰减 |
| 3 | `json_object` 只承诺"合法 JSON",字段名/类型/取值无强制力;本轮恰好合规是模型自觉,赌不得 |
| 4 | 思考默认开启不影响正确性:官方 API 含思考的 json_schema 输出实测正常(社区第三方自部署的损坏报告未复现) |
| 5 | 业务 JSON 是 `output_text` 里的**字符串**,要二次解析;截断看 `status=incomplete` + `incomplete_details` |

## 4. API 行为核实

### 4.1 端点与参数

- `text.format.type` 枚举:`text`(默认)/ `json_object` / `json_schema`;`type=json_schema` 时 `name` 与 `schema` 必填,`schema` 即"输出必须符合的 JSON Schema";
- 实测 `json_schema` 正常返回,输出严格符合 Schema;`/v1/responses` 与 `/responses` 均可用;
- API 无状态,客户端需重发完整对话历史。

文档对 `text.format` 没有记载 refusal 等例外情形(不同于 OpenAI),截断只通过 `incomplete_details` 表达;承诺范围是"正常完成(`status=completed`)的输出符合 Schema",值的**事实正确性**不在承诺内。

### 4.2 响应结构(解析要点)

```json
{ "status": "completed",
  "output": [ { "type": "reasoning", "content": [ { "type": "reasoning_text", "text": "思考过程..." } ] },
              { "type": "message",   "content": [ { "type": "output_text",  "text": "{\"movieName\": ...}" } ] } ] }
```

- `deepseek-flash` 默认思考:`output[]` 同时含 `reasoning` 与 `message` 条目,解析必须按 `type` 分流,不能默认 `output[0]` 就是答案;
- 业务 JSON 是 `output_text` 里的**字符串**,要二次反序列化;
- 先查 `status == "completed"` 再解析;截断时 `incomplete_details.reason` 给原因(`max_output_tokens` / `content_filter`);
- `max_output_tokens` 给足:思考与输出共享该额度。

## 5. 实测:长文本 json_schema vs json_object(两轮)

| 轮次 | Part 1 `json_schema` | Part 2 `json_object`(对照) |
|---|---|---|
| 第 1 轮 | 流浪地球3 / **POSITIVE** / 8.5 / true / **恰好 5 条** ✓ 全对 | 流浪地球3 / POSITIVE / 8.5 / true / 5 条 ✓ 恰好 |
| 第 2 轮 | 流浪地球3 / **POSITIVE** / 8.5 / true / **恰好 5 条** ✓ 全对 | 流浪地球3 / POSITIVE / 8.5 / true / 5 条 ✓ 恰好 |

第 1 轮 Part 1 输出原文(亮点恰被 `minItems:2, maxItems:5` 夹在 5 条):

```json
{"movieName": "流浪地球3", "sentiment": "POSITIVE", "rating": 8.5, "recommend": true,
 "highlights": ["月球坠落段落的视效", "IMAX与杜比音效的沉浸体验", "数字生命线与MOSS暗线的交汇",
                "吴京与刘德华的对手戏", "献给老影迷的情书"]}
```

**与示例 6 同文本对照**:同一篇影评,GLM 工具通道(示例 6 报告 §5.2,required 修复后)情感返回自由文本 `"正面(克制型好评)"`、亮点倾倒 30+ 条、`recommend` 事实性填反;DeepSeek `json_schema` 两轮 2/2 全部守住。

判读:

- `json_schema` 的合规是**解码层保证**——enum 只会落在约定值、亮点被硬夹在 2~5、必填一个不少,长文本下无衰减;
- `json_object` 两轮恰好合规,但这是**模型自觉而非合同条款**——字段名漂移、枚举变自由文本、亮点超量随时可能发生,业务校验必须兜底;
- `sentiment` 用真正的 Java enum 承接:模型若返回约定外的值,Jackson 反序列化直接失败,类型系统在最后一层兜底。

## 6. 生产建议

按需求选通道:

| 需求 | 通道 |
|---|---|
| 严格 Schema(枚举/数量/必填是硬性合同) | `text.format.type="json_schema"` |
| 只要"是合法 JSON" | `json_object` |

裸调 Responses API 检查清单:

1. 先查 `status == "completed"`,不是就读 `incomplete_details`,不要直接解析;
2. 按 `output[].type` 分流取 `output_text`,业务 JSON 是字符串,二次反序列化;
3. `max_output_tokens` 给足(思考 + 输出共享该额度);
4. 可选参数(`tools` 等)要么不发,不要显式 `null`;
5. 保留业务校验兜底(值的事实正确性不在 Schema 承诺内)。

## 7. 复现方式

```bash
mvn -q compile exec:java -Dexec.mainClass=com.wittycat.agentscope.Example7_DeepSeekStructuredOutput
```

单轮 2 次 API 调用;配置在 `application-local-deepseek.yml`(`llm:` 段,已被 .gitignore 排除),读取优先级 `-Dllm.*` > `DEEPSEEK_*` 环境变量 > local yml > application.yml。

## 8. 局限性说明

- 单模型、小样本(各 2 轮),结论是工程判读而非统计显著;`json_object` 两轮恰好合规,不能引申为"json_object 够用";
- 未测 `deepseek-v4-pro`、`reasoning.effort` 各档位;更长文本与嵌套 Schema 未覆盖;
- API 行为核实基于 2026-10-28 的官方文档与实测,端点行为后续版本可能调整。

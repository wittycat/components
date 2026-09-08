# sca2025-test —— Spring Cloud Alibaba 2025 微服务全家桶示例

[Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba)（SCA）是阿里微服务组件在 Spring Cloud 体系下的官方集成。本模块基于 **Spring Boot 3.5.3 + Spring Cloud 2025.0.0（Northfields）+ Spring Cloud Alibaba 2025.0.0.0 + JDK 17**，用一个"账户扣款 → 商品扣库存"的下单场景，把 SCA 生态的核心组件各演示一遍。

> **学习主线**：Nacos 注册与配置中心 → OpenFeign 声明式调用 → Gateway 网关（路由/JWT 鉴权/日志）→ Sentinel 流控 → Seata 分布式事务（AT 与 TCC 两种模式）→ Micrometer Tracing + Zipkin 链路追踪。

## 技术栈总览（本模块涉及的技术）

| 分类 | 技术 | 版本/说明 | 用在哪 |
|---|---|---|---|
| 基础框架 | Spring Boot | 3.5.3（父工程管理） | 全部模块 |
| 云原生基座 | Spring Cloud | 2025.0.0（Northfields） | BOM 统一管理版本 |
| 阿里系集成 | Spring Cloud Alibaba | 2025.0.0.0 | BOM 统一管理版本 |
| 注册/配置中心 | Nacos | 服务端 2.4.2.1，client 强制 2.3.2 | 服务发现 + 动态配置（`@RefreshScope`） |
| 远程调用 | OpenFeign | spring-cloud-starter-openfeign | consumer 声明式调用 provider |
| 客户端负载均衡 | Spring Cloud LoadBalancer | 2025.0.x 默认（Ribbon 已移除） | `lb://` 协议、Feign 调用 |
| API 网关 | Spring Cloud Gateway（WebFlux 版） | `spring-cloud-starter-gateway-server-webflux` | 路由转发、全局过滤器 |
| 流量防护 | Sentinel | 控制台 8858 | 服务限流 + 网关限流（gateway-v6x-adapter） |
| 分布式事务 | Apache Seata | 2.4.0（与服务端同版本），注册/配置中心托管在 Nacos | AT 模式 + TCC 模式 |
| 链路追踪 | Micrometer Tracing + Brave + Zipkin | 采样率 1.0，上报 `http://localhost:9411/api/v2/spans` | 全链路 traceId 透传 |
| 认证 | JJWT | 0.12.5（适配 Spring 6/Jakarta） | 网关统一 JWT 校验 |
| 持久层 | MyBatis + MySQL | mybatis-spring-boot-starter + mysql-connector-j | 两个业务库各自读写 |
| 日志 | Logback（MDC） | 日志模式含 `[%X{traceId},%X{spanId}]` | 日志与链路追踪关联 |
| 工具 | Lombok | — | `@Slf4j` 等 |

## 模块结构

```
sca2025-test/
├── pom.xml                  # 聚合工程：SC/SCA 双 BOM + nacos-client/seata 版本锁定 + 全局依赖(Tracing/Sentinel/Lombok)
├── sca-common/              # 公共模块：JwtUtil（JJWT 签发/校验，网关与业务服务共用）
├── sca-provider-service/    # 服务提供者（端口 8081，库 product）：商品扣减、Nacos 配置读取、TCC/AT 库存分支
├── sca-consumer-service/    # 服务消费者（端口 8082，库 account）：Feign 调用、JWT 登录、TCC/AT 账户分支（全局事务发起方）
└── sca-gateway-service/     # 网关（端口 8083）：路由 + JWT 鉴权过滤器 + 访问日志过滤器 + Sentinel 网关适配
```

## 中间件环境（启动前需备好）

| 中间件 | 地址 | 用途 |
|---|---|---|
| Nacos | 127.0.0.1:8848（账号 nacos/nacos） | 注册中心 + 配置中心；Seata 也注册在这 |
| MySQL | localhost:3306 | 两个库：`product`、`account`（含 TCC 用的 `*_freeze` 冻结表） |
| Seata Server | 2.4.0，注册/配置到 Nacos（SEATA_GROUP，data-id `seata.properties`） | TC 事务协调器 |
| Sentinel 控制台 | localhost:8858 | 查看服务/网关流控规则与监控 |
| Zipkin | localhost:9411 | 链路追踪 UI |

Nacos 上需创建的配置：

- `provider-service.yaml`、`consumer-service.yaml`（DEFAULT_GROUP）：各服务的扩展配置，含演示用的 `demo.config.message`、`address.name`；
- `seata.properties`（SEATA_GROUP）：Seata 数据源代理等参数，与 application.yml 里 `seata.*` 对应。

## 功能演示（每个组件怎么验）

启动顺序建议：Nacos → MySQL/Seata/Sentinel/Zipkin → provider(8081) → consumer(8082) → gateway(8083)。以下命令均走网关 8083（也可直连各服务）。

### 1. Nacos 服务发现 + OpenFeign + 负载均衡

```bash
curl "localhost:8083/consumer/call?name=sc"
```

`ConsumerTestController` 先用 `DiscoveryClient` 打印 provider 的在线实例，再通过 `@FeignClient("provider-service")` 发起调用——Feign 从 Nacos 拿地址、LoadBalancer 选实例，全程没有写死 IP。

### 2. Nacos 配置动态刷新

```bash
curl "localhost:8083/provider/config"
```

`ConfigController` 用 `@RefreshScope + @Value` 读取 Nacos 上的 `demo.config.message`；在 Nacos 控制台改这个值，不用重启再调一次接口即生效。

### 3. 网关路由 + JWT 统一鉴权

```bash
# 登录（白名单，换回 JWT token）
curl -X POST "localhost:8083/user/login?userName=tom&password=123456"
# 带 token 访问受保护接口
curl -H "Authorization: Bearer <token>" "localhost:8083/consumer/call?name=sc"
# 不带 token → 401 {"code":401,"msg":"缺少Token"}
```

`AuthFilterConfig`（GlobalFilter，@Order(-10)）校验 token 后把 `X-Login-UserId`、`X-User-Role` 透传给下游服务，业务方从 Header 直接拿登录态（`ATAccountController` 里有读取示例）。`LogFilterConfig`（@Order(-100)）负责打印每个请求的方法/路径/状态码/耗时。

### 4. Seata 分布式事务——AT 模式

```bash
curl -H "Authorization: Bearer <token>" \
  "localhost:8083/consumer/account2/deduct-at?userId=1&productId=1&count=2&money=99.9"
```

`ATAccountController#deduct` 标注 `@GlobalTransactional`，本地扣账户余额，再经 Feign 调 provider 的 `/atproduct/atdeduct` 扣库存。任一环节抛异常，两边自动回滚——AT 模式对业务零侵入，回滚靠 undo_log 反向补偿。

### 5. Seata 分布式事务——TCC 模式

```bash
curl -H "Authorization: Bearer <token>" \
  "localhost:8083/consumer/account/deduct?userId=1&productId=1&count=2&money=99.9"
```

`TCCAccountServiceImpl` 标注 `@LocalTCC`，Try 方法上用 `@TwoPhaseBusinessAction(name="deduct", commitMethod="confirm", rollbackMethod="cancel")` 声明 Confirm/Cancel 回调，参数用 `@BusinessActionContextParameter` 传递。Try 阶段扣可用余额并往 `account_freeze` 冻结表写记录（provider 侧同样有 `product_freeze`）；Cancel 阶段凭 xid 查冻结表做反向补偿，且"已 Cancel 过则拒绝业务"的空回滚/悬挂防护也在代码里体现了。

### 6. 链路追踪（Micrometer Tracing + Zipkin）

全链路（Gateway → consumer → provider）经 Brave 桥接上报 Zipkin，打开 `http://localhost:9411` 可看到完整调用链。三个服务的 Logback pattern 都带了 `[%X{traceId},%X{spanId}]`，日志与 Zipkin 里的 traceId 对得上；`/test-log` 接口可手动验证 MDC 输出。

### 7. Sentinel 流控

三个服务都配了 `sentinel.transport.dashboard: localhost:8858` 且 `eager: true`（启动即连控制台）。网关额外引入 `sentinel-spring-cloud-gateway-v6x-adapter` 并在 `GatewayConfiguration` 注册 `SentinelGatewayFilter` 与阻断异常处理器，可在控制台给路由维度配限流规则。

## 版本与依赖的坑（2025.0.x 变化）

- **Gateway starter 改名**：旧的 `spring-cloud-starter-gateway` 在 2025.0.0 被标记废弃，官方拆分为 WebFlux 专用 `spring-cloud-starter-gateway-server-webflux`（本模块用的这个）和 MVC 兼容版 `...-webmvc`（基于 Servlet，性能差不推荐）；配置前缀也相应变为 `spring.cloud.gateway.server.webflux.*`。
- **Ribbon 已移除**：2025.0.x 默认客户端负载均衡是 Spring Cloud LoadBalancer，需显式引入 `spring-cloud-starter-loadbalancer`，否则 `lb://` 转发和 Feign 调不通。
- **nacos-client 强制 2.3.2**：父 pom 里覆盖了版本，适配 Nacos 服务端 2.4.2.1；Nacos 3.x 默认走 gRPC 端口（9848），无需额外配置。
- **Seata 版本对齐**：客户端 `seata-spring-boot-starter` 锁定 2.4.0，必须与 Seata Server 版本一致；注意 `@GlobalTransactional` 的包是 `org.apache.seata.*`（Seata 2.x 已捐给 Apache）。
- **JJWT 0.12.5**：适配 Spring 6/Jakarta 的版本线，API 与老版（`Jwts.parser().setSigningKey(...)`）差异较大。

## 常见问题

- **服务起不来报 Nacos 连接失败**：确认 8848 可达、账号密码正确（application.yml 中 discovery/config 两处都要配 `username/password`，缺省容易报错）。
- **Seata 事务不生效**：检查 Seata Server 是否已注册进 Nacos 的 SEATA_GROUP、`seata.properties` 是否存在、`tx-service-group`/`vgroup-mapping` 与服务端配置是否一致。
- **Feign 404**：网关路由用了 `StripPrefix=1`，走网关访问时要带 `/consumer`、`/provider` 前缀；直连服务则不要带。
- **网关链路追踪 Header 丢失**：网关侧配置了 `spring.reactor.context-propagation: auto`，用于 Reactor 上下文自动传播 traceId，不要删。
- **依赖下载失败**：全局 settings 镜像的私有 Nexus 间歇性不在线，等私服恢复重试（加 `-U` 强刷缓存）；急用可临时写只含阿里云公共仓镜像的 settings 用 `mvn -s` 绕行。

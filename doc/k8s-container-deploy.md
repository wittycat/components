# K8s 容器化部署知识整理

> 复习用笔记,覆盖容器化部署从概念到落地的完整链路。示例以 Spring Boot 应用为例。

## 目录

1. [K8s 与 Docker 的关系](#1-k8s-与-docker-的关系)
2. [三大核心概念:Pod / Deployment / Service](#2-三大核心概念)
3. [Deployment YAML 解析](#3-deployment-yaml-解析)
4. [部署 YAML 放在哪里](#4-部署-yaml-放在哪里)
5. [Helm](#5-helm)
6. [HTTPRoute 与 Gateway API](#6-httproute-与-gateway-api)
7. [一条完整的生产部署链路](#7-一条完整的生产部署链路)
8. [速记卡](#8-速记卡)

---

## 1. K8s 与 Docker 的关系

一句话:**Docker 负责"造容器、跑容器",K8s 负责"管理一大堆容器"**。上下游配合,不是竞争。

| | Docker | Kubernetes (K8s) |
|---|---|---|
| 定位 | 容器引擎(单机) | 容器编排平台(集群) |
| 干什么 | 打镜像、启停单个容器 | 调度、扩缩容、自愈、滚动更新、服务发现、负载均衡 |
| 解决的问题 | 程序在每台机器上都能同样跑起来 | 几百个容器分布在几十台机器上,谁来管 |

比喻:Docker 是**集装箱**(把应用连同环境打包),K8s 是**港口调度系统**(决定集装箱放哪、坏了自动补)。

演进关键点:

- 早期:K8s 直接调 Docker 跑容器。
- K8s 1.24+:移除 dockershim,改用 **containerd**(本就是 Docker 拆出来的组件)经 CRI 接口对接。
- **Docker 构建的镜像完全不受影响**——镜像遵循 OCI 标准,Docker/containerd/Podman 都能跑。

典型分工:开发者 `Dockerfile` → `docker build` 出镜像 → 推镜像仓库 → 写 K8s YAML → `kubectl apply`。Docker 管"造",K8s 管"排兵布阵"。

---

## 2. 三大核心概念

一句话:**Deployment 管着一组 Pod 保证数量和版本,Service 给这组 Pod 一个不变的访问入口**。

```
                        外部用户
                           │
                    ┌──────▼───────┐
                    │   Ingress /  │   可选:对外域名/路径路由
                    │   HTTPRoute  │   (新标准叫 Gateway API)
                    └──────┬───────┘
                           │
              ┌────────────▼─────────────┐
              │  Service: order-svc      │   ★ 固定虚拟 IP + DNS 名
              │  ClusterIP: 10.96.0.100  │   按 label 找"在岗"的 Pod
              └────────────┬─────────────┘   在它们之间负载均衡
                           │  selector: app=order-service
      ┌────────────────────┼─────────────────────┐
      │   ┌────────────────▼─────────────────┐   │
      │   │ Deployment: order-service        │   │
      │   │ "要 3 个副本、用 1.2.0 的镜像"    │   │
      │   └────────────────┬─────────────────┘   │
      │          ┌─────────▼─────────┐           │
      │          │    ReplicaSet     │           │  真正数 Pod、挂了补新
      │          └─────────┬─────────┘           │  (Deployment 的执行者)
      │     ┌──────────────┼──────────────┐      │
      │ ┌───▼─────┐  ┌─────▼─────┐  ┌─────▼───┐  │
      │ │  Pod 1  │  │  Pod 2    │  │  Pod 3  │  │     K8s 集群
      │ │10.1.0.11│  │10.1.0.12  │  │10.1.0.13│  │   (可跨多台 Node)
      │ │ [容器]  │  │ [容器]    │  │ [容器]  │  │
      │ └─────────┘  └───────────┘  └─────────┘  │
      └──────────────────────────────────────────┘
```

### Pod —— 干活的,但朝不保夕

- **最小调度单位**。K8s 不直接调度容器,而是调度 Pod:1 个或多个共享网络和存储的容器打包(如业务容器 + 日志容器),互相用 `localhost` 访问。
- 每个 Pod 有 IP,但**只在活着期间有效**——节点宕机、OOM、滚动更新都会销毁重建,**新 Pod 换新 IP**。

### Deployment —— 只发号施令的经理

回答"要几个、用什么版本":

- 声明 `replicas: 3` 后持续对账:少了创建、多了删除(具体数数的是中间层 ReplicaSet,基本不用直接碰)。
- 改镜像版本 → 新建 ReplicaSet 跑新版 Pod、旧的缩到 0 = **滚动更新**;`rollout undo` 即回滚。
- 直觉:**生产上几乎从不裸建 Pod,永远通过 Deployment 等更高层资源管**——裸 Pod 挂了没人管。

### Service —— 号码不变的前台电话

回答"别人怎么找到这批 Pod":

- 创建后得到**终生不变的虚拟 IP(ClusterIP)**和集群内 DNS 名(`order-svc.prod.svc.cluster.local`),应用配置永远写这个。
- 按 `selector` 实时抓取匹配且健康的 Pod,轮询分发——新 Pod 自动纳入,死的自动剔除。
- 三种类型:**ClusterIP**(集群内,最常用)→ **NodePort**(每台节点开端口)→ **LoadBalancer**(对接云厂商公网 LB)。

### 比喻串联

| 概念 | 比喻 | 解决什么 |
|---|---|---|
| Pod | 一线员工(工位会换、会离职) | 真正干活 |
| Deployment | 部门经理(定编制 3 人,离职即补招) | 数量与版本的保障 |
| Service | 前台总机(号码永不变,转接在岗者) | 稳定的访问入口 |

**label 是这一切的粘合剂**:Deployment→Pod 靠它管,Service→Pod 靠它找。

---

## 3. Deployment YAML 解析

以 Spring Boot 应用为例的最小可用版:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  labels:
    app: order-service
spec:
  replicas: 3                    # 期望 3 个副本,挂了自动拉起
  selector:
    matchLabels:
      app: order-service         # 管哪些 Pod(按标签匹配)
  template:                      # ↓ Pod 模板
    metadata:
      labels:
        app: order-service       # 必须能被上面 selector 匹配
    spec:
      containers:
        - name: order-service
          image: registry.example.com/order-service:1.2.0
          ports:
            - containerPort: 8080
          resources:             # 资源限制,调度器按此分配机器
            requests:
              cpu: 250m          # 保底 0.25 核
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
          readinessProbe:        # 就绪探针:不通过不导流量
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
          livenessProbe:         # 存活探针:连续失败重启容器
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: DB_URL       # 敏感信息实际用 Secret,不明文写
              valueFrom:
                secretKeyRef:
                  name: order-db-secret
                  key: url
```

记忆结构 = **三层套娃**:

- **Deployment 层**(`spec.replicas` + `selector`):声明式 API,K8s 持续对账。
- **Pod 模板**(`template`):"每个 Pod 长什么样"。Pod 是最小调度单位。
- **容器层**(`containers`):Docker 那套——镜像、端口、环境变量、资源限制。

常用命令:

```bash
kubectl apply -f deployment.yaml             # 创建/更新(可反复执行)
kubectl get pods -l app=order-service -w     # 看滚动拉起过程
kubectl rollout status deployment/order-service
kubectl rollout undo deployment/order-service  # 一键回滚上一版
```

要点:改镜像 tag 再 `apply` 即触发**滚动更新**——新旧交替、探针通过才放流量、全程不中断,这是手动 `docker run` 做不到的。另外 **Deployment 只管 Pod,外部访问不到**,暴露还要配 Service / Ingress。

---

## 4. 部署 YAML 放在哪里

按规模递进三种方式:

### 方式 1:放应用仓库(小团队/起步)

```
order-service/
├── src/  ├── pom.xml  ├── Dockerfile
└── k8s/                # 或 deploy/、manifests/
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    └── configmap.yaml
```

好处:应用和部署定义一起改、一起评审。CI 构建镜像后接 `kubectl apply -f k8s/`。

### 方式 2:独立配置仓库 + GitOps(中大型主流)

应用仓库只放代码,YAML 放独立部署仓库,由 **Argo CD / Flux** 监听并自动同步到集群。发版 = 改仓库里镜像 tag 一行。

好处:变更历史/审批/回滚全在 Git,与发代码解耦——"部署状态以 Git 为准"是生产金标准。

### 方式 3:Helm Chart(YAML 多、要复用时)

多环境重复、只差几个值 → 模板化成 Chart,`helm upgrade --install ... -f values-prod.yaml`。

### 建议

学习期用方式 1:项目建 `k8s/` 目录 + 一条 CI apply 步骤,跑通"代码→镜像→仓库→apply"整条链。**反模式:文件不进 Git、只在某台机器手工 apply**——没人说得清线上跑的是什么。

---

## 5. Helm

一句话:**K8s 的包管理器,相当于 Java 世界的 Maven**。

解决的痛点:① 一个应用多份 YAML;② 多环境重复维护。解法:YAML 写成**模板**,可变值抽到 **values.yaml**,部署时渲染成纯 YAML 提交集群:

```
values.yaml (默认值)  ┐
                      ├─ helm 渲染 ──→ 纯 K8s YAML ──→ 提交给集群
--set 参数 (CI 覆盖)  ┘     (部署时一次性,集群里没有 helm 这个常驻服务)
```

关键认知:**Helm 只在执行那一刻起作用,是"渲染器 + 版本记录员"**。每次部署记一个版本(`--history-max 10` 留 10 个),`helm rollback <name> <版本号>` 一键回滚。

### chart 目录结构与各文件用途

```
my-app-chart/
├── Chart.yaml              ← chart 的"身份证"(元数据)
├── values.yaml             ← 默认配置集(所有"会变的东西"集中在这)
└── templates/
    ├── deployment.yaml     ← 干活主体:副本数/滚动策略/探针/资源/env 全模板化
    ├── service.yaml        ← 集群内稳定入口(ClusterIP)
    └── httproute.yaml      ← 外部流量大门(Gateway API 路由,亦可用 ingress.yaml)
```

| 文件 | 类比 | 要点 |
|---|---|---|
| Chart.yaml | pom.xml 的坐标段 | `version`=chart 模板版本;`appVersion`=应用版本,两者独立维护 |
| values.yaml | Maven properties | CI 用 `--set` / `--set-string` 逐项覆盖;replicaCount / image.tag / 环境变量 / JVM 参数都放这 |
| templates/*.yaml | — | Go 模板语法 `{{ .Values.xxx }}`,渲染时取值 |

### 学习技巧

```bash
helm template my-app ./my-app-chart --set springProfile=prod
```

本地打印渲染结果,把**模板 → values → 渲染产物**三者对照看,Helm 就透明了。

---

## 6. HTTPRoute 与 Gateway API

**HTTPRoute 不是 Ingress,是官方新标准 Gateway API 的核心路由对象,定位就是取代 Ingress。**

一句话答法:*"HTTPRoute 是 Gateway API 的路由对象,本质干的都是七层流量入口的活,是 Ingress 的继任者。"* (Gateway API 2023 年 GA)

### Ingress 的老毛病

2019 年转正的老 API,规范只定义了按 host/path 转发。**路径改写、header 操作、权重灰度**等规范都没有,各实现(Nginx/Traefik/Higress 等)全靠 `annotations` 自行扩展——同一件事每家写法不同,YAML 换网关实现就不可移植。

### Gateway API 的改进:三层对象 + 职责分离

```
GatewayClass   网关的"类"(实现是谁)                  ← 平台组管
     │
Gateway        实际入口:监听 80/443、证书、IP/域名      ← 平台组管,一次配好
     │  parentRefs: "把路由挂到我身上"
     ▼
HTTPRoute      路由规则:host/path/改写/header/权重      ← 应用开发者自己写
     │  backendRefs: 转给谁
     ▼
   Service → Pod
```

- 表达力进规范:URLRewrite 等是原生字段,不再依赖各家 annotation。
- 权限分层:运维管 Gateway(入口/证书),开发者只写 HTTPRoute。
- 同一 API 不同实现通吃(Envoy Gateway / Nginx / Higress…)。

### HTTPRoute 的角色定位

**它是 Gateway 与 Service 之间的桥梁**:`parentRefs` 接 Gateway(挂在谁身上),`backendRefs` 接 Service(转给谁),`rules` 定义中间怎么匹配/改写。

修正一个细节:HTTPRoute **只是配置声明,不在流量路径上**:

```
客户端 → Gateway 数据面(网关进程) → Service → Pod
              ↑
        HTTPRoute 只是告诉它"这么转发"的规则
```

网关控制器监听 HTTPRoute 后把规则下发到网关进程生效;删掉 HTTPRoute 网关照跑,只是不再转这条路由。

比喻:**Gateway 是总机,Service 是分机,HTTPRoute 是贴给总机的转接表**——表不是电话线,但没有它总机不知道往哪转。

### 学习取舍

- 存量系统/面试:Ingress 仍是事实主流,基本用法要会,知道 annotation 历史包袱。
- 新趋势:Gateway API 是官方钦定方向,新项目值得直接采用。

---

## 7. 一条完整的生产部署链路

把前面所有概念串成典型的"代码 → 生产"流水线(通用模板,可对照任意公司实践):

```
git tag (如 release-1.2.0 / test-xxx)
   │
   ├─ 质量门禁:  mvn verify + SonarQube / 测试
   ├─ 发布包:    mvn deploy → Nexus (可选)
   ├─ 构建镜像:  Dockerfile 多阶段构建 → push 到 Harbor/Nexus 等镜像仓库
   └─ 部署:      helm upgrade --install → K8s 指定命名空间
                    ├── 按 tag 填 image.tag
                    ├── 按环境(test/prod)切换 profile 与凭证
                    └── 凭证从 CI Variables 注入,不进代码库
```

配套的三个文件,职责各一:

| 文件 | 角色 |
|---|---|
| `Dockerfile` | 造镜像:多阶段构建,builder 阶段编译,运行阶段只放 jar |
| `chart/templates/` | deployment + service + 路由 三件套模板 |
| CI 配置 | tag 触发:构建→推镜像→helm 部署,按环境注入凭证 |

### 生产级实践要点

- **多阶段构建**:maven 镜像只当 builder,运行镜像用 JRE-alpine,体积从几百 MB 降到几十 MB。
- **`exec java` 接管 PID 1**(`ENTRYPOINT ["sh","-c","exec java ..."]`):SIGTERM 直达 JVM,K8s 删 Pod 时 Spring 走优雅停机。少了 `exec` 信号被 sh 吞掉,Pod 只能等 30s 强杀。
- **滚动策略 `maxSurge: 1, maxUnavailable: 0`**:先起新 Pod、探针通过再停旧,全程副本不缩水。
- **容器里调 JVM**:`JAVA_OPTS` 经 values 注入,用 `-XX:MaxRAMPercentage=60` 按 limit 比例算堆——不写死 -Xmx,改资源配额时 JVM 自动跟随。
- **凭证注入链路**:CI 平台 Variables(masked)→ `--set-string env[N]` → 容器环境变量 → Spring `${XXX:}` 占位符;敏感项不写默认值 = 启动强校验,漏配即失败,不带病运行。
- **DB 迁移并发**:多副本滚动更新期间新旧 Pod 共存,Flyway 这类迁移工具需配 PostgreSQL advisory lock(或类似机制)防止并发改表。
- **探针选型**:Spring 应用优先 `/actuator/health` 的 httpGet(粒度准);`tcpSocket` 只能证明端口通,不能证明应用就绪。

---

## 8. 速记卡

| 问题 | 一句话答案 |
|---|---|
| K8s 和 Docker 关系? | Docker 造+跑容器(单机),K8s 编排管理容器(集群);镜像走 OCI 标准互通 |
| 最小调度单位? | Pod(1..n 个共享网络的容器),IP 朝不保夕 |
| Deployment 干嘛? | 声明副本数和版本,持续对账+滚动更新+回滚(经 ReplicaSet 执行) |
| Service 干嘛? | 给一组 Pod 固定 VIP/DNS,按 label 负载均衡 |
| 外部怎么进来? | 旧:Ingress;新:Gateway(GatewayClass/Gateway/HTTPRoute) |
| HTTPRoute 是 Ingress 吗? | 不是,是替代者;Gateway 和 Service 之间的桥梁(纯配置,不在流量路径上) |
| YAML 放哪? | 应用仓库 k8s/ 目录(起步)→ GitOps 独立仓库(主流)→ Helm(多环境) |
| Helm 是什么? | K8s 包管理器(Maven 类比);模板+values 渲染;部署时一次性,带版本可回滚 |
| 生产裸建 Pod 行吗? | 不行,永远用 Deployment 等高层资源管 |
| label 的意义? | 一切粘合剂:Deployment 管 Pod、Service 找 Pod、路由转 Service 都靠它 |

### 复习自测题

1. Pod 重建后 IP 变了,依赖它的客户端为什么不受影响?(Service + DNS)
2. 滚动更新时新 Pod 什么时候开始接流量?(readinessProbe 通过后;配合 maxUnavailable: 0)
3. `exec java` 在容器里解决什么问题?(PID 1 → SIGTERM 直达 JVM → 优雅停机)
4. 为什么 Helm 能回滚?集群里有 helm 服务吗?(部署时记版本;没有,渲染器+版本记录员)
5. HTTPRoute 删了网关还在转发吗?(这条路由没了;网关本身照跑)
6. HTTPRoute 里 parentRefs / backendRefs / rules 各定义什么?(接哪个网关 / 转给哪个 Service / 怎么匹配改写)

# user-management 项目宪法

> 本文件是项目最高约束。所有 `spec.md`、`plan.md`、`tasks.md` 都必须通过下方 Constitution Check 才能进入实现。

## Core Principles

### I. 分层单向依赖（不可协商）

依赖方向只能是 `Controller → Service → Mapper → 数据库`，禁止反向引用、禁止跨层调用。

- **Controller** 只做三件事：接收参数、调用 Service、包装 `ApiResponse`。**不得出现任何业务判断、SQL、事务注解。**
- **Service** 是业务逻辑与事务边界的唯一归属地。跨多个 Mapper 的写操作必须标 `@Transactional`。
- **Mapper** 只负责 SQL 映射，不得写业务分支（`if` 判断业务含义即为越界，`<if>` 拼接查询条件除外）。
- **config / security** 不依赖 service 之外的上层包。

### II. 契约先行，DTO 隔离

对外接口的数据形状先定，再写实现。

- 所有入参使用 `dto` 包下的 `XxxRequest` 类，并加 `@Valid` 与 `jakarta.validation` 注解；**禁止**直接用 `Map<String, Object>` 或实体类接收入参。
- 所有出参使用 `ApiResponse<T>` 统一包装；**新增接口禁止直接返回 entity**。存量返回 `User` 的接口，在因其他需求而触碰该文件时顺带迁移为 DTO。
- 响应 DTO **不得包含** `password`、`passwordHash`、`secret`、`token` 等敏感字段。

### III. 测试与功能同步交付

- Service 层每个新增 public 方法至少一个单元测试（Mockito 模拟 Mapper）。
- Mapper 层新增 SQL 必须有 H2 集成测试（现有 `UserMapperTest` 为范本）。
- Controller 层新增端点必须有 `@WebMvcTest` + MockMvc 用例，覆盖至少一条成功路径与一条校验失败路径。
- **合并前 `mvn test` 必须全绿**；跳过测试的提交一律拒绝。

### IV. 安全默认拒绝

- 新端点默认受 JWT 保护。要公开，必须在 `plan.md` 中写明理由，并在 `SecurityConfig` 白名单里显式列出。
- 密码只以 BCrypt 哈希形式落库，禁止明文、禁止可逆加密。
- 日志与响应体中不得出现密码、完整 token、完整身份证/手机号。
- 鉴权失败返回 401、权限不足返回 403，不得用 200 包裹错误。

### V. 配置外置且类型安全

- 所有可变配置写进 `application.yml`，通过 `@ConfigurationProperties` 绑定（参照 `JwtProperties`），**禁止**在业务代码里散落 `@Value`。
- 密钥、连接串不写进仓库，用环境变量注入并给出默认值说明。
- 新增配置项必须在 `application.yml` 与 README 同步说明。

### VI. 小步提交、可回滚

- 一次提交对应一个 Functional Requirement（FR 编号写在 commit message 里）。
- 不顺手重构无关文件；破坏性变更（改表结构、改接口路径）必须在 `plan.md` 写迁移步骤与回滚方案。

## 技术栈约束

| 组件 | 版本 | 说明 |
|---|---|---|
| Java | 17 | 语言级别固定，可用 record / sealed / text block |
| Spring Boot | 3.2.0 | 不轻易升级大版本，升级需单独提 issue |
| MyBatis | starter 3.0.3 | SQL 写在 `resources/mapper/*.xml` |
| jjwt | 0.12.3 | JWT 签发与解析 |
| 数据库 | H2（开发）/ MySQL 8（生产） | 两者 SQL 方言需兼容，避免方言特有函数 |
| 缓存 | Redis / 本地 simple | 通过 `spring.cache.type` 切换 |
| 消息 | RabbitMQ | 异步解耦场景使用 |
| 注册中心 | Nacos | 生产启用，本地默认关闭 |

补充约束：

- **不引入 Lombok**（项目现有代码为显式 getter/setter，保持一致），示例与生成代码一律手写访问器。
- 新增第三方依赖必须满足：`plan.md` 说明用途 + 有替代方案对比 + 不引入已知 CVE。
- 统一使用 Spring Boot 管理的依赖版本，不手写版本号（除 jjwt、MyBatis starter 这类已显式声明的）。

## 包结构与命名规范

```
com.example.demo
├── controller   XxxController        路径统一 /api/xxx
├── service      XxxService           业务与事务
├── mapper       XxxMapper            接口，XML 在 resources/mapper/
├── entity       Xxx                 数据库映射对象，仅限内部流转
├── dto          XxxRequest / XxxResponse / ApiResponse
├── config       XxxConfig / XxxProperties   （含全局异常处理）
└── security     JWT 过滤器、UserDetails 实现、Token 工具
```

- 类名不做缩写（`UserService` 而非 `UsrSvc`）。
- 常量集中在 `常量` 所在类的 `private static final`，不放配置文件。
- 异常：自定义业务异常继承 `RuntimeException`，由 `GlobalExceptionHandler` 统一映射；**禁止吞异常**（空 catch 块）。

## 开发流程与质量门

1. `/speckit-constitution` 定原则（本文件，改动需升版本）
2. `/speckit-specify` 写规格，产出 `specs/NNN-xxx/spec.md`
3. `/speckit-clarify` 消除歧义（规格里出现 `[NEEDS CLARIFICATION]` 必须清空）
4. `/speckit-plan` 出方案，产出 `plan.md`，填写 Constitution Check
5. `/speckit-tasks` 拆任务，产出 `tasks.md`
6. `/speckit-implement` 逐条实现
7. `/speckit-analyze` 三文档一致性复核（可选，建议在 implement 前）

**质量门（全部满足才可合并）**：

| 门 | 标准 |
|---|---|
| 编译 | `mvn -q compile` 无警告级错误 |
| 测试 | `mvn test` 全绿，新增代码有对应测试 |
| 契约 | 新端点走 DTO + `ApiResponse`，响应无敏感字段 |
| 安全 | 新端点默认受保护，白名单有书面理由 |
| 一致性 | `spec.md` 的 FR 与 `tasks.md` 的条目一一对应 |

## Governance

- 本宪法优先于个人编码偏好与历史习惯；与宪法冲突的存量代码，在触碰该文件时按宪法修正。
- 修改宪法必须：说明动机 → 影响范围 → 升 `Version`（语义化版本）→ 更新 `Last Amended`。
- 每次 `/speckit-implement` 结束前，逐条自查 Constitution Check，勾选结果写入 PR 描述或提交说明。
- 已知存量违规（待随需求修复）：`User.password` 缺少序列化屏蔽，导致用户查询接口可能返回密码哈希。

**Version**: 1.0.0 | **Ratified**: 2026-10-19 | **Last Amended**: 2026-10-19

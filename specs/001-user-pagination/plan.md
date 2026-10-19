# Implementation Plan: 用户列表分页与条件查询

**Branch**: `001-user-pagination` | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-user-pagination/spec.md`

## Summary

把 `GET /api/users` 从"全量返回 `List<User>`"改造为"物理分页 + 条件过滤 + 白名单排序，返回 `PageResponse<UserSummary>`"。

技术路径：MyBatis XML 中用 `<sql>` 片段复用过滤条件，`SELECT ... LIMIT #{size} OFFSET #{offset}` 取当页数据，另一条 `SELECT COUNT(*)` 复用同一片段求总数；排序字段由 Java 侧白名单映射成列名后以 `${}` 拼入（唯一允许的 `${}` 场景，值不来自用户输入原文）；`keyword` 做通配符转义后走 `LIKE ... ESCAPE '\'`。响应侧新增 `UserSummary` 投影，从根上切断 `password` 外泄。

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 3.2.0、MyBatis starter 3.0.3、spring-boot-starter-validation（已引入，无需新增依赖）

**Storage**: H2（开发，`schema.sql` 初始化）/ MySQL 8（生产）

**Testing**: JUnit 5 + Mockito（Service）、`@MybatisTest` + H2（Mapper）、`@WebMvcTest` + MockMvc（Controller）

**Target Platform**: Windows/Linux 上的 Spring Boot 内嵌 Tomcat 服务端

**Project Type**: 单模块 Web 服务（single project）

**Performance Goals**: 1 万条数据下单页查询 P95 < 200 ms

**Constraints**: 单页上限 100 条；禁止 `${}` 接收用户输入；响应不得出现 `password`

**Scale/Scope**: 用户表预期 ≤ 100 万行；本次改动涉及 1 个 Controller、1 个 Service、1 个 Mapper（接口+XML）、新增 3 个 DTO/响应类

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 要求 | 本方案如何满足 | 结论 |
|---|---|---|---|
| I. 分层单向依赖 | Controller 不写业务、Service 持事务 | Controller 只做 `@Valid` + 转调 `UserService.findPage`；无事务注解出现在 Controller | ✅ |
| II. 契约先行，DTO 隔离 | 入参 Request、出参 DTO、响应无敏感字段 | 新增 `PageQuery`（入参，带校验注解）、`PageResponse<T>`、`UserSummary`（不含 password） | ✅ |
| III. 测试同步交付 | Service/Mapper/Controller 三层测试 | Mapper 用 H2 测条件组合与通配符转义；Service 测参数归一；Controller 测 400 分支 | ✅ |
| IV. 安全默认拒绝 | 新端点默认受保护；无敏感字段外泄 | 复用现有 JWT 过滤器，路径 `/api/users` 已在受保护范围；`UserSummary` 无 password 字段 | ✅ |
| V. 配置外置 | 分页默认值可配 | `page.default-size` 与 `page.max-size` 写入 `application.yml`，用 `@ConfigurationProperties` 绑定 `PageProperties` | ✅ |
| VI. 小步可回滚 | 破坏性变更标注迁移 | 响应结构变更已在 spec SC-005 标注为破坏性；回滚方式：还原 `UserController#findAll` 与删除新增类即可 | ✅ |

**GATE 结果**：通过。无违反项，无需 Complexity Tracking 例外。

## Project Structure

### Documentation (this feature)

```text
specs/001-user-pagination/
├── spec.md              # 已完成
├── plan.md              # 本文件
└── tasks.md             # /speckit-tasks 产出
```

### Source Code (repository root)

```text
src/main/java/com/example/demo/
├── controller/
│   └── UserController.java              # 改：findAll(...) 接收 PageQuery 并返回 PageResponse<UserSummary>
├── service/
│   └── UserService.java                 # 改：新增 findPage(PageQuery) → PageResponse<UserSummary>
├── mapper/
│   └── UserMapper.java                  # 改：新增 findPageByCondition(...) 与 countByCondition(...)
├── dto/
│   ├── PageQuery.java                   # 新：分页与筛选入参
│   ├── PageResponse.java                # 新：泛型分页响应
│   └── UserSummary.java                 # 新：不含 password 的用户视图
├── config/
│   ├── PageProperties.java              # 新：default-size / max-size 配置绑定
│   └── GlobalExceptionHandler.java      # 改：补 ConstraintViolationException / MethodArgumentNotValidException 映射（若已有则确认覆盖）
└── entity/
    └── User.java                        # 不改（password 仍保留，仅不再对外序列化）

src/main/resources/
├── mapper/UserMapper.xml                # 改：新增 <sql id="pageWhere"> + 两条查询语句
└── application.yml                      # 改：新增 page.default-size / page.max-size

src/test/java/com/example/demo/
├── mapper/UserMapperTest.java           # 改：新增分页、条件组合、通配符转义用例
├── service/UserServiceTest.java         # 改：新增 size 上限与默认值归一化用例
└── controller/UserControllerTest.java   # 改：新增 400 参数校验用例与分页响应结构断言
```

**Structure Decision**：沿用现有 `com.example.demo` 七层分包（controller/service/mapper/dto/config/entity/security），不引入新包、不新增第三方依赖。

## 关键实现决策

1. **总数与列表共用 `<sql>` 片段**：`<sql id="pageWhere">` 内放全部 `<if>` 条件，两条 `SELECT` 通过 `<include>` 引用，杜绝"列表过滤了、count 没过滤"的经典 bug（FR-008）。
2. **排序用白名单映射，不用字符串拼接**：`Map.of("id","id","name","name","age","age","createdAt","created_at")` 把 `sortBy` 映射成真实列名，`sortDir` 只允许 `asc`/`desc` 两个常量。这是全项目唯一允许 `${}` 进入 SQL 的位置，且入参已经过白名单过滤（FR-012）。
3. **keyword 转义**：`keyword.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")` 后拼 `%...%`，SQL 侧写 `LIKE #{kw} ESCAPE '\'`。H2 与 MySQL 均支持该语法（FR-011）。
4. **分页参数归一在 Service 层**：`page<0` 抛 `IllegalArgumentException` 由全局异常处理器转 400；`size>max-size` 直接拒绝而非静默截断（FR-010 要求可预期）。
5. **响应投影用 `UserSummary`**：不采用 `@JsonIgnore` 屏蔽现有实体字段，避免影响其他内部使用 `User` 的场景（如认证流程需要 password）。这同时修掉了宪法里登记的存量违规。

## Complexity Tracking

无违反宪法之处，本表留空。

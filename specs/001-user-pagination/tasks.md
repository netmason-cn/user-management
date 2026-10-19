---
description: "Tasks for 001-user-pagination"
---

# Tasks: 用户列表分页与条件查询

**Input**: Design documents from `/specs/001-user-pagination/`

**Prerequisites**: plan.md（必需）、spec.md（必需）

**Tests**: 本项目宪法 Core Principle III 要求测试与功能同步交付，因此测试任务为**必做**，不属可选项。

**Organization**: 按用户故事分组，每个故事可独立实现与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: 所属用户故事（US1 / US2 / US3）
- 每条任务均含确切文件路径

## Path Conventions

单模块项目：`src/main/java/com/example/demo/`、`src/main/resources/`、`src/test/java/com/example/demo/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 新增文件骨架与配置项，不涉及业务逻辑

- [ ] T001 [P] 新增 `src/main/java/com/example/demo/dto/PageResponse.java`：泛型分页响应，字段 `items`、`page`、`size`、`totalElements`、`totalPages`、`hasNext`，手写 getter/setter（宪法禁止 Lombok）
- [ ] T002 [P] 新增 `src/main/java/com/example/demo/dto/UserSummary.java`：字段 `id`、`name`、`email`、`age`、`role`、`createdAt`，**不得**含 `password`
- [ ] T003 [P] 新增 `src/main/java/com/example/demo/config/PageProperties.java`：`@ConfigurationProperties(prefix="page")`，字段 `defaultSize=20`、`maxSize=100`；并在 `src/main/resources/application.yml` 增加 `page:` 配置段

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 入参契约与 SQL 骨架，所有用户故事都依赖

**⚠️ CRITICAL**: 本阶段完成前，任何用户故事都无法开始

- [ ] T004 新增 `src/main/java/com/example/demo/dto/PageQuery.java`：字段 `page`（默认 0）、`size`、`keyword`、`role`、`ageMin`、`ageMax`、`sortBy`（默认 `createdAt`）、`sortDir`（默认 `desc`），加 `@Min`/`@Max`/`@Pattern` 校验注解
- [ ] T005 [P] 在 `src/main/java/com/example/demo/mapper/UserMapper.java` 新增两个方法：`List<User> findPageByCondition(@Param("q") PageQuery q, @Param("sortColumn") String col, @Param("sortDir") String dir, @Param("offset") int offset)`、`long countByCondition(@Param("q") PageQuery q)`
- [ ] T006 在 `src/main/resources/mapper/UserMapper.xml` 新增 `<sql id="pageWhere">`，内含 role、ageMin、ageMax 三组 `<if>` 条件（keyword 条件留到 US2 追加），全部以 AND 组合
- [ ] T007 在 `src/main/resources/mapper/UserMapper.xml` 写两条语句：`findPageByCondition`（`<include refid="pageWhere"/>` + `ORDER BY ${sortColumn} ${sortDir}` + `LIMIT #{size} OFFSET #{offset}`）、`countByCondition`（同样 `<include>` 同一片段）

**Checkpoint**: 契约与 SQL 骨架就绪，`mvn -q compile` 必须通过

---

## Phase 3: User Story 1 - 分页浏览用户列表 (Priority: P1) 🎯 MVP

**Goal**: `GET /api/users?page=&size=` 返回当页数据 + 分页元信息

**Independent Test**: 造 55 条数据，`page=0&size=20` → 20 条 / total 55 / pages 3 / hasNext true；`page=2&size=20` → 15 条 / hasNext false

### Tests for User Story 1 ⚠️

> 先写测试并确认失败，再写实现

- [ ] T008 [P] [US1] 在 `src/test/java/com/example/demo/mapper/UserMapperTest.java` 新增分页用例：55 条数据下第 1 页 20 条、第 3 页 15 条、`countByCondition` 返回 55
- [ ] T009 [P] [US1] 在 `src/test/java/com/example/demo/controller/UserControllerTest.java` 新增用例：`page=0&size=20` 返回 200 且响应含 `totalElements`/`totalPages`/`hasNext`；`size=500` 返回 400；`page=-1` 返回 400

### Implementation for User Story 1

- [ ] T010 [US1] 在 `src/main/java/com/example/demo/service/UserService.java` 新增 `findPage(PageQuery q)`：归一化 `page`（负值拒绝）、给 `size` 兜底为 `defaultSize`、超 `maxSize` 抛异常、计算 `offset`、调用 Mapper 两个方法、装配 `PageResponse<UserSummary>`
- [ ] T011 [US1] 在 `src/main/java/com/example/demo/service/UserService.java` 新增 `private UserSummary toSummary(User u)` 转换方法
- [ ] T012 [US1] 改 `src/main/java/com/example/demo/controller/UserController.java`：`findAll()` → `findPage(@Valid PageQuery query)`，返回 `ResponseEntity<ApiResponse<PageResponse<UserSummary>>>`
- [ ] T013 [US1] 在 `src/main/java/com/example/demo/config/GlobalExceptionHandler.java` 确认/补充 `MethodArgumentNotValidException`、`ConstraintViolationException`、`IllegalArgumentException` 三类异常统一映射为 400 `ApiResponse`
- [ ] T014 [US1] 在 `src/main/java/com/example/demo/service/UserService.java` 补 `@Transactional(readOnly = true)` 到 `findPage`

**Checkpoint**: 分页可用且参数校验生效，`mvn test` 中 US1 相关用例全绿

---

## Phase 4: User Story 2 - 按条件筛选用户 (Priority: P2)

**Goal**: keyword / role / ageMin / ageMax 可单独或组合生效，总数与列表一致

**Independent Test**: 5 个不同属性用户，四种条件各查一次，结果与 `countByCondition` 一致；`keyword=50%` 只匹配字面含 `50%` 的记录

### Tests for User Story 2 ⚠️

- [ ] T015 [P] [US2] 在 `src/test/java/com/example/demo/mapper/UserMapperTest.java` 新增：`keyword` 大小写不敏感匹配 name 与 email、`role` 精确匹配、`ageMin`/`ageMax` 闭区间、三条件 AND 组合
- [ ] T016 [P] [US2] 在 `src/test/java/com/example/demo/mapper/UserMapperTest.java` 新增通配符转义用例：数据含 `a%b`，查询 `keyword=%` 不得返回全部记录
- [ ] T017 [P] [US2] 在 `src/test/java/com/example/demo/service/UserServiceTest.java` 新增：空字符串 keyword 视为无此条件（Mockito 验证传入 Mapper 的查询对象字段为 null）

### Implementation for User Story 2

- [ ] T018 [US2] 在 `src/main/java/com/example/demo/dto/PageQuery.java` 增加 `getEscapedKeyword()` 方法：null/空白返回 null，否则转义 `\`、`%`、`_` 并包 `%...%`（返回计算属性，XML 侧以 `#{q.escapedKeyword}` 引用，MyBatis 按 getter 取值）
- [ ] T019 [US2] 在 `src/main/java/com/example/demo/service/UserService.java` 的 `findPage` 中归一化 keyword（空白 → null）
- [ ] T020 [US2] 在 `src/main/resources/mapper/UserMapper.xml` 的 `<sql id="pageWhere">` 中补 `AND (LOWER(name) LIKE LOWER(#{q.escapedKeyword}) ESCAPE '\' OR LOWER(email) LIKE LOWER(#{q.escapedKeyword}) ESCAPE '\')`

**Checkpoint**: 四种筛选可独立与组合生效，转义用例通过

---

## Phase 5: User Story 3 - 指定排序 (Priority: P3)

**Goal**: `sortBy`（白名单）+ `sortDir`（asc/desc）生效，非法值被拒

**Independent Test**: `sortBy=name&sortDir=asc` 结果按 name 升序；`sortBy=password` 返回 400 且未执行 SQL

### Tests for User Story 3 ⚠️

- [ ] T021 [P] [US3] 在 `src/test/java/com/example/demo/mapper/UserMapperTest.java` 新增：按 `name` 升序、按 `createdAt` 降序的返回顺序断言
- [ ] T022 [P] [US3] 在 `src/test/java/com/example/demo/controller/UserControllerTest.java` 新增：`sortBy=password` 与 `sortDir=up` 均返回 400

### Implementation for User Story 3

- [ ] T023 [US3] 在 `src/main/java/com/example/demo/service/UserService.java` 新增常量 `SORT_COLUMNS = Map.of("id","id","name","name","age","age","createdAt","created_at")` 与 `resolveSortColumn(String sortBy)`，未知字段抛 `IllegalArgumentException`
- [ ] T024 [US3] 在 `src/main/java/com/example/demo/dto/PageQuery.java` 用 `@Pattern(regexp="(?i)asc|desc")` 约束 `sortDir`，`sortBy` 留空时取默认 `createdAt`

**Checkpoint**: 三个用户故事全部可独立验证，构成完整特性

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T025 [P] 在 `src/main/resources/application.yml` 确认 `page.default-size`、`page.max-size` 生效，并在 `README` 或 `docs/api.md` 补接口说明（新增查询参数表）
- [ ] T026 [P] 在 `src/test/java/com/example/demo/controller/UserControllerTest.java` 新增安全断言：任意成功响应 JSON 中不含 `"password"` 字符串（覆盖 SC-002）
- [ ] T027 回归：执行 `mvn test`，确认原有 `findById`/`create`/`update`/`delete`/`login`/`register` 用例无回归
- [ ] T028 自查 Constitution Check 六条并记录到提交说明；确认新端点仍在 JWT 保护范围内（不加入 `SecurityConfig` 白名单）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**：无依赖，可立即开始；T001-T003 三者可并行
- **Foundational (Phase 2)**：依赖 Phase 1；T005-T007 顺序为 接口 → `<sql>` 片段 → 语句
- **User Story 1 (Phase 3)**：依赖 Phase 2；是 MVP，必须最先完成
- **User Story 2 (Phase 4)**：依赖 US1 的 `findPage` 骨架（T010）
- **User Story 3 (Phase 5)**：依赖 US1；与 US2 无文件冲突，可并行
- **Polish (Phase 6)**：依赖三个故事全部完成

### User Story Dependencies

- **US1 (P1)**：无故事依赖，独立可交付
- **US2 (P2)**：依赖 US1 提供的查询入口，但筛选逻辑可独立测试
- **US3 (P3)**：依赖 US1 的分页 SQL，排序逻辑独立

### Within Each User Story

测试先行（先红后绿）→ DTO/常量 → Service → Controller → 回归

### Parallel Opportunities

- T001 / T002 / T003 并行
- T008 / T009 并行（不同测试文件）
- T015 / T016 / T017 并行（不同测试文件或不同用例区）
- T021 / T022 并行
- T025 / T026 并行

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + Phase 2（骨架与 SQL 片段）
2. Phase 3（分页可用，`GET /api/users?page=0&size=20` 返回分页结构）
3. **停下验证**：跑 `mvn test`，用 H2 手工请求一次，确认 `password` 不再出现在响应里
4. 此时已可交付——这是本特性最有价值的一刀

### Incremental Delivery

MVP 通过后，按 P2（筛选）→ P3（排序）逐个追加，每次追加后重跑全量测试。三个故事完成即特性完成，无需额外重构窗口。

---

## Notes

- `User.java` 全程不改，`password` 字段保留供认证流程使用；对外投影由 `UserSummary` 负责
- 全项目唯一允许 `${}` 进入 SQL 的位置是 `ORDER BY`，且入参已经过 `SORT_COLUMNS` 白名单映射
- 若 `GlobalExceptionHandler` 已能处理本特性涉及的异常，T013 只需补充缺失的一条，不要重写现有逻辑
- 破坏性变更（响应结构）需在提交说明中显式标注，对应 spec SC-005

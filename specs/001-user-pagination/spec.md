# Feature Specification: 用户列表分页与条件查询

**Feature Branch**: `001-user-pagination`

**Created**: 2026-10-19

**Status**: Draft

**Input**: User description: "当前 GET /api/users 一次性返回全部用户，数据量一大就不可用。改成支持分页、按关键字/角色/年龄筛选、可指定排序，并且响应里不能带密码。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 分页浏览用户列表 (Priority: P1)

运营人员在后台查看用户列表，每次只看一页（默认 20 条），需要知道总共有多少页、能不能翻到下一页。

**Why this priority**: 这是当前接口最直接的痛点。没有分页，数据超过几百条接口就会变慢甚至拖垮内存，其他功能都建立在列表可用这个前提上。

**Independent Test**: 造 55 条用户数据，请求 `GET /api/users?page=0&size=20`，应返回 20 条 + `totalElements=55` + `totalPages=3`；请求 `page=2` 返回剩余 15 条。

**Acceptance Scenarios**:

1. **Given** 库中有 55 个用户，**When** 请求 `GET /api/users?page=0&size=20`，**Then** 返回 20 条记录，`totalElements=55`、`totalPages=3`、`hasNext=true`
2. **Given** 库中有 55 个用户，**When** 请求 `page=2&size=20`，**Then** 返回 15 条记录，`hasNext=false`
3. **Given** 不传任何参数，**When** 请求 `GET /api/users`，**Then** 按默认 `page=0&size=20` 处理，不报错
4. **Given** 请求 `size=500`，**When** 服务端处理，**Then** 返回 400 并说明单页上限为 100

---

### User Story 2 - 按条件筛选用户 (Priority: P2)

客服需要按名字或邮箱关键字找人，按角色（ADMIN/USER）过滤，按年龄段圈人。

**Why this priority**: 分页只解决了"能看"，筛选才解决"找得到"。用户量上万后，翻页找人不可接受。

**Independent Test**: 造 5 个不同角色、不同年龄的用户，分别用 `keyword`、`role`、`ageMin/ageMax` 各查一次，结果集与预期完全一致，且 `totalElements` 与列表长度匹配。

**Acceptance Scenarios**:

1. **Given** 存在 `alice@example.com`，**When** 请求 `keyword=alice`，**Then** 只返回 name 或 email 含 "alice" 的用户（大小写不敏感）
2. **Given** 存在 3 个 ADMIN，**When** 请求 `role=ADMIN`，**Then** 只返回这 3 个，`totalElements=3`
3. **Given** 用户年龄分布 18~60，**When** 请求 `ageMin=20&ageMax=30`，**Then** 只返回闭区间内的用户
4. **Given** 同时传 `keyword=li&role=USER&ageMin=25`，**When** 查询，**Then** 三个条件以 AND 组合生效
5. **Given** `keyword` 中含 `%` 或 `_`，**When** 查询，**Then** 这些字符按字面量匹配，不被当作通配符

---

### User Story 3 - 指定排序 (Priority: P3)

管理员希望按注册时间倒序看最新用户，或按名字正序做成通讯录。

**Why this priority**: 属于体验优化，不影响主流程可用性，但缺了会让倒查最新用户很别扭。

**Independent Test**: 请求 `sortBy=name&sortDir=asc`，返回结果中 name 呈升序；请求 `sortBy=createdAt&sortDir=desc`，`createdAt` 呈降序。

**Acceptance Scenarios**:

1. **Given** 默认不传排序参数，**When** 请求列表，**Then** 按 `createdAt` 降序（新用户在前）
2. **Given** 请求 `sortBy=name&sortDir=asc`，**When** 查询，**Then** 结果按 name 升序
3. **Given** 请求 `sortBy=password&sortDir=asc`，**When** 查询，**Then** 返回 400（字段不在白名单），且不执行任何 SQL

---

### Edge Cases

- `page` 超出总页数（如 999）：返回空 `items`，`totalElements` 仍为真实总数，`totalPages` 正确。
- `page<0` 或 `size<1`：返回 400，经 `GlobalExceptionHandler` 统一格式。
- `role` 传入不存在的值（如 `GUEST`）：返回空列表，不报错（避免枚举变更导致前端崩溃）。
- 查询期间有用户被删除：`totalElements` 与当前页条数可能轻微不一致，属可接受最终一致。
- 关键字为空白字符串 `keyword=`：视为未传条件，全量分页。
- 空表：返回 `items=[]`、`totalElements=0`、`totalPages=0`、`hasNext=false`。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `GET /api/users` 必须接受 `page`（默认 0，从 0 开始）与 `size`（默认 20，**上限 100**）两个分页参数。
- **FR-002**: 响应必须包含分页元信息：`items`、`page`、`size`、`totalElements`、`totalPages`、`hasNext`。
- **FR-003**: 必须支持 `keyword` 参数，对 `name` 与 `email` 做大小写不敏感的模糊匹配（OR 关系）。
- **FR-004**: 必须支持 `role` 参数做精确匹配。
- **FR-005**: 必须支持 `ageMin`、`ageMax` 参数做闭区间过滤，允许只传其中一个。
- **FR-006**: 必须支持 `sortBy`（白名单：`id`、`name`、`age`、`createdAt`，默认 `createdAt`）与 `sortDir`（`asc`/`desc`，默认 `desc`）。
- **FR-007**: 多个筛选条件之间为 AND 关系；`keyword` 内部对 name/email 为 OR 关系。
- **FR-008**: 列表查询与总数统计必须使用同一组过滤条件，禁止各写一套 SQL 导致数字对不上。
- **FR-009**: 响应元素**禁止包含** `password` 字段（宪法 Core Principle II / IV 红线）。
- **FR-010**: 非法参数（`page<0`、`size<1`、`size>100`、`sortBy` 不在白名单、`sortDir` 非 asc/desc）必须返回 400 且响应体为统一 `ApiResponse` 错误结构。
- **FR-011**: `keyword` 中的 SQL 通配符（`%`、`_`）必须按字面量处理，不得被解释为通配。
- **FR-012**: 排序字段只能来自白名单常量，**禁止**把用户输入直接拼进 SQL（防注入）。

### Key Entities

- **User（已存在）**：`id`、`name`、`email`、`age`、`role`、`createdAt`、`updatedAt`；`password` 仅内部使用，不对外。
- **PageQuery（新增请求对象）**：承载 page/size/keyword/role/ageMin/ageMax/sortBy/sortDir，并做参数校验。
- **PageResponse&lt;T&gt;（新增响应对象）**：承载分页元信息 + 数据列表。
- **UserSummary（新增响应对象）**：User 的对外投影，去掉 password。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 1 万条用户数据、单机 H2 环境下，单页查询 P95 延迟低于 200 ms。
- **SC-002**: 任意查询响应体中 `password` 字段出现次数为 **0**（含嵌套对象与错误响应）。
- **SC-003**: `size=500` 的请求 100% 返回 400，不产生全表扫描。
- **SC-004**: 同一组条件下的 `totalElements` 与全量遍历计数一致（误差 0）。
- **SC-005**: 现有 `GET /api/users` 的调用方（若有）在只传分页参数时行为可预期：默认参数下不再返回全量数据，需在发布说明中标注为破坏性变更。

## Assumptions

- 前端/调用方可以接受响应结构变更（`ApiResponse<List<User>>` → `ApiResponse<PageResponse<UserSummary>>`），本项目当前无外部消费者。
- 数据库需为 `name`、`email`、`role`、`age` 建索引（MySQL 生产环境），H2 开发环境忽略。
- 分页采用物理分页（SQL `LIMIT/OFFSET`），不做游标分页；数据量预期在百万级以内。
- 不做全文检索；`keyword` 为简单 `LIKE`，后续如有需要可替换为搜索引擎（不在本特性范围）。

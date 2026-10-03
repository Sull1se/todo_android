---
title: Todo 应用与架构
purpose: 理解当前业务行为、数据归属、实现入口及已有约束
status: 当前有效
owner: 当前任务 Agent；维护者负责审阅与合并
scope: Android 应用的业务、Compose 界面、状态和持久化
updated: 2026-10-02
verification: 已静态检查
verified: 2026-10-02；对照 3.0.0 源码静态核对行为表、数据模型与实现地图
---

# Todo 应用与架构

[文档总索引](index.md) · [开发与发布](development.md) · [当前问题与解决进度](quality.md)

## 定位与范围

Todo 是一个本地 Android 待办应用，使用 Kotlin、Jetpack Compose / Material 3 和 Room。产品介绍与功能概览集中在本页，构建指引集中在开发文档。

应用支持多清单、主题色、任务标题与纯文本备注、完成状态、插旗重排、手势拖拽、批量操作与底部已完成项一键清理。已实现版本化本地全量数据导入导出；没有实现云同步、AI 调用、提醒调度或富文本编辑。

核心排序与完成交互模型：任务强制归属自建清单，不支持无清单待办；纯排序引擎（`TaskOrderEngine`）根据清单展示开关将任务严格划分为汇总排序范围 S 与各独立清单排序范围 L(id)，每个范围内基于清单完成模式维护主区域 M 与底部已完成区域 C；C 项清旗且禁止插旗/拖拽/移动；普通恢复追加至对应主区域末尾，撤销恢复则精准还原原旗帜与原位；提供底部已完成项一键清理、多选“退出/全选”文字按钮、独立计时的撤销卡片堆叠（`UndoMessageHost`）；数据库 schema 为 6，通过 `AppMetadata` 单行表完成排序范围偏好的一次性初始化；备份格式为版本 2 并兼容读取合法版本 1。

运行时业务数据保存在本机；[主 Manifest](../app/src/main/AndroidManifest.xml) 未声明网络权限，应用代码未接入网络服务。但 `allowBackup="true"`，且 [备份规则](../app/src/main/res/xml/backup_rules.xml) 与 [数据提取规则](../app/src/main/res/xml/data_extraction_rules.xml) 仍为模板，不能据此承诺数据绝不会进入系统备份或设备迁移。实际备份行为尚未验证；APP-006 按维护者要求无限期暂缓，仅在维护者主动提出解决时恢复，具体条件见 [当前问题](quality.md)。

## 当前可观察行为

以下按源码描述当前实现，作为回归基线；未闭环问题独立记入 [当前问题](quality.md)，验证证据按规则记入 [验收与验证历史](verification-history.md)。

| 场景 | 操作与预期 | 边界与实现 |
| --- | --- | --- |
| 首次进入 | 显示虚拟“汇总”；无清单时新建待办应提示先创建清单 | `selectedListId = -1`；空清单提示与输入表单生命周期相互隔离，标题输入框布局就绪后请求焦点，零清单时新建不崩溃 |
| 新建任务 | 首页加号打开快速新建弹窗；必须选择归属清单，标题非空才能保存，可选插旗 | 移除底部文字插旗方框，保留右上角旗帜图标；禁止创建无清单待办 |
| 编辑任务 | 点击卡片进入详情，修改标题与备注；C 项隐藏/禁用插旗 | 文本保存与业务命令解耦，详情改旗与首页走统一命令并支持撤销；正向进场与退场均配置 120ms 对称淡入淡出；`exitRequested` 防重锁与入场 `RESUMED` 状态门禁，路由限定 `popBackStack("home", inclusive = false)`，防止转场连击导致白屏崩溃 |
| 清单管理 | 重命名、更换主题色、设置完成模式、勾选是否在汇总展示；确认删除时同时删除所属任务 | 外键级联删除，无回收站；删除清单使该清单所有任务撤销失效 |
| 汇总展示与排序 | 勾选展示的清单纳入范围 S，未勾选的维护独立范围 L(id)；S 主区域允许自由拖拽 | 参与汇总的独立清单主区域禁用拖拽（以 S 投影显示）；展示切换按 F/U/C 块稳定拼接 |
| 完成模式 | `0` 保持原位；`1` 将已完成项清旗放入默认折叠的“已完成”区域 C | 逐任务遵循所属清单模式；取消汇总独立完成模式；模式切换批量移动任务并清旗 |
| 插旗与取消 | 主任务区左滑切换；插旗置顶对应主区域，取消插旗置底对应主区域 | 仅主区域 M 允许插旗；C 项严格禁止插旗与左滑手势；左滑后上下滚动中断时，通过页面快照与中断即刻发帧同步旗帜与顺序显示，无需切页刷新 |
| 普通恢复与撤销 | C 项普通取消完成追加至所属主区域末尾（汇总中为整个 S.M 末尾）；撤销恢复原旗帜与原位 | 普通恢复不恢复原旗帜；完成撤销精准恢复原 M 索引与原旗帜；撤销插旗接入视口协调器，通过 `UndoFlagResult` 快照与 `PinPosition` 锁定点击撤销时的绝对视口位置，消除首项 key 跟随与视口跟底；`UndoMessageHost` 倒计时解耦 snapshot 消除脏重组 |
| 撤销提示堆叠 | 插旗与收至底部完成生成独立撤销卡片；多条最新在上堆叠，基准时长 4 秒 | `UndoMessageHost` 独立计时，弹窗期间暂停计时，左滑可手动关闭单个提示 |
| 一键清理 | “已完成（N）”标题右侧提供“清理”文字按钮，弹窗确认后永久删除当前页面/清单所属 C 项 | 仅删除收至底部的已完成项，保持原位的已完成项不受影响；事务内严格复核候选 |
| 任务拖拽 | 汇总主区域及未展示清单主区域应支持长按卡片拖动，松手持久化 | 参与汇总的独立清单主区域及多选模式禁用任务拖拽；C 项禁止拖拽；页面数据流与拖拽生命周期统一管理，机制见下文“手势实现与保留决策” |
| 清单拖拽 | 长按自建清单 Tab 横向排序，松手保存 | “汇总”和“新建列表”固定且禁用换位目标与拖拽手柄（`enabled = false`），自建 Tab 拖拽支持视觉覆盖与库原有回弹，不发生拖拽闪烁；清单 Tab 顺序决定展示批量加入时的块拼接顺序 |
| 多选 | 顶部左侧 `TextButton("退出")`，右侧 `TextButton("全选")`；批量完成/取消/移动/删除 | 混合选中包含 C 项时禁止跨清单移动（提示先取消完成）；换 Tab 自动退出多选 |
| 主主题 | 支持跟随系统（默认）、浅色与深色三态切换；首页右上角设“应用设置”入口 | 深色模式采用 OLED 纯黑（`#000000`）背景，卡片与弹窗以深灰分层，保留清单色条纹、蓝色主点缀与插旗红色。主题偏好持久化于 `todo_prefs`（`appearance_mode`），即刻生效且系统栏（状态栏与导航栏）按深浅有效模式动态更新图标对比度。清单旁齿轮独立保留为“清单设置”。动态配色、无障碍专项与多语言不在当前范围 |

## 模块与数据流

```text
MainActivity / TodoApp：组装数据库、仓储、偏好、ViewModel；定义 home / detail/{taskId}
                    ↓
HomeScreen / TaskDetailScreen → TodoViewModel → TodoRepository → TodoDao → Room
        │               ↑             │                │
        │               │     TaskOrderEngine          │
  UndoMessageHost ← UndoController                     │
                    └── StateFlow ──────────────── Flow ──────────────────┘
```

- [MainActivity.kt](../app/src/main/java/com/example/MainActivity.kt) 手动组装依赖；控制初始化加载门禁，页面共享一个 ViewModel。
- [HomeScreen.kt](../app/src/main/java/com/example/ui/HomeScreen.kt) 负责展示筛选、完成分组 M/C、清理入口、新建与设置弹窗、手势与多选；统一采用固定 `Modifier.animateItem(placementSpec = tween(180))` 保证位移自然过渡，废弃 Modifier 动态切换。
- [UndoMessageHost.kt](../app/src/main/java/com/example/ui/components/UndoMessageHost.kt) 与 [UndoController.kt](../app/src/main/java/com/example/ui/UndoController.kt) 管理多条撤销卡片堆叠、独立计时与手势关闭。
- [TaskOrderEngine.kt](../app/src/main/java/com/example/data/TaskOrderEngine.kt) 纯排序业务引擎，处理 S/L 范围划分、M/C 规范编号、F/U 头尾拼接、恢复与跨清单移动。
- [TaskDetailScreen.kt](../app/src/main/java/com/example/ui/TaskDetailScreen.kt) 详情展示与编辑，C 项禁用旗帜，文本保存与业务重排解耦。
- [TodoViewModel.kt](../app/src/main/java/com/example/ui/TodoViewModel.kt) 统一管理单向页面流 `PageTasks(pageId, tasks, revision)`，`tasks` 派生为只读 `StateFlow`；严格维护拖拽生命周期（`startDragSession`, `reorderTasks`, `saveTaskOrder`, `cancelDrag`），保证临时重排即刻发布至页面，并结合 `commitScopeReorder` 在事务中做原子完整性校验与持久化。
- [TodoRepository.kt](../app/src/main/java/com/example/data/TodoRepository.kt) 持有数据库并在 Room 事务（`withTransaction`）中执行全部持久化、完整范围重排 `commitScopeReorder`、一次性偏好迁移初始化与导入导出。
- [TodoDao.kt](../app/src/main/java/com/example/data/TodoDao.kt) 提供各范围快照查询、定向字段更新与批量删除，统一并列三键 `(displayOrder, timestamp, id)`。

## 数据模型与偏好

数据库入口为 [AppDatabase.kt](../app/src/main/java/com/example/data/AppDatabase.kt)，文件名 `todo_database`；schema 版本升至 6。实体定义在 [Models.kt](../app/src/main/java/com/example/data/Models.kt)。

| 实体 | 字段与含义 |
| --- | --- |
| `TaskList` / `task_lists` | `id` 自增主键；`name`；`themeColor`；`displayOrder`；`completionMode`；`showInSummary`（布尔，默认 1）；`timestamp` |
| `Task` / `tasks` | `id` 自增主键；非空 `listId: Int`；`title`；纯文本 `content`；`isCompleted`；`displayOrder`；`timestamp`；默认 `false` 的 `isFlagged` |
| `AppMetadata` / `app_metadata` | 单行内部元数据表，主键 `id=1`，`summaryOrderInitialized: Boolean`，记录首次升级排序范围划分与偏好迁移是否完成 |

`tasks.listId` 有外键索引，外键约束为非空且级联删除 `CASCADE`。不再允许无清单任务。

偏好由 Room 统一接管展示配置，`todo_prefs` SharedPreferences 中仅保留运行期轻量记录：

| 键 | 默认值 | 用途 |
| --- | --- | --- |
| `last_list_id` | `-1` | 上次选中清单 |
| `appearance_mode` | `system` | 主题外观模式（`system` 跟随系统 / `light` 浅色 / `dark` 深色） |

旧 `all_list_completion_mode` 与 `summary_excluded_list_ids` 已在首次启动初始化后被迁移并清理。每个排序范围（S 或各 L）内的 `displayOrder` 均为从 0 开始的规范连续整数。

## 手势实现与保留决策

活动实现为 `sh.calvin.reorderable`，由 `HomeScreen` 中 `rememberReorderableLazyListState`、`ReorderableItem` 和 `longPressDraggableHandle` 驱动。清单 Tab 采用系统边界换位排除（`ReorderableItem(enabled = tab.first > 0)`，保留库原有视觉覆盖与回弹）；空清单新建弹窗与输入表单生命周期分离，表单离开组合自动取消焦点请求。

左滑视口协调独立于界面层，实现在 [SwipeViewportCoordinator.kt](../app/src/main/java/com/example/ui/SwipeViewportCoordinator.kt)。当前手势与视口体系的关键机制：

- **拖拽生命周期与页面流一致性**：界面不维护独立可写任务列表，`pageTasks` 携带 `revision` 驱动界面；拖拽 `onMove` 立即同步发布最新 `PageFrame`；松手通过 `commitScopeReorder` 校验原子完整性后批量写库；支持换页、取消与语义冲突自动回滚。
- **左滑动画与防闪烁**：统一使用 `Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween(180))`，不动态切换 `suppressItemAnimations`；`SwipeableTaskItem` 采用真实物理偏移量与沉淀判定（`abs(offset) <= 0.5f && isSettled`），消除伪回位与整表闪烁。
- **回顶时序与视口协调**：七阶段状态机（`Idle -> AwaitResultAndPageAndCard -> ApplyFrame -> AwaitConsumed -> AwaitLayout -> Placing -> Scrolling`）；采用不可变意图（`KeepKey`, `PinPosition`, `AnimateTop`）与 `(pageId, epoch, actionId, generation)` 四元组屏障；首 Pass 测量核对可见 M 项后经 180ms 补位再平滑回顶，避免末段停顿与位置反跳。
- **撤销插旗视口保持**：仓储层 `undoFlag` 返回携带原索引与跨区域状态的 `UndoFlagResult` 事务快照；`TodoViewModel.undoAction` 回调透传 `UndoActionResult`；视口协调器含 `AwaitUndoResultAndPage` 状态与 `onUndoFlag` 入口，在撤销操作发出时锁定当前视口，页面刷新后使用 `PinPosition` 锚定当时视口位置，避免首项 key 跟随与列表跳底；`UndoMessageCard` 倒计时解耦 Compose snapshot 状态，消除高频重组触发的视口抖动。
- **详情退场防重与栈保护**：`TaskDetailScreen` 使用 `DetailExitAction`、`exitRequested` 防重锁与 `DisposableEffect` 监听 `NavBackStackEntry.lifecycle`（仅 `RESUMED` 响应退出）；`MainActivity` / `TodoApp` 统一路由退场 `performDetailExit`，限定 `popBackStack("home", inclusive = false)` 并核验当前 entry 路由，防止快速连点或转场未完成期间重复出栈导致回退栈清空白屏。
- **中断与滚动帧发布一致性**：`SwipeViewportCoordinator` 引入携带 `pageId` 的 `PageDataSnapshot`，在用户滑动触发、滚动中断或手势回退时，通过 `publishLatestFrameForActivePage()` 立即发布活动页面的最新数据库快照帧，使目标卡片旗帜与顺序瞬时同步；同时保护进行中的独立拖拽会话不受干扰。
- **详情页面进退转场**：`MainActivity` 为 `home` 退出与 `detail/{taskId}` 进入配置 120ms 正向淡入淡出（`fadeOut`/`fadeIn(tween(120))`），为 `detail/{taskId}` 退出与 `home` 返回配置 120ms 逆向淡入淡出（`popExitTransition`/`popEnterTransition`），过渡干脆且与防重锁及生命周期保护并存。

汇总主区域与未展示清单主区域均允许拖拽，参与汇总的独立清单主区域禁用拖拽；所有页面底部已完成区域 C 均禁止拖拽。

## 数据管理与本地文件导入导出

应用通过 Android Storage Access Framework（SAF）提供本地全量数据导入与导出，不接入云端同步或三方云服务，不申请外部存储敏感权限：

- **数据传输组件**：[BackupModels.kt](../app/src/main/java/com/example/data/transfer/BackupModels.kt)、[JsonBackupCodec.kt](../app/src/main/java/com/example/data/transfer/JsonBackupCodec.kt)、[DataTransferManager.kt](../app/src/main/java/com/example/data/transfer/DataTransferManager.kt) 与 [DataTransferDialogs.kt](../app/src/main/java/com/example/ui/components/DataTransferDialogs.kt)。
- **文件格式**：独立 UTF-8 JSON 文件，格式标识固定为 `com.aistudio.todo.backup`，格式版本为整数 `2`。清单包含 `showInSummary`，任务 `listId` 非空。向后兼容读取合法版本 1（新清单默认参与汇总）；对任何缺失或 null 归属的文件整体拒绝导入。
- **校验与边界保护**：上限单文件 32 MiB、清单 10,000 个、待办 100,000 个；流式解析严格检查必需字段与外键引用完整性；导入前将目标模式为 1 的 C 项旗帜规范化为 false。
- **备份集合强校验**：顶层 JSON 必须显式包含 `lists` 与 `tasks` 键；缺失任一集合整体拒绝导入并抛出格式异常，原库保持不变且预检时清理临时文件；显式空数组（`"lists": []`, `"tasks": []`）保持合法支持。

## 其它入口与限制

- [Theme.kt](../app/src/main/java/com/example/ui/theme/Theme.kt)、[Color.kt](../app/src/main/java/com/example/ui/theme/Color.kt)、[Type.kt](../app/src/main/java/com/example/ui/theme/Type.kt)、[Icons.kt](../app/src/main/java/com/example/ui/theme/Icons.kt)：主题、排版与图标。
- [strings.xml](../app/src/main/res/values/strings.xml)：应用名；多数其它文案直接写在 Composable 中，未形成完整多语言资源。三态主题切换见上文“主主题”。
- 详情编辑草稿使用 `rememberSaveable(taskId)` 持久化（`title`、`content`、`isFlagged` 与 `draftInitialized` 门禁），支持旋转屏幕与进程/组合重建下的状态恢复。
- Android 安装包标识以构建配置为准（见 [开发与发布](development.md) 的配置表）。
- 已有拖拽时才挂载图层、左滑背景按条件绘制等优化；没有足以证明稳定 120 Hz 或具体提升比例的测量报告。Roborazzi 相关依赖仍在构建配置中声明，但当前没有活动截图断言或截图回归。

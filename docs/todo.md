---
title: Todo 应用与架构
purpose: 理解当前业务行为、数据归属、实现入口及已有约束
status: 当前有效
owner: 当前任务 Agent；维护者负责审阅与合并
scope: Android 应用的业务、Compose 界面、状态和持久化
updated: 2026-10-30
verification: 已静态检查
verified: 2026-10-30；对照 3.4.3 源码静态核对行为表、桌面小卡片、数据模型与实现地图
---

# Todo 应用与架构

[文档总索引](index.md) · [开发与发布](development.md) · [当前问题与解决进度](quality.md)

## 定位与范围

Todo 是一个本地 Android 待办应用，使用 Kotlin、Jetpack Compose / Material 3 和 Room。产品介绍与功能概览集中在本页，构建指引集中在开发文档。

应用支持多清单、主题色、任务标题与纯文本备注、完成状态、插旗重排、手势拖拽、批量操作、底部已完成项一键清理、可选“合并为信息”的清单待办格式与桌面小卡片。已实现版本化本地全量数据导入导出；没有实现云同步、AI 调用、提醒调度或富文本编辑。

核心排序与完成交互模型：任务强制归属自建清单，不支持无清单待办；纯排序引擎（`TaskOrderEngine`）根据清单展示开关将任务严格划分为汇总排序范围 S 与各独立清单排序范围 L(id)，每个范围内基于清单完成模式维护主区域 M 与底部已完成区域 C；C 项清旗且禁止插旗/拖拽/移动；普通恢复追加至对应主区域末尾，撤销恢复则精准还原原旗帜与原位；提供底部已完成项一键清理、多选“退出/全选”文字按钮、独立计时的撤销卡片堆叠（`UndoMessageHost`）；数据库 schema 为 7，通过 `AppMetadata` 单行表完成排序范围偏好的一次性初始化；备份格式为版本 3 并兼容读取合法版本 2 与版本 1。

运行时业务数据保存在本机；[主 Manifest](../app/src/main/AndroidManifest.xml) 未声明网络权限，应用代码未接入网络服务。但 `allowBackup="true"`，且 [备份规则](../app/src/main/res/xml/backup_rules.xml) 与 [数据提取规则](../app/src/main/res/xml/data_extraction_rules.xml) 仍为模板，不能据此承诺数据绝不会进入系统备份或设备迁移。实际备份行为尚未验证；APP-006 按维护者要求无限期暂缓，仅在维护者主动提出解决时恢复，具体条件见 [当前问题](quality.md)。

## 当前可观察行为

以下按源码描述当前实现，作为回归基线；未闭环问题独立记入 [当前问题](quality.md)，验证证据按规则记入 [验收与验证历史](verification-history.md)。

| 场景 | 操作与预期 | 边界与实现 |
| --- | --- | --- |
| 首次进入 | 显示虚拟“汇总”；无清单时新建待办应提示先创建清单 | `selectedListId = -1`；空清单提示与输入表单生命周期相互隔离，标题输入框布局就绪后请求焦点，零清单时新建不崩溃 |
| 新建任务 | 首页加号打开快速新建弹窗；必须选择归属清单，标题非空才能保存，可选插旗 | 移除底部文字插旗方框，保留右上角旗帜图标；禁止创建无清单待办。插旗新建落为对应主区域首项；若新任务属于当前页，保存后由 `SwipeViewportCoordinator.onFlaggedTaskCreated` 等其成为当前帧 M 首项且布局按该帧测量后平滑回顶，换页、用户拖动列表、左滑或撤销协调开始以及 2.5 秒未就绪时放弃；未插旗新建追加主区域末尾，视口保持 |
| 编辑任务 | 点击卡片进入详情，修改标题与备注；C 项隐藏/禁用插旗 | 文本保存与业务命令解耦，详情改旗与首页走统一命令并支持撤销；正向进场与退场均配置 120ms 对称淡入淡出；`exitRequested` 防重锁与入场 `RESUMED` 状态门禁，路由限定 `popBackStack("home", inclusive = false)`，防止转场连击导致白屏崩溃 |
| 清单管理 | 重命名、更换主题色、设置完成模式与待办格式、勾选是否在汇总展示；确认删除时同时删除所属任务 | 外键级联删除，无回收站；删除清单使该清单所有任务撤销失效 |
| 待办格式 | 新建列表与列表设置中可选“标题 + 详细信息”（默认）或“合并为信息”。合并格式下新建、编辑（含桌面弹窗）只有一个可自由换行的“信息”输入框；应用列表与桌面卡片显示信息跳过空行后的前两行，第一行用标题样式、第二行用详细信息样式，各一行、超出省略；插旗不变 | 存储不变：信息首个非空行存为 `title`，其后各行原样存为 `content`（去掉末尾空行），因此默认 → 合并时标题占首行、换行接原详细信息，切换不改写任务；合并 → 默认时首行为标题，其余行为详细信息，并在同一事务中去掉详细信息首尾空行（`TaskText`、`TodoRepository.updateList`）。汇总页与汇总卡片按各任务所属清单的格式显示；新建弹窗切换到不同格式的清单时互相换算已输入内容 |
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
| 桌面小卡片 | 常驻显示一个清单（默认汇总）的未完成待办，可快捷新建、左侧勾选完成、点待办在桌面弹窗编辑、卡片内滚动；只有一个可调大小的入口（2×2 至 4×3） | 卡片列表本身不提供拖动排序与插旗；详见下文“桌面小卡片” |

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
- widget 包（画面构建 [WidgetViews.kt](../app/src/main/java/com/example/widget/WidgetViews.kt)、接收器与渲染 [WidgetReceivers.kt](../app/src/main/java/com/example/widget/WidgetReceivers.kt)） 实现桌面小卡片，经 `WidgetDataAccess` 复用同一 Room 单例与 `TodoRepository`，见下文“桌面小卡片”。

## 数据模型与偏好

数据库入口为 [AppDatabase.kt](../app/src/main/java/com/example/data/AppDatabase.kt)，文件名 `todo_database`；schema 版本为 7（`MIGRATION_6_7` 增加 `contentMode`）。实体定义在 [Models.kt](../app/src/main/java/com/example/data/Models.kt)。

| 实体 | 字段与含义 |
| --- | --- |
| `TaskList` / `task_lists` | `id` 自增主键；`name`；`themeColor`；`displayOrder`；`completionMode`；`showInSummary`（布尔，默认 1）；`timestamp`；`contentMode`（待办格式，`0` 标题 + 详细信息、`1` 合并为信息，默认 0，见 [TaskText.kt](../app/src/main/java/com/example/data/TaskText.kt)） |
| `Task` / `tasks` | `id` 自增主键；非空 `listId: Int`；`title`；纯文本 `content`；`isCompleted`；`displayOrder`；`timestamp`；默认 `false` 的 `isFlagged` |
| `AppMetadata` / `app_metadata` | 单行内部元数据表，主键 `id=1`，`summaryOrderInitialized: Boolean`，记录首次升级排序范围划分与偏好迁移是否完成 |

`tasks.listId` 有外键索引，外键约束为非空且级联删除 `CASCADE`。不再允许无清单任务。

偏好由 Room 统一接管展示配置，`todo_prefs` SharedPreferences 中仅保留运行期轻量记录：

| 键 | 默认值 | 用途 |
| --- | --- | --- |
| `last_list_id` | `-1` | 上次选中清单 |
| `appearance_mode` | `system` | 主题外观模式（`system` 跟随系统 / `light` 浅色 / `dark` 深色） |

旧 `all_list_completion_mode` 与 `summary_excluded_list_ids` 已在首次启动初始化后被迁移并清理。每个排序范围（S 或各 L）内的 `displayOrder` 均为从 0 开始的规范连续整数。

桌面小卡片另用 `todo_widget_prefs` 保存 `widget_list_id_<卡片编号>`（各卡片绑定的页面，删除卡片时清除），不进入导入导出备份。

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

## 桌面小卡片

卡片直接以 RemoteViews 构建，不使用 Jetpack Glance。实现位于 widget 包：画面构建 [WidgetViews.kt](../app/src/main/java/com/example/widget/WidgetViews.kt)（布局 `res/layout/widget_todo.xml`、`widget_task_row.xml` 及 `layout-v31` 变体），接收器与渲染 [WidgetReceivers.kt](../app/src/main/java/com/example/widget/WidgetReceivers.kt)，列表项点击与刷新 [WidgetSync.kt](../app/src/main/java/com/example/widget/WidgetSync.kt)。

| 场景 | 行为 | 边界与实现 |
| --- | --- | --- |
| 尺寸 | 卡片选择器中只有一个“待办清单”入口：默认 2×2，可在 2×2 与 4×3 之间调整。宽度 ≥ 250dp 时显示待办数量；有详细信息（合并格式为信息的第二行）的待办在任何宽度都显示第二行，各一行、超出省略 | [todo_widget_info.xml](../app/src/main/res/xml/todo_widget_info.xml)（`targetCell` 2×2、`minResize` 110dp、`maxResizeHeight` 400dp，宽度上限由桌面列数决定），接收器为 `TodoWidgetReceiver`（继承 `BaseTodoWidgetReceiver`） |
| 显示清单 | 添加卡片时弹出清单选择（默认“汇总”），Android 12+ 可长按卡片重新选择；每个卡片实例独立 | 绑定页保存在 `todo_widget_prefs` 的 `widget_list_id_<卡片编号>`，删除卡片时清除、备份恢复换号时迁移；绑定清单被删除时自动回退汇总（`resolveWidgetListId`） |
| 待办列表 | 只显示未完成项，顺序与应用页面一致（插旗项在前并显示旗帜）；汇总卡片每项带所属清单色条；卡片内上下滚动；空列表显示“暂无待办”；合并格式清单的待办显示信息的前两个非空行 | 读取与应用相同的 DAO 查询后过滤 `isCompleted`；`ListView` + `RemoteCollectionItems`（经 `androidx.core:core-remoteviews` 的 `RemoteViewsCompat`，Android 12 以下由其兼容服务提供）实现滚动，每行一个稳定 id；卡片列表不支持拖动排序、插旗与左滑 |
| 勾选完成 | 点左侧圆圈后待办立即从卡片消失（Android 12+ 桌面可能先闪现打勾），并按所属清单完成模式完成；卡片内不提供撤销，可在应用中取消完成 | Android 12+ 行布局为原生 `CheckBox`（`layout-v31/widget_task_row.xml`，`android:button` 为圆形勾选图），由桌面本地立即切换勾选态，卡片推送的勾选框恒为未勾选；Android 12 以下 RemoteViews 不支持复选框，用同一圆形图标的 `ImageView` 点击完成。列表项点击模板为发往 `WidgetItemActionReceiver` 的可变广播 `PendingIntent`，每行以 fill-in 补上动作与任务编号，勾选不经过任何 Activity。`WidgetItemActionReceiver` 在 `goAsync` 内调用 `WidgetTaskActions.complete`：先沿用卡片当前内容（不读库）重绘、移除该任务，再调用 `TodoRepository.completeTask`；处理中的任务在读库刷新时同样隐藏，未实际写入时结束后按数据库恢复显示；重复点击去重并强制重绘；已完成或已删除的任务不写入；完成后经 `ExternalTaskChanges` 通知同进程 ViewModel 使该任务的旧撤销记录失效 |
| 编辑待办 | 点待办在桌面上弹出编辑小窗口，不进入应用：与应用编辑页一致，可改标题 / 详细信息（合并格式为“信息”）、插旗、删除（二次确认）、保存或取消，保存后回到桌面 | 点正文经同一广播模板由 `WidgetItemActionReceiver` 启动 `WidgetEditTaskActivity`（桌面以可见窗口发出的广播允许接收方随即启动 Activity）；透明主题 + `WidgetTaskEditDialog`；`WidgetTaskActions.saveEdit` 先改文字，旗帜变化时按插旗规则切换（收至底部清单中的已完成项不改旗帜），`delete` 删除任务，均经 `ExternalTaskChanges` 通知应用；任务已不存在时提示并关闭 |
| 快捷新建 | 点“＋”在桌面上弹出透明窗口，复用应用的新建待办弹窗（标题、详细信息、插旗、归属清单），保存后回到桌面 | `WidgetQuickAddActivity`；默认归属清单与应用内新建一致（`resolveQuickAddDefaultListId`）：卡片显示具体清单时默认该清单，显示汇总（含绑定清单已删除而回退汇总）时不预选，需在弹窗中选择后才能保存；无清单时提示并可跳转应用创建 |
| 打开应用 | 点卡片标题或空列表打开应用并切到该清单 | `MainActivity` 为 `singleTop`，由 `WidgetLaunch` 的 Intent 解析为 `AppLaunchRequest`，`TodoApp` 中 `handleLaunchRequest` 切换清单并导航。已知桌面限制：荣耀 MagicOS 桌面从应用返回卡片时，缩回动画按直角绘制第三方卡片，约 0.5 秒后恢复圆角，卡片声明圆角无效 |
| 同步与主题 | 应用、卡片任一入口改动数据后卡片自动刷新；配色跟随应用“应用设置”的外观模式（跟随系统 / 浅色 / OLED 深色） | `WidgetRefresher` 监听 Room `tasks` / `task_lists` 表失效与 `appearance_mode` 偏好，收到改动约 50ms 后（合并同一操作的连续写入）立即刷新，刷新期间的新改动合并为一次；跟随系统时布局使用带 `values-night` 变体的 `widget_*` 颜色资源，由桌面按自身昼夜状态解析；强制浅色 / 深色时由 `WidgetColors`（色值与资源一致，有测试校验）直接设置文字色、图标着色与背景图 |
| 渲染机制 | 添加、调整尺寸、数据或主题变化后卡片直接显示最新内容，不依赖后台任务 | `WidgetRenderer` 先读好数据，再由 `WidgetViews.build` 直接生成 RemoteViews，经 `AppWidgetManager.updateAppWidget` 推送；只按竖屏最小宽度生成一份，记录每张卡片最近推送的内容、处理中被隐藏的任务、主题与宽度，未变化的卡片跳过，不同卡片并行、同一卡片串行，单个卡片超时 8 秒。触发点：接收器 `onUpdate` / `onAppWidgetOptionsChanged`（在 `goAsync` 内完成，强制重绘）、应用更新后的 `MY_PACKAGE_REPLACED`、配置页确认（前台完成首帧并在 0.8 秒后补推一次）、勾选广播（`goAsync` 内）、快捷新建与编辑弹窗保存后、应用内数据变化。每次推送以 `TodoWidget` 标签输出读库、构建与推送耗时（`adb logcat -s TodoWidget`），用于真机计时 |

## 数据管理与本地文件导入导出

应用通过 Android Storage Access Framework（SAF）提供本地全量数据导入与导出，不接入云端同步或三方云服务，不申请外部存储敏感权限：

- **数据传输组件**：[BackupModels.kt](../app/src/main/java/com/example/data/transfer/BackupModels.kt)、[JsonBackupCodec.kt](../app/src/main/java/com/example/data/transfer/JsonBackupCodec.kt)、[DataTransferManager.kt](../app/src/main/java/com/example/data/transfer/DataTransferManager.kt) 与 [DataTransferDialogs.kt](../app/src/main/java/com/example/ui/components/DataTransferDialogs.kt)。
- **文件格式**：独立 UTF-8 JSON 文件，格式标识固定为 `com.aistudio.todo.backup`，导出格式版本为整数 `3`（清单包含必需字段 `contentMode`，取值 0 或 1）。清单包含 `showInSummary`，任务 `listId` 非空。向后兼容读取合法版本 2 与版本 1（清单按默认格式，版本 1 新清单默认参与汇总）；格式版本 2 的旧版应用不能导入版本 3 文件（会提示不支持的备份版本）；对任何缺失或 null 归属的文件整体拒绝导入。
- **校验与边界保护**：上限单文件 32 MiB、清单 10,000 个、待办 100,000 个；流式解析严格检查必需字段与外键引用完整性；导入前将目标模式为 1 的 C 项旗帜规范化为 false。
- **备份集合强校验**：顶层 JSON 必须显式包含 `lists` 与 `tasks` 键；缺失任一集合整体拒绝导入并抛出格式异常，原库保持不变且预检时清理临时文件；显式空数组（`"lists": []`, `"tasks": []`）保持合法支持。

## 其它入口与限制

- [Theme.kt](../app/src/main/java/com/example/ui/theme/Theme.kt)、[Color.kt](../app/src/main/java/com/example/ui/theme/Color.kt)、[Type.kt](../app/src/main/java/com/example/ui/theme/Type.kt)、[Icons.kt](../app/src/main/java/com/example/ui/theme/Icons.kt)：主题、排版与图标。
- [strings.xml](../app/src/main/res/values/strings.xml)：应用名；多数其它文案直接写在 Composable 中，未形成完整多语言资源。三态主题切换见上文“主主题”。
- 详情编辑草稿使用 `rememberSaveable(taskId)` 持久化（`title`、`content`、`isFlagged` 与 `draftInitialized` 门禁），支持旋转屏幕与进程/组合重建下的状态恢复。
- Android 安装包标识以构建配置为准（见 [开发与发布](development.md) 的配置表）。
- 已有拖拽时才挂载图层、左滑背景按条件绘制等优化；没有足以证明稳定 120 Hz 或具体提升比例的测量报告。Roborazzi 相关依赖仍在构建配置中声明，但当前没有活动截图断言或截图回归。

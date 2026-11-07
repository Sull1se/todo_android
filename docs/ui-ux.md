---
title: Todo UI 与 UX 规范
purpose: 统一应用与桌面小卡片的视觉、交互、无障碍与自适应要求，并登记现有实现与规范的差距
status: 当前有效
owner: 当前任务 Agent；维护者负责审阅与合并
scope: Compose 界面、主题令牌、对话框与反馈、手势、无障碍、自适应与桌面小卡片
updated: 2026-11-07
verification: 已静态检查
verified: 2026-11-07；对照 3.5.2 源码静态核对条款与实现；规范决定由维护者确认；M3 默认令牌经 AndroidX 源码核对，对比度按 WCAG 公式计算；未做 TalkBack 走查或大屏实测
---

# Todo UI 与 UX 规范

[文档总索引](index.md) · [应用与架构](todo.md) · [当前问题与解决进度](quality.md) · [开发与发布](development.md)

## 1. 定位、来源与优先级

本文规定界面“应该怎样”，[应用与架构](todo.md) 记录界面“现在怎样”及已确认的业务行为。两者冲突时：已由维护者确认的产品行为（如 OLED 纯黑深色、撤销卡片堆叠、插旗左滑）保持不变，本文以“已确认偏离”记录；其余不符合本文的实现视为差距，记入 [当前问题](quality.md)，不降低本文要求。

| 来源 | 本文采用的内容 |
| --- | --- |
| [Material 3 规范](https://m3.material.io/) | 色彩角色、组件用法；对话框、Snackbar 的行为规则 |
| [Compose Material 3 源码](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/) | 组件默认令牌，例如对话框容器为 `surfaceContainerHigh`、Snackbar 操作色为 `inversePrimary` |
| [Compose 中的 Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3?hl=zh-CN) | `MaterialTheme` 的颜色、排版、形状三个子系统，动态配色、强调层级与色调高度 |
| [Compose 设计系统](https://developer.android.com/develop/ui/compose/designsystems?hl=zh-cn) | 在 Material 之上扩展自定义语义令牌，不另起一套设计系统 |
| [Jetpack Compose](https://developer.android.com/compose) | 声明式状态、自适应布局、Material 3 Adaptive |
| [移动设计指南](https://developer.android.com/design/ui/mobile?hl=zh-cn) | 安全区与输入法、可达区域、对齐与分组、预测性返回、桌面小卡片设计 |
| [无障碍](https://developer.android.com/guide/topics/ui/accessibility) | 内容描述、语义合并、状态描述、自定义操作、字体缩放 |
| [自适应应用质量](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality) | Tier 3/2 的配置变化、多窗口、键鼠与大屏布局要求 |
| [核心应用质量](https://developer.android.com/docs/quality-guidelines/core-app-quality) | 48dp 触控目标、对比度、深浅主题、返回导航、状态保存、启动与渲染性能 |

另外参考了来源页链接的子页面：[Compose 无障碍默认行为](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)、[预测性返回设计](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back)和[桌面小卡片质量](https://developer.android.com/docs/quality-guidelines/widget-quality)。

规则用语：**必须**表示违反即为差距；**应**表示默认要求，偏离时须写明理由；**可**表示可选做法。

## 2. 设计原则

1. **主流程最短**：新建、勾选完成、插旗、查看详情都必须一步可达。破坏性操作优先提供撤销，无法撤销时才用二次确认。
2. **语义先于外观**：颜色、字号和形状都从主题令牌取值，组件内不写死色值或字号。
3. **手势只是快捷方式**：每个手势都必须有可发现的按钮、菜单或无障碍操作作为等价入口。
4. **状态可感知**：完成、插旗、选中、禁用要同时用视觉和语义表达，不能只靠颜色。
5. **一致优于新奇**：同类元素采用相同的组件、间距、按钮顺序和措辞。

## 3. 主题与令牌

### 3.1 颜色

- `MaterialTheme.colorScheme` **必须**完整定义浅色和深色方案中组件实际会用到的角色，至少包括 `primary` 系、`secondary` 系、`tertiary` 系、`error` 系、`surface` 与 5 级 `surfaceContainer*`、`surfaceDim/Bright`、`outline`、`outlineVariant`、`inverse*` 和 `scrim`。未定义的角色会回退到 M3 基线紫色调色板：浅色 `surfaceContainerHigh` = Neutral92，`inversePrimary` = Primary80 `#D0BCFF`；深色 `surfaceContainerHigh` = Neutral17，`inversePrimary` = Primary40 `#6750A4`。
- 应由品牌主色（当前为蓝 `#0B57D0`）经 Material Theme Builder 或同类工具生成整套色调，再按需覆盖个别角色，不手工拼凑零散色值。
- 深色主题保留维护者已确认的 OLED 方案：`background` 与 `surface` 为 `#000000`，层级用 `surfaceContainerLow → Highest` 的深灰色阶表达。卡片、对话框、菜单分别对应容器层级，不依赖阴影（黑底上阴影不可见）。
- M3 角色没有覆盖的业务颜色，**必须**作为扩展令牌集中定义（例如 `TodoColors` 经 `CompositionLocal` 提供，深浅各一套）：插旗、完成态文字、左滑插旗背景与前景、清单主题色。界面代码中**不得**出现 `Color(0xFF…)`；判断深浅**不得**依赖 `background == Color.Black` 这类比较。
- 清单主题色仅作标识（色条、圆点），不承载文字。可选色板集中定义一份，新建与设置共用。色块保持 48dp 触控区与圆形，一行放不下时按行均分换行（窄对话框 6 色为 3×2），不得压缩单个色块。
- 对比度（WCAG AA）：正文 ≥ 4.5:1；大字（≥ 18pt，或 ≥ 14pt 粗体）及图标、控件边界 ≥ 3:1。禁用态可以使用 `onX` 加透明度，并免除对比度要求。
- 不采用动态配色（第 10 节决定 2），应用与桌面小卡片都使用固定品牌配色。`MyApplicationTheme` 不提供 `dynamicColor` 参数。

### 3.2 排版

- 文字**必须**使用 `MaterialTheme.typography` 的角色，只在必要时用 `copy()` 微调。**不得**只取 `fontSize` 而丢掉行高，也不得在调用处散写 `fontSize` 或 `fontWeight`。
- 角色映射：

| 用途 | 角色 |
| --- | --- |
| 顶部栏标题 | `titleLarge` |
| 对话框标题 | `headlineSmall`（AlertDialog 默认） |
| 任务标题、合并格式的第一行 | `titleMedium` |
| 任务详细信息、说明文字 | `bodyMedium` |
| 计数、分组标题（如“已完成（N）”） | `titleSmall` 或 `labelLarge` |
| 辅助说明 | `bodySmall` |
| 按钮、标签、清单标签页 | `labelLarge` |
| 底部操作栏标签 | `labelMedium`（12sp） |

- 最小字号为 12sp（`labelSmall` 的 11sp 只用于纯装饰性标签）。字号一律用 `sp`；含文字的容器**不得**写死高度，在系统字体放大到 200% 时文字不得被裁切或重叠。
- 正文行长应限制在 45–75 字符（核心质量 Line_Length）；在宽窗口下限制内容最大宽度（见 3.4）。

### 3.3 形状

- **必须**在 `MaterialTheme(shapes = …)` 中定义形状阶梯，组件通过 `MaterialTheme.shapes.*` 取值，不散写 `RoundedCornerShape(n.dp)`。
- 建议阶梯：extraSmall 4、small 8、medium 12、large 16、extraLarge 24（与当前任务卡片 24dp 的观感一致）。对应关系：任务卡片与左滑背景用 extraLarge，FAB 用 large，输入框和下拉框用 extraSmall，菜单用 extraSmall，Snackbar 和撤销卡片用 extraSmall 或 small（M3 默认为 4dp），清单标签页与按钮用全圆角（M3 按钮默认即为全圆角，无需显式指定）。

### 3.4 间距与布局

- 间距以 4dp 为网格，常用值为 4、8、12、16、24。
- 紧凑窗口（宽度 < 600dp）页面左右边距 16dp；中等及以上窗口（≥ 600dp）为 24dp，且主列表内容最大宽度建议 840dp 并居中。
- 任务卡片之间间距 12dp，卡片内边距 16dp。勾选控件、色条、正文与旗帜按固定顺序排列，左右对齐保持一致。
- 列表底部 `contentPadding` 必须为 FAB 与撤销卡片留出空间，确保最后一项可以完全滚出遮挡区。

### 3.5 高度与动效

- 层级优先用色调（容器色阶）表达，阴影只用于拖拽中的元素和浮层。静止的任务卡片不应同时使用阴影和色差。
- 时长：微交互（位移、淡入淡出）50–200ms，当前页面转场 120ms、列表位移 180ms 已在此范围内；进出场使用标准缓动（emphasized decelerate / accelerate）。
- 尊重系统“移除动画”设置：不得依赖动画完成作为业务逻辑的前提，现有视口协调器的等待都有超时兜底，应继续保持。

## 4. 组件规则

### 4.1 顶部栏与清单标签

- 普通状态使用 `TopAppBar`（标题 `titleLarge`），右上角放全局入口“应用设置”。如需随列表滚动变色，应使用 `scrollBehavior`。
- 清单标签应使用 M3 组件（`PrimaryScrollableTabRow` 或 `FilterChip` 行）。如保留自定义胶囊，**必须**补齐：最小高度 48dp，`selectable(role = Role.Tab)` 语义与选中状态，可选中的标签行置于 `selectableGroup()` 内，以及长按拖拽的无障碍替代操作（“左移”“右移”）。
- “汇总”固定在首位，“新建清单”固定在末位，二者不参与拖拽（已确认行为）。

### 4.2 任务卡片

- 结构从左到右：清单色条（装饰，语义为空）、完成控件、正文（标题加最多一行次要信息）、旗帜状态图标。
- 完成控件**必须**有至少 48dp 的触控区（`minimumInteractiveComponentSize()` 放在尺寸修饰符之前），并以 `toggleable(role = Role.Checkbox)` 或 Material `Checkbox` 暴露“已完成 / 未完成”状态。**不得**出现无描述的可点击元素。
- 整卡点击进入详情，应使用 `Card(onClick = …)` 或 `clickable(onClickLabel = "编辑")`，不要叠加 `clip + clickable`。
- 完成态：标题加删除线，颜色使用扩展令牌“完成态文字”，对比度 ≥ 4.5:1（删除线已提供非颜色线索）。
- 多选模式：完成控件替换为选择指示，卡片整体以 `toggleable` 暴露“已选中”状态。选中背景使用 `secondaryContainer` 或 `primaryContainer`，不借用 `surfaceVariant`。
- 手势的等价入口：左滑插旗 → 无障碍自定义操作“插旗 / 取消插旗”，详情页中也有旗帜按钮；长按拖拽排序 → 自定义操作“上移 / 下移 / 移到顶部”，仅在允许拖拽的范围内提供。

### 4.3 FAB

- 首页主操作“新建待办”使用标准 FAB，颜色为 `primaryContainer`，形状为 `shapes.large`，描述为“新建待办”。多选模式下隐藏。
- 列表滚动时可以收起为小尺寸，但不得遮挡最后一项。

### 4.4 对话框

- **用途**：只用于需要决定或确认的高优先级交互。低优先级结果反馈用 Snackbar。
- **操作数**：最多两个，即确认与取消。只有一个时必须是确认类操作（如“知道了”）。确认按钮在末端（右侧），取消在其左侧。取消**永不**禁用，确认在输入无效时可禁用。
- **按钮样式统一**（第 10 节决定 4）：对话框中的确认与取消都**必须**使用 `TextButton`，破坏性确认用 `error` 色文字；不得再使用实心、色调或胶囊按钮作为对话框操作。对话框内容区中的独立操作（如“导出全部数据”）可以使用 `OutlinedButton`，但一个对话框中最多只能有一个强调按钮。
- **措辞**：标题直接陈述问题或动作（如“删除这个清单？”），不用“确认删除”“提示”“警告！”这类空泛标题；确认按钮写具体动作（“删除”“创建”“保存”），不写“确定”“完成”。
- **破坏性操作**：可撤销的不弹确认，事后用撤销提示；不可撤销的（删除清单、清理已完成、覆盖导入、详情删除）弹确认，正文写明影响范围和数量，并说明“无法撤销”。
- **第三个操作**：“删除清单”不得作为清单设置对话框的第三个按钮，应移到对话框内容中的独立危险区，或改为全屏设置页。
- **表单型对话框**：在紧凑窗口中，含多个输入框和下拉框的新建/编辑应优先使用全屏对话框（`Dialog` + `Scaffold`，左上角“关闭”，右上角“保存”）；中等及以上窗口使用基本对话框。若保留基本对话框，必须保证输入法弹出时输入框和按钮可见。
- **放弃修改确认**（第 10 节决定 3）：在编辑已有待办的界面（应用详情页、桌面编辑弹窗）中，若内容与打开时不同，系统返回、左上角关闭、“取消”和点击遮罩**必须**先弹出“放弃修改？”确认（操作为“继续编辑 / 放弃”）。没有修改时直接关闭。任何入口都不得自动保存。

### 4.5 Snackbar 与撤销提示

- 一次性的结果反馈（导出成功、导入失败、清理数量）使用 `SnackbarHost`。文字不超过两行，没有操作时 4–10 秒后自动消失，并显示在 FAB 上方。
- 撤销提示是 Snackbar 的项目化扩展，配色**必须**取 `inverseSurface / inverseOnSurface`，操作文字取 `inversePrimary`，三者都需在主题中显式定义。
- M3 要求“同一时间只显示一个 Snackbar，有操作的 Snackbar 应保持到用户处理”。本项目保留已确认的多条撤销卡片堆叠、每条 4 秒自动消失（第 10 节决定 1，属已确认偏离），但**必须**满足：最新的在最上方；不遮挡 FAB 与底部操作栏；在 TalkBack 中以 `liveRegion = Polite` 播报；无障碍服务开启时延长停留时间（参考 `AccessibilityManager.getRecommendedTimeoutMillis`）。

### 4.6 多选与底部操作栏

- 进入多选后，顶部栏显示“已选择 N 项”，左侧为退出，右侧为全选（已确认行为）；系统返回**必须**先退出多选。
- 底部批量操作使用 `BottomAppBar`，每个操作是“图标 + 12sp 标签”的整体按钮（`Role.Button`）。图标描述为空、由标签提供名称，避免重复朗读。危险操作用 `error` 色。
- 没有选中项时，批量操作应禁用。

### 4.7 输入框与菜单

- 统一使用 `OutlinedTextField`。单行字段设置 `ImeAction.Next / Done`，多行字段（信息、详细信息）设置 `minLines`。必填字段为空时禁用保存，不另弹错误。
- 打开对话框时自动聚焦首个输入框并弹出键盘（现有做法），焦点请求必须等布局就绪。
- 下拉选择使用 `ExposedDropdownMenuBox`，展开态与箭头有语义。不得用 `OutlinedCard + clickable` 模拟。

### 4.8 空状态、加载与错误

- 列表为空时，应显示居中的说明文字（如“暂无待办”），可附带“新建待办”操作，不留白屏。
- 首次加载在 2 秒内完成时不显示进度；超过 2 秒必须有进度指示（核心质量 App_Startup_Time）。
- 字段错误就地显示；操作失败（导入、导出）用 Snackbar 或基本对话框，文案说明原因和解决办法，不直接拼接异常原文。

## 5. 交互与导航

- **返回**：系统返回优先级为：关闭菜单或对话框 → 退出多选 → 取消拖拽 → 离开详情页 → 退出应用。不得提供自定义的屏幕内“返回”提示；详情页左上角的关闭按钮与系统返回语义必须一致。
- **预测性返回**：`targetSdk` 为 36，系统默认启用预测性返回。应在 Manifest 中显式声明 `android:enableOnBackInvokedCallback="true"`。拦截返回只能用 `BackHandler / PredictiveBackHandler`，不得依赖 `onBackPressed`；自定义返回动效遵循官方参数（缩放到 90%，进度到 35% 时完成淡入淡出切换）。
- **保存语义**：同一实体在所有入口（应用详情页、桌面编辑弹窗）的保存、取消和返回语义必须一致。
- **触觉反馈**：只在跨过手势阈值（左滑到位、长按开始拖拽）时触发一次，不在普通点击时触发。
- **键鼠**：Esc 关闭对话框、菜单并退出多选；Enter 在单行输入框中提交；Tab 和方向键能遍历主流程（自适应 Tier 2 Keyboard_Navigation）。
- **手势区域**：不在系统手势边缘（左右边缘与底部手势条）放置拖拽目标；左滑插旗从卡片内部起手，不依赖屏幕边缘。

## 6. 无障碍

| 项 | 要求 |
| --- | --- |
| 触控目标 | 所有可交互元素 ≥ 48×48dp，包括桌面小卡片；视觉尺寸可以更小，但须用 `minimumInteractiveComponentSize()` 预留布局空间，避免与相邻可点区域重叠 |
| 内容描述 | 用简体中文描述用途，不描述外观；装饰元素（色条、圆点、与文字重复的图标）设为 `null`；不得出现英文描述（如 `"Selected"`、`"Completed"`、`"Toggle Completed"`） |
| 语义合并 | 卡片、单选行、复选行用 `toggleable`、`selectable` 或 `semantics(mergeDescendants = true)` 合并为一个焦点；子控件的点击回调设为 `null` 并提升到父级 |
| 状态 | 完成、插旗、选中、展开/折叠通过角色或 `stateDescription` 播报；可折叠的“已完成（N）”须暴露展开状态 |
| 自定义操作 | 左滑、长按拖拽、左滑关闭撤销卡片都必须有对应的 `customActions` |
| 标题 | 分组标题（“主题外观”“已完成（N）”）设为 `heading()` |
| 实时播报 | 撤销提示、计数变化使用 `liveRegion = Polite` |
| 对比度 | 见 3.1；完成态文字、左滑提示文字须达到 4.5:1 |
| 缩放 | 字体 200% 和显示大小最大时，所有页面可用、无裁切 |
| 验证 | 每个界面类版本至少做一次 TalkBack 走查与 Accessibility Scanner 扫描；Compose 测试启用无障碍检查（`enableAccessibilityChecks`） |

## 7. 自适应与系统集成

- **目标层级**（第 10 节决定 5）：必须满足自适应 Tier 3（全屏无黑边、旋转/折叠/分屏不丢状态、外接键鼠可用），并在宽窗口下限制内容宽度。不做双栏等 Tier 2 布局；第 5 节中的 Esc/Enter 键盘行为照常要求。
- **边到边**：所有页面用 `Scaffold` 的 `innerPadding` 或 `WindowInsets.safeDrawing` 处理系统栏与刘海；系统栏图标深浅跟随有效主题（已实现）。
- **输入法**：编辑页和表单在键盘弹出时，输入框与保存按钮必须可见（`imePadding()`，或 `Scaffold` 的 `contentWindowInsets` 包含 IME）。
- **状态保存**：所有用户输入、对话框开关和对话框内的选择（含主题色）都使用 `rememberSaveable`，在旋转、分屏和进程重建后保留。
- **窗口尺寸类**：按窗口宽度（`currentWindowAdaptiveInfo()` 或 `BoxWithConstraints`）判断。≥ 600dp 时页面边距改为 24dp，主列表与详情内容最大宽度 840dp 并居中，对话框保持默认最大宽度、不撑满。
- **多窗口**：分屏缩小到最小高度时，FAB、撤销提示和底部栏不得互相遮挡。

## 8. 文案与本地化

- **术语表**：统一使用以下术语，不混用近义词。

| 统一术语 | 不使用 |
| --- | --- |
| 清单 | 列表 |
| 待办 | 待办事项、任务（界面文案中） |
| 插旗 / 取消插旗 | 置顶、标记 |
| 详细信息、信息（合并格式） | 备注、内容 |
| 汇总 | 全部 |
| 已完成（N） | 半角括号 |

- 句式：按钮用动词（“新建清单”“删除”），说明用完整短句；全角标点；数字与中文之间加空格（现有风格）；不用感叹号制造紧迫感。
- 用户可见文字（含内容描述、Toast、Snackbar 和卡片文字）**必须**放在资源中：`values/strings.xml` 为简体中文（默认语言），`values-en/strings.xml` 为英文翻译（第 10 节决定 6）；带数量的文案用 `plurals`。新增文字必须同时提供两种语言。
- 英文按 Material 写作习惯：按钮用句首大写的动词（“Delete”“New list”），术语表对应为 List / Task / Flag / Details / Summary / Completed。
- 布局必须容纳英文较长的文字：按钮、标签页、底部栏标签不得写死宽度，可以换行或省略号截断，且不得遮挡相邻元素。

## 9. 桌面小卡片

- 遵循 [Widget 质量标准](https://developer.android.com/docs/quality-guidelines/widget-quality) Tier 2：可调整大小并定义最小/最大尺寸（已实现）；内容随尺寸增减；有准确的预览（已实现）；应用内与卡片内的操作都能即时同步（已实现）；所有可点区域 ≥ 48×48dp。
- 卡片顶部为标题栏（清单圆点、名称、数量、“＋”），列表可滚动，空状态显示“暂无待办”（已实现）。列表紧贴 48dp 标题栏，首行不加上内边距，其余行间距不变。
- 圆角应使用系统值 `@android:dimen/system_app_widget_background_radius`（Android 12+，Tier 1 WS-2）。低版本保留 20dp 作为兜底。荣耀桌面返回动画期间圆角失效属于桌面限制，不因此放弃系统值。
- 配色与应用同源，深浅两套颜色资源经测试保持一致（已实现）。与应用一样不采用动态配色，因此不满足 Tier 1 WC-1，属已确认偏离。
- 桌面弹窗（快捷新建、编辑）复用应用的对话框组件，规则与第 4.4 节相同。

## 10. 维护者已确认的规范决定

| 决定 | 结论 | 落到的条款 |
| --- | --- | --- |
| 1. 撤销提示形态 | 保留多条堆叠、每条 4 秒自动消失（偏离 M3 的单条规则），补齐无障碍要求 | 4.5 |
| 2. 配色来源 | 固定品牌蓝，补齐完整色调，深色保留 OLED 纯黑；不采用动态配色（应用与卡片均不采用） | 3.1、9 |
| 3. 编辑界面的返回 | 有修改时弹出“放弃修改？”确认，不自动保存 | 4.4、5 |
| 4. 对话框按钮 | 统一为 M3 文字按钮，破坏性确认用 `error` 色 | 4.4 |
| 5. 自适应目标 | Tier 3 加宽窗口内容限宽，不做双栏 | 3.4、7 |
| 6. 无障碍与多语言 | 纳入必须项：TalkBack 与触控目标（第 6 节）、文字迁入资源、提供英文翻译 | 6、8 |
| 7. 差距登记 | 实现与本文不符之处登记到 [当前问题](quality.md)，关闭后迁入验收历史 | 11 |


## 11. 规范的维护与验收

- 新增或修改界面时以本文为准，并在 [应用与架构](todo.md) 中记录实际行为。修改本文条款须由维护者确认，并在第 10 节登记。
- 每个改动界面的版本至少验证：浅色、深色、中文与英文、字体 200%、横屏、分屏、TalkBack 走查。验证结果写入 [验收与验证历史](verification-history.md)，静态检查通过不等于真机验收通过。
- 实现与本文的差距在 [当前问题](quality.md) 中跟踪进度，关闭后迁入 [验收与验证历史](verification-history.md)。

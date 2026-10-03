---
title: Todo 开发与发布
purpose: 构建、安装、签名、版本更新与数据库迁移的可复做入口
status: 当前有效
owner: 当前任务 Agent；维护者负责审阅、合并与正式发布
scope: 本地 Android 开发、验证与发布流程
updated: 2026-10-02
verification: 已静态检查
verified: 2026-10-02；对照 3.0.0 构建配置静态核对命令、签名边界与迁移描述；Debug 命令已在本地 Windows 环境实际执行通过
---

# Todo 开发与发布

[文档总索引](index.md) · [应用与架构](todo.md) · [当前问题与解决进度](quality.md)

## 当前配置：直接由源码生成

此表由文档工具 `Sync` 从 [应用构建配置](../app/build.gradle.kts)、[版本目录](../gradle/libs.versions.toml)、[Wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 和 [数据库定义](../app/src/main/java/com/example/data/AppDatabase.kt) 提取；`Check` 比较生成结果，配置变化后未同步即失败。不要手改生成区。

<!-- docs:config:start -->
| 配置项 | 当前声明值 |
| --- | --- |
| versionName | `3.0.0` |
| applicationId | `com.aistudio.todo.xqwdfa` |
| namespace | `com.example` |
| versionCode | `20` |
| minSdk | `24` |
| targetSdk | `36` |
| compileSdk API | `36` |
| compileSdk minor | `1` |
| Java sourceCompatibility | `11` |
| Java targetCompatibility | `11` |
| Gradle Wrapper | `9.3.1` |
| 版本目录 agp | `9.1.1` |
| 版本目录 kotlin | `2.2.10` |
| 版本目录 googleDevtoolsKsp | `2.3.5` |
| 版本目录 composeBom | `2024.09.00` |
| 版本目录 roomRuntime | `2.7.0` |
| 版本目录 roomKtx | `2.7.0` |
| 版本目录 roomCompiler | `2.7.0` |
| 版本目录 reorderable | `3.1.0` |
| 版本目录 kotlinxCoroutinesAndroid | `1.10.2` |
| 版本目录 kotlinxCoroutinesCore | `1.10.2` |
| 版本目录 navigationCompose | `2.8.9` |
| Room schema | `6` |
<!-- docs:config:end -->

当前依赖以构建脚本及上表为准。Kotlin 行指版本目录中的 Compose 编译插件声明，不冒充实际解析得到的编译器报告。

## 版本与发布状态

当前源码版本为 **3.0.0 / versionCode 20**（Room schema 6），配置变化后以生成表为准。正式 Release APK 由维护者在对外发布时放入 GitHub Releases，不提交进源码仓库。

本地构建的 APK 按约定复制到仓库根目录 `dist/`（该目录被 Git 忽略），命名 `todo-v<versionName>-<variant>.apk`（如 `todo-v3.0.0-debug.apk`）。Gradle 默认输出在 `app/build/outputs/apk/`，没有自动复制任务。

## 构建前置条件

下面命令从仓库根目录的 PowerShell 7 执行（Windows 为主要维护环境；macOS/Linux 可运行 Gradle 与 JVM 测试，但交付脚本与文档按 Windows 描述）。

- 兼容当前 AGP 的 JDK：AGP 9.1 系列最低 JDK 为 17、Gradle 为 9.3.1。Java 源码/字节码目标 11 不等于用 JDK 11 启动 Gradle。[官方兼容表](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- Android SDK：SDK 平台匹配配置表的 API 及 minor 版本（当前为 SDK 36 minor 1）。通过本地 `local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` 定位 SDK；`local.properties` 不入库。
- Wrapper 首次运行会下载 Gradle 和依赖，需要网络。JDK、SDK 与缓存不由仓库自动安装。
- [settings.gradle.kts](../settings.gradle.kts) 只包含 `:app`，工程显示名仍为 `My Application`；[gradle.properties](../gradle.properties) 设置 4 GiB JVM 堆、最多 4 workers、并行与配置缓存。IDE 工程名不是应用名称。

```powershell
java -version
.\gradlew.bat --version
```

成功条件：显示可兼容的 JVM 和 Wrapper 版本。缺少 Java、SDK、依赖或许可证时停止对应构建，记录具体错误；不要把环境失败写成功能失败或靠降级版本掩盖问题。

## 构建与安装（Debug 快速开始）

Debug 构建不依赖任何发行私钥，是本地验证的默认路径：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Debug 构建失败' }
New-Item -ItemType Directory -Force dist | Out-Null
if (Test-Path dist/todo-v3.0.0-debug.apk) { throw '目标 APK 已存在；先核对版本与哈希，不覆盖历史产物' }
Copy-Item app/build/outputs/apk/debug/app-debug.apk dist/todo-v3.0.0-debug.apk
if (-not (Test-Path dist/todo-v3.0.0-debug.apk)) { throw 'Debug APK 复制失败' }
```

成功条件：测试与 lint 通过、`BUILD SUCCESSFUL`，并已将本次产物复制到 `dist/`。测试报告位于 `app/build/reports/`；报告是构建产物，不提交进 Git。

命令行安装前先确认测试设备：

```powershell
adb devices
adb -s <测试设备序列号> install -r dist/todo-v3.0.0-debug.apk
```

尖括号必须换成测试设备序列号。成功条件包括安装成功、应用启动及对应行为验收；仅有 APK 不代表运行正常。覆盖安装若签名或版本不兼容，停止并核对包名、签名和版本，不默认卸载真实用户应用。

Android Studio 可直接 Open 仓库根目录、同步、选择设备并 Run。

## 签名行为与发布边界

| 场景 | 实际配置 |
| --- | --- |
| Debug | 仓库根目录存在 `debug.keystore` 时使用它，否则采用 Android 默认调试签名 |
| Release，存在发行密钥 | 仅使用 `.signing/todo-release.jks`（别名 `todo-release`），密码来自构建会话环境变量 `STORE_PASSWORD` 与 `KEY_PASSWORD` |
| Release，无发行密钥或缺密码 | 前置门禁直接拦截并报错退出（`GradleException`），绝不回退调试证书，绝不产出可交付 APK |

发行私钥与密码不出现在本仓库中；`.gitignore` 已明确忽略 `/.signing/`、`debug.keystore`、`*.jks`、`.env` 与 `local.properties`。**未持有发行凭据的人员与 Agent 无法构建 Release 交付包，这是有意的数据与身份保护边界**：需要验证应用行为时一律使用 Debug 构建。

**Fork 构建提示：** 本项目不考虑社区共同维护。他人 Fork 并自行构建 App 时，请使用自己的签名私钥并修改包名（`applicationId`）——本仓库的发行签名身份与包名属于本项目，直接沿用会造成安装冲突与签名身份混淆。

公开发行身份只登记**公共证书指纹**：`scripts/quality/release-certificate.sha256` 保存发行证书的 SHA-256 指纹（当前为 `936961c6acce74ade345fab6cc81902096d8f59fa73db3356fbec49f0667695d`），用于核验对外发布的 Release APK 确实由维护者的发行密钥签名。维护者交付使用 `scripts/quality/Build-Release.ps1` 统一门禁：调用 `apksigner`、`aapt` 比对指纹基线并校验 Manifest 元数据（脚本按 Windows 的 `apksigner.bat`/`aapt.exe` 描述）。从外部渠道获取 Release APK 时，应先核对签名指纹与安装边界（发行签名的 APK 无法覆盖安装旧调试签名的版本，反之亦然）；详见 [安全说明](security.md)。

当前 Release 启用 R8 与资源压缩，[proguard-rules.pro](../app/proguard-rules.pro) 保留数据层和 Reorderable 类。尚无帧率与列表规模性能测量，不承诺固定大小或帧率。

## 应用版本规则

`versionName` 使用 `MAJOR.MINOR.PATCH`：不兼容的用户行为/数据兼容性改变升 MAJOR，兼容新增功能升 MINOR，兼容修复与调优升 PATCH。内部重构不因规模大自动升 MAJOR，数据库增字段也不独立决定应用版本等级，应根据用户行为与兼容性判断。

每次发布同时更新 `versionName` 与严格递增的正整数 `versionCode`，沿用发布时加 1 的方式，不另引入整数映射策略。仅改文档或文档工具不改变 Android 版本。包名、namespace、显示名是不同概念，以生成表和资源为准。

## 数据迁移规则与落地

**要求：保留已有清单、任务与偏好，不用破坏性重建规避缺失迁移。** 当前注册 `MIGRATION_4_5` 与 `MIGRATION_5_6`：
- `MIGRATION_4_5` 为 `tasks` 增加默认值 0 的 `isFlagged` 列。
- `MIGRATION_5_6`（Room schema 升至 6 并导出 `6.json`）：验证并强制 `tasks.listId` 为非空 `Int`（发现 null 立即中止迁移以保全原数据）；为 `task_lists` 增加默认值 1 的 `showInSummary` 列；创建单行内部 `AppMetadata` 表；将所属清单完成模式为 1 且已完成任务的旗帜重置为 false。
- 启动时由仓储检查 `AppMetadata.summaryOrderInitialized`，在 Room 事务中将旧偏好（`summary_excluded_list_ids`）迁移为对应清单的 `showInSummary` 属性并为汇总范围及各独立清单范围做连续 0-based 规范编号，完成后置初始化标志为 true 并清理旧偏好。

数据库未配置破坏性回退（无 `fallbackToDestructiveMigration` 调用），缺失迁移路径时抛出异常中止打开，严格禁止清空或重建 Room 数据表。4→5 与 5→6 真实迁移数据保留、缺失路径保护失败退出及 DAO 外键约束已通过 `AppDatabaseMigrationTest` 与 `TodoDaoTest` 验证。

**当前迁移下限为 schema 4。** schema 1–3 没有到 6 的迁移路径；现有 schema 3 用例验证的是失败时保留数据，不是成功升级或应用级恢复。当前启动协程没有迁移异常恢复界面。仓库内 schema 基线文件只有 `5.json` 和 `6.json`；4→5→6 用例通过手写 SQL 创建 v4 库，因此缺少 `4.json` 不等于没有测试，但仍缺真实 schema 4 基线文件对应证据。维护者决定不支持 schema 1–3 历史兼容，迁移下限固定为 schema 4；保留缺失迁移抛错保全原库保护与 4/5→6 验证，不补 1–3 迁移或恢复界面。

4/5→6 还要求旧任务全部具有有效清单归属（升级的前置保证），故拒绝 null 归属为已确认的保护边界，不自动视为新缺陷；不得因此清除旧数据。

涉及 schema 的后续任务应：

1. 确定支持的旧 schema 和测试数据，保存可恢复的测试副本，不直接试升唯一真实数据。
2. 更新实体与 `@Database` 版本，补充并注册完整非破坏性路径；处理回退冲突，补 schema 与实际迁移测试。
3. 对每条受支持路径验证结构、内容、完成/插旗和外键关系，再验证新安装。
4. 失败立即停止升级，保留错误和原测试副本，不能清空数据库让测试通过；APK 降级不等于数据库回退。
5. 同步应用主文档、生成表和状态页，未验证的旧版本路径继续登记。

### 不可逆操作显式标记

无法回滚的操作必须在执行前显式标记为【无法回滚/高风险】，并要求更高层级的用户显式确认：
- 破坏性数据清理：如无备份的数据库重置、清空生产用户数据或在迁移异常时强制清除数据库以掩盖迁移问题；
- 破坏性设备操作：严禁在未确认且无备份的真实设备上执行 `pm clear` 或 `adb uninstall` 导致应用数据丢失；
- 破坏性版本控制操作：严禁执行 `git reset --hard`、`git clean -fdx` 导致未提交改动或 `dist/` 关键交付产物永久丢失；
- 凭据操作：严禁物理删除或覆盖签名私钥与关键配置文件。

## 测试与交付

### 高保护级别规则与防弱化

单元测试、插桩测试、自动化回归用例、CI 与 Guardrail 校验脚本（如 `Manage-Docs.ps1`、`Test-Docs.ps1`）及核心 Requirements 拥有高于普通业务代码的保护级别：
- **严禁弱化检查**：禁止 Agent 因测试或检查失败直接删除、注释或放宽用例，绝不允许为了使当前实现通过而降低标准或修改既定需求。
- **合规演进与修正**：当测试用例或检查工具自身确实存在缺陷、不兼容或过时时可以修正，但必须在提交说明中详细解释修改原因、影响范围与验证方式，严格遵循既有授权边界。
- **免除额外审批**：日常已授权的正常维护与合规修正不额外引入审批流程。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest   # 需要已连接的测试设备
```

以当次退出码及 `app/build/reports/` 中报告判断结果；测试用途与回归要求见下方说明，实际结果写入 [验收与验证历史](verification-history.md)，未闭环错误记入 [当前问题](quality.md)，不可预先勾选通过。

**自动化检查入口：**
- 提交钩子 `.githooks/pre-commit` 调用 [Check-Local.ps1](../scripts/quality/Check-Local.ps1) `-Mode Commit`：自动对暂存区运行受管文档检查与格式检查；对代码变更自动执行单元测试与 `lintDebug`；纯文档提交（含 `README.md` 与 `LICENSE`）自动跳过 Gradle。
- [Check-Local.ps1](../scripts/quality/Check-Local.ps1) `-Mode Full` 在工作区运行文档检查、`git diff --check` 与完整 Gradle 测试/lint。
- [Test-Docs.ps1](../scripts/docs/Test-Docs.ps1) 与 [Test-LocalGate.ps1](../scripts/quality/Test-LocalGate.ps1) 分别是文档工具与本地门禁自身的隔离回归。
- GitHub CI（`.github/workflows/ci.yml`）在推送与 PR 时运行文档检查、工具回归、单元测试、lint 与 Debug 构建；不运行需要发行凭据的 Release 任务。远程实际运行结果在部署后记录，不提前声称 CI 已通过。

**发布边界：** 每次发布更新本页的源码配置表；对外发布由维护者执行（GitHub Releases + 签名指纹核验）。构建成功不表示已发布。实际测试、迁移、签名、安装与交互结果及未覆盖范围记入验收与验证历史。达到关闭条件的问题立即迁出 quality.md，具体流程见 [维护规则](documentation.md#问题记录与关闭迁移)。

## 现有测试实际覆盖

- `TodoUnitTest` 保留实体与手工列表重排测试，不单独作为生产持久化证据；迁移由 `AppDatabaseMigrationTest` 验证，约束与级联由 `TodoDaoTest` 验证。
- `TodoPersistenceTest` 覆盖生产 ViewModel 的连续拖拽、即时插旗、批量更新、页面快照 Flow 发射匹配；`HomeInteractionTest` 覆盖拖拽会话生命周期、防抖、换页取消与语义冲突拒绝；`SwipeViewportCoordinatorTest` 覆盖左滑视口协调器七阶段状态流转、纯决策与世代校验，以及滚动中断发帧、取消帧重置、快照页面标识校验与独立拖拽会话保持；`SwipeRefreshInteractionTest` 覆盖汇总页、参与汇总独立清单及隐藏清单左滑中断 Compose UI 交互；`UndoViewportInteractionTest` 覆盖撤销插旗视口状态机、PinPosition 定位与倒计时解耦防抖；`DetailNavigationInteractionTest` 覆盖详情页双击/快速退出防重锁、淡出转场生命周期门禁与栈保护；`DetailDraftRestorationTest` 覆盖任务详情草稿状态（标题、备注、旗帜）在 Activity/组合状态恢复、横竖屏旋转或内存重建下的持久保持与任务切换隔离；`JsonBackupCodecTest` 和 `DataTransferIntegrationTest` 覆盖必需集合校验、追加/覆盖与事务回滚；`TaskOrderEngineTest` 覆盖纯排序变换与范围划分；`ThemeAndSettingsTest` 覆盖三态主题偏好持久化、设置入口交互与 OLED 纯黑色值断言。`app/build.gradle.kts` 配置了 `testOptions.unitTests.all { it.forkEvery = 1 }`，消除测试类间静态线程调度状态污染。具体运行日期、测试数量与结果见 [验收与验证历史](verification-history.md)。
- [ExampleUnitTest.kt](../app/src/test/java/com/example/ExampleUnitTest.kt) 是加法示例；设备上下文测试 [ExampleInstrumentedTest.kt](../app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt) 用于在真实设备核对 applicationId。示例与包名检查不覆盖复杂 UI 交互；构建配置中声明的 Roborazzi 依赖当前没有活动截图断言，不存在截图回归基线。
- 设备端 `androidTest` 当前仅 1 项包名检查；Compose 交互、迁移与 DAO 等业务自动化在 JVM/Robolectric 层运行。维护者决定设备端测试主要依靠 APK 交付后的手动安装验收，暂不增设设备业务自动化；保留已有 androidTest 和 JVM/Robolectric 测试资产。
- 测试覆盖说明描述用例用途；实际通过结果统一记录在验收与验证历史，不将覆盖说明作为运行证据。

## 可复用的设备回归矩阵

使用测试设备和测试数据，按改动选择场景，记录系统、设备、包版本/签名、步骤及结果。以下是通用设备回归矩阵，纳入后续交互要求，不是全量已完成清单。

| 场景 | 成功条件 |
| --- | --- |
| 新安装与持久化 | 零清单 FAB 提示先创建且不崩溃；创建两个清单及所属任务，重启后内容、颜色、完成/插旗、设置与顺序正确 |
| 编辑与取消 | 保存后保留，取消后不变，空标题不能保存；快速新建插旗顺序正确 |
| 完成与汇总 | 按清单两种完成模式、C 区域折叠/展开、汇总展示设置与 S/L 排序隔离正确，无任务丢失 |
| 插旗与多选 | 连续切换、详情保存、撤销、移动、批量状态正确；多选时任务拖拽和左滑禁用 |
| 拖拽边缘 | 长任务列表上下边缘、长 Tab 列左右边缘持续滚动并换位，无明显漂移 |
| 拖拽边界 | 短列表、首项、滚至尽头、快速连续拖动无跳动/闪回；松手和重启后顺序一致 |
| 删除 | 取消不删除；确认清单删除只删除所属任务，其他任务保留 |
| 升级与签名 | 旧库测试升级保留数据，签名匹配覆盖安装；不靠清空数据通过验收 |
| 本地导入导出 | v2 全量包含隐藏/已完成及其有效清单归属；追加保留原数据，覆盖须确认；拒绝无有效归属数据，非法文件或写入失败不改变原库，往返保留字段和关系 |
| 主题与设置 | 三态主题即时切换与重启持久化；深色 OLED 纯黑背景；应用设置与清单设置入口独立正常 |

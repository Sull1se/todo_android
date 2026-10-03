# Todo 待办应用

这是一个 Agent First Android 项目，纯氛围编程。仓库中保留了完整的项目文档。

主要功能：

- 多清单与虚拟汇总页；勾选展示的清单纳入汇总统一排序（范围 S），未展示清单独立排序（范围 L），互不干扰。
- 按清单选择完成模式：完成后保持原位，或清旗收入底部“已完成”区域并支持一键清理。
- 插旗置顶/取消置底、长按拖拽重排、批量完成/移动/删除、独立计时的撤销卡片堆叠。
- 任务详情支持纯文本备注、未保存草稿在旋转与进程重建后保留。
- 基于 SAF 的本地 JSON 全量导入导出（格式 v2，兼容读取 v1），无需任何云服务或联网权限。
- 深浅色三态主题（跟随系统/浅色/深色），深色为 OLED 纯黑背景。

界面截图暂未提供，功能与行为以 [应用与架构](docs/todo.md) 的行为表为准。

## Agent First 的工作方式

本项目按“文档即事实源”的方式维护，维护者与 Agent 遵循同一套流程：

```text
需求 / 问题分析 → 独立执行方案 → 实施 → 本地验证 → 手动验收 → 文档同步与提交
```

- 规则、行为边界、构建命令与验收证据都保存在 `docs/`，接手的 Agent 无需私人对话即可发现规则、找到事实、实施变更、运行验证并同步知识。入口见 [AGENTS.md](AGENTS.md)。
- `.agents/skills/` 中的三个项目 Skill（独立方案编写、文档收尾、文档巡检）描述可复用的 Agent 流程；支持的 Agent 工具会自动注入它们，不支持的按主文档执行同等流程。**Skill 自动注入与否不改变检查与验证要求，也不构成“无人审查自动提交”的保证**——所有变更仍经维护者审阅合并。
- 提交钩子与 [GitHub CI](.github/workflows/ci.yml) 强制执行文档一致性检查、工具回归、单元测试、lint 与 Debug 构建；关键测试与检查脚本受高保护级别治理，不允许为通过检查而削弱。

## 快速开始（Debug 构建）

环境前提：

| 前提 | 说明 |
| --- | --- |
| JDK 17+ | AGP 9.1 最低要求；Java 字节码目标为 11，但不等于用 JDK 11 启动 Gradle |
| Android SDK | SDK Platform 36（minor 1）、Build-Tools；通过 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir` 定位（该文件不入库） |
| PowerShell 7 | 运行 `scripts/` 下的文档与门禁工具（Windows 为主要维护环境） |
| 网络 | Gradle Wrapper 首次运行会下载 Gradle 9.3.1 与依赖 |

命令（仓库根目录）：

```powershell
git clone <本仓库地址> todo && cd todo
git config --local core.hooksPath .githooks   # 启用提交钩子
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebug
Copy-Item app/build/outputs/apk/debug/app-debug.apk dist/todo-v3.0.0-debug.apk
```

Debug 构建不依赖任何发行私钥，是本地验证的默认路径。完整说明（安装到设备、签名边界、版本与迁移规则、Release 交付）见 [开发与发布](docs/development.md)。

## 文档入口

| 文档 | 内容 |
| --- | --- |
| [应用与架构](docs/todo.md) | 行为边界、模块关系、数据模型与代码地图 |
| [开发与发布](docs/development.md) | 构建配置、签名、版本与迁移规则、测试覆盖说明 |
| [安全说明](docs/security.md) | 本地数据与系统备份边界、签名身份 |
| [当前问题](docs/quality.md) | 未闭环问题与进度（含 APP-006 系统备份暂缓事项） |
| [验收与验证历史](docs/verification-history.md) | 验证证据与已解决问题记录（按规则追加） |

## 已知限制

- 没有云同步、AI 功能、提醒调度与富文本编辑，也没有联网权限——这是产品边界，不是缺失。
- **系统备份策略未定（APP-006）**：应用允许系统备份但规则仍为模板，实际行为未验收；细节与影响见 [安全说明](docs/security.md)。
- 数据库迁移下限为 schema 4，不承诺更早历史版本的升级路径。
- Release 构建需要维护者的发行私钥，本仓库之外无法构建正式签名包（有意边界）；对外发布的 APK 由维护者提供。
- 无自动化的性能/帧率/体积门禁与设备端业务自动化；设备行为以手动验收为准，测试覆盖说明见 [开发与发布](docs/development.md)。
- 交付与检查脚本按 Windows（PowerShell 7）描述；Gradle 与 JVM 测试可在其他平台运行，但交付脚本未声明跨平台支持。

## Fork

**若想要 Fork 并构建 App：请使用自己的私钥并修改包名。** 本仓库的发行签名身份与包名（`applicationId`）属于本项目，Fork 直接沿用会造成安装冲突与签名身份混淆；Fork 自签名的 APK 也无法覆盖安装本项目产物。详见 [开发与发布的签名边界](docs/development.md#签名行为与发布边界) 与 [安全说明](docs/security.md)。

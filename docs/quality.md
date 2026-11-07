---
title: Todo 当前问题与解决进度
purpose: 仅记录项目当前存在的问题及各自解决进度
status: 当前有效
owner: 当前任务 Agent；维护者负责审阅与合并
scope: 尚未闭环的应用与文档问题、当前进度、阻碍、下一步及关闭条件
updated: 2026-11-07
verification: 已静态检查
verified: 2026-11-07；对照 3.5.2 源码与已知验收范围静态核对：新增 APP-007（TalkBack 真机走查未做）与 APP-008（大屏与分屏走查未做）；APP-006 维持暂缓；本次未执行真机验证
---

# Todo 当前问题与解决进度

[文档总索引](index.md) · [验收与验证历史](verification-history.md) · [开发与发布](development.md) · [文档维护规则](documentation.md)

本页只记录尚未闭环的问题及解决进度。关闭记录及历史摘要一律归入 [验收与验证历史](verification-history.md)，不在本页保留；具体维护规则见 [问题记录与关闭迁移](documentation.md#问题记录与关闭迁移)。

## 当前应用问题

| 编号 / 优先级 / 状态 | 问题、证据与当前进度 | 下一步、阻碍与关闭条件 |
| --- | --- | --- |
| APP-007 / 高 / 已实现待验收 | 无障碍要求见 [UI 与 UX 规范第 6 节](ui-ux.md#6-无障碍)。当前实现：完成控件为 48dp `toggleable(Role.Checkbox)` 并描述为“完成”；任务卡片提供插旗 / 取消插旗、上移、下移、移到顶部自定义操作，清单标签提供左移、右移；撤销提示以 `liveRegion` 播报、可经无障碍操作关闭，停留时长按系统建议延长；标签页、多选卡片、“已完成（N）”标题暴露角色与状态；单选、复选行整行合并为一个焦点。`UiUxSpecTest` 覆盖复选框角色与自定义操作；尚未做 TalkBack 真机走查 | Compose 测试未启用 `enableAccessibilityChecks`（需额外依赖，另行评估）。关闭条件：在真机上完成一次 TalkBack 走查（含字体 200%），并在 [验收与验证历史](verification-history.md) 记录 |
| APP-008 / 中 / 手机已验收，大屏待验收 | 自适应要求见 [UI 与 UX 规范第 7 节](ui-ux.md#7-自适应与系统集成)。当前实现：内容最大宽度 840dp，宽度 ≥ 600dp 时左右边距 24dp；主题色选择与导入预览在配置变化后保留；界面文字中英双语跟随系统语言。手机上的布局、状态保持与中英文切换已人工确认；平板、横屏、分屏未实测 | 关闭条件：在平板或大屏模拟器上完成横屏、分屏与中英文切换走查并记录；手机通过不替代大屏验收 |
| APP-006 / 中 / 无限期暂缓 | [主 Manifest](../app/src/main/AndroidManifest.xml) 允许系统备份（`allowBackup="true"`），[备份规则](../app/src/main/res/xml/backup_rules.xml) 与 [数据提取规则](../app/src/main/res/xml/data_extraction_rules.xml) 仍为模板，实际策略未验收；应用数据主要保存在本机，未接入云服务；本地 JSON 导入导出不替代系统备份验收 | 无预定恢复日期；恢复时分别确认云备份/设备迁移的数据允许范围，配置旧版 `fullBackupContent` 与新版 `dataExtractionRules`，核对合并 Manifest 并验证目标设备实际行为后方可关闭；手动导入导出不替代系统备份验收 |

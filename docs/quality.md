---
title: Todo 当前问题与解决进度
purpose: 仅记录项目当前存在的问题及各自解决进度
status: 当前有效
owner: 当前任务 Agent；维护者负责审阅与合并
scope: 尚未闭环的应用与文档问题、当前进度、阻碍、下一步及关闭条件
updated: 2026-10-02
verification: 已静态检查
verified: 2026-10-02；静态核对当前仅 APP-006 未闭环；本次未执行 Android 功能或真机验证
---

# Todo 当前问题与解决进度

[文档总索引](index.md) · [验收与验证历史](verification-history.md) · [开发与发布](development.md) · [文档维护规则](documentation.md)

本页只记录尚未闭环的问题及解决进度。关闭记录及历史摘要一律归入 [验收与验证历史](verification-history.md)，不在本页保留；具体维护规则见 [问题记录与关闭迁移](documentation.md#问题记录与关闭迁移)。

## 当前应用问题

| 编号 / 优先级 / 状态 | 问题、证据与当前进度 | 下一步、阻碍与关闭条件 |
| --- | --- | --- |
| APP-006 / 中 / 无限期暂缓 | [主 Manifest](../app/src/main/AndroidManifest.xml) 允许系统备份（`allowBackup="true"`），[备份规则](../app/src/main/res/xml/backup_rules.xml) 与 [数据提取规则](../app/src/main/res/xml/data_extraction_rules.xml) 仍为模板，实际策略未验收；应用数据主要保存在本机，未接入云服务；本地 JSON 导入导出不替代系统备份验收 | 无预定恢复日期；恢复时分别确认云备份/设备迁移的数据允许范围，配置旧版 `fullBackupContent` 与新版 `dataExtractionRules`，核对合并 Manifest 并验证目标设备实际行为后方可关闭；手动导入导出不替代系统备份验收 |

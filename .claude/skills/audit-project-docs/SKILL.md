---
name: audit-project-docs
description: 仅在维护者独立要求对项目文档体系进行全量巡检、排查矛盾或纠偏规则时使用。全面核查主题边界、事实一致性、规则合规性、高保护级别规则与正式文档索引可达性；修正授权内问题并记录审计结果。日常任务收尾不触发。
metadata:
  title: 独立文档巡检与纠偏 Claude 入口
  purpose: 让 Claude Code 发现本 Skill 并转读唯一正文来源
  status: 当前有效
  owner: 当前任务 Agent；维护者负责审阅、合并与正式发布
  scope: Claude Code 对 audit-project-docs 的发现与触发
  updated: 2026-10-30
  verification: 已静态检查
  verified: 2026-10-30；Check 核对与正文来源的 name、description 一致；未实测 Claude 自动触发
---

# 独立文档巡检与纠偏（Claude 入口）

本文件只供 Claude Code 从 `.claude/skills/` 发现此 Skill，不维护任何步骤。触发后先完整阅读 `.agents/skills/audit-project-docs/SKILL.md`，并严格按该正文执行。修改 Skill 时先改正文来源；`name` 或 `description` 变化时同步本入口。

---
name: update-project-docs
description: 在任务结束时执行知识影响判断，同步应用事实、验证证据与状态，并维护文档总索引与提交检查。用于授权的日常文档收尾；纯查询或无新信息时不扩大记录。
metadata:
  title: 项目文档影响与收尾 Claude 入口
  purpose: 让 Claude Code 发现本 Skill 并转读唯一正文来源
  status: 当前有效
  owner: 当前任务 Agent；维护者负责审阅、合并与正式发布
  scope: Claude Code 对 update-project-docs 的发现与触发
  updated: 2026-10-30
  verification: 已静态检查
  verified: 2026-10-30；Check 核对与正文来源的 name、description 一致；未实测 Claude 自动触发
---

# 项目文档影响与收尾（Claude 入口）

本文件只供 Claude Code 从 `.claude/skills/` 发现此 Skill，不维护任何步骤。触发后先完整阅读 `.agents/skills/update-project-docs/SKILL.md`，并严格按该正文执行。修改 Skill 时先改正文来源；`name` 或 `description` 变化时同步本入口。

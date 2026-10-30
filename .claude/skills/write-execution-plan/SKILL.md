---
name: write-execution-plan
description: 在维护者于当前任务或仍有效约束中明确交给其他 Agent 执行、且需要准备方案时使用。只编写并保存可交接的独立方案文件，覆盖技术背景、实施、本地验证、手动验收与验收后文档更新；本次不执行方案。普通讨论、标准 Skill 直接执行、维护者要求当前 Agent 实施均不自动触发；只参考方案要素不等于调用交接流程。
metadata:
  title: 独立执行方案编写 Claude 入口
  purpose: 让 Claude Code 发现本 Skill 并转读唯一正文来源
  status: 当前有效
  owner: 当前任务 Agent；维护者负责审阅、合并与正式发布
  scope: Claude Code 对 write-execution-plan 的发现与触发
  updated: 2026-10-30
  verification: 已静态检查
  verified: 2026-10-30；Check 核对与正文来源的 name、description 一致；未实测 Claude 自动触发
---

# 独立执行方案编写（Claude 入口）

本文件只供 Claude Code 从 `.claude/skills/` 发现此 Skill，不维护任何步骤。触发后先完整阅读 `.agents/skills/write-execution-plan/SKILL.md`，并严格按该正文执行。修改 Skill 时先改正文来源；`name` 或 `description` 变化时同步本入口。

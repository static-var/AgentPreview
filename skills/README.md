# AgentPreview Skills

This directory contains agent-tool-agnostic workflow skills.

These files are plain Markdown so they can be copied into, referenced by, or loaded from most agentic coding tools. They are not tied to Pi, Claude, Cursor, Copilot, or any one runner.

## Available skills

- [`agentpreview-compose-iteration/SKILL.md`](agentpreview-compose-iteration/SKILL.md): how agents should use AgentPreview to build, inspect, and iterate on Jetpack Compose UI from Figma/design/screenshot requests.

## How to use

Ask your coding agent to read the skill before starting UI work, or copy the skill into your agent tool's preferred rules/skills/instructions location.

Example prompt:

```text
Before editing Compose UI, read skills/agentpreview-compose-iteration/SKILL.md and follow that workflow.
```

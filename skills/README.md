# AgentPreview Skills

This directory contains agent-tool-agnostic workflow skills.

These files are plain Markdown so they can be copied into, referenced by, or loaded from most agentic coding tools. They are not tied to Pi, Claude, Cursor, Copilot, or any one runner.

## Available skills

- [`agentpreview`](agentpreview/SKILL.md): use AgentPreview capture artifacts to visually and structurally inspect Compose UI while writing or reviewing code.

## Install

List the skill:

```bash
npx skills add static-var/AgentPreview --list
```

Install the AgentPreview skill:

```bash
npx skills add static-var/AgentPreview --skill agentpreview
```

Install globally for Codex:

```bash
npx skills add static-var/AgentPreview --skill agentpreview -g -a codex -y
```

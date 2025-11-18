# KlaudeKode - Quick Start Guide

🤖 **AI Coding Agent powered by Koog and Claude**

## Setup (One-time)

1. **Get an Anthropic API key**:
   - Visit: https://console.anthropic.com/settings/keys
   - Create a new API key

2. **Set environment variable**:
   ```bash
   export ANTHROPIC_API_KEY=your_key_here

   # Or add to ~/.bashrc for persistence:
   echo 'export ANTHROPIC_API_KEY=your_key_here' >> ~/.bashrc
   source ~/.bashrc
   ```

## Usage

### Build the agent:
```bash
./gradlew installDist
```

### Run the agent:
```bash
JAVA_HOME=/home/admin/.jdks/openjdk-24.0.1 \
./app/build/install/app/bin/app "Your task here"
```

### Example Tasks

#### 1. Read and explain code:
```bash
./app/build/install/app/bin/app "Read CodingAgent.kt and explain how it works"
```

#### 2. List files:
```bash
./app/build/install/app/bin/app "List all Kotlin files in the app directory"
```

#### 3. Make code changes:
```bash
./app/build/install/app/bin/app "Add a function called greet() to App.kt that prints a greeting"
```

#### 4. Navigate codebase:
```bash
./app/build/install/app/bin/app "Find where AIAgent is configured and tell me what tools are registered"
```

## Alias for Easy Use

Add this to your `~/.bashrc`:

```bash
alias koog-agent='JAVA_HOME=/home/admin/.jdks/openjdk-24.0.1 /home/admin/ACodeSpace/push/KlaudeKode/app/build/install/app/bin/app'
```

Then use it like:
```bash
koog-agent "Your task"
```

## Current Capabilities

✅ **File Operations**:
- Read files
- Edit files
- List directories

✅ **AI Model**:
- Claude Sonnet 4.5 (latest model)
- Temperature: 0.3 (consistent code generation)

✅ **Features**:
- Single-run strategy (completes task in one go)
- Max 50 iterations
- Tool call logging
- Error handling

## Limitations (MVP)

❌ Not yet implemented:
- Custom search (glob/grep)
- Git operations
- Task management
- Interactive questions
- Kotlin script execution
- Sub-agents

See [reference/building-claude-code-with-koog.md](./reference/building-claude-code-with-koog.md) for the full roadmap.

## Troubleshooting

### "ANTHROPIC_API_KEY not set"
Make sure you've exported the environment variable:
```bash
export ANTHROPIC_API_KEY=your_key_here
```

### "UnsupportedClassVersionError"
You need to run with JDK 24:
```bash
JAVA_HOME=/home/admin/.jdks/openjdk-24.0.1 ./app/build/install/app/bin/app "task"
```

### "Command not found"
First build the distribution:
```bash
./gradlew installDist
```

### Rebuild after code changes:
```bash
./gradlew build installDist
```

## Development

### Run tests:
```bash
./gradlew test
```

### Clean build:
```bash
./gradlew clean build
```

### Check what tasks are available:
```bash
./gradlew tasks
```

## Next Steps

1. **Test the agent** - Try various coding tasks
2. **Read the planning docs** in `reference/` folder
3. **Follow Phase 1 plan** to add more tools
4. **Contribute** - Implement missing features!

## Documentation

- **Master Plan**: [reference/building-claude-code-with-koog.md](./reference/building-claude-code-with-koog.md)
- **Risk Analysis**: [reference/issues-and-uncertainties.md](./reference/issues-and-uncertainties.md)
- **Implementation Guide**: [reference/implementation-kickoff.md](./reference/implementation-kickoff.md)
- **Session Summary**: [reference/session-summary.md](./reference/session-summary.md)
- **Development SOPs**: [CLAUDE.md](./CLAUDE.md)

## Support

For questions or issues:
1. Check the planning documents in `reference/`
2. Review Koog documentation: https://docs.koog.ai
3. Check Claude Code patterns for inspiration

---

**Ready to code with AI!** 🚀

# Koog Code Agent - GUI Version

This application has been converted to a Desktop GUI application using Compose Multiplatform.
It now includes interactive capabilities inspired by `VibeKoog`.

## Prerequisites
- JDK 21+
- `config.yml` in the working directory with your Anthropic API Key.

## Configuration
Create a `config.yml` file in the root directory (where you run the app from):
```yaml
llm:
  apiKey: "your-anthropic-api-key"
```

## Running the Application
Run the application using Gradle:

```bash
./gradlew :app:run
```

## Features
- **Task Input**: Enter your coding task description.
- **Real-time Logs**: See tool calls (file reading, listing, editing) as they happen.
- **Interactive Communication**: The agent can now ask you questions (e.g., for clarification) and you can reply directly in the UI.
- **Result Display**: View the final output of the agent.
- **Thread Safety**: Agent runs in a background thread, keeping the UI responsive.

## Interaction
1. Enter a task and click "Run".
2. If the agent needs more information, it will use the `waitForUserInput` tool.
3. The input field will be re-enabled, and the button will change to "Send".
4. Type your answer and click "Send" to resume the agent.
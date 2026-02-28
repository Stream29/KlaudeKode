package io.github.stream29.kode.config.core

public object ConfigTemplateProvider {

    public fun getDefaultTemplate(): String = DEFAULT_TEMPLATE

    private val DEFAULT_TEMPLATE = """
        # Kode Configuration
        # Three-level architecture:
        # 1. auths: Provider credentials (can be shared by multiple models)
        # 2. models: Specific model configurations referencing an auth
        # 3. Chat: Runtime model selection from the pre-registered model list
        # 4. storage: App-private data root (sessions/global state)
        # 5. defaults: Runtime defaults (model + thinking)
        # 6. loop_control: Agent loop control limits
        # 7. services: External service config (web search/fetch)
        # 8. mcp: MCP client/servers config
        # 9. skills/preset/ui/logging: UX and behavior settings
        
        auths:
          - id: anthropic-main
            provider_id: anthropic
            auth:
              type: api_key
              api_key: your-anthropic-api-key-here
              env_keys:
                - ANTHROPIC_API_KEY
              base_url: null
              custom_headers: {}
          - id: openai-subscription
            provider_id: openai-subscription-browser
            auth:
              type: oauth
              oauth:
                storage: file
                key: ~/.kode/oauth/openai-subscription.oauth.json
                authorization_endpoint: https://auth.openai.com/oauth/authorize
                token_endpoint: https://auth.openai.com/oauth/token
                client_id: app_EMoamEEZ73f0CkXaXp7hrann
                scopes:
                  - openid
                  - profile
                  - email
                  - offline_access
                callback_uri: http://localhost:1455/auth/callback
                authorization_additional_params:
                  id_token_add_organizations: "true"
                  codex_cli_simplified_flow: "true"
                  originator: opencode
                token_additional_params: {}
  base_url: https://chatgpt.com
              custom_headers: {}
          # Add more auths as needed:
          # - id: openai-main
          #   provider_id: openai-api-key
          #   auth:
          #     type: api_key
          #     api_key: your-openai-key
          #     env_keys:
          #       - OPENAI_API_KEY
          #     base_url: null
          #     custom_headers: {}
          # - id: moonshot-main
          #   provider_id: moonshot
          #   auth:
          #     type: api_key
          #     api_key: your-moonshot-key
          #     env_keys:
          #       - MOONSHOT_API_KEY
          #     base_url: https://api.moonshot.cn/v1
          #     custom_headers: {}
        
        models:
          - id: claude-sonnet
            auth_id: anthropic-main
            model: claude-sonnet-4-5-20250929
            display_name: Claude Sonnet 4.5
          # Add more models using the same or different auths:
          # - id: claude-haiku
          #   auth_id: anthropic-main
          #   model: claude-haiku-4-5-20251001
          #   display_name: Claude Haiku 4.5
          # - id: gpt-4o
          #   auth_id: openai-main
          #   model: gpt-4o
          #   display_name: GPT-4o
          # - id: kimi-k2
          #   auth_id: moonshot-main
          #   model: kimi-k2-0711-preview
          #   display_name: Kimi K2

        storage:
          data_dir: "~/.kode/"

        defaults:
          model_id: claude-sonnet
          thinking: false
          work_dir: "."

        loop_control:
          max_steps_per_turn: 100
          max_retries_per_step: 3
          max_ralph_iterations: 0
          reserved_context_size: 50000

        services:
          web_search:
            provider: none
            api_key: ""
            base_url: null
          web_fetch:
            provider: builtin
            api_key: ""
            base_url: null

        mcp:
          client:
            tool_call_timeout_ms: 60000
          servers: {}

        skills:
          dir: "~/.kode/skills"

        preset:
          builtin: default
          file: null

        ui:
          theme: dark
          message_alignment: left
          message_max_width_ratio: 0.9
          send_key_mode: ctrl_or_cmd_enter_send
          last_opened_session_id: null

        logging:
          level: info
          file: null

        tools:
          disabled: []
    """.trimIndent()
}

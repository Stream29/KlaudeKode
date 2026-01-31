package io.github.stream29.kode.config.core

public object ConfigTemplateProvider {
    
    public fun getDefaultTemplate(): String = DEFAULT_TEMPLATE
    
    private val DEFAULT_TEMPLATE = """
        # Kode Configuration
        # Three-level architecture:
        # 1. auths: Provider credentials (can be shared by multiple models)
        # 2. models: Specific model configurations referencing an auth
        # 3. Chat: Runtime model selection from the pre-registered model list
        
        auths:
          - type: Anthropic
            id: anthropic-main
            api_key: your-anthropic-api-key-here
            base_url: null
          # Add more auths as needed:
          # - type: OpenAI
          #   id: openai-main
          #   api_key: your-openai-key
          #   base_url: null
          # - type: Moonshot
          #   id: moonshot-main
          #   api_key: your-moonshot-key
          #   base_url: https://api.moonshot.cn/v1
        
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
    """.trimIndent()
}

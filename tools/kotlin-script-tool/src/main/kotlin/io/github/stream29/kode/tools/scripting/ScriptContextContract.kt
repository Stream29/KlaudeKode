package io.github.stream29.kode.tools.scripting

public interface ScriptContext {
    public val defaultImports: List<String>
    public val systemPromptInjection: String
}

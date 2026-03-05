# 模型与LLM API

## 设计思想

我们希望每一款ModelProvider都对应着一种模型厂商的鉴权/认证方式，比如Anthropic的API Key。
而OpenAI的API key和订阅OAuth应该是两个不同的认证方式，所以是两套不同的ModelProvider，不同的ModelProviderConfig。
每个ModelProvider都对应着一个ModelProviderConfig的类定义。
如果ModelProvider收到了不符合预期的ModelProviderConfig，它应该直接抛出异常。
每种认证方式都对应着一个ModelProvider，用来承载实际的LLM调用逻辑。
每种认证方式都对应着一个ModelProviderConfig，用来对接那个前端让用户进行认证的表单。
区别在于， 同一个ModelProvider可以接入同一个ModelProviderConfig的多个实例，比如一个用户可能有多个Anthropic的API Key。
ModelProvider应该是无状态的，只需要依赖注入一下httpClient即可，只需要一个单例注入给全局。
而ModelProviderConfig实际存储了配置信息的状态，所以可能有数量不定的实例。

全局的依赖注入使用koin，持有全量的ModelProvider列表，因此就持有全量的ModelCard列表。
ModelCard是预定义的，用户不能自定义新模型，只能填写ModelConfig。

我们希望每个ModelCard都对应着一个ModelConfig类定义，多个ModelCard可以共享同一个ModelConfig类，如果它们的配置参数列表完全相同。
因为每个ModelConfig都对应着一个模型的配置表单。

`name: ModelName`应当是ModelCard的唯一标识，同一个Provider下的所有ModelConfig的name都应该不同。
每一个ModelCard都应该对应着一款模型，多个变体应当算作同一款模型。

同一个ModelCard也可能对应着同一个ModelConfig类的多个实例。
可能是接入的providerId不同（比如使用了不同的api key）， 也可能是配置了不同的参数（比如不同的temperature）。
如果有多个实例，那么它们的id应该不同。

我们希望每个ModelConfig都对应着一个前端上可以让用户填写并修改的表单，所以ModelConfig的实现类里的字段应当是写死的。
而一个ModelProvider旗下的模型虽然认证方式相同，但是参数可能有一些小的差距，所以一个ModelProvider可能对应着不同的ModelConfig。

我们通过依赖注入的方式，订阅全局的配置文件，从中直接获取对应的ModelProviderConfig和ModelConfig列表。
注意：每个ModelProviderConfig都需要用户手动添加，是api key的要填api key，是OAuth的要跳转登录认证。
而每个ModelConfig也需要用户手动添加，ModelProvider提供模型列表，用户选择一个模型以后再让用户去配置模型参数。

不再使用koog自带的那套管理系统了！我们自定义相关的接口，只是在底层实现上可能借用一些koog的东西。

对于用户来说，他们能交互的那个ModelProvider，实际上是我们存储的那个ModelProviderConfig。
类似地，对于用户来说，他们能交互的那个Model，实际上是我们存储的那个ModelConfig。

## Model Provider

这些是我们预定义好的全局对象。我们提前把全量的模型供应商、全量的模型列表、调用逻辑都预定义好。

```kotlin
@Serializable
sealed interface ModelConfigType

@Serializable
value class ModelName(val value: String)

@Serializable
value class ModelProviderType(val value: String)

interface ModelCard {
    val name: ModelName
    val maxInputTokens: Long
    val modelIdentifier: ModelId
    val modelConfigType: ModelConfigType
}

interface ModelProvider {
    val providerType: ModelProviderType
    val modelList: List<ModelCard>
    suspend fun invoke(
        modelConfig: ModelConfig,
        input: Sequence<AgentMessage>
    ): AgentMessage.AgentScript
}
```

## Model Config

以下为运行期涉及的配置等信息，存在app级别的config下。这里只给出接口。
```kotlin
// 这个ID对应着运行时和ModelProvider配对好的一个ModelConfig
@Serializable
value class ModelId(val value: String)

// 这个ID对应着运行时的一个ModelProviderConfig
@Serializable
value class ModelProviderId(val value: String)

@Serializable
sealed interface ModelProviderType

@Serializable
sealed interface ModelProviderConfig {
    val id: ModelProviderId
    // 通过这个字段将全局的无状态ModelProvider和ModelProviderConfig进行关联
    val providerType: ModelProviderType
}

@Serializable
sealed interface ModelConfig {
    val id: ModelId
    // 通过这个字段将全局的无状态ModelCard和ModelConfig进行关联
    val modelName: ModelName
    val providerId: ModelProviderId
    val providerType: ModelProviderType
}
```

## 举例说明

对于Anthropic的`claude-sonnet-4-6`模型，首先要定义对应的ModelConfig和ModelProviderCredential：

```kotlin
@Serializable
data class ClaudeApiKeyProviderConfig(
    override val id: ModelProviderId,
    val apiKey: String // 举API key作为例子，如果是OAuth的话会更复杂一点
): ModelProviderConfig {
    companion object {
        val type = ModelProviderType("claude-api-key")
    }
    override val providerType: ModelProviderType = type
}

@Serializable
data class ClaudeApiKeyModelConfig(
    override val id: ModelId,
    override val modelName: ModelName,// 这个字段在配置文件里被填为claude-sonnet-4-6
    override val providerId: ModelProviderId,
    override val providerType: ModelProviderType = ClaudeApiKeyProviderConfig.type,
    val temperature: String? = null // 举这个作为例子，实际可能还有别的配置字段
): ModelConfig {
    companion object {
        @Serializable
        data object TypeMark: ModelConfigType
    }
}
```

然后要定义对应的ModelProvider：
```kotlin
class ClaudeModelProvider(
    val httpClient: HttpClient
): ModelProvider {
    override val providerType: ModelProviderType = ClaudeApiKeyProviderConfig.type

    override val modelList: List<ModelCard> = listOf(
        object : ModelCard {
            override val name = ModelName("claude-sonnet-4-6")
            override val maxInputTokens = 200_000L
            override val modelIdentifier = ModelId("claude-sonnet-4-6-default")
            override val modelConfigType = ClaudeApiKeyModelConfig.TypeMark
        }
    )

    override suspend fun invoke(
        modelConfig: ModelConfig,
        input: Sequence<AgentMessage>
    ): AgentMessage.AgentScript {
        // 具体实现
        TODO("Not yet implemented")
    }
}
```
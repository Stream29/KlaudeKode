# Script

传统Agent：每次只能输出一个工具调用，
然后再经过一个痛苦且肮脏的过程对这个工具调用进行解析，尝试返回一个对Agent友好的东西。
基于Script的理想情况：Kode Agent直接通过脚本的程序化手段去编排工具调用，并且可以利用Kotlin生态里的协程等基础设施。

## ScriptContext

### `implicitReceiver`
Kotlin Script can have `implicitReceiver`。这使得我们可以向脚本的运行环境中注入一些全局可用的方法，供Agent调用。
同时，这个receiver被调用了成员方法以后，相当于接收了成员方法的副作用，而我们可以在这个副作用的基础上执行一些特殊操作。
比如，可以把对`ScriptContext.sayToUser`的调用记录下来，转换成实际展示给用户的文字信息。

我们将这个注入到脚本执行环境中的`implicitReceiver`称为`ScriptContext`。
不同的情况下我们会注入不同的`ScriptContext`。
比如MainAgent和SubAgent的`ScriptContext`是不同的，因为它们可以调用的工具就不一样。

### `systemPromptInjection`
因为MainAgent和SubAgent可以调用的工具就不一样，为了让它们知道自己可以以什么方式在脚本中调用什么方法，
所以`ScriptContext`需要提供一个`systemPromptInjection: String`属性，将一段文字注入到系统提示词中，
让Agent知道自己可以调用什么方法。
这段文字至少要明确说明这些方法的ABI，如果方法的参数和返回值涉及了自定义的类型，也需要说明。
这段文字也需要说明什么情况下应该/不应该调用这些方法。

### `defaultImports`
为了方便Agent调用，我们希望Agent在调用我们注入的这些方法时尽可能不需要手写import语句，尤其是这些方法需要传入我们自定义的数据模型时。
这里就涉及到Kotlin Script的另一个特性了：`defaultImports`。
每个`ScriptContext`可以指定一组`defaultImports: Sequence<String>`，这样Agent在使用这些方法/类时就不需要手写import语句了。

### 模块化
因为`ScriptContext`有这么多属性，还有一些需要提供给Agent调用的方法，且我们需要有重叠但不完全相同的各种`ScriptContext`，
所以`ScriptContext`需要模块化，将不同的原子功能封装成一个个小的`ScriptContext`，有自己的方法、`systemPromptInjection`和`defaultImports`。
然后，我们就可以想办法把它们组合在一起，得到我们实际想要使用的那个`ScriptContext`。
比如说，`sayToUser`和`suspendForUserInput`这几个方法就可以打包成一个`UserCommunicationScriptContext`。

这个构造`ScriptContext`的过程应当是开销很低的，可以每次执行脚本的时候都重新构造。

具体的组合方法是这样的：为每个`ScriptContext`模块分开定义其接口和实现类。
而最终的合体产物（比如`MainScriptContext`）则是通过Kotlin的`by`委托，通过这些实现类，无缝实现了对应的模块接口。
暴露给Agent的方法可以直接继承，而`systemPromptInjection`和`defaultImports`则是各个子实现类对应属性的组合产物。
比如`MainScriptContext`的`defaultImports`就是其下各个模块的`defaultImports`拼接而来。

具体的模块列表请参考`./ScriptContextModules/`。

## 脚本的执行

脚本通过`reified fun <T: ScriptContext> T.eval`来执行，相当于直接以当前的`ScriptContext`执行脚本。
在实际实践中，我们会需要将这个过程和主线程隔离，开一个独立的线程来执行脚本。
即造一个新的方法`suspend inline fun <reified T : ScriptContext> T.evalInThreadCancellable`。
它需要完整地实现Kotlin协程的取消语义，确保可以取消脚本中的耗时操作。
虽然`Thread.stop`是个废弃的操作，但是当脚本的执行被外界通过Kotlin协程取消时，我们的策略是：
首先尝试使用`Thread.interrupt`，如果1秒内没有成功停止，则使用`Thread.stop`。

这是目前的做法。在未来可能会考虑为脚本专门启动一个进程来执行，从而实现完整的`workDir`语义。
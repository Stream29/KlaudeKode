# Todo List 工具

## 数据结构设计

Todo List应该是Agent级别的，MainAgent和SubAgent可以有自己独立的Todo List。
它应该被存在Agent的Metadata中。换言之，每次对它的更新都会触发对应的Metadata的更新。

```kotlin
// 这个类需要添加到defaultImports中
@Serializable
data class TodoItem(
    val name: String,
    val completed: Boolean = false,
    val subItems: List<TodoItem> = emptyList()
)

interface TodoListScriptContext: ScriptContext {
    // 暴露给Agent的接口
    fun listTodoItems(): List<TodoItem>
    fun clearTodoItems()
    fun resetTodoItems(items: List<TodoItem>)
    // path为包含本级的路径，由对应的name构成，比如["step1", "substep1"]
    fun editTodoItem(vararg path: String, update: (TodoItem) -> TodoItem)
}
```

## UI

在输入框之上，附带着一个平时默认折叠的panel，只露出一个"Todo List <上箭头>"的按钮。
用户点击按钮以后，上箭头变成下箭头，并且panel向上弹出，展示当前的Todo List树。
panel的大小包裹树，同时设置最大值。

前端应该订阅Agent Metadata中的Todo List，实时更新UI。

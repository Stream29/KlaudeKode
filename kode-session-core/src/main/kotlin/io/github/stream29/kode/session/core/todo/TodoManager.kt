package io.github.stream29.kode.session.core.todo

import io.github.stream29.kode.session.core.model.TodoNode
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class TodoManager(
    initialNodes: List<TodoNode>,
) {
    private val nodes: MutableList<TodoNode> = initialNodes.toMutableList()

    public constructor() : this(initialNodes = emptyList())

    init {
        validateInitialNodes(nodes = nodes)
    }

    public fun addNode(parentId: String?, text: String): TodoNode {
        validateParentId(parentId = parentId)
        val node = TodoNode(
            id = generateId(),
            text = text,
            completed = false,
            parentId = parentId,
            metadata = null,
        )
        nodes.add(node)
        return node
    }

    public fun updateNode(id: String, text: String): TodoNode {
        val index = requireNodeIndex(id = id)
        val updatedNode = nodes[index].copy(text = text)
        nodes[index] = updatedNode
        return updatedNode
    }

    public fun removeNode(id: String) {
        val subtreeIds = collectSubtreeIds(rootId = id)
        nodes.removeAll { node -> subtreeIds.contains(node.id) }
    }

    public fun getNode(id: String): TodoNode? {
        return nodes.firstOrNull { node -> node.id == id }
    }

    public fun listAllNodes(): List<TodoNode> {
        return nodes.toList()
    }

    private fun validateInitialNodes(nodes: List<TodoNode>) {
        val duplicateId = nodes
            .groupingBy { node -> node.id }
            .eachCount()
            .entries
            .firstOrNull { entry -> entry.value > 1 }
            ?.key
        require(duplicateId == null) { "Duplicate todo node id: $duplicateId" }

        val nodeIds = nodes.map { node -> node.id }.toSet()
        nodes.forEach { node ->
            val parentId = node.parentId
            if (parentId != null) {
                require(parentId in nodeIds) { "Parent todo node does not exist: $parentId" }
                require(parentId != node.id) { "Todo node cannot reference itself as parent: ${node.id}" }
            }
        }

        val nodeById = nodes.associateBy { node -> node.id }
        nodes.forEach { node ->
            val visited: MutableSet<String> = mutableSetOf()
            var currentId: String? = node.id
            while (currentId != null) {
                require(visited.add(currentId)) { "Cycle detected in todo tree at node: $currentId" }
                currentId = nodeById[currentId]?.parentId
            }
        }
    }

    private fun validateParentId(parentId: String?) {
        if (parentId == null) {
            return
        }
        require(nodes.any { node -> node.id == parentId }) { "Parent todo node does not exist: $parentId" }
    }

    private fun requireNodeIndex(id: String): Int {
        val index = nodes.indexOfFirst { node -> node.id == id }
        require(index >= 0) { "Todo node not found: $id" }
        return index
    }

    private fun collectSubtreeIds(rootId: String): Set<String> {
        requireNodeIndex(id = rootId)

        val subtreeIds: MutableSet<String> = mutableSetOf(rootId)
        var discovered = true
        while (discovered) {
            discovered = false
            nodes.forEach { node ->
                val parentId = node.parentId
                if (parentId != null && parentId in subtreeIds && subtreeIds.add(node.id)) {
                    discovered = true
                }
            }
        }
        return subtreeIds
    }

    private fun generateId(): String {
        var candidate = Uuid.random().toString()
        while (nodes.any { node -> node.id == candidate }) {
            candidate = Uuid.random().toString()
        }
        return candidate
    }
}

package io.github.stream29.kode.app.view

import androidx.navigation3.runtime.NavKey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
public enum class AppPage(
    public val title: String,
    public val icon: ImageVector
) : NavKey {
    Chat(title = "Chat", icon = Icons.AutoMirrored.Filled.Chat),
    Sessions(title = "Sessions", icon = Icons.Default.FolderOpen),
    Models(title = "Models", icon = Icons.Default.Settings),
    Settings(title = "Settings", icon = Icons.Default.Tune),
    Tools(title = "Tools", icon = Icons.Default.Build),
    Mcp(title = "MCP", icon = Icons.Default.Link),
    Acp(title = "ACP", icon = Icons.Default.DeviceHub),
    Terminal(title = "Terminal", icon = Icons.Default.Code),
    Web(title = "Web", icon = Icons.Default.Public),
    Info(title = "Info", icon = Icons.Default.Info),
}

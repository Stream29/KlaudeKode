package io.github.stream29.kode.app.view

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
public data class AddModelDialogRoute(
    val preselectedAuthId: String?,
    val requestNonce: Long,
) : NavKey

@Serializable
public data class EditModelDialogRoute(
    val modelId: String,
    val requestNonce: Long,
) : NavKey

@Serializable
public data class AddAuthDialogRoute(
    val requestNonce: Long,
) : NavKey

@Serializable
public data class EditAuthDialogRoute(
    val authId: String,
    val requestNonce: Long,
) : NavKey

@Serializable
public data class DeleteAuthConfirmDialogRoute(
    val authId: String,
    val requestNonce: Long,
) : NavKey

@Serializable
public data object NewSessionDirDialogRoute : NavKey

@Serializable
public data object EditSessionDirDialogRoute : NavKey

@Serializable
public data object SessionManagerDialogRoute : NavKey

@Serializable
public data object ConfigEditorDialogRoute : NavKey

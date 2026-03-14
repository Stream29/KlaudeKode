package io.github.stream29.kode.ui.core.preferences

public enum class SendKeyModePreference(
    public val value: String,
    public val label: String,
    public val description: String,
) {
    CtrlOrCmdEnterSend(
        value = "ctrl_or_cmd_enter_send",
        label = "Ctrl/Cmd+Enter send",
        description = "Enter inserts a new line",
    ) {
        override fun shouldSubmitShortcut(
            isCtrlPressed: Boolean,
            isMetaPressed: Boolean,
            isShiftPressed: Boolean,
        ): Boolean {
            return isCtrlPressed || isMetaPressed
        }
    },

    EnterSendShiftEnterNewline(
        value = "enter_send_shift_enter_newline",
        label = "Enter send",
        description = "Shift+Enter inserts a new line",
    ) {
        override fun shouldSubmitShortcut(
            isCtrlPressed: Boolean,
            isMetaPressed: Boolean,
            isShiftPressed: Boolean,
        ): Boolean {
            return !isShiftPressed
        }
    },
    ;

    public abstract fun shouldSubmitShortcut(
        isCtrlPressed: Boolean,
        isMetaPressed: Boolean,
        isShiftPressed: Boolean,
    ): Boolean

    public companion object {
        public fun fromValue(value: String): SendKeyModePreference {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { option -> option.value == normalized } ?: CtrlOrCmdEnterSend
        }
    }
}

public enum class MessageAlignmentPreference(
    public val value: String,
    public val label: String,
) {
    Left(
        value = "left",
        label = "Same side (left)",
    ) {
        override fun userAlignsToEnd(): Boolean {
            return false
        }
    },

    Split(
        value = "split",
        label = "Split sides",
    ) {
        override fun userAlignsToEnd(): Boolean {
            return true
        }
    },
    ;

    public abstract fun userAlignsToEnd(): Boolean

    public companion object {
        public fun fromValue(value: String): MessageAlignmentPreference {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { option -> option.value == normalized } ?: Left
        }
    }
}

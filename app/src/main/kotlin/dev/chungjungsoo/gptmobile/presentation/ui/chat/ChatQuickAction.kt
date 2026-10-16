package dev.chungjungsoo.gptmobile.presentation.ui.chat

enum class ChatQuickAction {
    Shorter,
    Explain,
    Continue,
    Proofread,
    Bullets,
    Irish
}

fun quickActionPrompt(action: ChatQuickAction): String = when (action) {
    ChatQuickAction.Shorter -> "Rewrite your previous answer so it is shorter. Keep the points that matter."
    ChatQuickAction.Explain -> "Explain your previous answer in plain language. Assume I am new to the topic."
    ChatQuickAction.Continue -> "Continue your previous answer from where it stopped."
    ChatQuickAction.Proofread -> "Proofread your previous answer. Fix grammar and clarity, and keep the meaning."
    ChatQuickAction.Bullets -> "Turn your previous answer into a short bullet list."
    ChatQuickAction.Irish -> "Translate your previous answer into Irish (Gaeilge). Keep names, numbers, and code unchanged."
}

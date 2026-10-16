package dev.chungjungsoo.gptmobile.presentation.voice

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

interface AssistantRoleGateway {
    fun isAvailable(): Boolean

    fun isHeld(): Boolean

    fun requestIntent(): Intent?
}

class AndroidAssistantRoleGateway(
    context: Context
) : AssistantRoleGateway {
    private val roleManager = context.applicationContext.getSystemService(RoleManager::class.java)

    override fun isAvailable(): Boolean = roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)

    override fun isHeld(): Boolean = roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)

    override fun requestIntent(): Intent? {
        if (!isAvailable() || isHeld()) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }
}

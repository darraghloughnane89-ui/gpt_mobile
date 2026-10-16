package dev.chungjungsoo.gptmobile.data.phonecontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object PackageSignature {
    fun certSha256(context: Context, packageName: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            null
        }
        val cert = signatures?.firstOrNull() ?: return null
        MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

class StructuredActionLauncher(private val context: Context) {
    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun launchView(uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun executeAppFunction(packageName: String, functionId: String): Boolean {
        if (Build.VERSION.SDK_INT < 36 || functionId.isBlank()) return false
        return runCatching {
            val managerClass = Class.forName("android.app.appfunctions.AppFunctionManager")

            @Suppress("UNCHECKED_CAST")
            val manager = context.getSystemService(managerClass as Class<Any>) ?: return false
            val requestClass = Class.forName("android.app.appfunctions.ExecuteAppFunctionRequest")
            val builderClass = Class.forName("android.app.appfunctions.ExecuteAppFunctionRequest\$Builder")
            val builder = builderClass.getConstructor(String::class.java, String::class.java).newInstance(packageName, functionId)
            val request = builderClass.getMethod("build").invoke(builder)
            val execute = managerClass.methods.firstOrNull { it.name == "executeAppFunction" } ?: return false
            execute.invoke(manager, request, context.mainExecutor, android.os.CancellationSignal(), null)
            true
        }.getOrDefault(false)
    }
}

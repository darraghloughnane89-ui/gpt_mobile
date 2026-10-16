# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep reactive streams dependencies
-keep class reactor.** { *; }
-keep class io.micrometer.** { *; }
-dontwarn io.micrometer.**
-dontwarn reactor.**

# AppAuth (Hugging Face OAuth redirect / token exchange)
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# LiteRT-LM (on-device inference)
# The litertlm-android AAR ships no consumer ProGuard rules, but its native layer
# resolves Java members by name through JNI. LiteRtLmJni.nativeCreateConversation
# calls SamplerConfig.getTopK()/getTopP()/getTemperature() via CallIntMethod, and
# the streaming path calls MessageCallback.onMessage/onDone/onError. R8 full mode
# strips these members because nothing in Java references them, GetMethodID
# returns null, and ART aborts the process with
# "JNI DETECTED ERROR IN APPLICATION: mid == null" on the first local message.
# Keeping the package also preserves the names of this app's MessageCallback and
# OpenApiTool implementations, which must match the interfaces they override.
-keep class com.google.ai.edge.litertlm.** { *; }

# Ignore missing optional logging dependencies used by Netty
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**

# R8 full-mode keep audit
# android.enableR8.fullMode=true and android.r8.strictFullModeForKeepRules=true.
#
# Checked and left to consumer rules (no extra keep):
# - Room entities, DAOs, and migrations
# - Hilt, WorkManager workers, and Glance receivers
# - Parcelize (ChatRoomV2)
# - Ktor CIO and OkHttp. Engines are passed directly, not loaded with ServiceLoader
# - AppAuth (kept above)
#
# Checked and kept explicitly:
# - kotlinx.serialization serializers. The rules below match the 1.11 consumer rules
#   so a future dependency cannot drop Companion.serializer() lookups under full mode.
# - MCP SDK companions. The SDK serializes its models and does not ship consumer rules.
# - LiteRT-LM JNI names. This is the llama.cpp-class native boundary. The package keep
#   above stays broad on purpose: narrowing it reintroduces mid == null aborts.
# - Line numbers, with the source file renamed, so release stack traces stay usable.

-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class **$$serializer {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class io.modelcontextprotocol.kotlin.sdk.** {
    *** Companion;
}
-dontwarn io.modelcontextprotocol.**

-keepclasseswithmembernames class com.google.ai.edge.litertlm.** {
    native <methods>;
}
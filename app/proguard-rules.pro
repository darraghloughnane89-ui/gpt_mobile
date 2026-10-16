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

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers so release crash reports can be retraced, without the path.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata is read by kotlinx.serialization, Hilt, and Compose.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }

# Room. Generated DAOs call these converters directly. Room's own consumer rules
# keep the database implementation; this keeps converter methods R8 full mode
# can treat as unused when the only reference is the generated adapter.
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-dontwarn androidx.room.paging.**

# kotlinx.serialization. serializer() is looked up on Companion / INSTANCE.
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
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.chungjungsoo.gptmobile.**$$serializer { *; }
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# Ktor CIO (chat) and OkHttp (URL tool). The factories are referenced by name.
# Optional TLS and logging backends are not packaged.
-keep class io.ktor.client.engine.cio.CIO { *; }
-keep class io.ktor.client.engine.okhttp.OkHttp { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**

# Hilt loads generated modules by name. The Gradle plugin adds the same roots;
# these rules keep a custom build type from dropping them under R8 full mode.
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **Hilt_* { *; }
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-dontwarn dagger.hilt.android.internal.managers.**

# Compose ships consumer rules. Unused Compose APIs should still shrink.
-dontwarn androidx.compose.**

# Enums stored in Room and in serialized model names.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
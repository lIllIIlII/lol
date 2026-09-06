# 吸析At R8 规则
# - Room/KSP/OkHttp/Compose 自带 consumer rules，无需重复
# - 代码不使用运行时反射；以下为防御性保留

# Kotlin 协程内部（部分版本 R8 误删元数据）
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# 枚举 values/valueOf（Room/持久化序列化兼容）
-keepclassmembers enum com.yunx.app.** { *; }

# Room 实体（注解处理器生成代码按字段名访问，保险起见不做混淆）
-keep class com.yunx.app.data.db.** { *; }

# 崩溃堆栈可读性：保留行号与源文件名（映射文件随构建产出）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp 平台检测（okhttp3.internal.platform）
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# javax.crypto AES（小飞机网盘解析使用，系统自带实现，仅需抑制告警）
-dontwarn javax.crypto.**

# 移除调试日志（release 减包体+提速）
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

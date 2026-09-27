# 项目 R8/ProGuard 规则

# 保留注解与调试信息,保证混淆后崩溃堆栈可读
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions

# Room:保留数据库实现类的公共构造函数(由 Room 运行时反射构建)
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# 主题与周次枚举以 name 字符串持久化于数据库,必须保留常量名与反序列化入口
-keep enum com.gdufs.schedule.data.model.ThemeMode { *; }
-keep enum com.gdufs.schedule.data.model.WeekMode { *; }

# 通用枚举安全网:保留 values()/valueOf()
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Koin 4 基于 Kotlin反射构建依赖图,保留模块声明所需的元数据
-dontwarn org.koin.**
-keep class org.koin.core.** { *; }

# ===== Kotlin 协程 kotlinx-coroutines =====
# 主线程调度器由 ServiceLoader 反射查找 MainDispatcherFactory,混淆后必须保留类名
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
# 协程状态机依赖 volatile 字段,保留字段结构避免 R8 优化导致协程状态错乱
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
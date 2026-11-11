# kotlinx.serialization: giữ serializer của các DTO
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.socialshop.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.socialshop.app.**$$serializer { *; }

# Retrofit (interface ApiService dùng reflection + suspend)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.socialshop.app.data.remote.ApiService { *; }
-keep interface com.socialshop.app.data.remote.AdminApiService { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**

# Regras R8/ProGuard do WebGames Launcher

# Métodos nativos JNI do core Rust — nomes não podem ser ofuscados
-keepclasseswithmembernames class com.deivid22srk.webgames.** {
    native <methods>;
}

# Modelos serializáveis (kotlinx.serialization)
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.deivid22srk.webgames.**$$serializer { *; }
-keepclassmembers class com.deivid22srk.webgames.** {
    *** Companion;
}
-keepclasseswithmembers class com.deivid22srk.webgames.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WebGL/WebView JS bridge: nada a manter além do padrão
-keep class android.webkit.JavascriptInterface { *; }

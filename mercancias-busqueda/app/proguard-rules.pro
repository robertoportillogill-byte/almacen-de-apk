# kotlinx.serialization: conservar los serializadores generados
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.mercancias.busqueda.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.mercancias.busqueda.**$$serializer { *; }

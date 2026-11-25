# kotlinx.serialization: conservar los serializadores generados
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.presupuesto.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

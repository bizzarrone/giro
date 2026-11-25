# Firebase Firestore usa reflection sui modelli (qui non ci sono @PropertyName, ma per sicurezza)
-keepclassmembers class simplylucaapps.girocalendar.data.remote.firestore.** {
  <init>();
  <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class simplylucaapps.girocalendar.**$$serializer { *; }
-keepclassmembers class simplylucaapps.girocalendar.** {
    *** Companion;
}
-keepclasseswithmembers class simplylucaapps.girocalendar.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

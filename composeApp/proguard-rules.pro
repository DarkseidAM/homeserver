# ProGuard rules for composeApp

# Keep Compose specific annotations
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations
-keepclassmembernames class  ** {
    @androidx.compose.runtime.Composable *;
}

# Add project specific rules here

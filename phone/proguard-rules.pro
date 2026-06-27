# Keep default Android Studio proguard rules.

# ML Kit barcode scanning crashes on some release/R8 builds when its internal
# vision classes are optimized. Keep the scanner stack intact for the phone QR
# login flow.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }

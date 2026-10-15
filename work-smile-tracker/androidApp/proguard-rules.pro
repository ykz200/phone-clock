# SmileTracker ProGuard Rules

# Keep Compose classes
-keep class androidx.compose.** { *; }
-keep class kotlin.coroutines.** { *; }

# Keep MediaPipe
-keep class com.google.mediapipe.** { *; }

# Keep SQLDelight
-keep class app.cash.sqldelight.** { *; }
-keep class com.ykz200.smiletracker.db.** { *; }

# Keep data classes
-keep class com.ykz200.smiletracker.domain.** { *; }

# Remove debug logs
-assumenosideseffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}

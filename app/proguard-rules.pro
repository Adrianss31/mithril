-keep class it.mithril.app.cast.CastOptionsProvider { *; }
-keep class it.mithril.app.widget.WidgetRefreshWorker { public <init>(...); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep,includedescriptorclasses class it.mithril.app.update.SignedRelease** { *; }
-keep,includedescriptorclasses class it.mithril.app.update.ReleaseInfo** { *; }
-keep class it.mithril.app.backup.AppHubBackupProvider { *; }

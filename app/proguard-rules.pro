-keep class it.mithril.app.cast.CastOptionsProvider { *; }
-keep class it.mithril.app.widget.WidgetRefreshWorker { public <init>(...); }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep,includedescriptorclasses class it.mithril.app.update.SignedRelease** { *; }
-keep,includedescriptorclasses class it.mithril.app.update.ReleaseInfo** { *; }

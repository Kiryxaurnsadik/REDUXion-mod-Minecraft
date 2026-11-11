-dontwarn **
-ignorewarnings
-dontnote **
-dontoptimize
-keepattributes *
-keepparameternames
-renamesourcefileattribute SourceFile
-adaptresourcefilenames **
-adaptresourcefilecontents **.json,fabric.mod.json
-repackageclasses 'dev.reduxion.r'
-keep class dev.reduxion.game.** { *; }
-keep class dev.reduxion.mixin.** { *; }
-keep class dev.reduxion.ui.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

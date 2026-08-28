# R8 rules for NVH Spectro.
#
# History note [audit C15]: until Phase 0 of the AAA plan, the JTransforms keep
# rule targeted the nonexistent package `com.github.wendykierp.jtransforms.**`
# (the Maven coordinates, not the Java package) and therefore kept nothing.
# JTransforms classes live in `org.jtransforms.**`.

# Ignore internal JTransforms / JLargeArrays optional Sun JVM classes on Android
-dontwarn sun.misc.Cleaner
-dontwarn pl.edu.icm.jlargearrays.**
-dontwarn org.apache.commons.**

# JTransforms and its array backend use reflective/thread-pool configuration
# internally; keep them intact in minified builds.
-keep class org.jtransforms.** { *; }
-keep class pl.edu.icm.jlargearrays.** { *; }

# Google Play Location Services
-keep class com.google.android.gms.location.** { *; }

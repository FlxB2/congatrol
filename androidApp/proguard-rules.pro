# Glance makes a widget button's ActionCallback from its class name, with its no-argument
# constructor, when the button is pressed. Nothing calls that constructor in code, so without this
# R8 removes it and a release build's buttons do nothing.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}

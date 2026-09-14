# Batangas font setup

Place the supplied licensed font file at:

`app/src/main/res/font/batangas.ttf`

Then change `PitakaFontFamily` in `ui/theme/Theme.kt` to:

```kotlin
val PitakaFontFamily = FontFamily(Font(R.font.batangas))
```

For multiple weights, add each TTF with its matching `FontWeight`. Android/Compose bundles custom fonts from `res/font` and exposes them through `FontFamily` and `Typography`.

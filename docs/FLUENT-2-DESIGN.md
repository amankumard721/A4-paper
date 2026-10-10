# Fluent 2 styling implementation

Applied to the existing Android Compose UI using shared semantic theme roles and native Material components. This is an app-specific adaptation of Fluent 2 guidance, not an installation of the Microsoft Fluent Android SDK or a claim of exact component parity.

- Complete light/dark neutral surfaces, brand/selected states, text, borders and error colors.
- Native Android sans-serif typography with system Hindi fallback and scalable sp units.
- Shared 8 dp rounded primary/outlined buttons, minimum 48 dp height and native focus/ripple/disabled behavior.
- Bordered document, template and settings cards; restrained elevation and 16 dp navigation/hero shapes.
- Theme-aware category text and document icons for dark mode.
- Short eased hero/nav animations; existing lifecycle-aware blinking cursor retained.
- Editor and PDF paper remain white with document-defined text colors.

Validation: 26 semantic foreground/background pairs checked at >=4.5:1 (light minimum 5.38:1, dark minimum 5.72:1). These checks cover the defined pairs, not every possible rendered state. Physical-device visual verification at large font sizes and in both themes remains pending. No APK/AAB generated for this change.

References:
- https://fluent2.microsoft.design/components/android
- https://fluent2.microsoft.design/color
- https://fluent2.microsoft.design/typography/
- https://fluent2.microsoft.design/shapes/
- https://fluent2.microsoft.design/motion

Kotlin compilation and Android lint completed successfully. See app/build/reports/lint-results-debug.html for lint details.

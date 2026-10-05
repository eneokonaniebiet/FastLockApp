# FastLockApp

FastLockApp is an Android app-owned temporary lock-layer prototype.

## Security boundary
This project does not replace, disable, read, or bypass Android's real PIN, password, lock screen, or fingerprint enrollment. It does not copy Android biometric templates. The fingerprint-looking control is an app-owned touch UI.

If Android's own secure lock screen is active, Android remains authoritative.

## Prototype behavior
- App activation is stored locally.
- App passcode fallback: 7924 (prototype only).
- Small fingerprint-style icon fades in after screen-on while active.
- Tapping it opens the app-owned authentication panel.
- Screen-off hides the app layer; screen-on can show it again while the service is alive.
- Reboot explicitly clears the active flag.
- Overlay permission is required.

## Build
Open the project in Android Studio and let Gradle sync. Build with normal Android Gradle tools.

## Limitation
A normal app cannot draw above a secure Android lock screen or bypass Android authentication.
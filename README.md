# Holo Launcher

A home-screen replacement for Android (built for Galaxy Z Fold / Flip, One UI 6+) in a soft holographic,
in-game-HUD style: glass panels over a live, blurred camera feed, a rotating exploded hologram of the phone,
real device vitals, environment sensors, notifications, media controls and app search. The whole UI leans
a few degrees as the phone moves.

## Install (from your phone)

1. Open **Releases → latest** in this repo and tap **HoloLauncher.apk**.
2. Allow your browser to install unknown apps when asked, then install.
3. Open **Holo Launcher** once. The SYSTEM SETUP card walks through:
   - **Home app**: make it your default home screen (you can switch back any time in
     Settings › Apps › Default apps › Home app).
   - **Camera**: for the live see-through backdrop. Android shows its green camera dot while it's on.
   - **Notification link**: notifications, badges and media controls. Because the APK is sideloaded,
     Android may grey this switch out. Fix: Settings › Apps › Holo Launcher › ⋮ › **Allow restricted settings**,
     then try again.
   - **Step counter**: optional, for the steps gauge.

Every push to `main` rebuilds the APK automatically (GitHub Actions) and replaces the `latest` release.
Installing a newer build updates the app in place.

## Layout

- **Narrow screens** (Flip, Fold cover screen): stacked cards, as in the design mockup.
- **Wide screens** (Fold inner screen, ≥ 600 dp): three columns, notifications | hologram | vitals.

Tabs: HOME (dashboard), COMMS (phone/messages/mail/contacts + social apps + their alerts),
MEDIA (now playing + media apps), TOOLS (system panels + utilities), APPS (everything).
Tap the emblem top-left for **Launcher config**.

## Settings worth knowing

- **Low power mode** turns off the camera, tilt and animations.
- **Backdrop blur / colour grade** tune the camera look; **Panel opacity** controls how see-through the glass is.
- **Invert tilt** if the lean feels backwards.

## Limits (stock, unrooted Android)

The status bar, notification shade, recents and Settings stay stock. CPU usage graphs are not available to
apps since Android 8. Everything else on the home screen is real data.

## Tech

Kotlin, Jetpack Compose, CameraX (TextureView preview + RenderEffect blur + AGSL colour-grade shader),
gyroscope leaky-integrator tilt, NotificationListenerService, MediaSessionManager, LauncherApps.
minSdk 33. Fonts: Rajdhani and Titillium Web (SIL Open Font License, see FONTS-OFL-LICENSE.txt).

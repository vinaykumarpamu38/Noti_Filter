# Noti Filter

An Android app that scores incoming notifications by importance using an on-device AI model — and, if you choose to turn it on, gets better over time by retraining itself weekly on your own notification history.

No ads. No analytics or tracking SDKs. Nothing leaves your device unless you explicitly connect Google Drive.

---

## What it does

- **On-device scoring.** Every notification's title, body, sender, source app, and category is fed into a TensorFlow Lite model running entirely on your phone. Nothing about the content needs to leave the device for this core feature to work.
- **Learns your Favorites, not your contacts.** Optional access to Contacts is scoped to display names of people you've starred as Favorites — nothing else (no numbers, no photos, no other contact fields).
- **Optional weekly self-improvement.** Connect your own Google Drive, and once a week the app uploads your local notification history to a folder it creates there, a Cloud Function retrains a fresh model against it, and the result is written back to that same folder. You're shown the new model's accuracy and asked to explicitly accept or reject it before it's ever used for real predictions.
- **Full transparency, always.** An in-app Accuracy History screen shows every training run — date, accuracy, kappa, top-2 accuracy, and whether you accepted or rejected it.
- **Optional low-priority auto-hide.** Once a personalized (non-default) model is active, the app can optionally auto-dismiss notifications it's highly confident are unimportant — gated behind an explicit off-by-default toggle, an allowlist for apps that should never be touched (messaging, payments), and a hard exclusion for ongoing/foreground-service/system-critical notifications. Suppressed notifications are still recorded for future training; suppression only affects what you see, never what the model learns from.

---

## Architecture

```
┌─────────────────────────┐
│   Android app (Java)    │
│                          │
│  NotificationListener    │──── reads incoming notifications
│         │                │
│  Preprocessor (on-device)│──── rebuilds the exact feature vector
│         │                │      the training pipeline produces
│  TFLite inference        │──── scores importance, locally
│         │                │
│  Room DB + local CSV     │──── every notification recorded,
└─────────┬────────────────┘      whether shown or suppressed
          │
          │  (only if Drive is connected)
          ▼
┌─────────────────────────┐
│   Your own Google Drive  │──── app-created folder only
│   (drive.file scope)     │      (drive.file scope - the app can
└─────────┬────────────────┘       never see anything else in your Drive)
          │
          ▼
┌─────────────────────────┐
│  Cloud Function (Python) │──── downloads the CSV, retrains,
│  TF-IDF + TargetEncoder   │      uploads results back to the
│  + TFLite export          │      same Drive folder, then deletes
└─────────┬────────────────┘       its own copy of the data
          │
          ▼
┌─────────────────────────┐
│   Back to the app         │──── staged, never auto-applied -
│   Yes/No decision          │     you review the accuracy first
│   → hot-swapped in live    │
└─────────────────────────┘
```

The on-device model and the training pipeline are numerically verified to agree — the Java feature-vector construction (TF-IDF, target encoding, numeric scaling) was checked against scikit-learn's actual `.transform()` output to within floating-point noise (~1e-17) before shipping.

---

## Tech stack

**Android app**
- Java, Room (local storage), WorkManager (scheduled + manual training triggers)
- TensorFlow Lite (on-device inference)
- Firebase App Check (Play Integrity in release, Debug provider in debug builds) — protects the training backend from being called by anything other than a genuine build of this app
- Google Sign-In + Drive REST API, scoped to `drive.file` only

**Training pipeline**
- Python, pandas, scikit-learn (TF-IDF, cross-fitted `TargetEncoder`, `StandardScaler`)
- TensorFlow/Keras → TFLite conversion
- Deployed as a Google Cloud Function (Python 3.11, gen2)

**Model approach**
- 4-class merged priority scheme (originally 5-class; two near-identical "not important" classes merged after honest evaluation showed the split relied on a feature not available at inference time)
- Target encoding (not one-hot) for `app` / `sender` / `category` / `type` — sender specifically carries real signal (some senders are consistently one label) that one-hot encoding couldn't capture
- ~92-93% held-out accuracy, quadratic-weighted kappa ~0.94, chronological (not random) train/test split to avoid leakage

---

## Privacy model

This is worth stating plainly rather than burying in a policy document:

| Data | Where it lives by default | Where it can optionally go |
|---|---|---|
| Notification content | On-device only | Your own Google Drive, if you connect it — never a third-party server long-term |
| Favorite contacts (names only) | On-device only | Never leaves the device |
| Training data during retraining | — | Briefly processed by the Cloud Function, deleted immediately after — never retained |

A full privacy policy and an in-app disclosure screen (shown before any sensitive permission is requested) are both part of the app.

---

## Project structure

```
noti_filter/                      Android app (Java)
├── app/src/main/java/com/techy/noti_filter/
│   ├── AI_Model/                 On-device Preprocessor + TFLite inference
│   ├── service/                  NotificationListenerService, suppression policy
│   ├── sync/                     Drive sync, Cloud Function client, weekly
│   │                             training Worker, decision handling
│   └── ui/                       Activities/Fragments (Home, Settings,
│                                 Accuracy History, Privacy Disclosure)
│
cloud_function/                   Training backend (Python, deployed to GCP)
├── train_notification_model.py   The actual training pipeline
├── main.py                       Cloud Function entry point
└── drive_helper.py               Drive REST API calls
```

---

## Setup

This project has three parts that need to be configured together — see the setup notes in each directory for specifics:

1. **Firebase project** — App Check (Play Integrity + Debug providers), linked to the same Google Cloud project used below
2. **Cloud Function** — deploy `cloud_function/` to Google Cloud (gen2, Python 3.11); requires `--cpu-boost` to avoid cold-start timeouts on TensorFlow's import
3. **Android app** — needs `google-services.json` from your Firebase project, and an OAuth Android client (with your debug *and* release SHA-1 fingerprints registered) for Drive Sign-In

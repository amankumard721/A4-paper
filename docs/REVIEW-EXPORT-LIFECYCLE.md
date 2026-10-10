# Export, lifecycle and accessibility review — 10 October 2026

## 1. Private PDF retention — implemented

Exports use immutable document UUID + export UUID filenames. At successful startup recovery and before another export, the collector keeps each document's latest PDF, and removes older versions only after a seven-day grace period. Files generated in the current session are protected by a fixed process cutoff. The collector accepts only owned filename patterns in the canonical exports directory; it ignores unknown files and partial exports. Recent shares can still use their original URI during the grace period. Very old shared URIs are not permanent bookmarks: use Save as for a durable external copy. Files saved by the user outside private exports are never cleaned. Latest exports remain even when old; this policy bounds repeated-export history after the grace period, not all storage to a fixed MB limit.

Deleting every older version immediately was rejected because a previous version can be open in a PDF viewer or handed to another app. Tests cover latest-per-document retention, old-version deletion, recent versions, unknown names, partials and absent directories.

## 2. Foreground export — implemented, with limits

ExportWorker awaits setForeground before creating its rendering WebView. The notification has an indeterminate progress indicator, open-app action and Cancel action tied to the worker. Manifest declares FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS and WorkManager's merged dataSync service type. Local file processing/export is a documented dataSync use. Android 13+ notification permission is requested when exporting; denial does not prevent export (the OS may show foreground activity in its task manager instead of the notification drawer). Labels have English and Hindi system-locale resources.

This improves user visibility and background execution priority; it does not guarantee uninterrupted WebView JavaScript, bypass Doze/OEM restrictions, or remove Android 12+ foreground-start restrictions and Android 16 job quotas. A delayed/restarted worker can still fail promotion; the existing failure path reports the error, and the user can retry with the app visible. Five-minute render timeout, cancellation and private original preservation remain in place. Native background behavior is not certified without physical-device tests.

## 3. Editor lifecycle — implemented

The Compose lifecycle observer invokes onPause/onResume on only the visible editor host, including the initial lifecycle state. ON_STOP still snapshots the document. The observer is removed on disposal; AndroidView's existing onRelease destroys its host. The offscreen worker renderer is not attached to the screen observer.

onPause is a best-effort pause for WebView processing and does not stop JavaScript timers. pauseTimers is deliberately not called because it affects all WebViews in the process and could stall export. No measured battery saving is claimed.

## 4. Accessibility — improved, not certified

The editor already had a textbox role, multiline state and a localized accessible name; these were retained. Added explicit keyboard focusability, language and an accessible editing/read-only-preview hint. A4 preview has a localized name. The layout measurement clone remains hidden from accessibility. Existing image alt text and semantic table elements remain available. No role=application or assertive typing announcements were added, avoiding interference with normal screen-reader navigation.

Desktop tests verify the Hindi accessible textbox and attributes. They are not TalkBack, switch-access, keyboard/IME or manual accessibility tests. Real-device TalkBack selection, formatting, tables and image descriptions still need validation; this does not claim native EditText parity or complete accessibility compliance.

## References

- [WorkManager long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Foreground service types: dataSync](https://developer.android.com/develop/background-work/services/fgs/service-types#data-sync)
- [WebView onPause and pauseTimers](https://developer.android.com/reference/android/webkit/WebView)

Build and test status is in TEST-RESULTS.md. Version for this review: 1.1.1 (3).

# Blutrixx Push

A [NativePHP Mobile](https://nativephp.com) plugin for Android push notifications through Firebase Cloud Messaging: the phone's Firebase token, Android's notification permission, the notification itself, and the page a tap opens. It is the same for every app: the app's own API decides who gets what.

Composer package: `blutrixx/nativephp-push`

## Requirements

- PHP ^8.2
- `nativephp/mobile` ^4.0 (from 4.0 the plugin, not the core template, sets up Firebase; this plugin does not work on 3.x)
- A Firebase project with this app's Android package added, and its `google-services.json`
- Android 8 (API 26) or later. There is no iOS code yet.

## Installation

```bash
composer require blutrixx/nativephp-push
```

Enable it in `app/Providers/NativeServiceProvider.php` (only listed plugins are compiled into the build):

```php
public function plugins(): array
{
    return [
        \Blutrixx\Push\PushServiceProvider::class,
    ];
}
```

Put the Firebase file at the **root of the app** (next to `composer.json`) and keep it out of git:

```
google-services.json      # Firebase console → Project settings → Your apps → <this app> → google-services.json
```

The build copies it to `app/google-services.json` and applies the Google Services Gradle plugin (`project_files` / `gradle_plugins` in `nativephp.json`). The build stops if the file is missing, or if it has no entry for the app's package (`NATIVEPHP_APP_ID`): a staging or dev build id needs its own app in the Firebase project.

Several apps can share one Firebase project: add each app's package in the console and give each app its own `google-services.json`.

## Use from the web view

Copy nothing: import the composable through a Vite alias, the same way as `blutrixx/nativephp-device-utils`.

```ts
// vite.config.ts
resolve: { alias: { '@blutrixx/push': path.resolve(__dirname, './vendor/blutrixx/nativephp-push/resources/js') } }
```

```ts
import { usePush } from '@blutrixx/push/usePush'

const push = usePush({
    register: (token) => api.post('/device-token-register', { token, platform: 'android' }),
})

await push.setChannel('Orders and alerts', 'New orders and things waiting for you') // once, optional
await push.enable()          // asks Android's permission if needed, then registers the token
await push.sync()            // after sign-in and when the app comes back: registers a new or changed token
const link = await push.takeLaunchLink()   // '/orders/42' when the app was opened by a notification
await push.disable((token) => api.post('/device-token-remove', { token }))  // before sign-out
```

In a browser (no app) every call answers `null`/`false`/`''` and does nothing.

## Bridge functions

| Function | Answer |
|---|---|
| `Push.Status` | `{ permission: 'granted' \| 'denied' \| 'ask', token }`. `ask` means the person has not been asked yet; `denied` means they refused or turned notifications off in Android's settings. |
| `Push.RequestPermission` | `{ granted, asked }`, at once. The person answers the Android dialog later: read the result with `Push.Status`. |
| `Push.GetToken` | `{ token }`, waiting up to 10 seconds for Firebase to give one. |
| `Push.TakeLaunchLink` | `{ link }` of the notification that was tapped, once. `''` when none. |
| `Push.SetChannel` | Names the channel in Android's notification settings: `{ name, description }`. |

## What the server sends

A **data-only** message, every value a string. The plugin builds the notification itself, so it shows whether the app is open or closed:

```json
{
  "message": {
    "token": "<the phone's token>",
    "data": { "title": "Order approved", "body": "Order 1042 was approved", "action_url": "/orders/1042", "notification_id": "order-1042" },
    "android": { "priority": "high", "ttl": "86400s" }
  }
}
```

- `action_url` is a path inside the app (it must start with `/`, and not `//`). A tap opens the app and the web asks for it with `takeLaunchLink`.
- `notification_id`: a notification with the same id replaces the one already showing.
- A message with no `body` and no `title` shows nothing.

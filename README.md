# wede-sdk-android

Official Android SDK (Kotlin) for the Wede Technology platform.

Wede is an offline-first middleware layer that keeps critical operational workflows running regardless of connectivity. When internet fails, operations continue locally and sync automatically on reconnect.

## Installation

Add to `build.gradle.kts`:

```kotlin
dependencies {
    implementation("pt.wede.sdk:wede-sdk-android:1.2.0")
}
```

Or clone and include as a local module:

```kotlin
include(":wede-sdk-android")
project(":wede-sdk-android").projectDir = File("../wede-sdk-android")
```

## Quick Start

```kotlin
import pt.wede.sdk.core.WedeClient
import pt.wede.sdk.sync.WedeDeviceId
import pt.wede.sdk.storage.SharedPreferencesStorage

val storage = SharedPreferencesStorage(context)
val client = WedeClient(
    apiKey = "wede_live_YOUR_KEY",
    storage = storage
)

// Register device on first launch
val deviceId = WedeDeviceId.getOrCreate(storage)
client.registerDevice(deviceId, "android", "2.0.0")

// Send an event
client.sendEvent(
    type = "EMERGENCY",
    priority = "high",
    vertical = "healthcare",
    idempotencyKey = "evt-001",
    payload = mapOf("condition" to "cardiac_arrest"),
    lat = 38.7169, lng = -9.1395
)

// Score and dispatch teams
val scored = client.scoreTeams(
    lat = 38.7169, lng = -9.1395,
    vertical = "healthcare", priority = "high"
)

client.dispatch(
    eventId = scored.data[0].eventId,
    teamId = scored.data[0].teamId,
    eventLat = 38.7169, eventLng = -9.1395
)
```

## Offline Operation

The SDK operates fully offline using a local score engine identical to the backend. Dispatches are queued with guaranteed delivery.

```kotlin
// Offline dispatch — queued if no connectivity
val result = client.dispatch(
    eventId = "uuid", teamId = "uuid",
    eventLat = 38.7169, eventLng = -9.1395
)
// result.queued == true when offline

// Sync when connectivity restored
client.syncDeviceQueue(deviceId)

// Refresh local team and catalog cache
client.refreshCache()
```

## Method Reference

| Method | Description |
| --- | --- |
| `sendEvent(...)` | Submit an operational event |
| `listEvents()` | List events for the tenant |
| `scoreTeams(...)` | Score available teams by proximity and capability |
| `dispatch(...)` | Dispatch a team to an event |
| `requestBackup(...)` | Request backup for an active mission |
| `listMissions()` | List missions |
| `getMission(id)` | Get a specific mission |
| `updateMissionStatus(id, status)` | Update mission status |
| `updateDispatchSettings(...)` | Configure auto-dispatch settings |
| `registerDevice(deviceId, platform, version)` | Register device for offline sync |
| `syncDeviceQueue(deviceId)` | Sync offline queue with server |
| `refreshCache()` | Refresh local team and catalog cache |
| `getTenantInfo()` | Get tenant configuration |
| `getUsage(from, to)` | Get usage statistics |
| `listZones()` | List operational zones |
| `listParsers()` | List event parsers |

## Requirements

- Android API 21+
- Kotlin 1.9+
- kotlinx-coroutines
- kotlinx-serialization

## Documentation

[docs.wede.pt](https://docs.wede.pt)

## Patent

Wede Technology INPI 120488 (pending) — Claim 5: local score engine and guaranteed offline dispatch queue.

## License

MIT

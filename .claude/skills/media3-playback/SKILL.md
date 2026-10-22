---
name: media3-playback
description: Rules for audio playback in PodOuvir using AndroidX Media3 (ExoPlayer, MediaSessionService, MediaController) — play/pause/stop, speed, seek, position saving, notifications, audio focus. Use for any change touching playback.
---

# Media3 playback

## Components
- `playback/PlaybackService` : `MediaSessionService`. Owns the single `ExoPlayer` and `MediaSession`.
  Declared in the manifest with `foregroundServiceType="mediaPlayback"` and the
  `androidx.media3.session.MediaSessionService` intent filter. Needs `FOREGROUND_SERVICE` +
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and (API 33+) `POST_NOTIFICATIONS` permissions.
- `playback/PlayerController` : app-scoped singleton wrapping `MediaController` (built async from a
  `SessionToken`). Exposes `StateFlow<PlayerUiState>` and commands: `play(episode)`, `pause()`,
  `resume()`, `stop()`, `seekTo(ms)`, `skipBack()`, `skipForward()`, `setSpeed(Float)`.

## Rules
- ExoPlayer built with `setAudioAttributes(AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_SPEECH), handleAudioFocus = true)`
  and `setHandleAudioBecomingNoisy(true)`.
- Speeds: allowed presets `1.0f, 1.25f, 1.5f, 2.0f` via `player.setPlaybackSpeed()`; pitch unchanged.
  Persist the last speed in DataStore and re-apply on new media.
- **Stop** = `pause()` + `seekTo(0)` + `stop()` + clear media items; saves position as 0 and does NOT
  mark the episode played. Notification disappears.
- Position persistence: save on pause, stop, media transition, service `onTaskRemoved`, and every 10s
  while playing. Mark completed when within 30s of the end. Resume with `setMediaItem(item, startPositionMs)`.
- Skip back 15s / forward 30s via `setSeekBackIncrement` / `setSeekForwardIncrement`.
- `MediaItem` carries `mediaId = episodeId` and `MediaMetadata` (title, artist = podcast, artworkUri)
  so the notification and lock screen look right.
- Release the player and session in `onDestroy`. Never hold a `Context`-leaking reference in the controller.

## Testing
- Unit-test `PlayerController` state mapping with a fake `Player` (or `TestExoPlayerBuilder` from `media3-test-utils`).
- Manually verify: screen off, app swiped away, headphones unplugged, incoming call, Bluetooth buttons.

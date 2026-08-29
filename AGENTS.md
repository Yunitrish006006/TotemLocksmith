# TotemLocksmith instructions

## Module-owned Observer UI

- Every new or modified player-facing `Screen`/`Menu` must provide a
  module-owned, read-only semantic Observer mode through TotemCore.
- TotemVanillaTweaks must not copy or redraw the management UI. Observation is
  framebuffer-free and carries only bounded, server-authorised semantic data.
- Suppress every observer mutation/packet path; viewer authority never becomes
  owner or manager authority. Escape only stops observing and private input is
  never relayed.
- Require unit tests, Client GameTest screenshots, dedicated three-JVM E2E and
  Production Runtime validation for UI changes.
- Provider capture/create and handle methods are client-thread-only; GameTests
  must use their client-thread context helpers.

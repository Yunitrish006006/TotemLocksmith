# Totem Locksmith

Totem Locksmith is the server-authoritative fixed-container protection module for the Totem platform.

## Features

- One physical Padlock protects a Chest, Trapped Chest, Barrel, double chest, or an entire fixed-Hopper-connected storage network.
- The first container you lock is the network root. If a middle Hopper is broken, only the component containing that root remains locked.
- Other players may still break protected containers under normal Minecraft rules; every successful non-owner break publishes one audit event for optional DiscordBridge delivery.
- Owner, Manager, User, Blocked, physical keys, four access modes, and three automation modes are evaluated only by the server.
- Persistent records contain ownership and topology, never container contents.

## Quick Start

1. Craft a Padlock.
2. Use it on a Chest, Trapped Chest, or Barrel. Connected fixed Hoppers and supported containers share the same lock.
3. Hold a Book or the Totem manual and use it on a Chest to record the Locksmith chapter.
4. Sneak-use a locked root container with an empty hand to inspect its current policy; use `/locksmith` to manage it.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API
- Totem Core 0.6.0 exactly
- Java 25+

## Performance

- There is no global per-tick container scan.
- Lock UUID, position, and owner-count lookups use derived constant-time indexes.
- Topology is recomputed only for bounded lock, placement, break, and relevant transfer operations; unloaded chunks are never force-loaded.
- The unit suite includes a 10,000-record create and lookup baseline with a five-second CI ceiling.

The canonical implementation specification lives at
`openspec/changes/add-totem-locksmith-chest-locking/` in this repository.

# Test Matrix

Status: planned

Required cases are release blockers. Control cases prove that Locksmith does not break
ordinary unlocked containers or installations where the module is absent.

## Unit

| ID | Case | Expected |
| --- | --- | --- |
| U01 | Owner access across every operation | Allowed |
| U02 | Blocked with valid key in PUBLIC | Denied |
| U03 | Manager in PRIVATE | Content/config per role; BREAK disposition is NON_OWNER_ALERT |
| U04 | User in PRIVATE then ALLOWLIST | Denied, then allowed without deleting ACL |
| U05 | Friend in FRIENDS and Nexus absent | Denied; no exception |
| U06 | Public actor in PUBLIC | Content allowed; configure denied; break is NON_OWNER_ALERT |
| U07 | Anonymous automation in DENY/TRUSTED/ALL | Denied/denied/allowed |
| U08 | Authorized identified automation in TRUSTED | Allowed for requested content operation |
| U09 | Wrong-lock Key UUID | Denied |
| U10 | Revoked Key UUID | Denied |
| U11 | Stale key epoch | Denied |
| U12 | Rotate keys | Epoch increments; registry clears |
| U13 | Member/key/lock limits | Boundary accepted; one over rejected without mutation |
| U14 | Label sanitation | Control characters removed; 32-code-point limit |
| U15 | LockRecord codec round trip | Every authoritative field preserved |
| U16 | Unknown newer dataVersion | Refused without writing empty data |
| U17 | Duplicate indexed position | Both records diagnosed CONFLICT |
| U18 | Invalid topology | REPAIR_REQUIRED; no guessed partner |
| U19 | Position-index rebuild | Same O(1) mapping after reload |
| U20 | Config reload failure | Last valid immutable snapshot retained |
| U21 | Confirmation token expiry/reuse | Expired and second use denied |
| U22 | Stale revision mutation | No partial update; latest safe snapshot returned |
| U23 | Record/index/attachment consistency | Every mismatch receives deterministic diagnostic |
| U24 | Owner versus non-Owner BREAK | OWNER_BREAK versus NON_OWNER_ALERT |
| U25 | Break event invariant | lockRemoved iff remainingLockedContainers is zero; counts non-negative |

## Gameplay

| ID | Case | Expected |
| --- | --- | --- |
| G01 | Padlock an empty single Chest | One record, one index, one item consumed |
| G02 | Padlock a filled Chest | Contents unchanged |
| G03 | Padlock a Trapped Chest | Lock created; no redstone until authorized open |
| G04 | Padlock a horizontal Barrel | Lock created with correct facing |
| G05 | Padlock vertical Barrels | Both up/down facings work |
| G06 | Apply to unsupported block | Rejected; Padlock unchanged |
| G07 | Apply to Ender Chest/Shulker | Rejected; no record |
| G08 | Apply to unresolved loot Chest | Rejected by default |
| G09 | Apply to active vanilla LockCode | Rejected; vanilla data unchanged |
| G10 | Add vanilla LockCode after Locksmith | Both policies required |
| G11 | Two players apply in same tick | One winner; loser item unchanged |
| G12 | Unauthorized normal use | No Menu/content snapshot |
| G13 | Owner normal use | Vanilla Menu opens |
| G14 | Manager/User mode matrix | Matches policy table |
| G15 | Valid held key | Menu opens |
| G16 | Key moved out of both hands | Menu closes before next mutation |
| G17 | Access revoked while Menu open | Menu closes; next click has no effect |
| G18 | Player walks out of range | Menu closes |
| G19 | Player changes Dimension | Menu closes; no remote mutation |
| G20 | Click-mode matrix | Normal, quick move, drag, number, double-click obey policy |
| G21 | Remove lock with inventory space | Record/index removed; one Padlock returned |
| G22 | Remove lock with full inventory | One Padlock safely dropped |
| G23 | Duplicate remove confirmation | No second Padlock |
| G24 | Owner direct break single | Vanilla break; record removed; at most one Padlock; no security event |
| G25 | Non-Owner break in Survival | Vanilla break succeeds; one permanent Discord event |
| G26 | Non-Owner break in Creative | Instant break succeeds; one permanent Discord event |
| G27 | Spectator inspect without permission | No contents |
| G28 | Explosion protection enabled | Locked container/member Hopper and contents survive |
| G29 | Explosion protection disabled | Vanilla destruction; record finalized once |
| G30 | Piston movement attempt | Locked container/member Hopper remains unmoved |
| G31 | Comparator read | Vanilla signal preserved |
| G32 | Authorized Trapped Chest open | Vanilla redstone/open count preserved |
| G33 | External replacement | Record becomes ORPHANED; replacement not owned |
| G34 | Protection hook cancels lock | No item or record change |
| G35 | Protection hook cancels break | Block/record unchanged; no success event |
| G36 | Attachment without record | Fail closed; ORPHANED |
| G37 | Record without attachment | Fail closed; repair can restore marker |
| G38 | Copied attachment at a new position | Does not copy ownership; CONFLICT/ORPHANED |
| G39 | Discord subscriber throws after break | Break/topology/Padlock remain committed |

## Double chest logical node

除 D16 外，本節使用未連接其他 Hopper 網路的 standalone 雙箱，避免把雙箱
的兩個 BlockPos 與使用者所稱的 Hopper「相連容器」混為一談。

| ID | Case | Expected |
| --- | --- | --- |
| D01 | Padlock left half | One Padlock consumed; one record indexes both halves |
| D02 | Padlock right half | One Padlock consumed; same canonical result as left |
| D03 | Non-Owner places merging half | Placement canceled and stack restored |
| D04 | Owner expands locked single | Same Lock UUID; no second Padlock |
| D05 | Merge two different locks | Rejected |
| D06 | Any player breaks one half | Remaining half keeps same lock/ACL/keys/epoch |
| D07 | Any player breaks last half | Only now record removed; Padlock at most once |
| D08 | Remove lock through GUI | Both halves unlock; one Padlock |
| D09 | Partner half unloaded | Topology mutation rejected |
| D10 | Double chest across chunk boundary reload | Both indexes and one lock restored |
| D11 | Concurrent merge and open | One serialized topology; stale Menu closes |
| D12 | Concurrent break and key use | No access after final destruction; no duplication |
| D13 | Non-Owner breaks first half | One event with remainingLockedContainers = 1; detached = 0; no Padlock |
| D14 | Non-Owner breaks last half | One event with remainingLockedContainers = 0; one final Padlock at most |
| D15 | Owner breaks either half | Correct topology; no security event |
| D16 | Double chest in Hopper graph | One logical container node with two indexed BlockPos |

## Hopper network

| ID | Case | Expected |
| --- | --- | --- |
| H01 | Padlock one box in Chest-Hopper-Chest chain | One Padlock, one Lock UUID, clicked box is root |
| H02 | Reverse-facing route in same chain | Edge direction retained; weak component still shares one lock |
| H03 | Hopper merely adjacent without pull/push edge | Not a member; neighboring box remains unlocked |
| H04 | Hopper Minecart passes below | Automation actor only; does not join topology |
| H05 | Owner adds box/Hopper to locked network | New component members join same lock; no second Padlock |
| H06 | Non-Owner tries to add connecting Hopper | Placement canceled; stack safely restored; topology unchanged |
| H07 | Placement would connect two Lock UUIDs | Rejected; neither record/ACL/Padlock changes |
| H08 | Apply/expand reaches unloaded chunk | Entire mutation rejected; no partial lock or force-load |
| H09 | Apply/expand exceeds 128 positions | Entire mutation rejected at configured bound |
| H10 | Hopper graph contains a cycle | Visited once per position; one record; traversal terminates |
| H11 | Break middle Hopper, root survives | Only root component keeps lock; detached component unlocks; no Padlock |
| H12 | One break creates multiple detached branches | All non-root branches unlock; exactly one lock record remains |
| H13 | Break root with one surviving component | Nearest surviving logical container becomes successor root |
| H14 | Root successor distance tie | Dimension + BlockPos ordering selects exactly one successor component |
| H15 | Break final logical container with Hoppers remaining | Record/connectors removed; at most one Padlock drops |
| H16 | Split detached branch | No duplicate Padlock, Lock UUID, ACL or keys on detached side |
| H17 | Open detached branch after split | Uses ordinary unlocked-container behavior immediately after commit |
| H18 | Open root branch after split | Original Owner/ACL/keys/modes remain effective |
| H19 | Internal same-lock Hopper transfer in DENY | Vanilla transfer succeeds; item remains in protection boundary |
| H20 | Transfer crosses lock boundary in DENY | Source/destination remain unchanged |
| H21 | Owner breaks middle connector | Same root-side split; no Discord security event |
| H22 | Non-Owner breaks middle connector | One event with remaining locked and detached unlocked counts |
| H23 | Root is a double chest | Root is one logical node; visual uses one seam anchor |
| H24 | Member chunk unload/reload | Unload is not a split; same root and members restore |
| H25 | Explosion targets member Hopper with protection on | Connector survives; no silent branch unlock |
| H26 | Concurrent split and remote branch open | Serialized revision; detached side is wholly locked-before or unlocked-after |
| H27 | Break a container in detached branch after split | It is already unlocked; no stale locked-network event |
| H28 | Owner reconnects a detached branch | Whole loaded branch rejoins root lock without consuming Padlock |

## Automation

| ID | Case | Expected |
| --- | --- | --- |
| A01 | Member Hopper transfers inside same lock in DENY | Vanilla transfer succeeds once |
| A02 | Member Hopper crosses into unsupported/unlocked boundary in DENY | No source or destination change |
| A03 | Boundary transfer in ALL | Vanilla transfer succeeds once |
| A04 | Hopper Minecart pull in DENY | No change |
| A05 | Hopper Minecart pull in ALL | Vanilla transfer succeeds once |
| A06 | Dropper insertion in DENY | Source remains; no fake success |
| A07 | Dropper insertion in ALL | One vanilla transfer |
| A08 | Dispenser target route in DENY | No change |
| A09 | Fabric Transfer API lookup in DENY | Storage rejects mutation |
| A10 | Identified Automata operator in TRUSTED | Authorized operation succeeds |
| A11 | Unauthorized Automata operator in TRUSTED | No pickup/deposit/fuel loss |
| A12 | Automata with no actor in TRUSTED | Treated anonymous and denied |
| A13 | Unlocked-container controls | All vanilla routes unchanged |
| A14 | Denied hopper for 1,000 ticks | No log/Payload flood or item drift |
| A15 | Optional adapter throws | Operation denied; Server continues |

## Security

| ID | Case | Expected |
| --- | --- | --- |
| S01 | Forged Lock UUID | Target resolved from Server session; denied |
| S02 | Forged Owner/role field | Ignored; denied |
| S03 | Forged remote BlockPos | Ignored or distance-denied |
| S04 | Cross-Dimension session replay | Denied |
| S05 | Expired session UUID | Denied |
| S06 | Another player reuses session UUID | Denied |
| S07 | Duplicate bind payload | One blank consumed; one key issued |
| S08 | Out-of-order revision payloads | Only current revision may mutate |
| S09 | Guessed Key UUID without Server ItemStack | Denied |
| S10 | Valid component on non-Key item | Denied |
| S11 | Unauthorized snapshot request | No ACL, keys, Owner UUID or contents returned |
| S12 | Admin inspect without contents permission | Diagnostics only |
| S13 | Admin bypass | Allowed and audited |
| S14 | Creative player without bypass | Same OPEN/config policy; non-Owner break alerts |
| S15 | Rate-limit memory bound | Old denial entries evicted |
| S16 | Client forges break notification | Ignored; only committed Server break publishes |
| S17 | Duplicate break callback/finalize | One event UUID and one local dispatch |
| S18 | Client claims detached/root counts | Ignored; counts computed from committed Server graph |
| S19 | Forged Hopper membership marker | Record/index/edge mismatch fails closed |

## Persistence

| ID | Case | Expected |
| --- | --- | --- |
| P01 | Seed/restart/verify single Chest | Owner, mode, contents and index retained |
| P02 | Seed/restart/verify double Chest | One record and two indexes retained |
| P03 | Revoke key then restart | Key remains invalid |
| P04 | Rotate epoch then restart | All old keys remain invalid |
| P05 | Split double then restart | One remaining position with same Lock UUID |
| P06 | Member and Blocked entries restart | UUID roles retained; names non-authoritative |
| P07 | Cross-Dimension records restart | Correct registry keys and positions |
| P08 | Chunk unload/reload | No visual or policy drift |
| P09 | Isolated malformed record | Diagnosed; valid records retained |
| P10 | Unknown future schema | Service stops safely; original data not overwritten |
| P11 | Uninstall module and load world | Vanilla containers and contents load |
| P12 | Reinstall after uninstall | Matching records restore; mismatches need repair |
| P13 | Hopper root-side split then restart | Root side remains locked; detached branch remains unlocked |
| P14 | Root successor then restart | Same successor/root, Lock UUID and ACL restore |

## Administration

| ID | Case | Expected |
| --- | --- | --- |
| M01 | Inspect active lock | Non-content diagnostics returned |
| M02 | Repair derived index | Index rebuilt without record/content rewrite |
| M03 | Repair valid remaining half | DOUBLE becomes SINGLE |
| M04 | Attempt merge different Lock UUIDs | Repair refuses |
| M05 | Orphan scan | Bounded progress; no chunk force-load |
| M06 | Purge without dry-run token | Denied |
| M07 | Purge with changed summary | Denied |
| M08 | Confirmed orphan purge | Only summary-matched records removed |
| M09 | Admin transfer | ACL/keys clear, epoch increments, PRIVATE/DENY |
| M10 | Audit subscriber throws | Mutation remains committed; failure logged |
| M11 | Repair stale detached Hopper branch with valid root | Removes only detached markers; creates no lock/Padlock |
| M12 | Repair record whose root vanished without journal | REPAIR_REQUIRED; does not guess successor |

## Discord

| ID | Case | Expected |
| --- | --- | --- |
| C01 | Owner successfully breaks member | No locked_container_network_broken event |
| C02 | Manager/User/Key/Friend/Public/Blocked/non-Owner OP breaks member | One event for every successful non-Owner break |
| C03 | Stranger breaks middle Hopper | Root side locked count and detached unlocked count are correct |
| C04 | Stranger breaks final root-side container | remainingLockedContainers = 0, lockRemoved = true |
| C05 | Break denied by protection | No event |
| C06 | Same event ID delivered twice to subscriber | One Discord dispatch |
| C07 | No configured Discord channel | Local audit only; no unknown fallback destination |
| C08 | Formatter or transport throws | Gameplay remains committed |
| C09 | Payload privacy | No contents, ACL, Key UUID, full Lock UUID/member list or secret |
| C10 | Transient policy | Event remains permanent and is not auto-deleted |
| C11 | Traditional Chinese format | Actor, Owner, kind, location, locked and detached states fit |
| C12 | Two separately broken members | Two distinct event IDs and committed counts for each revision |
| C13 | One connector break detaches three branches | One event ID; detached count is aggregate, not three messages |
| C14 | Root break promotes successor | rootMoved = true and remaining locked count matches successor side |
| C15 | Detached branch is broken later | No stale second locked-network event |

## Installation

| ID | Installation | Expected |
| --- | --- | --- |
| I01 | TotemCore only | Starts; no Locksmith registration |
| I02 | Core + Locksmith | Full base feature starts |
| I03 | Locksmith without Nexus | FRIENDS fails closed; base feature works |
| I04 | Locksmith + Nexus | Mutual friend matrix works |
| I05 | Locksmith without Automata | No reflective/class-loading failure |
| I06 | Locksmith + Automata | Source/destination bridge works |
| I07 | Automata without Locksmith | Existing automation behavior remains |
| I08 | Locksmith + Villagers | Owner-aware policy or explicit Work Chest rejection |
| I09 | Dedicated Server | No client class load or Mixin failure |
| I10 | DeadRecall candidate bundle | One authority and one registration surface |
| I11 | Previous immutable bundle pin | Rollback starts with unchanged world |
| I12 | Addon test adapter | Supported fixed container obeys every operation |
| I13 | Locksmith without DiscordBridge | Break alerts no-op externally; gameplay works |
| I14 | Locksmith + DiscordBridge | Non-Owner member break becomes locked_container_network_broken |
| I15 | Discord transport failure | Server and completed break remain healthy |

## Visual

| ID | Case | Evidence |
| --- | --- | --- |
| V01 | Module icon 16x16/64x64 | Validator output |
| V02 | Padlock item 16x16 | Validator + native-scale screenshot |
| V03 | Key Blank and Bound Key 16x16 | Validator + inventory screenshot |
| V04 | Single Chest four facings | In-game screenshots |
| V05 | Double Chest all LEFT/RIGHT facings | One seam lock, screenshots |
| V06 | Trapped Chest | Model and authorized redstone state |
| V07 | Barrel six facings | In-game screenshots |
| V08 | Chest opening/closing | Before/open/after screenshots or Client GameTest |
| V09 | Management empty/populated/error | Deterministic screenshots |
| V10 | GUI scales | No clipping at supported scales |
| V11 | English and Traditional Chinese | No text collision |
| V12 | Keyboard/focus/Narration | Client test plus manual narration checklist |
| V13 | Invalid/revoked key tooltip | Inventory screenshot |
| V14 | Light/dark world backgrounds | Lock silhouette remains readable |
| V15 | Double-to-single break | One lock moves from seam to remaining-container anchor |
| V16 | Hopper network root | Only root box renders a lock; member boxes/Hoppers do not |
| V17 | Middle-Hopper split | Root-side visual remains; detached side clears in one revision |
| V18 | Root successor | Old anchor disappears as one successor anchor appears; never two locks |

## Multiplayer

| ID | Case | Expected |
| --- | --- | --- |
| R01 | Owner gives physical key to second player | Second player opens only while holding it |
| R02 | Owner revokes while second player Menu open | Menu closes before next mutation |
| R03 | Owner blocks key holder | Access immediately denied |
| R04 | Owner and User move stacks same tick | Vanilla serialization; no duplication |
| R05 | Owner transfers to second player | Old Owner loses access under safe reset |
| R06 | Player disconnects with key in management slot | Item returns or safely drops exactly once |
| R07 | Two players open Trapped Chest | Original open count/redstone semantics |
| R08 | Two clients observe double-chest model | Same anchor/revision, no duplicate render |
| R09 | Second player breaks middle Hopper | Root side stays locked; detached side unlocks; one Discord alert |
| R10 | Second player breaks final root-side box | Lock ends and drops once; second Discord alert |
| R11 | Owner and stranger split/open same tick | Serialized topology; no half-committed ACL or duplicate alert |

## Performance

| ID | Case | Expected |
| --- | --- | --- |
| F01 | 10,000 record encode/decode | Within recorded Java 25 CI baseline |
| F02 | 100,000 indexed lookups | No linear record scan |
| F03 | 1,000 denied hopper ticks | Bounded allocations/log state |
| F04 | 64-record-per-tick orphan scan | Tick budget respected and cancel works |
| F05 | Chunk tracking visual batch | No per-lock per-tick broadcast |
| F06 | 128-position Hopper graph traversal | Bounded visited set; no recursive overflow or force-load |
| F07 | 1,000-tick stable Hopper graph | No per-tick topology BFS or temporary membership allocation flood |

## Evidence

- JUnit XML and Fabric GameTest reports for headless cases.
- Three-JVM marker files and logs for persistence.
- Screenshots under
  `test-artifacts/screenshots/add-totem-locksmith-chest-locking/<test-id>-<stage>.png`.
- Build command, Java version, commit SHA and artifact SHA-512 in the verification report.
- GitHub Actions URL/status recorded before release readiness is marked complete.
- Manual cases record Minecraft version, GUI scale, language and participating player count.

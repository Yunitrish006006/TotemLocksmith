# Tasks: TotemLocksmith

所有未完成項目預設為 release blocker。只有標記為「人工」的項目可在無
顯示環境時保持未驗證；它們仍不得在正式發布前跳過。

## 0. 規格

- [x] 0.1 確認 TotemLocksmith 是獨立 bounded context，不把玩法放入 Core。
- [x] 0.2 定義第一版容器範圍、非目標、角色、模式、鑰匙與自動化政策。
- [x] 0.3 定義 Server authority、SavedData、Payload、修復與安全邊界。
- [x] 0.4 定義 Hopper 相連容器共用一鎖、root-side split、非 Owner 容器／
  connector 破壞警報、爆炸、原版 LockCode 與可選模組行為。
- [x] 0.5 定義 GUI、視覺、本地化、accessibility 及測試證據。
- [x] 0.6 建立可追蹤的實作階段與驗收矩陣。
- [ ] 0.7 Owner 審閱並接受第一版玩法預設。

### Gate

- [x] Proposal、spec、design、tasks 與 test matrix 無互相矛盾的規則。
- [x] 所有會影響資料格式或玩家資產的 Open Question 已關閉。

## 1. Repository

- [ ] 1.1 建立 `TotemLocksmith` repository 與預設保護分支。
- [ ] 1.2 Scaffold Fabric module，對齊 Minecraft 26.2、Java 25、Loader、
  Fabric API 及精確 TotemCore 版本。
- [ ] 1.3 設定 mod ID `totem-locksmith`、package
  `dev.totem.locksmith`、artifact `totem-locksmith`。
- [ ] 1.4 建立 main、client、test、gametest source sets，避免 Dedicated
  Server 載入 Client class。
- [ ] 1.5 建立 reproducible JAR、license、README、changelog 與 publishing
  metadata。
- [ ] 1.6 建立 GitHub Actions：Java 25 build、JUnit、GameTest、Dedicated
  Server、restart probe、resource validation。
- [ ] 1.7 匯入已驗證的 TotemLocksmith 16x16 module icon 及精確 4x derivative。
- [ ] 1.8 將本 change 的規格複製到新 repo，並指定單一 canonical source；
  不維護兩份可分歧的規格。
- [ ] 1.9 在系統 repository map 將 Locksmith 標記為 planned standalone
  owner，但不加入 active bundle manifest。

### Gate

- [ ] Core + empty Locksmith 能 build、啟動、正常停止。
- [ ] Dedicated Server JAR 不含或不載入 client implementation。
- [ ] Module icon 通過 strict 16x16 validator。

## 2. Domain

- [ ] 2.1 實作 LockId、LockLocation、LogicalContainerNode、HopperConnector、
  ContainerKind、AccessMode、AutomationMode、MemberRole、LockState。
- [ ] 2.2 實作含 rootContainer、containers、connectors、topologySchema 的
  immutable LockRecord，以及 MemberEntry、KeyGrant 與 revision。
- [ ] 2.3 實作 v1 Codec、集合上限、字串清理及 invalid-record diagnostics。
- [ ] 2.4 實作 LocksmithSavedData 與載入時 position-index rebuild。
- [ ] 2.4a 註冊 `totem:locksmith/lock_id` BlockEntity persistent attachment，
  只保存 Lock UUID marker。
- [ ] 2.5 偵測 duplicate position、duplicate Lock UUID、root 非 member、
  非法 Hopper topology、超限及 unknown newer dataVersion。
- [ ] 2.6 建立 LockDataMigrator dispatcher 與 v1 round-trip fixture。
- [ ] 2.7 實作 immutable Server config snapshot、reload 及最後有效值 fallback。
- [ ] 2.8 實作 Owner lock count、member count、active key count 與
  maxNetworkNodesPerLock 上限。
- [ ] 2.9 實作 O(1) lookup 與 0／1,000／10,000 records baseline。
- [ ] 2.10 實作無全域 tick scan 的 chunk-local validation。

### Gate

- [ ] Domain JUnit 全通過。
- [ ] 壞記錄不會讓整份 SavedData 被空資料覆寫。
- [ ] 10,000 records 測試符合記錄的 CI memory/time baseline。

## 3. Policy

- [ ] 3.1 實作 AccessOperation 與 AccessActor。
- [ ] 3.2 實作 admin、Owner、Blocked、Manager、key、mode grant 的固定優先序。
- [ ] 3.3 實作 PRIVATE、ALLOWLIST、FRIENDS、PUBLIC。
- [ ] 3.4 實作 DENY、TRUSTED、ALL automation。
- [ ] 3.5 分離 OPEN、INSERT、EXTRACT、BREAK、CONFIGURE；BREAK 回傳
  OWNER_BREAK／NON_OWNER_ALERT disposition，不以 ownership 硬性拒絕。
- [ ] 3.6 實作 Creative、Spectator、Adventure 與 permission fallback。
- [ ] 3.7 將 public API 限制為 immutable decision，不暴露 mutable record。
- [ ] 3.8 對 adapter 例外與未知 actor 採 fail closed。
- [ ] 3.9 實作 bounded denial rate limiter。

### Gate

- [ ] 角色 × 模式 × operation 的完整 table-driven tests 通過。
- [ ] Blocked 覆蓋 key/friend/public，但不覆蓋 Owner/admin。
- [ ] Client UUID、座標或角色欄位不能改變權威判定。

## 4. Items

- [ ] 4.1 註冊 Padlock、Key Blank、Bound Key。
- [ ] 4.2 註冊 `totem:locksmith/key_binding` Data Component codec 與 stream
  codec。
- [ ] 4.3 建立 Padlock 與 Key Blank data-driven recipes。
- [ ] 4.4 實作 Bound Key 單獨 crafting 回收 Key Blank。
- [ ] 4.5 實作 key validity、revoked、wrong-lock、stale-epoch tooltip。
- [ ] 4.6 實作物品英文與繁中名稱、tooltip 及 creative inventory placement。
- [ ] 4.7 確認 Bound Key max stack 1，bind 操作從 blank stack 只消耗一把。
- [ ] 4.8 測試 custom name、Components、drop/pickup 與 restart 保存。

### Gate

- [ ] Key Component round-trip 與封包同步通過。
- [ ] 無任何 recipe 或重送路徑可複製 active Key UUID。

## 5. Locking

- [ ] 5.1 建立 Chest、Trapped Chest、Barrel logical-container adapters 與
  fixed Hopper connector resolver。
- [ ] 5.2 實作 Padlock use 的距離、物品、permission、loot table、LockCode、
  完整載入、bounded Hopper traversal、上限與 topology 驗證。
- [ ] 5.3 原子建立 root、LockRecord／index 並在成功後只消耗一個 Padlock。
- [ ] 5.3a 同一 transaction 寫入每個容器／Hopper BlockEntity attachment；
  rollback 清除所有半成品 marker。
- [ ] 5.4 在 open 高階入口與 `canOpen`／Menu authority 建立雙層驗證。
- [ ] 5.5 未授權時不建立 Menu、不傳內容／ACL，播放本地化 feedback。
- [ ] 5.6 原版 LockCode 存在時拒絕上鎖；後加雙重鎖時採 AND。
- [ ] 5.7 實作 Owner/Manager 空手 Sneak + Use 管理入口。
- [ ] 5.8 實作 Menu 存續期間距離、Dimension、revision 與授權重驗證。
- [ ] 5.9 覆蓋 click、shift-click、drag、number-key、double-click、
  pickup-all。
- [ ] 5.10 實作權限中途撤銷或 key 離手時關閉 Menu。

### Gate

- [ ] 單箱、陷阱箱、木桶的上鎖、授權、拒絕與內容 mutation GameTest 通過。
- [ ] 同 tick 套鎖只有一位成功且物品 exactly once。
- [ ] 未授權 Client 沒有取得內容或管理 snapshot。

## 6. Management

- [ ] 6.1 建立 session UUID、expiry、scope 與 expected revision。
- [ ] 6.2 實作管理 snapshot 的最小欄位與角色裁切。
- [ ] 6.3 實作成員搜尋、UUID/profile-cache 解析、新增、移除、角色更新。
- [ ] 6.4 實作 Manager 權限限制及 Owner-only manager 管理。
- [ ] 6.5 實作 Server-backed key slot、bind、label、revoke。
- [ ] 6.6 實作 Rotate Keys 與所有開啟 session 失效。
- [ ] 6.7 實作 mode 與 automation mode 更新。
- [ ] 6.8 實作 transfer 的 ACL 清空、key rotation、PRIVATE、DENY 安全重設。
- [ ] 6.9 實作 Remove Lock、背包滿時安全掉落及 idempotent confirmation。
- [ ] 6.10 實作 stale revision 回最新 snapshot、不套用部分 mutation。
- [ ] 6.11 關閉／斷線／死亡時安全歸還 key slot 物品並清 session。

### Gate

- [ ] 所有 mutation 對 forged session、remote、cross-dimension、stale revision
  及 duplicate payload tests 通過。
- [ ] Key Blank、Bound Key、Padlock 在任何失敗／關閉路徑 exactly once。

## 7. Topology

- [ ] 7.1 實作 single/double logical-container resolver 與 stable position
  sorting；雙箱在 graph 中只算一個 container node。
- [ ] 7.2 實作固定 Hopper pull source／facing destination edges，以 weak
  connectivity 計算 bounded component，忽略單純相鄰、Minecart 與 item。
- [ ] 7.3 對任一容器套鎖時原子保存 root、全部 container nodes、connectors
  與 attachments，只消耗一個 Padlock。
- [ ] 7.4 阻止非 Owner placement 擴充已鎖 component；安全取消並返還 stack。
- [ ] 7.5 Owner 加入新箱、雙箱半或 Hopper 時沿用 Lock UUID，不消耗第二把
  Padlock；未載入 chunk 或超限時整筆拒絕。
- [ ] 7.6 阻止兩個不同 Lock UUID 透過箱合併、Hopper placement 或 state
  update 形成同一 component。
- [ ] 7.7 任一 member 成功移除後重算 components；只有包含 root 的一側
  保留 ACL／keys／epoch，其餘分支原子解除鎖定且不掉 Padlock。
- [ ] 7.8 root 被移除時依 pre-break graph distance、Dimension、BlockPos
  選唯一 successor；只保留 successor component，Hopper 不得成為 root。
- [ ] 7.9 根側最後一個 logical container 被移除時才刪除 record／connector
  markers，Padlock 最多掉落一次。
- [ ] 7.10 Topology revision 改變時同步 index／attachments／visual 並關閉
  所有舊 Menu。
- [ ] 7.11 保護雙箱半、Hopper edge、chunk boundary、cycle、外部 state change
  及 replacement race。
- [ ] 7.12 測試從中間分裂只保留最初套鎖箱所在側，分離側立即可視為未鎖。

### Gate

- [ ] 不存在 half-locked usable double chest。
- [ ] 每個 Hopper component 最多一個 Lock UUID；split 後只有 root／successor
  component 保留鎖。
- [ ] 所有 merge/split/break race 無 item/Padlock/record/ACL duplication。

## 8. Automation

- [ ] 8.1 在 Hopper push/pull 的來源 remove 前評估 EXTRACT/INSERT。
- [ ] 8.2 同一 Lock UUID 兩 endpoint 的內部 transfer 即使 DENY 仍允許；
  未鎖、不同鎖或 unsupported endpoint 依邊界政策分別驗證。
- [ ] 8.2a 覆蓋六面 Hopper、Hopper chain 及 ordinary unlocked control。
- [ ] 8.3 覆蓋 Hopper Minecart。
- [ ] 8.4 覆蓋 Dropper 與 Dispenser。
- [ ] 8.5 註冊 Fabric Transfer API storage wrapper/lookup。
- [ ] 8.6 確保拒絕不改來源、目的地、ItemStack 或成功 cooldown。
- [ ] 8.7 保留 Comparator 原版訊號。
- [ ] 8.8 建立 TotemAutomata optional source/destination bridge。
- [ ] 8.9 驗證 IDENTIFIED_AUTOMATION 與 ANONYMOUS_AUTOMATION。
- [ ] 8.10 壓力測試 hopper denial 不產生 log、Payload 或 allocation flood。

### Gate

- [ ] DENY、TRUSTED、ALL 的 route matrix 全通過。
- [ ] DENY 不阻塞同鎖網路內部 transfer，且不允許物品跨出保護邊界。
- [ ] Automata 缺席、Locksmith 缺席與兩者同時安裝皆能啟動。
- [ ] 已知直接 Container 路徑全部在 mutation 前查詢 policy。

## 9. Break alerts

- [ ] 9.1 Locksmith 不因 ownership 取消玩家 BREAK；仍尊重原版 GameMode、
  spawn protection、Fabric 及領地 adapter。
- [ ] 9.2 只在上鎖容器或 member Hopper 實際移除與 root-component topology
  commit 後判斷 Owner／非 Owner。
- [ ] 9.3 實作 explosion protection on/off。
- [ ] 9.4 拒絕 piston 或第三方 BlockEntity movement。
- [ ] 9.5 保留 trapped-chest redstone、Comparator、waterlogging 與一般更新。
- [ ] 9.6 外部 setblock/WorldEdit replacement 後標記 ORPHANED/CONFLICT。
- [ ] 9.6a 對 attachment 缺失、孤兒、複製及 Lock UUID mismatch fail closed。
- [ ] 9.7 實作 `LockedContainerNetworkBrokenEvent` 的 commit-after-success、
  remaining locked、detached unlocked、root moved、event UUID 與重入去重。
- [ ] 9.8 Owner break 不警報；所有非 Owner 角色成功破壞容器或 member Hopper
  各警報一次。
- [ ] 9.9 protection 取消、未移除、無法歸因的環境破壞不送玩家成功警報。
- [ ] 9.10 實作 chunk-local diagnostics，不自動刪除內容或 record。
- [ ] 9.11 測試 middle-Hopper split、container split、root successor、final
  break、explosion、Wither 及 replacement。

### Gate

- [ ] 玩家依原版規則可拆箱／Hopper；非 Owner 成功時 topology 與警報各
  commit 一次，不能靠拆 connector 無警報解鎖分支。
- [ ] 爆炸防護關閉的合法 environment destruction 只 finalize 一次。

## 10. Administration

- [ ] 10.1 註冊 inspect、inspect_contents、bypass、mutate、purge permissions。
- [ ] 10.2 實作玩家 raycast commands。
- [ ] 10.3 實作 admin inspect/list/bypass-open/transfer/remove/repair。
- [ ] 10.4 實作 bounded orphan scan、cancel、progress 與 dry-run。
- [ ] 10.5 實作 summary-bound short-lived purge confirmation。
- [ ] 10.6 Repair 僅允許 index rebuild、合法 single-half、明確 root-side stale
  member detach 與有可信 journal 的 successor rebind／remove；不得猜 root。
- [ ] 10.7 實作管理 audit 與 Core `AdminAuditEvent`。
- [ ] 10.7a 非 Owner 玩家破壞使用獨立事件，不冒充管理操作。
- [ ] 10.8 節流 denial/adapter errors，audit subscriber failure 不 rollback。
- [ ] 10.9 本地化所有 command feedback，不硬編碼玩家可見 literal。

### Gate

- [ ] Inspect permission 不能 mutate 或看內容。
- [ ] Admin mutation 有 actor、target、result audit。
- [ ] Orphan scan 不 force-load chunk、不在單 tick 掃描全世界。

## 11. Client

- [ ] 11.1 建立 tracking-chunk visual snapshot、revision cache 與 invalidation。
- [ ] 11.2 實作單箱、雙箱接縫、陷阱箱及木桶六方向 render anchors。
- [ ] 11.3 鎖模型跟隨 Chest lid openness，不重疊、不穿模。
- [ ] 11.3a 整個 Hopper 網路只在 root container 渲染一把鎖；中間分裂時
  留在 root 側，root 被破壞時同 revision 移到 successor，不產生雙鎖。
- [ ] 11.4 設計 Padlock、Key Blank、Bound Key 嚴格 16x16 sprites。
- [ ] 11.5 每個 16x16 asset 先通過 validator 再進行後續 batch。
- [ ] 11.6 建立 Management Screen 的原版 176px panel、18px slots、widgets。
- [ ] 11.7 實作名單分頁/scroll、pending state、disabled reason 及 confirmation。
- [ ] 11.8 實作 keyboard focus、Narration、Tooltip 與 click sound。
- [ ] 11.9 完成 en_us、zh_tw 並驗證長字串。
- [ ] 11.10 Client visual cache 不影響 Server access decision。

### Gate

- [ ] Client GameTest 或人工截圖涵蓋每種容器方向、動畫與 GUI state。
- [ ] 各 GUI scale、英文、繁中、鍵盤與 Narration 通過。
- [ ] 截圖只保存在規定 artifact 路徑，不含私人聊天或桌面。

## 12. Integrations

- [ ] 12.1 在 Core API v1 新增 immutable
  `LockedContainerNetworkBrokenEvent`。
- [ ] 12.2 DiscordBridge 訂閱並格式化永久
  `locked_container_network_broken` 事件，區分 root 側仍鎖定與分離側解鎖
  數量。
- [ ] 12.3 DiscordBridge 實作 bounded event-ID dedup、本地化及 transport
  failure isolation。
- [ ] 12.4 Nexus optional bridge 只查雙向好友，不直接讀 private SavedData。
- [ ] 12.5 Nexus 缺席／API 失敗時 FRIENDS fail closed 並顯示警告。
- [ ] 12.6 Automata operator-aware TRUSTED integration 與 pairwise GameTest。
- [ ] 12.7 Villagers owner-aware adapter 或已註冊 Work Chest 明確拒絕政策。
- [ ] 12.8 Protection adapter SPI 與至少一個 test adapter fixture。
- [ ] 12.9 Remnant portable-container policy 保持獨立，無重複 mixin/registration。
- [ ] 12.10 發布 API v1 javadoc、addon example 及相容性承諾。

### Gate

- [ ] Locksmith 不安裝任何 optional module 仍完整運作。
- [ ] 每組有明確整合的 pairwise installation 通過 Dedicated Server。
- [ ] Optional exception 不使鎖 fail open。
- [ ] DiscordBridge 缺席或失敗不回滾成功破壞。

## 13. Persistence

- [ ] 13.1 建立 v1 clean-world seed fixture。
- [ ] 13.2 建立單箱、雙箱、Hopper chain、root、connectors、ACL、Blocked、
  keys、mode 的 codec round trip。
- [ ] 13.3 建立三 JVM seed/mutate/verify restart probe。
- [ ] 13.4 驗證 key revoke、epoch、root-side split、detached unlocked branch、
  successor 與 container contents 跨重啟。
- [ ] 13.5 驗證 chunk unload/reload 與 cross-dimension records。
- [ ] 13.6 驗證 unknown newer dataVersion 停止服務且不覆寫。
- [ ] 13.7 驗證 damaged isolated record diagnostics。
- [ ] 13.8 驗證移除 Locksmith 後原版世界仍能載入容器與內容。
- [ ] 13.9 驗證重新安裝後合法 record 恢復、錯位 record 進入 repair。

### Gate

- [ ] 三個獨立正常停止 JVM markers 全通過。
- [ ] 無 silent data reset、ownership drift 或內容 duplication。

## 14. Verification

- [ ] 14.1 完成 `test-matrix.md` 全部 required cases。
- [ ] 14.2 `./gradlew build --stacktrace` 以 Java 25 通過。
- [ ] 14.3 JUnit、Fabric GameTest、Client GameTest、restart probe 全通過。
- [ ] 14.4 Dedicated Server 無 Client class loading 或 Mixin apply failure。
- [ ] 14.5 驗證 Core only、Core + Locksmith、Locksmith + each integration。
- [ ] 14.6 建立 candidate artifact、SHA-512 與 reproducible rebuild 比對。
- [ ] 14.7 將精確 artifact 加入 DeadRecall candidate bundle。
- [ ] 14.8 跑 assembled E2E、legacy world、resource ownership 與 rollback pin。
- [ ] 14.9 至少兩名真人完成 multiplayer/accessibility/visual 驗收。
- [ ] 14.10 查詢或重新觸發 GitHub Actions；取得成功前標記未驗證。

### Gate

- [ ] Standalone 及 bundle 中都只有一個 authority/registration surface。
- [ ] 所有 required automated、visual、manual 與 remote CI evidence 完整。

## 15. Release

- [ ] 15.1 更新 README、manual、recipes、commands、permissions 與 config 文件。
- [ ] 15.2 建立 migration、backup、repair 及 uninstall 說明。
- [ ] 15.3 產出 0.1.0 changelog、known limitations 與 support matrix。
- [ ] 15.4 建立 GitHub Release 並核對 artifact SHA-512。
- [ ] 15.5 建立 Modrinth project，使用通過驗證的 icon 與精簡 section headers。
- [ ] 15.6 上傳與 GitHub 相同 hash 的正式 JAR。
- [ ] 15.7 DeadRecall pin 正式精確版本並保留上一個 immutable rollback graph。

### Definition of Done

- [ ] 玩家無法經開箱、跨邊界 automation 或未授權 placement 繞過內容存取；
  玩家破壞依規格允許，非 Owner 拆容器／connector 會警報，分離側依設計
  解除鎖定。
- [ ] 世界重啟、模組缺席及 rollback 不遺失容器內容。
- [ ] UI、visual、英文、繁中、keyboard、Narration 皆有證據。
- [ ] Local 與 GitHub CI 成功，正式 artifact hash 一致。
- [ ] Modrinth project 及 DeadRecall bundle 都消費唯一的 TotemLocksmith
  0.1.0 authority。

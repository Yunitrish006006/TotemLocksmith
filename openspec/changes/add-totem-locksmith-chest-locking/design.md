# Design: TotemLocksmith

## Context

Minecraft 26.2 的 `BaseContainerBlockEntity` 已有 `LockCode`，但它只以
`ItemPredicate` 判斷玩家手上的物品。它不表示玩家所有權，也不涵蓋雙箱、
Hopper 儲存網路、可撤銷 bearer key、ACL、自動化或外部破壞。

TotemLocksmith 因此保留原版 LockCode，相互採 AND 語意，並另外建立一個
Server-authoritative domain。鎖資料不放在 Client，也不複製容器內容。

箱鎖保護「開啟與自動化存取」，不是不可破壞的領地。非 Owner 仍可依原版
規則拆除容器或受保護 Hopper，但成功後會產生可追蹤的 DiscordBridge
安全警報。

## Decisions

### 獨立模組

TotemLocksmith 是新的 bounded context：

- 必要依賴 TotemCore。
- 可選整合 Nexus、Automata、Villagers 及 protection adapters。
- 自己擁有 `dev.totem.locksmith.api.v1`。
- DeadRecall 最終只 nested-jar pin 一個已驗證版本。

不把 access policy 放入 Core。等第二個獨立模組證明需要完全相同的抽象後，
才以另一份 OpenSpec 提升共用 primitive。

### 世界索引

使用一份 Overworld-backed SavedData 保存權威資料，並在每個受保護
受保護容器與 Hopper BlockEntity 放一個非權威 Lock UUID persistent
attachment。

    LocksmithSavedData
    ├── records: LockId -> LockRecord
    └── runtime index: Dimension + BlockPos -> LockId

    Chest/Barrel/Hopper BlockEntity
    └── attachment: totem:locksmith/lock_id -> LockId

原因：

- 單箱、雙箱及整個 Hopper weak component 能共用一筆權威記錄。
- Pick Block、structure copy 或容器掉落不能只靠一個 attachment 複製
  ownership，因為完整 record 與合法位置仍由 SavedData 驗證。
- Persistent attachment 使用 Fabric API，不把完整 ACL 寫進原版欄位。
- 可在容器未載入時管理 UUID、ACL 與 keys。

Position index 由 records 在載入時重建，不另存第二份可能分歧的權威資料。
若兩筆記錄宣告同一位置，兩者進入 CONFLICT 並 fail closed。

Attachment 只用來偵測「record 遺失」或「位置被複製」；它不授權存取，也
不直接同步到 Client。Record、index、attachment 三者不一致都需要修復。

### 不保存內容

LockRecord 絕不保存 ItemStack。容器內容仍由原版 BlockEntity 掌權，鎖只
決定某項操作能否抵達原版行為。這避免鎖資料 rollback 時複製或覆寫物品。

### 穩定身份

- Lock UUID：一把實體鎖的身份，整個 Hopper 網路與雙箱兩半共用。
- Root container：最初套用 Padlock 的 logical container；網路分裂時決定
  唯一保留鎖的一側。root 被破壞時依確定性 successor 規則移交。
- Key UUID：一把已發行鑰匙的身份。
- Key epoch：一次撤銷全部 keys 的世代。
- Revision：任何 ACL、key、mode、owner 或 topology 變更都遞增。

位置不是鎖身份。方塊被替換時不能讓新箱沿用舊 Owner。

## Access model

### 操作

Policy 不只回傳 allow/deny，而是評估具體 operation：

- OPEN。
- INSERT。
- EXTRACT。
- BREAK。
- CONFIGURE。

Player Menu 的 slot mutation 分別視為 INSERT 或 EXTRACT；只通過 OPEN 不會
產生一個可永久繞過後續撤權的 session。

BREAK 是例外：Locksmith 不以角色拒絕玩家挖掘，而是標記
`OWNER_BREAK` 或 `NON_OWNER_ALERT`。真正能否破壞仍由原版 GameMode、
spawn protection、Fabric event 與領地 adapter 決定。

### Actor

- PLAYER：ServerPlayer UUID。
- IDENTIFIED_AUTOMATION：由可信 adapter 提供 operator UUID。
- ANONYMOUS_AUTOMATION：Hopper 等沒有 Owner 的行為。
- ENVIRONMENT：爆炸或無 actor destruction。
- ADMIN：已通過明確 permission 的 CommandSource。

Client 不能自行宣告 ADMIN 或 IDENTIFIED_AUTOMATION。

### 順序

政策使用以下固定順序：

1. 明確 Administrator bypass。
2. Owner。
3. Blocked。
4. Manager。
5. 目前有效且仍在手上的 Bound Key。
6. 依 AccessMode 啟用的 User、Friend 或 Public。
7. Deny。

PRIVATE 只啟用 Owner、Manager 與 key。ALLOWLIST 另啟用 User。FRIENDS
再加入 Nexus 雙向好友；PUBLIC 再加入所有未被 Blocked 的玩家。

Blocked 覆蓋 User、key、friend 及 public，讓 Owner 能阻止一名已取得實體
鑰匙的特定玩家。它不覆蓋 Owner 或明確 admin bypass。

### 能力

Manager 能維護 User/Blocked 與一般 keys，但不能：

- 建立或移除 Manager。
- 更改任何 AccessMode 或 AutomationMode。
- 輪替全部 keys。
- 轉移 Owner。
- 拆鎖、轉移所有權或免警報破壞。

這使日常共管不等於永久接管。

## Interaction

### 套用

Padlock 的 block-use callback 只送出玩家意圖；Server 從實際 hit result
解析目標 logical container，再沿固定 Hopper pull/push edges 計算 weak
component。建立 root、record、全部 index／attachment 及消耗物品在同一
Server-thread transaction 完成。

雙箱先合成一個 logical node，再加入 Hopper graph。任一成員或可達 edge
未載入、種類不符、超過節點上限或已有另一 Lock UUID 時整筆拒絕，不
force-load chunk，也不只鎖已遍歷到的前半段。

### 開箱

注入點採兩層防線：

1. `ChestBlock.useWithoutItem`、`BarrelBlock.useWithoutItem` 或相同
   高階入口在建立 Menu 前評估 OPEN。
2. `BaseContainerBlockEntity.canOpen`／Menu authority 再驗證，避免其他
   開啟路徑只繞過第一層。

授權後沿用原版 Chest/Barrel Menu，不建立容器內容副本。Locksmith 只增加
session validity check；slot、quick-move、drag 及 click 行為保持原版。

以 key 開啟時，Server 記錄授權來源為 Key UUID，但每次 mutation 前仍要求
同一有效 key 位於主手或副手。ACL/Owner 授權則直接重新計算角色。

### 管理

空手 Sneak + Use 開啟 Locksmith Management Menu。Padlock 或 Key 的 item
interaction 優先，避免 Sneak 時誤開管理畫面。

管理 Menu 是獨立 `AbstractContainerMenu`：

- 一個 Server-backed key slot。
- 玩家原版背包 slots。
- Client widgets 只顯示 snapshot。
- mutation 使用 session UUID + expected revision。

Client text field 的玩家名稱只是搜尋字串；Server 只接受解析出的 UUID。

### 移除

Remove Lock 先讓 Server 更新 record/index，再產生一個 Padlock。若玩家背包
已滿，物品以原版安全掉落方式出現在玩家位置。確認封包重送不得重複產生。

任何玩家直接破壞根側最後一個支援容器都採相同 finalize path。網路從
中間分裂時只保留 root component，其他 components 原子解除鎖定且不產生
Padlock；actor 不是 Owner 時，在 commit 後發布一個
`LockedContainerNetworkBrokenEvent`。

## Topology

### 狀態機

    ABSENT
      | apply padlock
      v
    ACTIVE_NETWORK(root, members)
      |  | owner-approved edge/member added
      |  +----------------------------------+
      | split: retain only root component
      +------------------------------------> ACTIVE_NETWORK(root/successor, members')
      | mismatch
      +------------------------------------> REPAIR_REQUIRED
      | remove lock or final container destruction
      +------------------------------------> ABSENT

一般遊戲流程只能在 ACTIVE 狀態開箱。REPAIR_REQUIRED、ORPHANED 與 CONFLICT
只允許 admin diagnostics。

### 合併

Topology hook 同時處理雙箱 logical node 與 Hopper network edges：

- 未授權 placement 若會與已鎖箱合併，取消且返還 placement stack。
- Owner placement 可在完整 weak component 已載入、未鎖且未超限時，將
  新箱、雙箱半或 Hopper 原子加入既有 record，不消耗第二個 Padlock。
- 兩把不同鎖不能合併；第一版拒絕連接 placement，不自動挑選 Lock UUID、
  合併 ACL 或返還其中一把 Padlock。
- block-state update 不能繞過上述規則。

若平台事件無法在不破壞原版 placement rollback 的位置阻止合併，實作必須
取消整次 placement，而不是先放方塊再手動補物品。

Graph edge 只來自固定 Hopper 的 vanilla pull source 與 facing destination。
Edge 本身保留方向供 transfer policy 使用，但 connected-component 計算忽略
方向。單純相鄰、Hopper Minecart、Dropper、Dispenser 與 item entity 不會
讓另一個箱子成為 LockRecord member。

### 拆分

任一玩家 break 容器或 member Hopper 時，都先捕捉原 graph、Lock UUID、
Owner、root 與 actor。原版成功移除方塊後才 commit：

1. 移除 broken positions／edges，計算 surviving weak components。
2. root 仍存在時保留 root component；其他 components 移除 index 與
   attachment 並成為 unlocked。
3. root 被移除時，在 pre-break graph 以最短距離、再以 Dimension + BlockPos
   排序選擇仍存在的 logical container successor；只保留 successor component。
4. 沒有 logical container 時刪除 record 與殘留 connector marker，最多掉落
   一個 Padlock。Hopper 不能單獨承接鎖。

這使「從中間拆開」只有最初套鎖箱所在側保持鎖定；分離側不複製 lock、
ACL、keys 或 Padlock。若一次 break 造成多個 detached components，全部
解除鎖定，但事件只對這一個實際移除方塊發布一次。

原版破壞被 protection mod 取消時，鎖資料保持不變。每次 topology commit
同步全部 attachment、revision 與 visual root。actor 不是 Owner 時發布一個
`LockedContainerNetworkBrokenEvent`，包含 root 側仍鎖定 logical container
數與本次解除鎖定 logical container 數。

## Transfer protection

### Vanilla

Minecraft 26.2 的 `HopperBlockEntity` 有 source/destination lookup、slot
take、place 與 add-item 路徑。Mixin 只在能取得 Level + BlockPos 的邊界
評估 policy，避免在沒有 context 的純 ItemStack helper 猜位置。

必須涵蓋：

- Hopper push。
- Hopper suction。
- Hopper Minecart。
- Dropper/Dispenser target insertion。
- Fabric Transfer API storage lookup。

拒絕必須發生在來源 remove 之前，或使用 exactly-once rollback。不得先移除
再以「盡量放回」作為正常安全路徑。

Resolver 先取得 source 與 destination 的 Lock UUID：

- 兩端是同一 Lock UUID：屬於網路內部 transfer，無論 DENY／TRUSTED／ALL
  都沿用原版行為。
- 任一端未鎖、unsupported 或屬於不同 Lock UUID：是保護邊界，分別評估
  source EXTRACT 與 destination INSERT；兩個 decision 都允許才 mutation。

因此 DENY 不會讓 Owner 建好的 Hopper chain 停擺，但也不允許物品跨出根側
保護網路。

### TotemAutomata

Automata 目前有直接 `Container.removeItem` 與 destination insert 路徑，
因此不能只依賴 Hopper Mixin。Locksmith 提供 v1 API；Automata 可選 bridge
在 source pick-up 與 destination insert 之前查詢。

若 Automata 能從 Copper Golem authority 取得 operator UUID，傳
IDENTIFIED_AUTOMATION；否則傳 ANONYMOUS_AUTOMATION。Locksmith 缺席時
Automata 維持原行為。

### Third party

任意模組直接呼叫原版 Container 方法而不經 Fabric Transfer API，且不採用
Locksmith API 時，無法可靠恢復 actor 與位置。第一版不宣稱攔截未知 direct
bytecode 路徑；公開 adapter contract、已知 Totem pairwise tests 與文件是
支援邊界。未知 adapter 失敗時 fail closed，不能 fail open。

## Persistence

### Codec

每一層都使用顯式 Codec，不以 Java class name 持久化。Dimension 使用
registry identifier，位置使用 packed BlockPos 或結構化三整數，UUID 使用
Minecraft UUID codec。

Decoder 流程：

1. 解碼頂層 dataVersion。
2. 逐筆 migration 至 current version。
3. 驗證集合上限、enum、UUID、revision、epoch、root、container nodes、
   Hopper connectors 與 topology schema。
4. 重建 position index。
5. 在 chunk 載入時比對 BlockEntity attachments。
6. 將可隔離的不合法記錄放入 diagnostics。
7. 若無法安全隔離，停止一般服務並保留原檔，不以空資料覆寫。

### Migration

0.1.0 使用 schema v1，沒有 legacy Locksmith 資料。仍從第一版建立：

- `LockDataMigrator` dispatcher。
- 未知較新 dataVersion 的拒絕。
- codec round trip fixture。
- 未來 v1 -> v2 測試插槽。

原版 LockCode 不是待 migration 的 Locksmith record。

### Save and restart

Restart probe 使用三個獨立 JVM：

1. Seed：建立單箱、雙箱、Hopper chain、root、ACL、keys、blocked 與 mode。
2. Migrate/Mutate：重載、撤銷 key、從中間分裂 chain、移交 root、正常存檔。
3. Verify：再次重載並驗證 root component、已解除鎖定分支、record/index、
   內容與失效 key。

每階段使用獨立 marker 與 world，不以同 JVM cache 冒充 persistence。

## Networking

管理 mutation 採 typed payload；不使用 `String operation`。

Session record：

- session UUID。
- actor UUID。
- Lock UUID。
- opening Dimension + BlockPos。
- opened revision。
- expiresAt tick。
- allowed management scope。

每次 mutation：

1. 切回 Server thread。
2. 解析 session，不接受 Client 位置作權威。
3. 驗證玩家在線、距離、Dimension、role。
4. 驗證 expected revision。
5. 驗證 operation-specific item、target UUID 與 limits。
6. 原子 commit。
7. 回傳新 snapshot 或非敏感 error key。

Destructive confirmation token 另綁 operation、target、revision 與短效 expiry。
同一 token 成功後立即消耗，重送只回傳先前結果，不重複 mutation。

Visual payload 與 management payload 分離。所有追蹤玩家可知道「這裡有鎖」
及 render anchor，但只有 Owner/Manager 能收到 ACL/key counts 等管理資料。

## Rendering

### Assets

- Module icon 從既有驗證通過的 16x16 TotemLocksmith source 匯入。
- Padlock、Key Blank、Key 各有獨立 16x16 source。
- Attached lock texture 使用原版 iron palette 與整數 UV。
- 所有 64x64 review asset 只用 nearest-neighbor 4x 匯出。

### Anchors

- Single Chest：正面中央，跟隨 lid transform。
- Double Chest：唯一 anchor 在正面接縫，依 LEFT/RIGHT 決定 canonical half。
- Trapped Chest：沿用 Chest anchor，不以紅色 UI overlay 代替模型。
- Barrel：依 FACING 放在可見正面，不假定只水平放置。

整個 Hopper network 只渲染 root logical container 的 anchor。一般中間分裂
不移動 root；分離側只清除 locked visual。root 被破壞時，同一 visual
revision 移除舊 anchor 並建立 successor anchor；DOUBLE -> SINGLE 時依目前
root node 形狀將接縫 anchor 改成單箱 anchor。Client 不得同時渲染舊鎖與
新鎖，也不得在每個網路容器上複製模型。

Client cache 不自行推論 ACL。Server visual state 遺失時寧可暫不畫鎖，不可
讓 Client 的缺圖影響 Server 保護。

### UI

Management Screen 優先使用原版 widget、slot texture、18-pixel slot grid、
8-pixel margin 與 176-pixel panel。長名單分頁或 scroll，不以縮小字體硬塞。

每一個按鈕有：

- hover tooltip。
- keyboard focus。
- narration label。
- disabled reason。
- pending Server state。

英文與繁中都需 real-game screenshot。任何 Client GameTest 證據保存到
`test-artifacts/screenshots/add-totem-locksmith-chest-locking/`。

## Administration

Permission nodes 由 Locksmith 定義，並提供原版 permission level fallback：

- `totem.locksmith.use`。
- `totem.locksmith.admin.inspect`。
- `totem.locksmith.admin.inspect_contents`。
- `totem.locksmith.admin.bypass`。
- `totem.locksmith.admin.mutate`。
- `totem.locksmith.admin.purge`。

Inspect 與 mutation 分離。能看診斷不代表能開箱、改 Owner 或清資料。

Orphan scan 只檢查已載入 chunk 與 SavedData 中可無載入判定的索引；全世界
掃描不 force-load chunk。Purge 一定先輸出 dry-run summary，confirmation
只對同一份 summary hash 有效。

成功的 admin mutation 發布 Core `AdminAuditEvent`。一般 ACL 變更也寫入
Locksmith audit logger；可選 Discord subscriber 的失敗不影響 commit。

## Integrations

### DiscordBridge

Locksmith 不直接呼叫 Discord、Worker 或 webhook。完成非 Owner 破壞
transaction 後，它透過 Core `TotemEventBus` 發布版本化
`LockedContainerNetworkBrokenEvent`：

    eventId
    lockId
    actorUuid / actorName
    ownerUuid / ownerName
    brokenMemberKind
    dimension / blockPos
    remainingLockedContainers
    detachedUnlockedContainers
    rootMoved
    lockRemoved
    occurredAt

DiscordBridge 訂閱事件並格式化成永久事件
`locked_container_network_broken`。訊息包含破壞者、Owner、被破壞的容器
或 Hopper、位置、root 側仍鎖定數與分離側解除鎖定數；不包含內容、ACL、
Key UUID、完整 Lock UUID 或完整 member list。

一個實際移除的 member 方塊只產生一個 event UUID，即使它同時分離多個
components。Subscriber 使用 bounded event-ID dedup 防止同 JVM 重複
callback；後續 HTTP retry 與 Worker 行為沿用 DiscordBridge transport
contract。Bridge 缺席、停用、無頻道、Worker 失敗或 Discord 失敗都只
記錄診斷，不回滾方塊、內容、topology 或 Padlock。

Owner 自己破壞不發布安全警報。Manager、User、Key Holder、Friend、
Public、Blocked、一般陌生玩家及正常挖掘的非 Owner OP 仍屬非 Owner。
只有明確 admin command mutation 使用管理 audit。若 protection event 取消
破壞，不發布成功事件。

### Nexus

FRIENDS 透過版本化只讀 API 查詢 Owner 與 actor 是否為雙向好友。查詢失敗
回 false。Locksmith 不直接讀 Nexus SavedData。

### Automata

source EXTRACT 與 destination INSERT 在真正改 ItemStack 前查詢 Locksmith。
TRUSTED 只在有 actor UUID 時成立；ALL 才允許匿名 golem/route。

### Villagers

Villagers 的舊 Work Chest 或未來 owner-aware storage 只有在 adapter 能證明
相同 Owner 及 operation 時才可作 IDENTIFIED_AUTOMATION。否則視為匿名。
沒有 adapter 時，掛鎖動作可依設定拒絕已註冊 Work Chest，避免悄悄停工。

### Remnant

Shulker、Bundle、backpack 等 portable-container policy 仍由 Remnant 擁有。
Locksmith 不複製其巢狀規則；將來若支援可攜式鎖，必須另提防複製規格。

### Protection mods

提供 cancellable hook 及 adapter SPI。上鎖與拆鎖都尊重 protection decision。
Locksmith 不以自身 Owner 判定繞過領地 Owner。

## Failure handling

| 失敗 | 結果 |
| --- | --- |
| record 不存在但 Client 有舊 visual | 清 visual；依未鎖容器處理 |
| attachment 存在但 record 不存在 | fail closed，ORPHANED |
| record/index 存在但 attachment 遺失 | fail closed，REPAIR_REQUIRED |
| attachment 與 record Lock UUID 不同 | fail closed，CONFLICT |
| index 指向不存在 record | fail closed，REPAIR_REQUIRED |
| record 指向錯誤方塊 | ORPHANED，不改內容 |
| 兩筆 record 共用位置 | CONFLICT，兩者都不可一般存取 |
| 雙箱只載入一半 | 不修改 topology；既有鎖 fail closed |
| Hopper traversal 遇到未載入 chunk／超限 | 不套鎖、不擴充、不截斷 component |
| 中間 member 成功移除 | root component 保留鎖；其餘 components 原子解鎖 |
| root 遺失且無可信 successor journal | REPAIR_REQUIRED，不依座標猜 root |
| optional integration exception | 節流記錄並拒絕該整合操作 |
| stale management revision | 不 mutation，回最新安全 snapshot |
| Client 重送 bind/remove | idempotent result，不二次消耗或掉落 |
| audit/Discord failure | gameplay commit 保留，記錄 subscriber failure |
| 非 Owner break 被其他保護取消 | 不改 topology，不發布成功警報 |
| 非 Owner 容器／Hopper break 成功但 Bridge 缺席 | topology 正常 commit，本地 audit 保留 |
| config reload 失敗 | 保留最後有效設定 |

「record 不存在但 Client 有舊 visual」只適用 Server 已確認位置也沒有
attachment 的情況；若 attachment、index 或 record 任一仍宣告有鎖則一律
fail closed。

## Performance

- 熱路徑 lookup 是 position index O(1)。
- Hopper graph traversal 只在套鎖、核准擴充、member break、repair 或 validation
  執行，使用 visited set 與 `maxNetworkNodesPerLock` 硬上限；不每 tick BFS。
- Policy 使用 immutable config snapshot。
- ACL 與 key registry 有硬上限。
- 不註冊每鎖 tick callback。
- denial log 使用 bounded rate-limit map 並在鎖移除／過期時清理。
- visual sync 依 chunk tracking 批次，不每 tick broadcast。
- admin scan 有 tick budget、cancel 與 progress。

實作前建立 0、1,000、10,000 locks 的 codec/lookup baseline；驗收比較相對
退化並記錄 JVM、heap 與測試命令，不寫一個無量測依據的絕對毫秒承諾。

## Security

威脅模型包含：

- 修改 Client 或直接偽造 Payload。
- 猜測 Lock UUID、Key UUID 或 session UUID。
- 遠距、跨 Dimension 或已關閉 GUI 的重送。
- stale revision 競爭。
- Key 被偷、複製或撤銷後繼續使用。
- Hopper／Automata 繞過玩家 open。
- 非 Owner 放置 Hopper 擴充鎖網路，或拆中間 Hopper 無警報地解鎖分支。
- Block placement 形成半鎖雙箱。
- Creative、Spectator 或非授權 OP 嘗試 bypass。
- World editor 造成 stale record。

UUID 不是秘密，也不單獨構成權限。Key 必須是 Server ItemStack、active
registry entry 與 epoch 三者同時相符。具有任意 Server command 或檔案寫入
能力的管理員已超出防護邊界，但其 Locksmith 操作仍需 audit。

## Trade-offs

- SavedData 位置索引不會隨箱子 item 移動；因此第一版排除 Shulker。
- 爆炸保護改變原版世界破壞行為，但符合「鎖保護容器」預期，且可設定。
- Comparator 保留會洩漏大致裝滿程度，但維持紅石相容且不洩漏 ItemStack。
- 非 Owner 可以用挖掘取得方塊與內容；Locksmith 對此提供 Discord 警報與
  audit，而不是領地式硬性阻止。這是明確的玩法選擇。
- Hopper 網路分裂時只有 root 側保留鎖；被分離的其他側會解除鎖定。這讓
  一把實體鎖永遠只有一份權威歸屬，代價是攻擊者可用一次會被警報的破壞
  解除分支保護。
- PRIVATE 與 ALLOWLIST 分開會讓 User 名單能預先配置而暫停生效，換取清楚
  的 Owner-only 快速模式。
- Unknown direct Container code 無法可靠取得 actor；公開 adapter 與
  fail-closed known integrations 比全域 ThreadLocal 猜測更安全可維護。

## Rollout

1. 建立獨立 repo、資料 schema、policy 與物品，尚不進 bundle。
2. 完成單箱／木桶上鎖、開啟及 persistence。
3. 完成雙箱 logical node、Hopper graph、root split 與 block lifecycle。
4. 完成 automation boundary、非 Owner 容器／connector 警報、環境保護與
   security tests。
5. 完成 GUI、commands、assets、accessibility。
6. 完成 optional integrations 與 pairwise tests。
7. 以 exact version 加入 DeadRecall candidate bundle。
8. 通過 standalone、bundle、legacy world、restart、Dedicated Server 與真人
   multiplayer 後建立 Modrinth project。

每一步都是可回滾 change；在新模組成為 bundle 唯一 authority 前，不在
DeadRecall 建立第二份箱鎖實作。

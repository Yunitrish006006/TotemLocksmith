# Discord Alert Contract

## 目的

非 Owner 成功破壞上鎖容器或該鎖網路的 Hopper connector 時，
TotemLocksmith 發布 Server-side typed event，TotemDiscordBridge 將它轉成
永久 Discord 安全警報。通知是事後觀測，不是破壞交易的一部分。

## 發布時機

只有下列條件全部成立才發布：

1. 破壞來源是可驗證的 ServerPlayer；一般挖掘的 OP 仍視為玩家。
2. 原版、Fabric break event、spawn protection 與領地 adapter 已允許。
3. 上鎖容器或 member Hopper 方塊已實際移除。
4. LockRecord topology、index 與 attachments 已成功 commit。
5. actor UUID 不等於 commit 前捕捉的 Owner UUID。

破壞嘗試被取消、方塊未移除、Owner 自己破壞、無法可靠歸因的爆炸及
WorldEdit 類管理變更不發布此事件。後兩者可使用環境或管理 audit；只有
明確 admin command mutation 才不走非 Owner 玩家警報。

## Core event

Core API v1 新增 immutable `LockedContainerNetworkBrokenEvent`：

| 欄位 | 型別 | 語意 |
| --- | --- | --- |
| `eventId` | UUID | 每個成功破壞方塊唯一 |
| `lockId` | UUID | 供診斷與 dedup，不直接顯示完整值 |
| `actorUuid` | UUID | Server 驗證的破壞者 |
| `actorName` | String | 非權威顯示名稱 |
| `ownerUuid` | UUID | 破壞 commit 前的 Owner |
| `ownerName` | String | 非權威最後已知名稱 |
| `brokenMemberKind` | String | chest、trapped_chest、barrel、hopper 或 adapter ID |
| `dimension` | String | Registry identifier |
| `x/y/z` | int | 被破壞方塊位置 |
| `remainingLockedContainers` | int | commit 後 root component 內 logical container 數 |
| `detachedUnlockedContainers` | int | 本次分裂後解除鎖定的 logical container 數 |
| `rootMoved` | boolean | 舊 root 被拆除並移交 successor 時為 true |
| `lockRemoved` | boolean | 根側最後一個 logical container 被拆除時為 true |
| `occurredAtEpochMillis` | long | Server 事件時間 |

建構器驗證：

- UUID、名稱、kind 及 Dimension 不得為 null。
- 兩個 container count 都必須 `>= 0`，且不計 Hopper connector。
- `lockRemoved` 必須等於 `remainingLockedContainers == 0`。
- 名稱及 kind 移除控制字元並限制長度。

事件不包含 ItemStack、ACL、Key UUID、完整 member/root position list、
Discord channel、webhook、token 或其他 secret。

## 網路語意

一串由固定 Hopper transfer route 連接的容器只有一個 Lock UUID，最初套鎖
的 logical container 是 root：

- 中間 Hopper 被拆除：只有 root component 保留鎖；其他 components 原子
  解除鎖定。事件分別記錄 `remainingLockedContainers` 與
  `detachedUnlockedContainers`，不掉 Padlock。
- 非 root 容器被拆除並造成分裂：套用相同 root-component 規則。
- root 被拆除但仍有容器：依最短 pre-break graph distance、再依穩定座標
  選 successor；`rootMoved=true`，只保留 successor component。
- 根側最後一個容器被拆除：`remainingLockedContainers=0`、
  `lockRemoved=true`，移除記錄並最多掉落一個 Padlock。
- 每次實際破壞一個 member 方塊各有一個 event ID；即使一次分離多個
  components 仍是一個 event，不把兩次分開挖掘誤合併。
- 同一 break callback 重入或 finalize 重送不得產生第二個 event ID。

雙箱只是 graph 中具有兩個 BlockPos 的一個 logical container node；它的
內部半箱生命週期不等於本文件的 Hopper 網路分裂。

## Discord event

DiscordBridge 訂閱 Core event，使用：

    event = locked_container_network_broken
    username = <actorName>

繁中格式：

- 網路仍有鎖：
  `<actor> 破壞了 <owner> 上鎖網路的 <kind>（<dimension> <x> <y> <z>）；根側仍鎖定 <remaining> 個容器，分離側 <detached> 個容器已解除鎖定。`
- 根側最後容器：
  `<actor> 破壞了 <owner> 上鎖網路的最後一個容器（<dimension> <x> <y> <z>）；鎖已掉落。`

Discord-facing 文字沿用 DiscordBridge 固定 `zh_tw` localization snapshot，
不把翻譯文字硬編碼進 Locksmith。未知名稱使用本地化的「未知玩家」；
不得顯示完整 UUID 作公開 fallback。

此事件為永久 security/audit 訊息，不加入十分鐘 transient allowlist。
只送到 DiscordBridge 已設定的 Minecraft event channels；沒有已設定頻道時
安全 no-op 並寫本地診斷，不自行選擇其他 Discord destination。

## Delivery

- Locksmith 在 topology commit 後呼叫一次 `TotemEventBus.publish`。
- Core event listener 例外由 event bus 隔離。
- DiscordBridge 使用 bounded event-ID cache 避免同 JVM 重複 dispatch。
- Cache 有容量與 expiry，不因惡意破壞無限成長。
- Worker、Discord、HTTP timeout、rate limit 或 formatter 失敗不得 throw 回
  gameplay transaction。
- Delivery 失敗可依 DiscordBridge transport policy retry，但不得重新發布
  Minecraft gameplay event。
- 本地 audit 記錄 event ID 簡寫、actor、owner、位置、remaining locked、
  detached unlocked 與 delivery submission 結果，不記內容或 secret。

## 驗收

- Owner 破壞不建立 `locked_container_network_broken`。
- Manager、User、Key Holder、Friend、Public、Blocked 及陌生玩家破壞上鎖
  容器或 member Hopper 都建立。
- 被 protection event 取消的破壞不建立。
- 中間 Hopper 分裂只有一個事件；root 側保持鎖定且分離側解除鎖定。
- root 被破壞時 successor 與 `rootMoved` 正確且只有一側保留鎖。
- 根側最後容器只有一個事件且 remaining locked = 0、lockRemoved = true。
- 同一 callback 重送仍只有一個 Discord dispatch。
- DiscordBridge 缺席時 Locksmith 可獨立啟動及完成破壞。
- Subscriber/formatter/Worker failure 不改變方塊、內容、鎖記錄或 Padlock。
- Discord payload 不包含內容、ACL、Key UUID、完整 Lock UUID 或 secret。

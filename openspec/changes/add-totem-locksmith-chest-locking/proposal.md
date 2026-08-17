# Proposal: TotemLocksmith

Status: planned
Target Version: TotemLocksmith 0.1.0
Target Platform: Fabric, Minecraft 26.2, Java 25

## 問題

原版容器鎖只驗證物品條件，沒有玩家 UUID 擁有權、成員角色、可撤銷鑰匙、
雙箱一致性或自動化存取政策。只攔截玩家右鍵也無法涵蓋漏斗、投擲器、
銅傀儡、偽造封包，以及 Hopper 儲存網路分裂後的鎖歸屬與警報，因此不足
以成為多人伺服器的箱子保護系統。

## 目標

- 建立獨立模組圖靈騰鎖匠（TotemLocksmith）。
- 讓玩家以實體掛鎖保護箱子、陷阱箱與木桶。
- 以玩家 UUID、角色、模式及可撤銷實體鑰匙決定存取。
- 將雙箱視為同一個原子鎖定單位。
- 讓 Hopper 連接的固定式容器網路共用一把鎖，並以最初套鎖的根容器決定
  網路分裂後哪一側保留鎖。
- 對玩家、漏斗、漏斗礦車、投擲器、發射器及模組自動化套用同一政策。
- 阻止未授權開啟與自動化存取，但允許非 Owner 依原版規則破壞容器。
- 非 Owner 成功破壞上鎖容器或受保護的連接 Hopper 後，發布事件並由
  DiscordBridge 傳送警報。
- 提供管理 GUI、指令、稽核、修復及資料 migration。
- 以原版風格的鎖具模型、介面、音效與雙語文字呈現狀態。
- 提供版本化公開 API，讓 Automata、Villagers、領地模組與 addon 查詢權限。

## 範圍

第一版原生支援：

- Chest。
- Trapped Chest。
- Barrel。
- 單箱與雙箱。
- 由固定 Hopper transfer route 連接的 Chest／Trapped Chest／Barrel 網路；
  同一網路只消耗並保存一把鎖。
- Survival、Adventure、Creative 與 Spectator 的明確權限規則。
- Owner、Manager、User、Blocked、Key Holder、Friend、Public 與 Administrator。
- 私人、名單、好友、公開四種開啟模式。
- 拒絕、可信任及完全開放三種自動化模式。

未列入第一版：

- Ender Chest。
- Shulker Box 及其他可搬動容器。
- Furnace、Brewing Stand 等工作方塊。
- 任意第三方容器的自動支援。
- 跨伺服器或 Proxy 網路共享鎖資料。
- 密碼、PIN、一次性密碼或真實密碼學秘密。
- 自動沒收長期離線玩家的箱子。
- 以鎖系統取代領地、區塊保護或備份模組。
- 阻止其他玩家挖掘上鎖容器或 member Hopper；此行為使用可追蹤警報而非
  硬性防破壞。

第三方固定式容器必須透過公開 adapter 明確加入；僅加入 block tag 不足以
取得支援，避免可移動或多方塊容器產生資料複製。

## 邊界

- Mod ID：`totem-locksmith`。
- Package：`dev.totem.locksmith`。
- Artifact：`totem-locksmith-<version>.jar`。
- Canonical registry namespace：`totem:locksmith/*`。
- TotemLocksmith 只必要依賴 TotemCore，不必要依賴其他功能模組。
- Locksmith 自己擁有物品、SavedData、Payload、GUI、Mixin、視覺資產與
  `dev.totem.locksmith.api.v1`。
- TotemCore 不承擔鎖箱玩法；只有在至少兩個模組需要同一抽象時，才另提
  Core API change。
- DeadRecall 只在獨立驗證完成後以精確版本收錄，不保留第二份執行中實作。
- 非 Owner 破壞使用 Core typed event；DiscordBridge 是可選 subscriber，
  未安裝或傳送失敗時不得取消或回滾已成功的原版破壞。

## 預設

| 項目 | 預設 |
| --- | --- |
| 存取模式 | PRIVATE |
| 自動化 | DENY |
| 爆炸保護 | 開啟 |
| 未展開 loot table 的容器 | 不可上鎖 |
| 每位玩家鎖數 | 128 |
| 每鎖成員數 | 32 |
| 每鎖有效鑰匙數 | 32 |
| 每鎖 Hopper 網路 BlockPos 數 | 128 |
| 失去擁有者後自動到期 | 關閉 |
| 未授權者看見擁有者名稱 | 關閉 |

所有限制皆為 Server config；Client 只接收呈現所需值。

## 相容性

- 原版 `LockCode` 仍由原版判定，Locksmith 不清除或繞過它。
- 對已有原版鎖的容器套用掛鎖時拒絕操作，避免兩套鑰匙造成誤解。
- 若管理指令之後加入原版鎖，兩套政策都必須通過，管理診斷標記雙重鎖。
- 世界資料自第一版即使用 `dataVersion`、UUID 與 Dimension + BlockPos。
- 玩家改名不改變權限；只更新非權威的最後已知名稱。
- 模組移除後原版容器及內容仍可載入，Locksmith 的保護不應破壞容器 NBT。
- 重新安裝後，仍存在且位置吻合的鎖記錄可恢復；不吻合者進入修復狀態。

## 成功條件

第一版只有在下列項目全部通過後才能稱為完成：

- 所有存取入口使用同一個 Server policy。
- 未授權玩家與匿名自動化無法讀取或修改內容。
- 雙箱與 Hopper 網路的合併、拆分、破壞、重啟及區塊重載不複製鎖。
- Hopper 網路從中間分裂時，只有包含最初上鎖根容器的一側保留唯一鎖；
  其他分支原子解除鎖定且不複製或掉落 Padlock。
- 根容器被拆除但仍有相連容器時，鎖移交給同一根側的確定性 successor；
  根側最後一個可鎖容器被拆除時才移除記錄並最多掉落一把 Padlock。
- 每次非 Owner 成功破壞鎖定容器或網路 Hopper 都只發布一個
  `locked_container_network_broken` 邏輯事件；Owner 破壞不發布安全警報。
- 鑰匙可個別撤銷及全部輪替，舊鑰匙立即失效。
- 偽造 Payload、過期 revision、遠距操作與競爭操作均被拒絕。
- Dedicated Server 不載入 Client class。
- Core only、Core + Locksmith、完整 DeadRecall bundle 皆能啟動。
- 英文與繁中 GUI、鍵盤、Narration、Tooltip 及所有 GUI scale 完成驗收。
- 掛鎖模型在單箱、雙箱、陷阱箱及木桶方向正確，並跟隨箱蓋動畫。
- Build、JUnit、GameTest、restart probe、Client visual test 與 CI 全部通過。

## 排程

本變更先完成規格，不提前加入 DeadRecall bundle。實作依
`tasks.md` 的階段 gate 進行；每個階段必須保持可回滾，最終才建立
Modrinth 專案與正式發布。跨模組破壞通知欄位與投遞語意以
`discord-alert-contract.md` 為準。

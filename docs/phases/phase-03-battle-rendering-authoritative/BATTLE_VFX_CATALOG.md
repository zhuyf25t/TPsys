# Battle VFX Catalog

更新时间：2026-04-27 16:44 Asia/Shanghai

## 设计原则

- VFX 只表达反馈和可读性，不反写 authoritative state。
- 本地玩家的开火反馈可以即时播放，但不能让玩家误以为服务器已经判定命中。
- 远程 projectile、terminal、hit confirm 必须尽量贴近服务端 truth，避免“看着打中但没扣血”。

## 已命名效果

### `pistol-short-muzzle-tracer`

中文名：手枪短管曳光。

用途：普通手枪的本地即时开火反馈。它只告诉玩家“我按下开火，枪响了”，不承担真实 projectile 主视觉，也不暗示已经命中。

当前实现参数：length `42`，thickness `2`，duration `78ms`，alpha `0.32`，ghostScale `0.7`，glint 关闭。muzzle burst 半径 `8`，sparks `2`。

锚点规则：起点必须继续使用 authoritative pistol birth distance `30px`，对应服务端 `HeroRadius 18 + ProjectileRadius 8 + 4px clearance`。禁止退回 `player.radius + 14`。

### `piercing-rail-tracer-long`

中文名：长束贯穿曳光。

来源：用户在 2026-04-27 截图中指出的白色长管状枪口/弹道特效。它视觉冲击力强，适合未来“狙击枪、穿透武器、激光/轨道枪、蓄力枪”一类武器。

当前问题：如果直接用于手枪，会显得子弹还没飞出，枪口特效已经打出很长一段；同时容易和真实 projectile 轨迹产生平行错位的错觉。

保留规则：不要删除这个视觉方向。后续如用户提到“那个很炫的长白色束流特效”，对应的稳定名字就是 `piercing-rail-tracer-long`。

手枪替代方向：`pistol-short-muzzle-tracer`，短、窄、低 alpha，只承担即时开火反馈，不抢真实 projectile 的视觉主线。

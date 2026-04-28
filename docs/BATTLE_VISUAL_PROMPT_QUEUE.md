# BP-28S-11 战斗视觉资产 / Prompt 队列

状态：计划 / 排队中。本文不声明任何资产已交付。

## 视觉北极星

目标方向：金属科幻竞技场 + 类 Hollow Knight 的圆润、高可读角色剪影。战斗视角保持俯视 top-down shooter，可快速辨认玩家、敌人、弹道、危险区和状态变化。

外部 UI 语言为中文；生成资产本身原则上不内嵌文字、数字、Logo 或伪文字，避免本地化和缩放问题。

推荐调性：
- 材质：拉丝金属、深色钢板、蓝色能量线、少量金色边框点缀。
- 角色：圆润头身比例、清晰外轮廓、少细碎装饰、暗底中高对比。
- 可读性：状态、命中、弹道、Dash/Blink/Freeze 必须比装饰更优先。
- 镜头：俯视正交为主，避免透视角导致碰撞范围误读。

## 不可协商约束

- 不改变 gameplay 语义：资产只能替换表现层，不修改伤害、射速、范围、移动、冷却、AI、同步逻辑。
- 不制造 hitbox / collision 欺骗：实体可见轮廓不得明显大于或小于真实碰撞范围；危险特效不得暗示错误范围。
- 状态通道与 VFX 通道分离：状态可读层（冻结、受击、无敌、冷却）不能被纯装饰 VFX 覆盖或混淆。
- 资产不能阻塞同步 / hit-feel 工作：若资源未完成，必须继续使用代码原生占位图形推进战斗手感、同步和命中反馈。
- 资产无嵌入文字：除非单独审批，prompt 均要求 no text / no letters / no numbers。
- 一致调色板：深枪metal、冷蓝能量、暖金点缀、白色高光、红橙命中危险提示。

## Prompt 队列

### 1. 竞技场地板 tiles

优先级：P0  
状态：计划 / 排队中  
目标文件：`assets/battle/arena/floor_metal_tiles_*.png`

Prompt：
```text
Top-down orthographic modular sci-fi metal arena floor tiles, dark brushed steel panels, subtle blue energy seams, small gold accent inlays, readable grid-like structure without text, clean stylized game asset, high contrast edges, slightly worn metal, seamless tileable texture set, polished but not noisy, inspired by premium sci-fi lobby materials and top-down arena shooter readability, 2D game art, no perspective distortion
```

Negative prompt：
```text
text, letters, numbers, logo, watermark, UI labels, characters, weapons, blood, gore, excessive scratches, photorealistic dirt, low contrast, busy noise, perspective camera, isometric angle, misleading depth, blurry edges
```

技术规格：
- PNG；优先 512x512 tile，可补 1024x1024 master。
- 正交俯视；可平铺；边缘对齐。
- 不透明底图；不带文字。
- 调色板固定：深钢灰、冷蓝、少量金色。

接入备注：
- 只用于背景层，不承载碰撞语义。
- 地板网格不得强到被误认为真实移动格或危险区。

### 2. 竞技场墙体 / 掩体 / 箱体

优先级：P0  
状态：计划 / 排队中  
目标文件：`assets/battle/arena/cover_metal_*.png`、`assets/battle/arena/wall_segment_*.png`

Prompt：
```text
Top-down orthographic sci-fi arena cover props and wall segments, rounded metal barricades, compact crates, blue edge lights, gold trim accents, readable silhouettes from above, clean collision-friendly shapes, dark steel material, stylized 2D top-down shooter asset sheet, transparent background, consistent scale, no embedded text, no logos
```

Negative prompt：
```text
text, letters, numbers, logo, watermark, characters, perspective view, tall side-view walls, extreme shadows hiding footprint, irregular misleading collision shape, overly detailed pipes, clutter, photorealism, blur
```

技术规格：
- 透明 PNG；单件 256x256 或 512x512。
- 从上往下正交视角；轮廓必须接近实际阻挡范围。
- 产出 wall straight、corner、crate、low cover、round pillar variants。

接入备注：
- 美术轮廓需跟物理碰撞占位一致；若不一致，以碰撞可读性优先。
- 墙体阴影不得遮挡角色脚下状态圈。

### 3. 英雄剪影 / 身体 variants

优先级：P0  
状态：计划 / 排队中  
目标文件：`assets/battle/actors/hero_body_*.png`

Prompt：
```text
Top-down orthographic cute dark knight hero body variants, Hollow Knight inspired rounded silhouette but original design, small cloak-like body, large readable head shape, smooth horn-like or antenna-like contour, dark navy and charcoal body, pale mask-like face area without facial text, blue rim light, compact readable footprint for fast arena combat, transparent background, 2D stylized game sprite, no weapon attached, no text
```

Negative prompt：
```text
Hollow Knight exact character, copyrighted character, text, letters, numbers, logo, watermark, realistic human anatomy, side view, isometric perspective, tiny thin limbs, excessive costume details, unclear silhouette, huge cape exceeding hitbox, gore
```

技术规格：
- 透明 PNG；建议 256x256 master，运行时缩放。
- 朝向 variants：front/top neutral、left/right tilt、dash pose 可选。
- 轮廓圆润清晰；视觉足迹不得明显欺骗碰撞半径。

接入备注：
- 身体图层与武器 overlay 分离，避免武器影响角色碰撞认知。
- 状态 tint / outline 仍由状态通道控制，不直接烘焙进基础 body。

### 4. 武器 overlays

优先级：P1  
状态：计划 / 排队中  
目标文件：`assets/battle/weapons/weapon_overlay_*.png`

Prompt：
```text
Top-down orthographic stylized sci-fi weapon overlay sprites for a rounded dark knight arena shooter character, compact readable silhouettes, small energy pistol, short blade, cannon module, blue energy core with subtle gold trim, transparent background, 2D game asset sheet, no hands required, no text, consistent scale
```

Negative prompt：
```text
text, letters, numbers, logo, watermark, realistic firearms, oversized weapon hiding character silhouette, side view, perspective angle, excessive muzzle flash baked in, gore, blur
```

技术规格：
- 透明 PNG；128x128 或 256x256。
- 武器中心点和朝向需可统一注册。
- 不内置开火特效，muzzle VFX 单独生成。

接入备注：
- 武器 overlay 只表现装备/朝向，不改变射线、射速或命中判定。
- 需要保持玩家身体剪影优先级高于武器装饰。

### 5. Projectile / tracer / muzzle / hit VFX

优先级：P0  
状态：计划 / 排队中  
目标文件：`assets/battle/vfx/projectile_*.png`、`tracer_*.png`、`muzzle_*.png`、`hit_*.png`

Prompt：
```text
Top-down sci-fi arena shooter VFX sprite sheet, clean blue-white energy projectiles, short readable tracers, compact muzzle flashes, orange-red hit sparks, transparent background, high contrast on dark metal floor, stylized 2D game effects, no text, no numbers, no logos, each effect isolated, readable at small size, consistent palette
```

Negative prompt：
```text
text, letters, numbers, logo, watermark, huge explosions covering gameplay, smoke hiding characters, long misleading projectile trails, photorealistic fire, gore, screen-filling bloom, blurry alpha edges
```

技术规格：
- 透明 PNG sprite sheet；单帧 128x128 或 256x256。
- Projectile、tracer、muzzle、hit 分文件或清晰分行。
- alpha 边缘干净；可在深色地板上高可读。

接入备注：
- Projectile 可视长度不得暗示错误命中长度。
- Hit VFX 是反馈层，不替代伤害/命中逻辑。
- VFX channel 不能覆盖状态 channel 的冻结、无敌、危险标识。

### 6. Dash / Blink / Freeze VFX

优先级：P0  
状态：计划 / 排队中  
目标文件：`assets/battle/vfx/dash_*.png`、`blink_*.png`、`freeze_*.png`

Prompt：
```text
Top-down 2D sci-fi ability VFX sprite sheet, dash afterimage streaks in blue-white, blink arrival ring with clean circular energy, freeze status aura with crystalline pale cyan shards, transparent background, high readability on dark metal arena floor, stylized not photorealistic, no text, no numbers, no logos, effects separated and compact
```

Negative prompt：
```text
text, letters, numbers, logo, watermark, giant opaque aura, confusing damage zone, heavy smoke, low contrast, perspective camera, overly complex particles, effect hiding character silhouette, misleading radius
```

技术规格：
- 透明 PNG；单帧 256x256，必要时 sprite sheet。
- Dash/Blink 为瞬时移动 VFX；Freeze 为状态可读辅助。
- Freeze 颜色与普通蓝色能量需有差异，可偏 pale cyan / icy white。

接入备注：
- Dash/Blink 只能表达移动反馈，不暗示额外攻击范围。
- Freeze 属于状态通道核心可读性，不能被普通蓝色装饰淹没。

### 7. HUD panels / icons / minimap ornaments

优先级：P1  
状态：计划 / 排队中  
目标文件：`assets/battle/hud/panel_*.png`、`icon_*.png`、`minimap_ornament_*.png`

Prompt：
```text
Sci-fi metal game HUD panels and icon ornaments, dark brushed metal frames, blue luminous lines, small gold accent corners, clean empty panels for Chinese UI text rendered separately by the game, top-down arena shooter style, transparent background where needed, no embedded text, no letters, no numbers, no logos, high readability, modular UI asset kit
```

Negative prompt：
```text
text, letters, numbers, fake UI words, logo, watermark, unreadable micro details, fantasy parchment, purple neon overload, photorealistic screen glare, cluttered labels
```

技术规格：
- PNG；panel 可 9-slice 友好；icons 透明 PNG / SVG 后续可选。
- 所有文字由前端中文 UI 渲染，不进图。
- minimap ornaments 不遮挡地图点位和危险提示。

接入备注：
- HUD 资产不能改变信息层级；血量、冷却、波次、目标状态优先。
- 图标语义需稳定，避免同一图标代表多个战斗状态。

### 8. Lobby / key art

优先级：P2  
状态：计划 / 排队中  
目标文件：`assets/battle/keyart/lobby_metal_arena_*.png`

Prompt：
```text
Polished sci-fi metal arena lobby key art, dark steel environment with blue energy columns and elegant gold accents, premium game menu atmosphere, rounded dark knight-like original silhouettes standing near an arena entrance, cinematic but clean, no readable text, no logos, no UI labels, high contrast, Chinese game UI will be rendered separately, 2D concept art
```

Negative prompt：
```text
text, letters, numbers, logo, watermark, exact Hollow Knight character, copyrighted character, messy crowd, gore, photorealistic humans, low readability, overbright bloom, UI mockup text
```

技术规格：
- PNG；1920x1080 master，可裁切 16:9 / 4:3。
- 允许轻微透视，因为不是战斗碰撞资产。
- 不作为战斗运行时 blocker。

接入备注：
- 仅用于氛围、入口、加载或宣传图；不得替代战斗场景可读性优化。
- 若与 BattlePage 主体验冲突，以实战 HUD / hit-feel 优先。

## BP-28S-12 代码原生视觉改进首轮

不依赖外部资产，优先服务同步、命中反馈和可读性：

- 统一战斗调色变量：深金属底、冷蓝能量、红橙命中、冰青冻结、白色高光。
- 用 Phaser graphics 绘制清晰角色轮廓环、脚下状态圈、受击闪白、敌我区分描边。
- 增强 projectile/tracer 的长度、alpha、颜色分层，但不改变碰撞和命中逻辑。
- Dash/Blink 使用短时 afterimage、到达圆环、轻量粒子；状态 channel 仍单独渲染。
- Freeze 使用独立冰青描边/晶体小片/减速符号，不复用普通蓝色攻击 VFX。
- HUD 使用代码原生金属面板、蓝线分隔、中文文本外置渲染，不把文字做进图片。
- 小地图和目标指示增加外圈/脉冲节奏，避免依赖贴图完成可读性。
- 所有新增表现挂在 renderer/VFX 层，禁止触碰伤害、冷却、位置同步、hitbox。

## 验收标准

- 所有资产条目保持 `计划 / 排队中`，不得伪造已完成交付。
- 每类 prompt 可直接复制到图像生成器，并包含 no text / no logo / top-down / palette / transparent PNG 等必要约束。
- 战斗运行时资产与状态/VFX 通道边界清楚，未把 gameplay 语义塞进图片。
- 每个接入备注说明如何避免碰撞误读、状态混淆或阻塞 hit-feel 工作。
- BP-28S-12 代码原生改进不依赖外部资产即可推进。

## 风险

- Hollow Knight 方向只能借鉴圆润剪影和高可读性，不能生成近似原角色。
- 过度金属细节会降低弹道和状态可读性，需要优先压低背景噪声。
- 蓝色能量、冻结、友方提示容易混色，必须通过亮度、轮廓和形状区分。
- 俯视资产若带透视或强阴影，可能造成 hitbox 误判感。
- HUD 装饰若过重，会抢夺中文信息层级。
- 外部资产排期不可成为同步、命中、战斗手感工作的阻塞条件。

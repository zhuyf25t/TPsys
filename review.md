# Hypersomnia 架构与 Slay/TPsys 战斗逻辑对比评审

## 1. 阅读范围

本次重点阅读 `reference/Hypersomnia`，并对照当前仓库的 battle 前后端实现。

Hypersomnia 重点路径：

- `reference/Hypersomnia/README.md`
- `reference/Hypersomnia/CMakeLists.txt`
- `reference/Hypersomnia/src/application`
- `reference/Hypersomnia/src/game`
- `reference/Hypersomnia/src/view`
- `reference/Hypersomnia/src/work.cpp`

Slay/TPsys 对照路径：

- `backend/src/main/scala/slaydemo/backend/battle/objects/player/BattlePlayerState.scala`
- `backend/src/main/scala/slaydemo/backend/battle/services/actors/BattleInputRules.scala`
- `backend/src/main/scala/slaydemo/backend/battle/services/actors/BattlePlayerRuntimeRules.scala`
- `backend/src/main/scala/slaydemo/backend/battle/services/world/BattleMotionRules.scala`
- `frontend/src/domains/battle/runtime/authoritative/inputCommandMapper.ts`
- `frontend/src/domains/battle/pages/battle/lib/authoritativeBattleInput.ts`
- `frontend/src/domains/battle/runtime/local/input/phaserPlayerCommandReader.ts`
- `frontend/src/domains/battle/runtime/local/input/controlKeys.ts`

## 2. Hypersomnia 项目架构

Hypersomnia 是一个现代 C++ 自研游戏项目。`README.md` 明确写到它是 multiplayer shooter，并且是 “without a game engine”。它不是在 Unity、Unreal 或 Phaser 上堆玩法，而是自己实现应用层、输入层、网络同步、实体系统、渲染视图、资源管理和玩法模拟。

顶层源码结构大致如下：

- `src/augs`：底层工具库，包含数学、窗口、图形、音频、输入、枚举映射、序列化等基础能力。
- `src/application`：应用壳层，负责 setup、网络、主菜单、配置、输入整合、HTTP/master server、平台差异。
- `src/game`：核心游戏域，负责 components、invariants、cosmos、messages、modes、stateless systems、entity organization。
- `src/view`：视图和 GUI 层，负责游戏 HUD、菜单、渲染配置、audiovisual state。
- `src/test_scenes`：测试场景和 playtest setup。
- `hypersomnia/content`：游戏素材内容。

它的核心实现思路是：用数据组件表达实体状态，用 stateless systems 推进玩法，用 message/entropy 传递输入和事件，用 deterministic simulation 支撑联机同步。

### 2.1 数据驱动和 ECS 思路

Hypersomnia 的 player 不是一个巨大的 `Player` 类。玩家控制的是 cosmos 里的一个 character entity；entity 上挂载 `components::movement`、`components::sentience`、rigid body、inventory、crosshair 等组件。系统按组件筛选实体，然后推进逻辑。

这意味着“玩家”不是单一对象，而是由多个明确职责的数据组件组合出来：

- `components::movement` 管移动输入、冲刺、惯性、脚步、移动动画状态。
- `components::sentience` 管生命、意识值、技能、perk、伤害来源、交互状态。
- `invariants::movement` 管移动参数和调参常量。
- `invariants::sentience` 管角色生命/意识/技能/受击等静态参数。
- `stateless_systems` 管纯推进逻辑，不把规则藏在实体对象里。

### 2.2 模拟和渲染分离

Hypersomnia 的 `src/game` 侧更像权威模拟层，`src/view` 侧负责呈现。`standard_solver.cpp` 展示了逻辑 tick 的推进顺序：输入系统先把 entropy 转换成消息，然后 movement、crosshair、melee、gun、car、force joint 等系统依次消费消息和组件状态。

关键代码路径：`reference/Hypersomnia/src/game/cosmos/solvers/standard_solver.cpp`

```cpp
input_and_intents_pass();
movement_system().set_movement_flags_from_input(step);
movement_system().apply_movement_forces(step);
crosshair_system().handle_crosshair_intents(step);
crosshair_system().update_base_offsets(step);
gun_system().launch_shots_due_to_pressed_triggers(step);
```

这条链路体现出两个设计习惯：

- 输入不是直接改坐标，而是先进入语义消息。
- 运动、瞄准、开火分别由不同 system 消费同一帧的模拟输入。

### 2.3 联机同步思路

Hypersomnia 的网络同步重点不是每帧传完整世界状态，而是基于 deterministic simulation 传玩家输入。项目文档强调跨平台模拟确定性，要求编译器、浮点、随机数、容器迭代顺序都可控。

这类模型的优势是带宽低、replay 能力强、服务端和客户端可以用同一套模拟规则推进。代价是工程约束更严，任何非确定性逻辑都会破坏同步。

## 3. Hypersomnia player 实现路径和核心逻辑

Hypersomnia 的 player 逻辑分散在 entity component、input system、movement system、setup control 和 entropy accumulator 中。这不是混乱，而是刻意的关注点分离。

### 3.1 玩家输入语义定义

路径：`reference/Hypersomnia/src/game/enums/game_intent_type.h`

核心代码：

```cpp
enum class game_motion_type {
	MOVE_CROSSHAIR,
	COUNT
};

enum class game_intent_type {
	SHOOT,
	SHOOT_SECONDARY,
	MOVE_FORWARD,
	MOVE_BACKWARD,
	MOVE_LEFT,
	MOVE_RIGHT,
	DASH,
	SPRINT,
	TOGGLE_SPRINT,
	WALK_SILENTLY,
	TOGGLE_WALK_SILENTLY,
	INTERACT,
	RELOAD,
	DROP,
	THROW,
	TOGGLE_ZOOM_OUT,
	COUNT
};

using game_intent = basic_input_intent<game_intent_type>;
using game_intents = std::vector<game_intent>;
using raw_game_motion = basic_input_motion<game_motion_type, basic_vec2<short>>;
```

解读：

- `game_intent_type` 是玩家动作语义，不是键盘按键。
- `game_motion_type` 目前只抽象鼠标准星移动。
- 下游系统只理解 `MOVE_FORWARD`、`SHOOT`、`DASH` 等语义，不依赖 W/A/S/D 或鼠标左键。
- 这给键位重绑定、Web 平台差异、GUI 捕获、AI 输入、网络序列化留下统一入口。

### 3.2 输入收集结果

路径：`reference/Hypersomnia/src/application/input/input_pass_result.h`

核心代码：

```cpp
struct input_pass_result {
	game_intents intents;
	raw_game_motion_vector motions;
	config_json_table viewing_config;
};
```

解读：

- 每帧输入最终被收敛成三个结果：语义意图、鼠标运动、当前视图配置。
- 输入层不会直接调用 movement 或 combat，它只产出可传递的输入结果。

### 3.3 主循环中的集中输入管线

路径：`reference/Hypersomnia/src/work.cpp`

核心代码：

```cpp
auto perform_input_pass = [&]() -> input_pass_result {
	/*
		The centralized transformation of all window inputs.
		No window inputs will be acquired and/or used beyond the scope of this lambda,
		to the exception of remote packets, received by the client/server setups.
	*/

	input_pass_result out;
	...
	if (const auto it = mapped_or_nullptr(viewing_config.game_controls, key)) {
		out.intents.push_back({ *it, *key_change });
	}
	...
	if (direct_gameplay && e.msg == message::mousemotion) {
		raw_game_motion m;
		m.motion = game_motion_type::MOVE_CROSSHAIR;
		m.offset = e.data.mouse.rel;
		out.motions.emplace_back(m);
	}
};
```

解读：

- 所有窗口输入先进入一个集中 lambda。
- 这里统一判断 ImGui、主菜单、游戏菜单、HUD、背包、直接 gameplay 是否要消费事件。
- 如果事件没有被 GUI 消费，才转换成 `game_intent` 或 `raw_game_motion`。
- 这避免了“页面监听一次、游戏场景监听一次、HUD 再监听一次”的输入冲突。

### 3.4 setup control 接入

路径：`reference/Hypersomnia/src/work.cpp`

核心代码：

```cpp
setup.control(result.motions);
setup.control(result.intents);
setup.accept_game_gui_events(game_gui.get_and_clear_pending_events());
```

解读：

- 主循环不直接知道当前是 editor、client、server 还是 test scene。
- 当前 setup 自己决定如何处理输入。
- 这让游戏模式和应用壳层解耦。

### 3.5 entropy accumulator

路径：`reference/Hypersomnia/src/application/input/entropy_accumulator.h`

核心代码：

```cpp
auto assemble(
	const E& handle,
	const mode_player_id& m_id,
	const input in
) const {
	mode_entropy out;
	out.general = mode_general;

	if (logically_set(m_id, mode)) {
		out.players[m_id] = mode;
		return out;
	}

	if (handle) {
		const auto player_id = handle.get_id();
		cosmic_entropy::player_entropy_type player_entry;
		player_entry.settings = in.settings.character;

		auto& player = player_entry.commands;

		if (const auto new_motion = calc_motion(handle, game_motion_type::MOVE_CROSSHAIR, in)) {
			player.motions[new_motion->motion] = new_motion->offset;
		}

		auto new_intents = intents;
		concatenate(player.intents, new_intents);
		player += cosmic;

		if (!player.empty()) {
			out.cosmic.players.try_emplace(player_id, std::move(player_entry));
		}
	}

	out.clear_dead_entities(handle.get_cosmos());
	return out;
}
```

解读：

- 输入会被组装成 `mode_entropy` 或 `cosmic_entropy`。
- 它把本地玩家输入、准星运动、角色输入设置、模式输入统一打包。
- 网络层可以传 entropy，模拟层可以消费 entropy。
- 这里已经考虑了鼠标灵敏度、自动缩放、左右键交换、死亡实体清理等边界。

### 3.6 input system：从 entropy 变成消息

路径：`reference/Hypersomnia/src/game/stateless_systems/input_system.cpp`

核心代码：

```cpp
void input_system::make_input_messages(const logic_step step) {
	for (const auto& p : step.get_entropy().players) {
		auto considered_id = p.first;
		const auto subject = cosm[considered_id];

		if (subject.dead()) {
			continue;
		}

		const auto& commands = p.second.commands;
		const auto& settings = p.second.settings;

		if (const auto movement = subject.template find<components::movement>()) {
			movement->forward_moves_towards_crosshair = settings.forward_moves_towards_crosshair;
		}

		for (const auto& intent : commands.intents) {
			auto msg = messages::intent_message();
			msg.game_intent::operator=(intent);
			msg.subject = considered_id;
			step.post_message(msg);
		}
	}
}
```

解读：

- entropy 不是直接作用到玩家坐标。
- 它先被转换为 `intent_message` 和 `motion_message`。
- 消息带上 `subject`，后续系统按 subject 改对应实体组件。

### 3.7 movement component

路径：`reference/Hypersomnia/src/game/components/movement_component.h`

核心代码：

```cpp
struct movement_flags {
	bool left = false;
	bool right = false;
	bool forward = false;
	bool backward = false;
	bool walking = false;
	bool sprinting = false;
	bool dashing = false;

	bool any_moving_requested() const {
		return left || right || forward || backward;
	}
};

namespace components {
	struct movement {
		movement_flags flags;
		bool frozen = false;
		bool was_sprint_effective = false;
		bool was_walk_effective = false;
		bool forward_moves_towards_crosshair = false;
		real32 surface_slowdown_ms = 0.f;
		real32 dash_cooldown_ms = 0.f;
		real32 const_inertia_ms = 0.f;
		real32 linear_inertia_ms = 0.f;
		real32 portal_inertia_ms = 0.f;
		real32 animation_amount = 0.f;
	};
}
```

解读：

- `movement_flags` 只表达输入请求。
- `components::movement` 保存运行时移动状态和动画状态。
- `invariants::movement` 保存移动参数，例如 acceleration、damping、dash、animation、surface drag。
- 这种拆分让“玩家现在怎样动”和“这个角色理论上应该怎样动”分开。

### 3.8 sentience component

路径：`reference/Hypersomnia/src/game/components/sentience_component.h`

核心代码：

```cpp
namespace components {
	struct sentience {
		augs::stepped_timestamp time_of_last_received_damage;
		augs::stepped_cooldown cast_cooldown_for_all_spells;
		augs::stepped_cooldown spawn_protection_cooldown;
		meter_instance_tuple meters;
		learnt_spells_array_type learnt_spells = {};
		spell_instance_tuple spells;
		perk_instance_tuple perks;
		spell_id currently_casted_spell;
		uint8_t requested_interactions = 0u;
		interaction_result_type last_interaction_result = interaction_result_type::NOTHING_FOUND;
		damage_owners_vector damage_owners;
		damage_origin knockout_origin;
	};
}
```

解读：

- Hypersomnia 把生命、意识、技能、perk、伤害来源、交互结果放在 sentience 组件中。
- 它不是独立执行 I/O 的主动对象，而是被系统读取和推进的被动数据。
- 这符合“domain data passive，state transition explicit”的架构习惯。

### 3.9 movement system：输入意图到移动状态

路径：`reference/Hypersomnia/src/game/stateless_systems/movement_system.cpp`

核心代码：

```cpp
void movement_system::set_movement_flags_from_input(const logic_step step) {
	const auto& events = step.get_queue<messages::intent_message>();

	for (const auto& it : events) {
		cosm(it.subject, [&](const auto subject) {
			if (auto* const movement = subject.template find<components::movement>()) {
				auto& flags = movement->flags;

				switch (it.intent) {
					case game_intent_type::MOVE_FORWARD:
						flags.forward = it.was_pressed();
						break;
					case game_intent_type::MOVE_BACKWARD:
						flags.backward = it.was_pressed();
						break;
					case game_intent_type::SPRINT:
						flags.sprinting = it.was_pressed();
						if (flags.sprinting) {
							flags.walking = false;
						}
						break;
					case game_intent_type::DASH:
						flags.dashing = it.was_pressed();
						break;
					default: break;
				}
			}
		});
	}
}
```

解读：

- movement system 只关心 movement 相关 intent。
- `SHOOT`、`RELOAD`、`INTERACT` 不会污染 movement system。
- sprint 和 walk 是互斥状态，规则在这里集中处理。

### 3.10 movement system：应用物理运动

路径：`reference/Hypersomnia/src/game/stateless_systems/movement_system.cpp`

核心代码：

```cpp
void movement_system::apply_movement_forces(const logic_step step) {
	cosm.for_each_having<components::movement>(
		[&](const auto& it) {
			auto& movement = it.template get<components::movement>();
			const auto& movement_def = it.template get<invariants::movement>();
			const auto& rigid_body = it.template get<components::rigid_body>();

			if (!rigid_body.is_constructed()) {
				return;
			}

			components::sentience* const sentience = it.template find<components::sentience>();
			auto considered_flags = movement.flags;

			if (it.is_frozen()) {
				considered_flags = {};
			}

			const auto requested_by_input_aa =
				considered_flags.get_force_requested_by_input(movement_def.input_acceleration_axes);
		}
	);
}
```

解读：

- movement system 用 rigid body 和 invariant 参数计算力，而不是直接 `position += velocity * dt`。
- 它可自然接入 inertia、surface slowdown、dash、冻结、haste、consciousness meter。
- 这类写法更适合复杂物理反馈和多人同步。

## 4. Hypersomnia 游戏按键机制

Hypersomnia 的按键系统可以分为四层：

- 物理输入层：窗口事件、键盘、鼠标、平台事件。
- 映射层：`config_json_table.game_controls` 把具体键映射到 `game_intent_type`。
- 语义层：`game_intent`、`raw_game_motion` 表达“玩家想做什么”。
- 模拟层：systems 消费 messages，更新 components。

Web 平台下的按键调整在 `reference/Hypersomnia/src/make_canon_config.hpp` 中可以看到：

```cpp
result.game_controls[key_type::C] = game_intent_type::TOGGLE_WALK_SILENTLY;
result.game_controls[key_type::V] = game_intent_type::WIELD_BOMB;
result.game_controls[key_type::K] = game_intent_type::THROW_PED_GRENADE;
result.game_controls[key_type::L] = game_intent_type::THROW_FLASHBANG;
```

这说明 Hypersomnia 的按键不是硬编码在 movement system 里，而是在配置层映射到统一的游戏意图。不同平台可以替换键位，但 gameplay system 不变。

它的关键优势：

- GUI 捕获、游戏输入、菜单输入集中仲裁。
- 具体按键和游戏动作分离。
- 输入可以被网络序列化。
- AI、replay、远端玩家和本地玩家都可以统一走语义输入模型。
- movement/combat/item systems 只消费自己关心的 intent。

## 5. Slay/TPsys 当前实现对比

### 5.1 当前玩家状态

路径：`backend/src/main/scala/slaydemo/backend/battle/objects/player/BattlePlayerState.scala`

核心代码形态：

```scala
final case class BattlePlayerState(
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  position: BattleVector2,
  aim: BattleVector2,
  facing: FacingRadians,
  movement: BattleVector2,
  sprint: Boolean,
  primaryHeld: Boolean,
  reloadPressed: Boolean,
  currentWeaponIndex: Int,
  weapons: Vector[BattleWeaponState],
  hp: HitPoints,
  stamina: Stamina,
  score: Score,
  skills: Vector[BattlePlayerSkillState],
  lifeState: BattlePlayerLifeState
)
```

评价：

- 这个模型是 immutable case class，基础方向是正确的。
- 但它把身份、输入、移动、武器、血量、体力、分数、技能、生命周期、bot 参与者类型放在同一个聚合里。
- 当玩法继续扩展到狩猎模式、大地图、复杂 bot、背包、地表、状态异常、复活规则时，这个类会继续变厚。

### 5.2 当前前端输入路径

本项目当前有两条主要输入路径：

- `authoritativeBattleInput.ts`：DOM/window 事件捕获，给后端权威模式生成输入 snapshot。
- `phaserPlayerCommandReader.ts`：Phaser scene 内读取键盘和鼠标，生成本地 runtime 命令。

核心代码形态：

```ts
export function createPlayerCommand(input: InputCommandContext): PlayerCommand {
  const movementInput = {
    x: Number(input.moveRight) - Number(input.moveLeft),
    y: Number(input.moveDown) - Number(input.moveUp)
  };
  const movement = normalizeVector(movementInput);
  const aimVector = {
    x: input.pointerWorld.x - input.playerPosition.x,
    y: input.pointerWorld.y - input.playerPosition.y
  };

  return {
    movement,
    aim,
    pointerWorld: input.pointerWorld,
    primaryHeld: input.primaryHeld,
    sprint: input.sprint,
    castDash: input.castDash,
    reloadPressed: input.reloadPressed
  };
}
```

评价：

- `createPlayerCommand` 作为统一命令构造器是好的，它在前端把键盘/鼠标输入收敛为 `PlayerCommand`。
- 但是输入捕获仍分散在 DOM authoritative path 和 Phaser local path。
- 这两条路径都要维护技能键、武器键、reload、pointer、sprint 等规则，长期容易漂移。

### 5.3 当前后端命令应用

路径：`backend/src/main/scala/slaydemo/backend/battle/services/actors/BattleInputRules.scala`

核心代码形态：

```scala
def applyCommandToPlayer(player: BattlePlayerState, request: BattleCommandRequest): BattlePlayerState = {
  val aim = normalizeAim(player.aim, BattleVector2(request.aim.x, request.aim.y))
  val movement = normalizeMovement(BattleVector2(request.movement.x, request.movement.y))
  val suppressPrimaryHeld = request.skillIntents.nonEmpty
  val inputPlayer = player.copy(
    aim = aim,
    facing = FacingRadians(math.atan2(aim.y, aim.x)),
    movement = movement,
    sprint = request.sprint,
    primaryHeld = request.primaryHeld && !suppressPrimaryHeld,
    reloadPressed = request.reloadPressed,
    lastClientCommandSeq = maxClientCommandSeq(player.lastClientCommandSeq, request.clientCommandSeq)
  )
  applyWeaponSwitchRequest(inputPlayer, request.switchWeaponDirection, request.switchWeaponIndex)
}
```

评价：

- 后端会重新 normalize aim/movement，这是必要的权威校验。
- 但 `BattleCommandRequest` 进入后端后直接写回 `BattlePlayerState` 的输入字段，没有类似 Hypersomnia 的 message queue 或 movement flags 层。
- 技能、武器切换、开火抑制、reload 等输入规则已经开始混在同一个 apply 过程中。

### 5.4 当前后端移动推进

路径：`backend/src/main/scala/slaydemo/backend/battle/services/actors/BattlePlayerRuntimeRules.scala`

核心代码形态：

```scala
def movePlayer(
  player: BattlePlayerState,
  deltaMs: Long,
  slowFields: Vector[BattleSlowFieldState],
  previousElapsed: Long,
  nextElapsed: Long
): BattlePlayerState = {
  val hasMovement = vectorLength(player.movement) > 0.0 && deltaMs > 0L
  val canSprint = player.sprint && hasMovement && player.stamina.value > 0.0
  val baseSpeed =
    if player.isBot then BattleBotCatalog.MoveSpeed.value
    else if canSprint then BattleMovementCatalog.SprintSpeed.value
    else BattleMovementCatalog.WalkSpeed.value
  val distance = baseSpeed * deltaMs.toDouble / 1000.0
  val motion = findMotionDestination(
    position = player.position,
    direction = player.movement,
    distance = distance,
    radius = BattleArenaCatalog.PlayerCollisionRadius
  )
  player.copy(position = motion.destination)
}
```

评价：

- 这套规则小而清楚，适合当前 Web demo。
- 但是它更接近速度积分和碰撞步进，不是完整 physics/invariant system。
- 想支持地表摩擦、惯性、dash 冲量、装备影响、受伤减速、不同角色运动参数时，会逐步把 `movePlayer` 变成厚函数。

## 6. 我们项目在代码逻辑上的不足

### 6.1 玩家聚合对象过厚

`BattlePlayerState` 现在承担太多职责。它既是玩家身份，又是战斗实体，又是输入缓存，又是移动状态，又是武器库存，又是生命/技能/分数载体。

建议方向：

- 拆出 `BattlePlayerIdentityState`：playerId、heroId、handle、displayName、seat、participantKind。
- 拆出 `BattlePlayerPoseState`：position、aim、facing、movement。
- 拆出 `BattlePlayerInputState`：sprint、primaryHeld、reloadPressed、lastClientCommandSeq。
- 拆出 `BattlePlayerVitalState`：hp、maxHp、stamina、maxStamina、lifeState。
- 拆出 `BattlePlayerLoadoutState`：currentWeaponIndex、weapons、currentWeaponKind、skills。
- 保持外层 `BattlePlayerState` 是聚合根，但内部字段按职责组合。

这样可以保留前后端 contract 的稳定性，同时让后端规则函数更容易按职责读取局部状态。

### 6.2 输入语义层还不够统一

当前前端已经有 `PlayerCommand`，但它更像“传给后端的快照 DTO”，还不是 Hypersomnia 那种语义 intent 层。

当前问题：

- DOM authoritative path 和 Phaser local path 都维护按键读取。
- `W/A/S/D`、`Shift`、`Q/E/R`、`T`、数字键在多个地方出现。
- 技能绑定通过 `readSkillBindingPresses` 做了一层，但 movement、reload、weapon switch 还没有统一配置模型。

建议方向：

- 建立 `BattleInputIntent` enum/type，例如 `MoveForward`、`MoveBackward`、`Sprint`、`Reload`、`ShootPrimary`、`CastDash`、`SwitchWeaponSlot1`。
- 建立统一 `BattleInputBindingMap`，DOM 和 Phaser 都从同一份 binding map 转换到 intent。
- `PlayerCommand` 只作为网络命令 DTO，输入层先产出 semantic intent，再由 mapper 组装 command。

### 6.3 movement runtime 和 movement tuning 没有分离到足够清楚

Hypersomnia 把 runtime component 和 invariant 参数分开。本项目目前有 `BattleMovementCatalog`，但 player 上的 movement/sprint/stamina/position 仍混在一个聚合里。

当前风险：

- 新增角色敏捷度、装备减速、地表减速、受击硬直、dash inertia 时，参数入口容易散落。
- bot speed、walk speed、sprint speed 分支已经出现在 `movePlayer` 内。
- movement 规则会越来越多地知道 bot、slow field、stamina、collision、life state。

建议方向：

- 建立 `BattleMovementState` 表达 runtime。
- 建立 `BattleMovementProfile` 或继续强化 `BattleMovementCatalog` 表达 invariant。
- `movePlayer` 改成读取 movement state + movement profile + world collision，避免直接知道太多 player 聚合细节。

### 6.4 缺少 message/event queue 式的系统解耦

Hypersomnia 的 input system 会发 `intent_message` 和 `motion_message`，movement/combat/item 各自消费消息。本项目后端现在更多是 service rules 直接依次调用。

当前风险：

- 技能抑制开火、reload、weapon switch、movement、bot input 可能继续耦合到同一批函数。
- 添加 replay、rollback、prediction 或 server-side debug trace 时，很难复盘“这一帧有哪些语义输入，哪些系统消费了它”。

建议方向：

- 不需要照搬完整 ECS，但可以引入轻量 `BattleFrameInputEvents`。
- command accept 阶段只把 request 转为 typed events。
- movement/combat/abilities 分别消费自己关心的 events。
- replay 可以记录 events，而不是只记录最后 snapshot。

### 6.5 网络模型更偏命令/快照，缺少 deterministic simulation 约束

本项目采用 Web 前后端模式，后端权威状态是合理选择。但如果目标是大地图和联机体验，仍需要吸收 Hypersomnia 的几个原则。

当前不足：

- 前端 local runtime 和后端 authoritative rules 存在两套相似逻辑。
- 缺少明确的输入序列、预测状态、服务端确认、回滚/修正边界。
- `PlayerCommand` 与 `BattleCommandRequest` 需要持续人工保持一致。

建议方向：

- 保留后端权威，不必强行做 deterministic lockstep。
- 引入更严格的 command sequence、server ack、client prediction correction。
- 把 movement/weapon/skill 的可共享调参配置生成到前端，减少漂移。
- 对关键 DTO 增加 contract audit 或 schema generation。

### 6.6 输入捕获和 UI 捕获边界不够集中

Hypersomnia 的 `perform_input_pass` 虽然很大，但它明确把所有输入传播规则放在一个地方处理。我们的输入分布在页面 hook、DOM event listener、Phaser scene input、runtime bridge 中。

当前风险：

- 弹窗、HUD、聊天、菜单、地图选择、游戏画布同时存在时，输入消费规则容易不一致。
- 鼠标点击可能既触发 UI，又触发射击。
- 键盘事件可能在页面级和 canvas 级重复处理。

建议方向：

- 设计一个 battle input gateway，统一处理 UI capture 状态。
- 页面只告诉 gateway 当前 UI 是否占用 keyboard/mouse。
- DOM/Phaser 读取层只负责 raw event，不能直接决定 gameplay command。

## 7. 对我们后续重构的优先级建议

优先级 1：统一 battle 输入语义层。

先不要大改 ECS。先把 DOM authoritative path 和 Phaser local path 都改为“raw input -> BattleInputIntent -> PlayerCommand”。这是收益最高、风险最低的改动。

优先级 2：拆薄 `BattlePlayerState` 内部结构。

保持外层 API 字段稳定，但后端内部先组合子状态。这样既能维持前后端契约，又能让 movement、combat、abilities 规则按职责读取数据。

优先级 3：引入轻量 frame input events。

不照搬 Hypersomnia 的完整 entropy/cosmos，但可以学习它的 message 思路。后端每帧先形成 typed input events，再由 movement/combat/abilities 分别消费。

优先级 4：movement state 和 profile 分离。

把 runtime 状态、调参常量、环境影响分开。未来支持狩猎模式、大地图、不同角色机动性、地表和障碍时，movement system 不会爆炸。

优先级 5：强化前后端 contract 生成或审计。

当前 TypeScript `PlayerCommand` 和 Scala `BattleCommandRequest` 是同一概念的两份定义。应继续保留 audit，或推进 schema/source-of-truth 生成，避免字段名和 optional/nullability 漂移。

## 8. 结论

Hypersomnia 最值得学习的不是具体 C++ 语法，而是四个架构原则：

- 玩家不是大对象，而是 entity + components + systems。
- 按键不是业务逻辑，按键要先变成语义 intent。
- 输入不能直接改世界，输入要经过消息/entropy，再由系统消费。
- 运行状态和调参 invariant 要分离，才能支撑复杂 movement、combat 和 multiplayer。

Slay/TPsys 当前实现更轻量，适合快速迭代 Web demo。但随着狩猎模式、大地图、bot、技能、武器和联机同步继续增加，当前最大风险是输入路径重复、玩家状态过厚、movement/combat/skill 规则继续耦合。

下一步最务实的重构不是照搬 Hypersomnia 全套自研 ECS，而是先建立统一输入语义层和拆薄玩家状态。这样能在不破坏现有前后端契约的前提下，提高代码结构的可管理性和长期扩展能力。

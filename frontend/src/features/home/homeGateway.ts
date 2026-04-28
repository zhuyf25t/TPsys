import { BATTLE_ARENA_PLAYER_CAPACITY, BATTLE_MATCH_DURATION_LABEL } from "../battle/rules/battleRules";

export interface NavItem {
  label: string;
  path: string;
}

export interface HomeHeroContent {
  eyebrow: string;
  title: string;
  summary: string;
  primaryActionLabel: string;
  primaryActionPath: string;
  secondaryActionLabel: string;
  secondaryActionPath: string;
  tertiaryActionLabel: string;
  tertiaryActionPath: string;
  highlights: Array<{
    label: string;
    detail: string;
  }>;
}

export interface HomePortalCard {
  title: string;
  detail: string;
  path: string;
  cta: string;
}

export interface HomePanel {
  title: string;
  detail: string;
}

const primaryNavSeed: NavItem[] = [
  { label: "首页", path: "/" },
  { label: "配装", path: "/loadout" },
  { label: "战斗", path: "/battle" },
  { label: "回放", path: "/replay" },
  { label: "站内信", path: "/mails" },
  { label: "排行", path: "/rating" },
  { label: "贡献", path: "/contribution" },
  { label: "个人主页", path: "/profile/Player-1" },
  { label: "讨论", path: "/discussion" }
];

const homeHeroSeed: HomeHeroContent = {
  eyebrow: "玩家大厅",
  title: "Player-1，准备进入下一局",
  summary:
    "默认手枪起手，Q 闪现 / E 冲刺 / 右键跳跃。这里先把你的身份、当前配置和下一局入口放在最前面，再把回放和社区放在后面。",
  primaryActionLabel: "进入下一局",
  primaryActionPath: "/battle?new=1",
  secondaryActionLabel: "调整配装",
  secondaryActionPath: "/loadout",
  tertiaryActionLabel: "查看战报",
  tertiaryActionPath: "/replay",
  highlights: [
    { label: "当前玩家", detail: "Player-1 作为当前大厅身份继续排队和进入战斗。" },
    { label: "当前配置", detail: "手枪起手，技能是 Q 闪现 / E 冲刺 / 右键跳跃。" },
    {
      label: "局内结构",
      detail: `${BATTLE_ARENA_PLAYER_CAPACITY} 人竞技场，${BATTLE_MATCH_DURATION_LABEL}一局，结束后会生成真实结果。`
    }
  ]
};

const homePortalCardsSeed: HomePortalCard[] = [
  {
    title: "回放",
    detail: "回看最近战局，复盘关键回合和收尾瞬间。",
    path: "/replay",
    cta: "打开战报库"
  },
  {
    title: "站内信",
    detail: "查看战后通知、系统消息和提醒。",
    path: "/mails",
    cta: "查看收件箱"
  },
  {
    title: "排行",
    detail: "查看评分变化、连胜走势和竞争层级。",
    path: "/rating",
    cta: "查看排行"
  },
  {
    title: "贡献",
    detail: "浏览真实活动变化与近期参与摘要。",
    path: "/contribution",
    cta: "查看贡献"
  },
  {
    title: "个人主页",
    detail: "查看玩家摘要、最近对局与常用配置。",
    path: "/profile/player-1",
    cta: "进入主页"
  },
  {
    title: "讨论区",
    detail: "浏览战术讨论和玩家交流，保持轻量社区气质。",
    path: "/discussion",
    cta: "进入讨论"
  }
];

const homePanelsSeed: HomePanel[] = [
  {
    title: "推荐路线",
    detail: "从大厅进入战斗，再去查看回放和评分，是最自然的玩家路径。"
  },
  {
    title: "当前对局",
    detail: `${BATTLE_ARENA_PLAYER_CAPACITY} 人竞技场与 ${BATTLE_MATCH_DURATION_LABEL}一局保持清晰节奏，战后结果会继续流向记录页。`
  },
  {
    title: "社区入口",
    detail: "排行、贡献、个人主页和讨论区都放在大厅的次级分流里。"
  }
];

export function getPrimaryNavItems(): NavItem[] {
  return primaryNavSeed.map((item) => ({ ...item }));
}

export function getHomeHeroContent(): HomeHeroContent {
  return {
    ...homeHeroSeed,
    highlights: homeHeroSeed.highlights.map((entry) => ({ ...entry }))
  };
}

export function getHomePortalCards(): HomePortalCard[] {
  return homePortalCardsSeed.map((card) => ({ ...card }));
}

export function getHomePanels(): HomePanel[] {
  return homePanelsSeed.map((panel) => ({ ...panel }));
}

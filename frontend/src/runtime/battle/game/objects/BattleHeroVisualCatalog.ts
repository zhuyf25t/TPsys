export interface HeroVisualDefinition {
  textureKey: string;
  tint: number;
}

export const HERO_VISUALS: Readonly<Record<string, Readonly<HeroVisualDefinition>>> = {
  "player-1": { textureKey: "hero-player", tint: 0x7ae2ff },
  "bot-1": { textureKey: "hero-zombie-boss", tint: 0xb7f06a },
  "bot-2": { textureKey: "hero-zombie-boss", tint: 0xb7f06a },
  "bot-3": { textureKey: "hero-zombie-boss", tint: 0xb7f06a },
  "bot-4": { textureKey: "hero-zombie", tint: 0xb7f06a },
  "bot-5": { textureKey: "hero-zombie", tint: 0xb7f06a },
  "bot-6": { textureKey: "hero-zombie", tint: 0xb7f06a },
  "bot-7": { textureKey: "hero-zombie", tint: 0xb7f06a },
  "bot-8": { textureKey: "hero-zombie", tint: 0xb7f06a },
  "bot-9": { textureKey: "hero-zombie", tint: 0xb7f06a },
  "bot-10": { textureKey: "hero-zombie", tint: 0xb7f06a },
  "bot-11": { textureKey: "hero-zombie", tint: 0xb7f06a }
};

export const SKIN_VISUALS: Readonly<Record<string, Readonly<HeroVisualDefinition>>> = {
  blue: { textureKey: "hero-player", tint: 0x7ae2ff },
  survivor: { textureKey: "hero-survivor", tint: 0x7dd87d },
  soldier: { textureKey: "hero-soldier", tint: 0xffd36e },
  brown: { textureKey: "hero-brown", tint: 0xff9d7a },
  old: { textureKey: "hero-old", tint: 0xc8b6ff },
  woman: { textureKey: "hero-woman", tint: 0x87f0d6 },
  zombie: { textureKey: "hero-zombie", tint: 0xb7f06a }
};

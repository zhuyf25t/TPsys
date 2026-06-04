import type { ResolvePlayerAbilityTextureKeyInput } from "../objects/PlayerAbilitySceneBridgeObjects";

const PLAYER_ABILITY_FALLBACK_TEXTURE_KEY = "hero-player";

export function resolvePlayerAbilityTextureKey({
  heroId,
  heroViews
}: ResolvePlayerAbilityTextureKeyInput): string {
  return heroViews.get(heroId)?.sprite.texture.key ?? PLAYER_ABILITY_FALLBACK_TEXTURE_KEY;
}

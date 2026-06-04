import type {
  HeroWorldViewFrameLayoutPlan,
  ResolveHeroWorldViewFrameLayoutPlanInput
} from "../objects/HeroWorldViewSyncObjects";

const HERO_NAME_LABEL_OFFSET_Y = -52;
const HERO_HEALTH_BACKGROUND_OFFSET_Y = -36;
const HERO_HEALTH_FILL_OFFSET_X = -24;
const HERO_HEALTH_FILL_OFFSET_Y = -36;
const HERO_ACTION_BACKGROUND_OFFSET_Y = -24;
const HERO_ACTION_FILL_OFFSET_X = -25;
const HERO_ACTION_FILL_OFFSET_Y = -24;
const HERO_ACTION_FILL_WIDTH = 50;

export function resolveHeroWorldViewFrameLayoutPlan({
  displayPosition,
  actionProgress
}: ResolveHeroWorldViewFrameLayoutPlanInput): HeroWorldViewFrameLayoutPlan {
  return {
    spritePosition: displayPosition,
    nameLabelPosition: {
      x: displayPosition.x,
      y: displayPosition.y + HERO_NAME_LABEL_OFFSET_Y
    },
    healthBackgroundPosition: {
      x: displayPosition.x,
      y: displayPosition.y + HERO_HEALTH_BACKGROUND_OFFSET_Y
    },
    healthFillPosition: {
      x: displayPosition.x + HERO_HEALTH_FILL_OFFSET_X,
      y: displayPosition.y + HERO_HEALTH_FILL_OFFSET_Y
    },
    markerPosition: displayPosition,
    actionBar: resolveHeroWorldViewActionBarLayoutPlan({ displayPosition, actionProgress })
  };
}

function resolveHeroWorldViewActionBarLayoutPlan({
  displayPosition,
  actionProgress
}: ResolveHeroWorldViewFrameLayoutPlanInput): HeroWorldViewFrameLayoutPlan["actionBar"] {
  if (!actionProgress.visible) {
    return { visible: false };
  }

  return {
    visible: true,
    visibility: {
      background: true,
      fill: true
    },
    backgroundPosition: {
      x: displayPosition.x,
      y: displayPosition.y + HERO_ACTION_BACKGROUND_OFFSET_Y
    },
    fillPosition: {
      x: displayPosition.x + HERO_ACTION_FILL_OFFSET_X,
      y: displayPosition.y + HERO_ACTION_FILL_OFFSET_Y
    },
    fillWidth: HERO_ACTION_FILL_WIDTH * actionProgress.progress
  };
}

import type { WorldViewIndicatorCreationPlan } from "../objects/WorldViewStateFactoryObjects";

export function resolveWorldViewIndicatorCreationPlan(): WorldViewIndicatorCreationPlan {
  return {
    rangeIndicator: {
      position: { x: 0, y: 0 },
      radius: 1,
      fillColor: 0x69d2ff,
      fillAlpha: 0.05,
      depth: 16,
      visible: false,
      strokeWidth: 2,
      strokeColor: 0x69d2ff,
      strokeAlpha: 0.85
    },
    targetIndicator: {
      position: { x: 0, y: 0 },
      radius: 11,
      fillColor: 0x69d2ff,
      fillAlpha: 0.15,
      depth: 17,
      visible: false,
      strokeWidth: 2,
      strokeColor: 0x69d2ff,
      strokeAlpha: 0.85
    }
  };
}

import type { SlowField, Vec2 } from "../../../../domain/types";
import { appendFreezeField } from "./freezeFieldController";

type FloatingTone = "neutral" | "warning" | "error" | "success";

export interface FreezeFieldSceneBridgeOptions {
  getSlowFields(): readonly SlowField[];
  setSlowFields(fields: SlowField[]): void;
  showFloatingText(position: Vec2, text: string, tone: FloatingTone): void;
}

export class FreezeFieldSceneBridge {
  private sequence: number;

  public constructor(private readonly options: FreezeFieldSceneBridgeOptions) {
    this.sequence = inferNextFreezeFieldSequence(this.options.getSlowFields());
  }

  public addFreezeField(ownerHeroId: string, position: Vec2, radius: number, durationMs: number): void {
    const result = appendFreezeField({
      fields: this.options.getSlowFields(),
      sequence: this.sequence,
      ownerHeroId,
      position,
      radius,
      durationMs
    });

    this.sequence = result.nextSequence;
    this.options.setSlowFields(result.nextFields);
    this.options.showFloatingText(position, "冰雾", "success");
  }
}

function inferNextFreezeFieldSequence(fields: readonly SlowField[]): number {
  let nextSequence = 0;

  for (const field of fields) {
    const match = /^freeze-(\d+)$/.exec(field.fieldId);
    if (!match) {
      continue;
    }

    const numericId = Number(match[1]);
    if (Number.isFinite(numericId)) {
      nextSequence = Math.max(nextSequence, numericId + 1);
    }
  }

  return nextSequence;
}

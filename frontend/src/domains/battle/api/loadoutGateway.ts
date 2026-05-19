import type { SkillKind } from "../objects/types";
import { getCurrentAuthHandle, getCurrentAuthSkin, updateCurrentAuthSkin } from "../../identity/api/authGateway";
import { getRatingEntryByHandle } from "../../governance/api/ratingGateway";

export type SkillSlotKey = "Q" | "E" | "R";
export type LoadoutSkillId = SkillKind;

export interface LoadoutPreset {
  id: string;
  label: string;
  description: string;
  primary: string;
  pickups: string[];
  modeLabel: string;
}

export interface LoadoutSkillOption {
  id: LoadoutSkillId;
  label: string;
  shortLabel: string;
  description: string;
  tone: "cyan" | "gold" | "ice";
}

export interface LoadoutSkillSlot {
  key: SkillSlotKey;
  skillId: LoadoutSkillId;
  label: string;
  shortLabel: string;
  description: string;
  tone: LoadoutSkillOption["tone"];
}

export interface LoadoutSummary {
  handle: string;
  presetId: string;
  presetLabel: string;
  presetDescription: string;
  primary: string;
  pickups: string[];
  skills: string[];
  modeLabel: string;
  rating: number;
  skinId: string;
  skinLabel: string;
  skinImageSrc: string;
}

const LOADOUT_STORAGE_KEY = "slay-demo.loadoutPresetId";
const SKILL_SLOTS_STORAGE_KEY = "slay-demo.loadoutSkillSlots.v1";

const LOADOUT_PRESETS: LoadoutPreset[] = [
  {
    id: "assault",
    label: "压制",
    description: "重火力控制，适合卡点和守线。",
    primary: "轻机枪",
    pickups: ["火箭筒", "护甲包", "治疗包"],
    modeLabel: "控场档"
  },
  {
    id: "mobile",
    label: "突击",
    description: "中距离爆发，适合先手推进。",
    primary: "突击步枪",
    pickups: ["霰弹枪", "加速器", "闪光弹"],
    modeLabel: "推进档"
  },
  {
    id: "skirmish",
    label: "游击",
    description: "高机动近身反打，适合侧切。",
    primary: "冲锋枪",
    pickups: ["护甲片", "治疗包", "火箭筒"],
    modeLabel: "游击档"
  }
];

const SKILL_OPTIONS: LoadoutSkillOption[] = [
  {
    id: "Blink",
    label: "闪现",
    shortLabel: "闪",
    description: "瞬间位移，脱离火线。",
    tone: "cyan"
  },
  {
    id: "Dash",
    label: "冲刺",
    shortLabel: "冲",
    description: "短距离爆发推进。",
    tone: "gold"
  },
  {
    id: "Freeze",
    label: "冰雾",
    shortLabel: "冰",
    description: "区域减速，压制走位。",
    tone: "ice"
  }
];

const DEFAULT_SKILL_SLOTS: Record<SkillSlotKey, LoadoutSkillId> = {
  Q: "Blink",
  E: "Dash",
  R: "Freeze"
};

const DEFAULT_PRESET_ID = LOADOUT_PRESETS[0]?.id ?? "assault";
const SKILL_SLOT_KEYS: SkillSlotKey[] = ["Q", "E", "R"];

type LoadoutListener = () => void;

let currentPresetId = readStoredPresetId();
let currentSkillSlots = readStoredSkillSlots();
let loadoutStateVersion = 0;
const listeners = new Set<LoadoutListener>();

function readStoredPresetId(): string {
  if (typeof window === "undefined") {
    return DEFAULT_PRESET_ID;
  }

  const stored = window.localStorage.getItem(LOADOUT_STORAGE_KEY);
  return LOADOUT_PRESETS.some((preset) => preset.id === stored) ? stored ?? DEFAULT_PRESET_ID : DEFAULT_PRESET_ID;
}

function writeStoredPresetId(presetId: string): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(LOADOUT_STORAGE_KEY, presetId);
}

function readStoredSkillSlots(): Record<SkillSlotKey, LoadoutSkillId> {
  if (typeof window === "undefined") {
    return { ...DEFAULT_SKILL_SLOTS };
  }

  try {
    const parsed = JSON.parse(window.localStorage.getItem(SKILL_SLOTS_STORAGE_KEY) ?? "null") as Partial<
      Record<SkillSlotKey, string>
    > | null;
    if (!parsed) {
      return { ...DEFAULT_SKILL_SLOTS };
    }

    return normalizeSkillSlots(parsed);
  } catch {
    return { ...DEFAULT_SKILL_SLOTS };
  }
}

function writeStoredSkillSlots(slots: Record<SkillSlotKey, LoadoutSkillId>): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(SKILL_SLOTS_STORAGE_KEY, JSON.stringify(slots));
}

function normalizeSkillSlots(slots: Partial<Record<SkillSlotKey, string>>): Record<SkillSlotKey, LoadoutSkillId> {
  const next = { ...DEFAULT_SKILL_SLOTS };
  const used = new Set<LoadoutSkillId>();

  for (const key of SKILL_SLOT_KEYS) {
    const skillId = slots[key];
    if (skillId && isKnownSkill(skillId) && !used.has(skillId)) {
      next[key] = skillId;
      used.add(skillId);
    }
  }

  for (const key of SKILL_SLOT_KEYS) {
    if (!used.has(next[key])) {
      used.add(next[key]);
      continue;
    }

    const replacement = SKILL_OPTIONS.find((option) => !used.has(option.id));
    if (replacement) {
      next[key] = replacement.id;
      used.add(replacement.id);
    }
  }

  return next;
}

function isKnownSkill(skillId: string): skillId is LoadoutSkillId {
  return SKILL_OPTIONS.some((option) => option.id === skillId);
}

function notifyLoadoutChange(): void {
  loadoutStateVersion += 1;
  for (const listener of listeners) {
    listener();
  }
}

function getPresetById(presetId: string): LoadoutPreset {
  return LOADOUT_PRESETS.find((preset) => preset.id === presetId) ?? LOADOUT_PRESETS[0]!;
}

function getSkillOption(skillId: LoadoutSkillId): LoadoutSkillOption {
  return SKILL_OPTIONS.find((option) => option.id === skillId) ?? SKILL_OPTIONS[0]!;
}

function clonePreset(preset: LoadoutPreset): LoadoutPreset {
  return {
    ...preset,
    pickups: [...preset.pickups]
  };
}

function toSkillSlot(key: SkillSlotKey, skillId: LoadoutSkillId): LoadoutSkillSlot {
  const option = getSkillOption(skillId);
  return {
    key,
    skillId,
    label: option.label,
    shortLabel: option.shortLabel,
    description: option.description,
    tone: option.tone
  };
}

export function getLoadoutPresets(): LoadoutPreset[] {
  return LOADOUT_PRESETS.map(clonePreset);
}

export function getCurrentLoadoutPreset(): LoadoutPreset {
  return clonePreset(getPresetById(currentPresetId));
}

export function setLoadoutPreset(presetId: string): boolean {
  if (!LOADOUT_PRESETS.some((preset) => preset.id === presetId) || presetId === currentPresetId) {
    return false;
  }

  currentPresetId = presetId;
  writeStoredPresetId(presetId);
  notifyLoadoutChange();
  return true;
}

export function getLoadoutSkillOptions(): LoadoutSkillOption[] {
  return SKILL_OPTIONS.map((option) => ({ ...option }));
}

export function getSelectedSkillSlots(): LoadoutSkillSlot[] {
  return SKILL_SLOT_KEYS.map((key) => toSkillSlot(key, currentSkillSlots[key]));
}

export function getSelectedSkillBindings(): Record<SkillSlotKey, LoadoutSkillId> {
  return { ...currentSkillSlots };
}

export function setSkillSlot(slotKey: SkillSlotKey, skillId: LoadoutSkillId): boolean {
  if (!isKnownSkill(skillId) || currentSkillSlots[slotKey] === skillId) {
    return false;
  }

  const next = { ...currentSkillSlots };
  const existingSlot = SKILL_SLOT_KEYS.find((key) => next[key] === skillId);

  if (existingSlot) {
    next[existingSlot] = next[slotKey];
  }

  next[slotKey] = skillId;
  currentSkillSlots = next;
  writeStoredSkillSlots(currentSkillSlots);
  notifyLoadoutChange();
  return true;
}

export function swapSkillSlots(firstSlot: SkillSlotKey, secondSlot: SkillSlotKey): boolean {
  if (firstSlot === secondSlot) {
    return false;
  }

  const next = { ...currentSkillSlots };
  const firstSkill = next[firstSlot];
  next[firstSlot] = next[secondSlot];
  next[secondSlot] = firstSkill;
  currentSkillSlots = next;
  writeStoredSkillSlots(currentSkillSlots);
  notifyLoadoutChange();
  return true;
}

export function subscribeLoadoutState(listener: LoadoutListener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getLoadoutStateVersion(): number {
  return loadoutStateVersion;
}

export function getLoadoutSummary(): LoadoutSummary {
  const handle = getCurrentAuthHandle();
  const skin = getCurrentAuthSkin();
  const rating = getRatingEntryByHandle(handle)?.score ?? 1200;
  const preset = getPresetById(currentPresetId);
  const skills = getSelectedSkillSlots().map((slot) => `${slot.key} ${slot.label}`);

  return {
    handle,
    presetId: preset.id,
    presetLabel: preset.label,
    presetDescription: preset.description,
    primary: preset.primary,
    pickups: [...preset.pickups],
    skills,
    modeLabel: preset.modeLabel,
    rating,
    skinId: skin.id,
    skinLabel: skin.label,
    skinImageSrc: skin.imageSrc
  };
}

export function setLoadoutSkin(skinId: string): boolean {
  const result = updateCurrentAuthSkin(skinId);
  if (result.ok) {
    notifyLoadoutChange();
  }

  return result.ok;
}

import type { BattleRuntimeMountRoots } from "./objects/BattleRuntimeDomObjects";

export function prepareBattleRuntimeMountRoots({ mountNode, hudRoot }: BattleRuntimeMountRoots): void {
  mountNode.replaceChildren();
  hudRoot.replaceChildren();
  hudRoot.id = "hud-root";
}

export function clearBattleRuntimeMountRoots({ mountNode, hudRoot }: BattleRuntimeMountRoots): void {
  mountNode.replaceChildren();
  hudRoot.replaceChildren();
}

export function installBattleRuntimeContextMenuLock(): () => void {
  const listener = (event: MouseEvent): void => {
    event.preventDefault();
  };

  window.addEventListener("contextmenu", listener);

  return () => {
    window.removeEventListener("contextmenu", listener);
  };
}

export function captureBattleRuntimeThumbnail(mountNode: HTMLElement): string | null {
  const canvas = mountNode.querySelector("canvas");
  if (!(canvas instanceof HTMLCanvasElement)) {
    return null;
  }

  try {
    return canvas.toDataURL("image/png");
  } catch {
    return null;
  }
}

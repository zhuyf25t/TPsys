export interface WheelSwitchDetail {
  deltaY: number;
}

export type WheelSwitchBridge = () => void;

export function installWheelSwitchBridge(): WheelSwitchBridge {
  const listener = (event: WheelEvent): void => {
    if (event.ctrlKey) {
      event.preventDefault();
      return;
    }

    event.preventDefault();
    window.dispatchEvent(
      new CustomEvent<WheelSwitchDetail>("game-wheel-switch", {
        detail: { deltaY: event.deltaY }
      })
    );
  };

  window.addEventListener("wheel", listener, { passive: false });

  return () => {
    window.removeEventListener("wheel", listener);
  };
}

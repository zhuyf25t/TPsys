export interface WheelSwitchDetail {
  deltaY: number;
}

export type WheelSwitchBridge = () => void;

/** 中文名：installwheelswitchbridge（installWheelSwitchBridge）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

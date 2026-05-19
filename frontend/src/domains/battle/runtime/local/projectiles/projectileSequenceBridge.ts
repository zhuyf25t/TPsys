export interface ProjectileSequenceBridge {
  getSequence(): number;
  setSequence(next: number): void;
}

/** 中文名：创建投射物sequencebridge（createProjectileSequenceBridge）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createProjectileSequenceBridge(initialSequence = 0): ProjectileSequenceBridge {
  let sequence = initialSequence;

  return {
    getSequence: () => sequence,
    setSequence: (next) => {
      sequence = next;
    }
  };
}

export interface ProjectileSequenceBridge {
  getSequence(): number;
  setSequence(next: number): void;
}

export function createProjectileSequenceBridge(initialSequence = 0): ProjectileSequenceBridge {
  let sequence = initialSequence;

  return {
    getSequence: () => sequence,
    setSequence: (next) => {
      sequence = next;
    }
  };
}

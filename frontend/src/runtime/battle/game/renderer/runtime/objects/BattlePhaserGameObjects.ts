import type Phaser from "phaser";

export interface CreateBattlePhaserGameInput {
  mountNode: HTMLElement;
  scene: Phaser.Scene;
}

export interface BattlePhaserGameViewportInput {
  mountWidth: number;
  mountHeight: number;
  windowWidth: number;
  windowHeight: number;
  fallbackWidth: number;
  fallbackHeight: number;
}

export interface BattlePhaserGameViewportSize {
  width: number;
  height: number;
}

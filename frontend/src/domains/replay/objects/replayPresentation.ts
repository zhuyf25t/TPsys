interface ReplayTitleSource {
  title: string;
  finishedAtLabel: string;
  resultLabel: string;
}

/** 中文名：获取回放展示title（getReplayDisplayTitle）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function getReplayDisplayTitle(replay: ReplayTitleSource): string {
  const withoutTime = replay.title
    .split(replay.finishedAtLabel)
    .join("")
    .replace(/^[\s·|-]+|[\s·|-]+$/g, "")
    .trim();

  return withoutTime || replay.resultLabel || replay.title;
}

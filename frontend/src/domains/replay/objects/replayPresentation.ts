interface ReplayTitleSource {
  title: string;
  finishedAtLabel: string;
  resultLabel: string;
}

export function getReplayDisplayTitle(replay: ReplayTitleSource): string {
  const withoutTime = replay.title
    .split(replay.finishedAtLabel)
    .join("")
    .replace(/^[\s·|-]+|[\s·|-]+$/g, "")
    .trim();

  return withoutTime || replay.resultLabel || replay.title;
}

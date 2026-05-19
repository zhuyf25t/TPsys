import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

const repoRoot = process.cwd();

const targetRoots = [
  path.join(repoRoot, "backend", "src", "main", "scala", "slaydemo", "backend", "battle", "objects"),
  path.join(repoRoot, "backend", "src", "main", "scala", "slaydemo", "backend", "battle", "services"),
  path.join(repoRoot, "frontend", "src", "shared"),
  path.join(repoRoot, "frontend", "src", "domains"),
];

const scalaDefPattern = /^\s*(?:override\s+)?def\s+([A-Za-z0-9_]+)/;
const tsExportFunctionPattern = /^\s*export\s+function\s+([A-Za-z0-9_]+)/;
const tsExportConstFunctionPattern = /^\s*export\s+const\s+([A-Za-z0-9_]+)\s*=\s*(?:async\s*)?\(/;

function walkFiles(root) {
  if (!statSync(root, { throwIfNoEntry: false })?.isDirectory()) {
    return [];
  }
  return readdirSync(root, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(root, entry.name);
    if (entry.isDirectory()) {
      return walkFiles(absolute);
    }
    if (entry.isFile() && /\.(scala|tsx?|jsx?)$/.test(entry.name)) {
      return [absolute];
    }
    return [];
  });
}

function hasChineseFunctionComment(lines, lineIndex) {
  let inspected = 0;
  let seenName = false;
  let seenGameContext = false;
  for (let index = lineIndex - 1; index >= 0 && inspected < 5; index -= 1) {
    const line = lines[index].trim();
    if (line.length === 0) {
      continue;
    }
    inspected += 1;
    seenName ||= line.includes("中文名：");
    seenGameContext ||= line.includes("游戏职责：") || line.includes("游戏视线：");
    if (seenName && seenGameContext) {
      return true;
    }
    if (!line.startsWith("*") && !line.startsWith("/**") && !line.startsWith("//")) {
      return false;
    }
  }
  return false;
}

function targetNameForLine(filePath, line) {
  if (filePath.endsWith(".scala")) {
    return scalaDefPattern.exec(line)?.[1] ?? null;
  }
  return tsExportFunctionPattern.exec(line)?.[1] ?? tsExportConstFunctionPattern.exec(line)?.[1] ?? null;
}

const missing = [];

for (const filePath of targetRoots.flatMap(walkFiles)) {
  const relativePath = path.relative(repoRoot, filePath);
  const lines = readFileSync(filePath, "utf8").split(/\r?\n/);
  lines.forEach((line, index) => {
    const targetName = targetNameForLine(filePath, line);
    if (targetName && !hasChineseFunctionComment(lines, index)) {
      missing.push(`${relativePath}:${index + 1} ${targetName}`);
    }
  });
}

if (missing.length > 0) {
  console.error(`Missing Chinese function comments: ${missing.length}`);
  for (const entry of missing.slice(0, 80)) {
    console.error(`- ${entry}`);
  }
  if (missing.length > 80) {
    console.error(`... ${missing.length - 80} more`);
  }
  process.exit(1);
}

console.log("Chinese function comment audit passed.");

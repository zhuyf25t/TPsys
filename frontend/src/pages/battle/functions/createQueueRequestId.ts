export function createQueueRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `queue-${crypto.randomUUID()}`;
  }

  return `queue-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

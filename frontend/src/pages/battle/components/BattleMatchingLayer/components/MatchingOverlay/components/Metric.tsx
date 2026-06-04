interface MetricProps {
  label: string;
  value: string;
}

export function Metric({ label, value }: MetricProps) {
  return (
    <article className="rounded border border-white/10 bg-white/[0.04] px-4 py-3">
      <small className="text-xs font-bold text-slate-400">{label}</small>
      <strong className="mt-1 block text-lg text-white">{value}</strong>
    </article>
  );
}

'use client';

import React, { useState } from 'react';
import { formatInr } from '@/lib/domain/ist';

export interface BarDatum {
  label: string;
  revenue: number;
  orders?: number;
}

interface SimpleBarChartProps {
  data: BarDatum[];
  isDark?: boolean;
  height?: number;
}

export default function SimpleBarChart({
  data,
  isDark = true,
  height = 190,
}: SimpleBarChartProps) {
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  if (!data || data.length === 0) {
    return (
      <div
        className={`flex items-center justify-center rounded-xl border text-xs ${
          isDark
            ? 'border-zinc-800 bg-zinc-950/40 text-zinc-500'
            : 'border-zinc-200 bg-zinc-50 text-zinc-400'
        }`}
        style={{ height }}
      >
        No data in selected period
      </div>
    );
  }

  const maxVal = Math.max(100, ...data.map((d) => d.revenue));
  const hovered = hoverIdx !== null ? data[hoverIdx] : null;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-xs min-h-[20px]">
        {hovered ? (
          <div className="flex items-center gap-2 font-bold">
            <span className="text-amber-500">{hovered.label}:</span>
            <span>{formatInr(hovered.revenue)}</span>
            {hovered.orders !== undefined && (
              <span className={isDark ? 'text-zinc-400' : 'text-zinc-500'}>
                ({hovered.orders} orders)
              </span>
            )}
          </div>
        ) : (
          <span className={isDark ? 'text-zinc-500' : 'text-zinc-400'}>
            Hover or tap bars to inspect revenue
          </span>
        )}
        <span className={`font-mono text-[11px] ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
          Peak: {formatInr(maxVal)}
        </span>
      </div>

      <div
        className="flex items-end gap-1.5 pt-4 pb-1 px-1 overflow-x-auto"
        style={{ height }}
      >
        {data.map((d, idx) => {
          const pct = Math.max(4, Math.round((d.revenue / maxVal) * 100));
          const isActive = hoverIdx === idx;
          return (
            <div
              key={`${d.label}-${idx}`}
              onMouseEnter={() => setHoverIdx(idx)}
              onMouseLeave={() => setHoverIdx(null)}
              onClick={() => setHoverIdx(idx)}
              className="flex-1 min-w-[20px] h-full flex flex-col items-center justify-end gap-1 cursor-pointer group"
            >
              <div className="w-full flex-1 flex items-end justify-center">
                <div
                  style={{ height: `${d.revenue === 0 ? 4 : pct}%` }}
                  className={`w-full max-w-[34px] rounded-t-md transition-all duration-150 ${
                    d.revenue === 0
                      ? isDark
                        ? 'bg-zinc-800/70'
                        : 'bg-zinc-200'
                      : isActive
                        ? 'bg-amber-400 shadow-lg shadow-amber-500/25'
                        : 'bg-amber-500/85 group-hover:bg-amber-400'
                  }`}
                />
              </div>
              <span
                className={`text-[10px] font-semibold truncate max-w-full ${
                  isActive
                    ? 'text-amber-500 font-bold'
                    : isDark
                      ? 'text-zinc-500'
                      : 'text-zinc-500'
                }`}
              >
                {d.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

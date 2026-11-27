'use client';

import React from 'react';

interface StatCardProps {
  label: string;
  value: string;
  subtitle?: string;
  accent?: 'amber' | 'emerald' | 'rose' | 'blue' | 'purple' | 'zinc';
  icon?: React.ReactNode;
  isDark?: boolean;
  onClick?: () => void;
}

export default function StatCard({
  label,
  value,
  subtitle,
  accent = 'amber',
  icon,
  isDark = true,
  onClick,
}: StatCardProps) {
  const accentStyles: Record<string, string> = {
    amber: 'text-amber-500 bg-amber-500/10 border-amber-500/20',
    emerald: 'text-emerald-500 bg-emerald-500/10 border-emerald-500/20',
    rose: 'text-rose-500 bg-rose-500/10 border-rose-500/20',
    blue: 'text-blue-500 bg-blue-500/10 border-blue-500/20',
    purple: 'text-purple-500 bg-purple-500/10 border-purple-500/20',
    zinc: isDark
      ? 'text-zinc-300 bg-zinc-800 border-zinc-700'
      : 'text-zinc-700 bg-zinc-100 border-zinc-200',
  };

  return (
    <div
      onClick={onClick}
      className={`rounded-2xl border p-4 transition-all ${
        onClick ? 'cursor-pointer hover:border-amber-500/50 active:scale-[0.99]' : ''
      } ${
        isDark
          ? 'border-zinc-800 bg-zinc-900/90 text-white'
          : 'border-[#E4E4E7] bg-white text-[#09090B] shadow-sm'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <span
            className={`text-[10px] font-black uppercase tracking-wider block ${
              isDark ? 'text-zinc-400' : 'text-zinc-500'
            }`}
          >
            {label}
          </span>
          <p className="mt-1.5 text-2xl font-black tracking-tight truncate">
            {value}
          </p>
          {subtitle && (
            <p
              className={`mt-1 text-[11px] font-medium truncate ${
                isDark ? 'text-zinc-400' : 'text-zinc-500'
              }`}
            >
              {subtitle}
            </p>
          )}
        </div>
        {icon && (
          <div
            className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border ${accentStyles[accent]}`}
          >
            {icon}
          </div>
        )}
      </div>
    </div>
  );
}

'use client';

import React from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { formatInr } from '@/lib/domain/ist';

interface TooltipEntry {
  name?: string;
  value?: number;
  color?: string;
}

function ChartTooltip({
  active,
  payload,
  label,
  isCurrency = true,
  unitSuffix = '',
}: {
  active?: boolean;
  payload?: TooltipEntry[];
  label?: string;
  isCurrency?: boolean;
  unitSuffix?: string;
}) {
  if (!active || !payload || payload.length === 0) return null;
  return (
    <div className="rounded-xl border border-border bg-overlay px-3 py-2 text-xs shadow-overlay">
      {label ? <p className="font-semibold text-foreground mb-1">{label}</p> : null}
      {payload.map((entry, idx) => (
        <div key={idx} className="flex items-center justify-between gap-4 tabular-nums">
          <span className="text-muted">{entry.name}:</span>
          <span className="font-bold text-foreground">
            {isCurrency
              ? formatInr(Number(entry.value || 0))
              : `${Number(entry.value || 0).toLocaleString('en-IN')}${unitSuffix}`}
          </span>
        </div>
      ))}
    </div>
  );
}

export function RevenueAreaChart({
  data,
  metric = 'revenue',
  height = 260,
}: {
  data: Array<{ label: string; revenue: number; orders: number }>;
  metric?: 'revenue' | 'orders';
  height?: number;
}) {
  if (!data || data.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center text-xs text-muted">
        No order activity in selected range
      </div>
    );
  }

  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="filtrAccentFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="var(--accent)" stopOpacity={0.38} />
              <stop offset="95%" stopColor="var(--accent)" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="var(--separator)" strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fill: 'var(--muted)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fill: 'var(--muted)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            width={52}
            tickFormatter={(v) =>
              metric === 'revenue' ? `₹${v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v}` : String(v)
            }
          />
          <Tooltip
            content={
              <ChartTooltip
                isCurrency={metric === 'revenue'}
                unitSuffix={metric === 'orders' ? ' orders' : ''}
              />
            }
          />
          <Area
            type="monotone"
            dataKey={metric}
            name={metric === 'revenue' ? 'Revenue' : 'Orders'}
            stroke="var(--accent)"
            strokeWidth={2.25}
            fill="url(#filtrAccentFill)"
            activeDot={{ r: 5 }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function PaymentDonutChart({
  upiRevenue,
  cashRevenue,
  unpaidRevenue,
  height = 175,
}: {
  upiRevenue: number;
  cashRevenue: number;
  unpaidRevenue: number;
  height?: number;
}) {
  const total = upiRevenue + cashRevenue + unpaidRevenue;
  const data = [
    { name: 'UPI', value: upiRevenue, color: 'var(--success)' },
    { name: 'Cash', value: cashRevenue, color: 'var(--accent)' },
    { name: 'Unpaid', value: unpaidRevenue, color: 'var(--danger)' },
  ].filter((d) => d.value > 0);

  if (total <= 0) {
    return (
      <div className="flex h-36 items-center justify-center text-xs text-muted">
        No billing volume yet
      </div>
    );
  }

  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="50%"
            innerRadius={48}
            outerRadius={70}
            paddingAngle={3}
            stroke="none"
          >
            {data.map((entry, idx) => (
              <Cell key={idx} fill={entry.color} />
            ))}
          </Pie>
          <Tooltip content={<ChartTooltip isCurrency />} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

export function CategoryBarChart({
  data,
  height = 240,
}: {
  data: Array<{ category: string; revenue: number; qty: number }>;
  height?: number;
}) {
  if (!data || data.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center text-xs text-muted">
        No category sales yet
      </div>
    );
  }

  const sliced = data.slice(0, 7);
  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={sliced}
          layout="vertical"
          margin={{ top: 4, right: 14, left: 4, bottom: 0 }}
        >
          <CartesianGrid stroke="var(--separator)" strokeDasharray="3 3" horizontal={false} />
          <XAxis
            type="number"
            tick={{ fill: 'var(--muted)', fontSize: 10 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => `₹${v >= 1000 ? `${(v / 1000).toFixed(0)}k` : v}`}
          />
          <YAxis
            type="category"
            dataKey="category"
            width={110}
            tick={{ fill: 'var(--foreground)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<ChartTooltip isCurrency />} />
          <Bar
            dataKey="revenue"
            name="Revenue"
            fill="var(--accent)"
            radius={[0, 6, 6, 0]}
            barSize={16}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function SkuPriceLineChart({
  data,
  height = 220,
}: {
  data: Array<{ label: string; price: number }>;
  height?: number;
}) {
  if (!data || data.length === 0) {
    return (
      <div className="flex h-40 items-center justify-center text-xs text-muted">
        No price points available
      </div>
    );
  }

  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 10, right: 14, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="var(--separator)" strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fill: 'var(--muted)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            tick={{ fill: 'var(--muted)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            width={48}
            tickFormatter={(v) => `₹${v}`}
          />
          <Tooltip content={<ChartTooltip isCurrency />} />
          <Line
            type="monotone"
            dataKey="price"
            name="Normalized Rate"
            stroke="var(--accent)"
            strokeWidth={2.5}
            dot={{ r: 4, fill: 'var(--accent)' }}
            activeDot={{ r: 6 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function UsageBarChart({
  data,
  height = 280,
}: {
  data: Array<{ name: string; cost: number }>;
  height?: number;
}) {
  if (!data || data.length === 0) {
    return (
      <div className="flex h-48 items-center justify-center text-xs text-muted">
        No usage cost data for chart
      </div>
    );
  }

  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data.slice(0, 10)}
          layout="vertical"
          margin={{ top: 4, right: 16, left: 6, bottom: 0 }}
        >
          <CartesianGrid stroke="var(--separator)" strokeDasharray="3 3" horizontal={false} />
          <XAxis
            type="number"
            tick={{ fill: 'var(--muted)', fontSize: 10 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => `₹${v}`}
          />
          <YAxis
            type="category"
            dataKey="name"
            width={120}
            tick={{ fill: 'var(--foreground)', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<ChartTooltip isCurrency />} />
          <Bar
            dataKey="cost"
            name="Est. Ingredient Cost"
            fill="var(--warning)"
            radius={[0, 6, 6, 0]}
            barSize={16}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

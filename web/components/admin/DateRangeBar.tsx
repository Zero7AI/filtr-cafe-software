'use client';

import React from 'react';
import { Input, Tabs } from '@heroui/react';
import { DateRangePreset, formatMonthDisplay, getNextMonthKey, getPreviousMonthKey } from '@/lib/domain/ist';
import { Button } from '@heroui/react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface DateRangeBarProps {
  range: DateRangePreset;
  onRangeChange: (next: DateRangePreset) => void;
  presets?: Array<[DateRangePreset, string]>;
  monthKey?: string;
  onMonthKeyChange?: (next: string) => void;
  customStart?: string;
  customEnd?: string;
  onCustomStartChange?: (next: string) => void;
  onCustomEndChange?: (next: string) => void;
  rightSlot?: React.ReactNode;
}

const DEFAULT_PRESETS: Array<[DateRangePreset, string]> = [
  ['TODAY', 'Today'],
  ['YESTERDAY', 'Yesterday'],
  ['LAST_7_DAYS', '7 Days'],
  ['THIS_MONTH', 'This Month'],
  ['MONTH', 'Month'],
  ['CUSTOM', 'Custom'],
];

export default function DateRangeBar({
  range,
  onRangeChange,
  presets = DEFAULT_PRESETS,
  monthKey,
  onMonthKeyChange,
  customStart = '',
  customEnd = '',
  onCustomStartChange,
  onCustomEndChange,
  rightSlot,
}: DateRangeBarProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap items-center gap-2.5">
        <Tabs
          selectedKey={range}
          onSelectionChange={(k) => onRangeChange(String(k) as DateRangePreset)}
          className="w-auto"
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="Date range selector">
              {presets.map(([key, label]) => (
                <Tabs.Tab key={key} id={key} className="text-xs px-3.5">
                  {label}
                  <Tabs.Indicator />
                </Tabs.Tab>
              ))}
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        {range === 'MONTH' && monthKey && onMonthKeyChange && (
          <div className="flex items-center gap-1 rounded-xl border border-border bg-surface px-1.5 py-1">
            <Button
              isIconOnly
              size="sm"
              variant="ghost"
              onPress={() => onMonthKeyChange(getPreviousMonthKey(monthKey))}
              aria-label="Previous month"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </Button>
            <span className="text-xs font-semibold px-2 tabular-nums">
              {formatMonthDisplay(monthKey)}
            </span>
            <Button
              isIconOnly
              size="sm"
              variant="ghost"
              onPress={() => onMonthKeyChange(getNextMonthKey(monthKey))}
              aria-label="Next month"
            >
              <ChevronRight className="h-3.5 w-3.5" />
            </Button>
          </div>
        )}

        {range === 'CUSTOM' && onCustomStartChange && onCustomEndChange && (
          <div className="flex items-center gap-1.5">
            <Input
              type="date"
              variant="secondary"
              value={customStart}
              onChange={(e) => onCustomStartChange(e.target.value)}
              aria-label="Start date"
              className="text-xs h-9"
            />
            <span className="text-xs text-muted">to</span>
            <Input
              type="date"
              variant="secondary"
              value={customEnd}
              onChange={(e) => onCustomEndChange(e.target.value)}
              aria-label="End date"
              className="text-xs h-9"
            />
          </div>
        )}
      </div>

      {rightSlot ? <div className="flex flex-wrap items-center gap-2">{rightSlot}</div> : null}
    </div>
  );
}

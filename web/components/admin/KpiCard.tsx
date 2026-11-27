'use client';

import React from 'react';
import { Card, Chip } from '@heroui/react';

export interface KpiCardProps {
  label: string;
  value: string;
  subtitle?: string;
  badgeText?: string;
  badgeColor?: 'default' | 'accent' | 'success' | 'warning' | 'danger';
  icon?: React.ReactNode;
  variant?: 'default' | 'secondary' | 'tertiary';
}

export default function KpiCard({
  label,
  value,
  subtitle,
  badgeText,
  badgeColor = 'default',
  icon,
  variant = 'default',
}: KpiCardProps) {
  return (
    <Card variant={variant} className="border border-border/80">
      <Card.Content className="p-4 flex flex-col gap-2">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-medium text-muted truncate">{label}</span>
          {icon ? (
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-accent">
              {icon}
            </div>
          ) : null}
        </div>

        <div className="flex items-baseline justify-between gap-2">
          <span className="text-2xl font-bold tracking-tight tabular-nums text-foreground">
            {value}
          </span>
          {badgeText ? (
            <Chip size="sm" variant="soft" color={badgeColor} className="shrink-0">
              {badgeText}
            </Chip>
          ) : null}
        </div>

        {subtitle ? (
          <p className="text-xs text-muted truncate">{subtitle}</p>
        ) : null}
      </Card.Content>
    </Card>
  );
}

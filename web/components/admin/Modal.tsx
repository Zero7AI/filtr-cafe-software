'use client';

import React from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  maxWidthClass?: string;
  isDark?: boolean;
  children: React.ReactNode;
}

export default function Modal({
  open,
  onClose,
  title,
  subtitle,
  maxWidthClass = 'max-w-lg',
  isDark = true,
  children,
}: ModalProps) {
  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-3 sm:p-5 overflow-y-auto"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={`w-full ${maxWidthClass} rounded-2xl border shadow-2xl my-auto max-h-[90vh] flex flex-col ${
          isDark
            ? 'border-zinc-800 bg-zinc-950 text-white'
            : 'border-[#E4E4E7] bg-white text-[#09090B]'
        }`}
      >
        <div
          className={`flex items-center justify-between px-5 py-4 border-b shrink-0 ${
            isDark ? 'border-zinc-800 bg-zinc-900/60' : 'border-[#E4E4E7] bg-zinc-50'
          }`}
        >
          <div>
            <h3 className="text-base font-black">{title}</h3>
            {subtitle && (
              <p className={`text-xs ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                {subtitle}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className={`rounded-xl p-1.5 transition ${
              isDark
                ? 'text-zinc-400 hover:bg-zinc-800 hover:text-white'
                : 'text-zinc-500 hover:bg-zinc-200 hover:text-zinc-900'
            }`}
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-5">{children}</div>
      </div>
    </div>
  );
}

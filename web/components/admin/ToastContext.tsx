'use client';

import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

interface ToastItem {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface ToastContextValue {
  pushToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}

const ToastContext = createContext<ToastContextValue>({ pushToast: () => {} });

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const pushToast = useCallback(
    (message: string, type: 'success' | 'error' | 'info' = 'info') => {
      const id = Date.now() + Math.floor(Math.random() * 1000);
      setToasts((prev) => [...prev.slice(-4), { id, message, type }]);
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, 4200);
    },
    []
  );

  const dismiss = (id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  return (
    <ToastContext.Provider value={{ pushToast }}>
      {children}
      <div
        className="fixed bottom-4 right-4 z-[90] flex flex-col gap-2 max-w-sm w-full px-3 pointer-events-none no-print"
        role="region"
        aria-live="polite"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`pointer-events-auto flex items-center justify-between gap-3 rounded-2xl border px-4 py-3 text-xs font-semibold shadow-overlay backdrop-blur-md transition-all ${
              t.type === 'success'
                ? 'border-success/40 bg-surface text-foreground'
                : t.type === 'error'
                  ? 'border-danger/40 bg-surface text-foreground'
                  : 'border-border bg-surface text-foreground'
            }`}
          >
            <div className="flex items-center gap-2.5 min-w-0">
              {t.type === 'success' ? (
                <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
              ) : t.type === 'error' ? (
                <AlertCircle className="h-4 w-4 text-danger shrink-0" />
              ) : (
                <Info className="h-4 w-4 text-accent shrink-0" />
              )}
              <span className="truncate">{t.message}</span>
            </div>
            <button
              type="button"
              onClick={() => dismiss(t.id)}
              aria-label="Dismiss notification"
              className="text-muted hover:text-foreground transition shrink-0"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

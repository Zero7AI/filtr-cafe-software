'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Coffee,
  LayoutDashboard,
  ClipboardList,
  Flame,
  TrendingUp,
  Package,
  Refrigerator,
  UtensilsCrossed,
  Users,
  Contact,
  Settings,
  LogOut,
  BarChart3,
  Menu,
  X,
  Sun,
  Moon,
  RefreshCw,
  Store,
  Lock,
  Eye,
  EyeOff,
  ShieldCheck,
} from 'lucide-react';
import { ToastProvider } from './ToastContext';

interface AdminContextValue {
  theme: 'dark' | 'light';
  isDark: boolean;
  operatorName: string;
  toggleTheme: () => void;
  refreshTick: number;
  triggerRefresh: () => void;
}

const AdminContext = createContext<AdminContextValue>({
  theme: 'dark',
  isDark: true,
  operatorName: 'Admin',
  toggleTheme: () => {},
  refreshTick: 0,
  triggerRefresh: () => {},
});

export function useAdminContext() {
  return useContext(AdminContext);
}

interface NavGroup {
  title: string;
  items: Array<{
    href: string;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
  }>;
}

const NAV_GROUPS: NavGroup[] = [
  {
    title: 'Live Operations',
    items: [
      { href: '/admin', label: 'Overview', icon: LayoutDashboard },
      { href: '/admin/orders', label: 'Orders Ledger', icon: ClipboardList },
      { href: '/admin/kds', label: 'Live KDS Queue', icon: Flame },
    ],
  },
  {
    title: 'Finance & Inventory',
    items: [
      { href: '/admin/pnl', label: 'Profit & Loss', icon: TrendingUp },
      { href: '/admin/inventory', label: 'Purchase Tracking', icon: Package },
      { href: '/admin/usage', label: 'Inventory Usage', icon: BarChart3 },
      { href: '/admin/kitchen', label: 'Kitchen Inventory', icon: Refrigerator },
    ],
  },
  {
    title: 'Catalog & Team',
    items: [
      { href: '/admin/menu', label: 'Menu & Recipes', icon: UtensilsCrossed },
      { href: '/admin/team', label: 'Attendance & Checklists', icon: Users },
      { href: '/admin/customers', label: 'Customers CRM', icon: Contact },
    ],
  },
  {
    title: 'System',
    items: [
      { href: '/admin/settings', label: 'Devices & Settings', icon: Settings },
    ],
  },
];

function AdminSidebar({
  pathname,
  isDark,
  operatorName,
  onCloseMobile,
  onLogout,
}: {
  pathname: string;
  isDark: boolean;
  operatorName: string;
  onCloseMobile: () => void;
  onLogout: () => void;
}) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between px-4 py-4 border-b border-zinc-200 dark:border-zinc-800">
        <Link href="/admin" className="flex items-center gap-2.5">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500 text-zinc-950 shadow-md shadow-amber-500/20">
            <Coffee className="h-5 w-5 stroke-[2.5]" />
          </div>
          <div>
            <span className="text-base font-black tracking-tight block leading-none">
              FILTR <span className="text-amber-500">ADMIN</span>
            </span>
            <span className={`text-[10px] font-semibold ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Command Center
            </span>
          </div>
        </Link>
        <button
          type="button"
          onClick={onCloseMobile}
          className="lg:hidden rounded-lg p-1.5 text-zinc-400 hover:text-white"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-5">
        {NAV_GROUPS.map((group) => (
          <div key={group.title}>
            <p className="px-3 mb-1.5 text-[10px] font-black uppercase tracking-widest text-amber-500/90">
              {group.title}
            </p>
            <div className="space-y-1">
              {group.items.map((item) => {
                const Icon = item.icon;
                const active = pathname === item.href;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={onCloseMobile}
                    className={`flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-xs font-bold transition ${
                      active
                        ? 'bg-amber-500 text-zinc-950 shadow-md shadow-amber-500/20'
                        : isDark
                          ? 'text-zinc-400 hover:bg-zinc-900 hover:text-white'
                          : 'text-zinc-600 hover:bg-zinc-100 hover:text-zinc-950'
                    }`}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span>{item.label}</span>
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="p-3 border-t border-zinc-200 dark:border-zinc-800 space-y-1.5">
        <Link
          href="/"
          className={`flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-bold border transition ${
            isDark
              ? 'border-zinc-800 bg-zinc-900/70 text-zinc-300 hover:bg-zinc-800 hover:text-white'
              : 'border-zinc-200 bg-zinc-50 text-zinc-700 hover:bg-zinc-100'
          }`}
        >
          <Store className="h-4 w-4 text-amber-500" />
          <span>Counter POS Mode</span>
        </Link>

        <button
          type="button"
          onClick={onLogout}
          className={`w-full flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-bold transition ${
            isDark
              ? 'text-rose-400 hover:bg-rose-500/10'
              : 'text-rose-600 hover:bg-rose-50'
          }`}
        >
          <LogOut className="h-4 w-4" />
          <span>Lock Admin ({operatorName})</span>
        </button>
      </div>
    </div>
  );
}

export default function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [authState, setAuthState] = useState<'loading' | 'unauthenticated' | 'authenticated'>('loading');
  const [operatorName, setOperatorName] = useState('Web Admin');
  const [passcode, setPasscode] = useState('');
  const [loginOperator, setLoginOperator] = useState('');
  const [showPasscode, setShowPasscode] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [theme, setTheme] = useState<'dark' | 'light'>('dark');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);

  const applyTheme = useCallback((next: 'dark' | 'light') => {
    setTheme(next);
    if (typeof document !== 'undefined') {
      const root = document.documentElement;
      if (next === 'dark') {
        root.classList.add('dark');
        root.classList.remove('light');
        document.body.style.backgroundColor = '#09090b';
        document.body.style.color = '#ffffff';
      } else {
        root.classList.remove('dark');
        root.classList.add('light');
        document.body.style.backgroundColor = '#fafafa';
        document.body.style.color = '#09090b';
      }
    }
    try {
      localStorage.setItem('filtr_pos_theme', next);
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(() => {
      try {
        const saved = localStorage.getItem('filtr_pos_theme');
        applyTheme(saved === 'light' ? 'light' : 'dark');
      } catch {
        applyTheme('dark');
      }
    });

    fetch('/api/admin/auth', { cache: 'no-store' })
      .then((r) => r.json())
      .then((data) => {
        if (data?.authenticated) {
          setOperatorName(data.operatorName || 'Web Admin');
          setAuthState('authenticated');
        } else {
          setAuthState('unauthenticated');
        }
      })
      .catch(() => setAuthState('unauthenticated'));
  }, [applyTheme]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!passcode.trim() || submitting) return;
    setSubmitting(true);
    setLoginError('');
    try {
      const res = await fetch('/api/admin/auth', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          passcode: passcode.trim(),
          operatorName: loginOperator.trim() || 'Web Admin',
        }),
      });
      const data = await res.json();
      if (!res.ok || !data?.authenticated) {
        setLoginError(data?.error || 'Incorrect Admin Passcode');
      } else {
        setOperatorName(data.operatorName || 'Web Admin');
        setPasscode('');
        setAuthState('authenticated');
      }
    } catch {
      setLoginError('Network error verifying passcode');
    } finally {
      setSubmitting(false);
    }
  };

  const handleLogout = async () => {
    await fetch('/api/admin/auth', { method: 'DELETE' }).catch(() => {});
    setAuthState('unauthenticated');
  };

  const isDark = theme === 'dark';

  if (authState === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-zinc-950 text-amber-500">
        <div className="flex flex-col items-center gap-3">
          <div className="h-9 w-9 animate-spin rounded-full border-2 border-amber-500 border-t-transparent" />
          <span className="text-xs font-bold tracking-wider uppercase text-zinc-400">
            Loading FILTR Admin...
          </span>
        </div>
      </div>
    );
  }

  if (authState === 'unauthenticated') {
    return (
      <div
        className={`min-h-screen flex items-center justify-center p-4 select-text ${
          isDark ? 'bg-zinc-950 text-white' : 'bg-[#FAFAFA] text-[#09090B]'
        }`}
      >
        <button
          type="button"
          onClick={() => applyTheme(isDark ? 'light' : 'dark')}
          className={`fixed top-5 right-5 flex h-10 w-10 items-center justify-center rounded-xl border transition ${
            isDark
              ? 'border-zinc-800 bg-zinc-900 text-amber-400 hover:bg-zinc-800'
              : 'border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-100 shadow-sm'
          }`}
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </button>

        <div
          className={`w-full max-w-md rounded-3xl border p-8 shadow-2xl ${
            isDark
              ? 'border-zinc-800 bg-zinc-900 text-white'
              : 'border-[#E4E4E7] bg-white text-[#09090B]'
          }`}
        >
          <div className="flex flex-col items-center text-center mb-6">
            <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-amber-500 text-zinc-950 shadow-lg shadow-amber-500/20">
              <ShieldCheck className="h-7 w-7 stroke-[2.5]" />
            </div>
            <h1 className="text-2xl font-black tracking-tight">
              FILTR <span className="text-amber-500">ADMIN</span>
            </h1>
            <p className={`text-xs mt-1 ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Store Command Center • Synced with Android POS
            </p>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className={`block text-[11px] font-bold uppercase tracking-wider mb-1.5 ${isDark ? 'text-zinc-400' : 'text-zinc-600'}`}>
                Owner / Manager Name (Optional)
              </label>
              <input
                type="text"
                value={loginOperator}
                onChange={(e) => setLoginOperator(e.target.value)}
                placeholder="e.g. Somesh (Owner)"
                className={`w-full rounded-xl border px-3.5 py-2.5 text-sm focus:outline-none focus:border-amber-500 ${
                  isDark
                    ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-600'
                    : 'bg-[#FAFAFA] border-zinc-200 text-zinc-900 placeholder-zinc-400'
                }`}
              />
            </div>

            <div>
              <label className={`block text-[11px] font-bold uppercase tracking-wider mb-1.5 ${isDark ? 'text-zinc-400' : 'text-zinc-600'}`}>
                Admin Passcode
              </label>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-500" />
                <input
                  type={showPasscode ? 'text' : 'password'}
                  value={passcode}
                  onChange={(e) => {
                    setPasscode(e.target.value);
                    setLoginError('');
                  }}
                  placeholder="Enter store Admin passcode"
                  autoFocus
                  className={`w-full rounded-xl border pl-10 pr-10 py-2.5 text-sm font-mono focus:outline-none focus:border-amber-500 ${
                    isDark
                      ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-600'
                      : 'bg-[#FAFAFA] border-zinc-200 text-zinc-900 placeholder-zinc-400'
                  }`}
                />
                <button
                  type="button"
                  onClick={() => setShowPasscode(!showPasscode)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-amber-500"
                >
                  {showPasscode ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {loginError && (
              <div className="rounded-xl border border-rose-500/40 bg-rose-500/10 px-3.5 py-2 text-xs font-bold text-rose-400">
                {loginError}
              </div>
            )}

            <button
              type="submit"
              disabled={submitting || !passcode.trim()}
              className="w-full rounded-xl bg-amber-500 py-3 text-sm font-black text-zinc-950 shadow-lg shadow-amber-500/20 hover:bg-amber-400 disabled:opacity-50 transition active:scale-[0.99]"
            >
              {submitting ? 'Verifying...' : 'Unlock Admin Portal'}
            </button>
          </form>

          <div className="mt-6 pt-4 border-t border-zinc-200 dark:border-zinc-800 text-center">
            <Link
              href="/"
              className={`inline-flex items-center gap-1.5 text-xs font-semibold ${
                isDark ? 'text-zinc-400 hover:text-white' : 'text-zinc-500 hover:text-zinc-900'
              }`}
            >
              <Store className="h-3.5 w-3.5" />
              <span>← Back to Tablet POS Terminal</span>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const currentNavLabel =
    NAV_GROUPS.flatMap((g) => g.items).find((it) => it.href === pathname)?.label ||
    'Admin';

  return (
    <ToastProvider>
      <AdminContext.Provider
        value={{
          theme,
          isDark,
          operatorName,
          toggleTheme: () => applyTheme(isDark ? 'light' : 'dark'),
          refreshTick,
          triggerRefresh: () => setRefreshTick((t) => t + 1),
        }}
      >
        <div
          className={`min-h-screen flex select-text ${
            isDark ? 'bg-zinc-950 text-white' : 'bg-[#FAFAFA] text-[#09090B]'
          }`}
        >
          {/* Desktop Sidebar */}
          <aside
            className={`hidden lg:block w-64 shrink-0 border-r sticky top-0 h-screen no-print ${
              isDark
                ? 'border-zinc-800 bg-zinc-950'
                : 'border-[#E4E4E7] bg-white'
            }`}
          >
            <AdminSidebar
              pathname={pathname}
              isDark={isDark}
              operatorName={operatorName}
              onCloseMobile={() => setMobileMenuOpen(false)}
              onLogout={handleLogout}
            />
          </aside>

          {/* Mobile Drawer */}
          {mobileMenuOpen && (
            <div
              onClick={() => setMobileMenuOpen(false)}
              className="fixed inset-0 z-50 bg-black/70 lg:hidden no-print"
            >
              <aside
                onClick={(e) => e.stopPropagation()}
                className={`h-full w-72 border-r ${
                  isDark
                    ? 'border-zinc-800 bg-zinc-950'
                    : 'border-[#E4E4E7] bg-white'
                }`}
              >
                <AdminSidebar
                  pathname={pathname}
                  isDark={isDark}
                  operatorName={operatorName}
                  onCloseMobile={() => setMobileMenuOpen(false)}
                  onLogout={handleLogout}
                />
              </aside>
            </div>
          )}

          {/* Main Workspace */}
          <div className="flex-1 flex flex-col min-w-0">
            <header
              className={`sticky top-0 z-30 flex h-14 items-center justify-between border-b px-4 sm:px-6 no-print ${
                isDark
                  ? 'border-zinc-800 bg-zinc-950/90 backdrop-blur'
                  : 'border-[#E4E4E7] bg-white/90 backdrop-blur'
              }`}
            >
              <div className="flex items-center gap-3 min-w-0">
                <button
                  type="button"
                  onClick={() => setMobileMenuOpen(true)}
                  className={`lg:hidden flex h-9 w-9 items-center justify-center rounded-xl border ${
                    isDark
                      ? 'border-zinc-800 bg-zinc-900 text-zinc-300'
                      : 'border-zinc-200 bg-white text-zinc-700'
                  }`}
                >
                  <Menu className="h-4 w-4" />
                </button>
                <h2 className="text-sm sm:text-base font-black tracking-tight truncate">
                  {currentNavLabel}
                </h2>
                <span className="hidden sm:inline-flex items-center gap-1 rounded-full bg-emerald-500/15 border border-emerald-500/30 px-2 py-0.5 text-[10px] font-bold text-emerald-500">
                  ● Live Supabase Sync
                </span>
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setRefreshTick((t) => t + 1)}
                  title="Refresh Active View"
                  className={`flex h-9 items-center gap-1.5 rounded-xl border px-3 text-xs font-bold transition ${
                    isDark
                      ? 'border-zinc-800 bg-zinc-900 text-zinc-300 hover:bg-zinc-800 hover:text-white'
                      : 'border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-100 shadow-sm'
                  }`}
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                  <span className="hidden sm:inline">Refresh</span>
                </button>

                <button
                  type="button"
                  onClick={() => applyTheme(isDark ? 'light' : 'dark')}
                  title="Toggle Theme"
                  className={`flex h-9 w-9 items-center justify-center rounded-xl border transition ${
                    isDark
                      ? 'border-zinc-800 bg-zinc-900 text-amber-400 hover:bg-zinc-800'
                      : 'border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-100 shadow-sm'
                  }`}
                >
                  {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                </button>
              </div>
            </header>

            <main className="flex-1 p-4 sm:p-6 max-w-7xl w-full mx-auto">
              {children}
            </main>
          </div>
        </div>
      </AdminContext.Provider>
    </ToastProvider>
  );
}

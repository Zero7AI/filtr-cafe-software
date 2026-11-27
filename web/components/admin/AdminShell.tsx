'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Avatar,
  Button,
  Card,
  Chip,
  Input,
  Label,
  Separator,
  Spinner,
  TextField,
  Tooltip,
} from '@heroui/react';
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
  Monitor,
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

interface NavItem {
  href: string;
  label: string;
  subtitle: string;
  icon: React.ComponentType<{ className?: string }>;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    title: 'Analytics',
    items: [
      {
        href: '/',
        label: 'Overview',
        subtitle: 'Revenue, P&L pulse, top sellers & purchase anomalies',
        icon: LayoutDashboard,
      },
      {
        href: '/pnl',
        label: 'Profit & Loss',
        subtitle: '5% inclusive GST, recipe COGS, overheads & break-even',
        icon: TrendingUp,
      },
      {
        href: '/usage',
        label: 'Inventory Usage',
        subtitle: 'Exact ingredient consumption from Recipe Master × Orders',
        icon: BarChart3,
      },
    ],
  },
  {
    title: 'Live Store',
    items: [
      {
        href: '/orders',
        label: 'Orders Ledger',
        subtitle: 'All bills, payment status, receipts & fulfillment control',
        icon: ClipboardList,
      },
      {
        href: '/kds',
        label: 'Live KDS Queue',
        subtitle: 'Real-time barista claims & FIFO drink queue',
        icon: Flame,
      },
    ],
  },
  {
    title: 'Inventory & Menu',
    items: [
      {
        href: '/inventory',
        label: 'Purchase Tracking',
        subtitle: 'SKU Price History, bill ledger, WhatsApp bot & .xlsx export',
        icon: Package,
      },
      {
        href: '/kitchen',
        label: 'Kitchen Inventory',
        subtitle: '4-zone opening/closing shifts, variance & reorders',
        icon: Refrigerator,
      },
      {
        href: '/menu',
        label: 'Menu & Recipes',
        subtitle: 'POS menu catalog, margins & ingredient COGS recipes',
        icon: UtensilsCrossed,
      },
    ],
  },
  {
    title: 'People & System',
    items: [
      {
        href: '/team',
        label: 'Team & Checklists',
        subtitle: 'Attendance matrix, geofence, staff RBAC & store checklist',
        icon: Users,
      },
      {
        href: '/customers',
        label: 'Customers CRM',
        subtitle: 'Loyalty spend, repeat orders & customer bill history',
        icon: Contact,
      },
      {
        href: '/settings',
        label: 'Devices & Settings',
        subtitle: 'Android devices, passcodes, UPI QR, receipts & OTA policy',
        icon: Settings,
      },
    ],
  },
];

function AdminSidebar({
  pathname,
  operatorName,
  onCloseMobile,
  onLogout,
}: {
  pathname: string;
  operatorName: string;
  onCloseMobile: () => void;
  onLogout: () => void;
}) {
  const initials = operatorName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((s) => s[0]?.toUpperCase() || '')
    .join('') || 'FA';

  return (
    <div className="flex h-full flex-col bg-surface text-foreground">
      <div className="flex items-center justify-between px-4 py-4 border-b border-separator">
        <Link href="/" className="flex items-center gap-3 min-w-0">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent text-accent-foreground shadow-surface">
            <Coffee className="h-5 w-5 stroke-[2.25]" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <span className="text-sm font-bold tracking-tight">FILTR Coffee</span>
              <Chip size="sm" variant="soft" color="accent">
                Admin
              </Chip>
            </div>
            <span className="text-[11px] text-muted block truncate">
              Analytics & Store Ops
            </span>
          </div>
        </Link>
        <Button
          isIconOnly
          size="sm"
          variant="ghost"
          onPress={onCloseMobile}
          aria-label="Close navigation menu"
          className="lg:hidden"
        >
          <X className="h-4 w-4" />
        </Button>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-5" aria-label="Admin Navigation">
        {NAV_GROUPS.map((group) => (
          <div key={group.title}>
            <p className="px-3 mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted">
              {group.title}
            </p>
            <div className="space-y-0.5">
              {group.items.map((item) => {
                const Icon = item.icon;
                const active =
                  item.href === '/'
                    ? pathname === '/'
                    : pathname === item.href || pathname.startsWith(`${item.href}/`);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={onCloseMobile}
                    className={`flex items-center gap-2.5 rounded-xl px-3 py-2 text-[13px] font-medium transition-colors ${
                      active
                        ? 'bg-accent text-accent-foreground font-semibold shadow-surface'
                        : 'text-muted hover:bg-surface-secondary hover:text-foreground'
                    }`}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    <span className="truncate">{item.label}</span>
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="p-3 border-t border-separator space-y-2">
        <div className="flex items-center gap-2.5 px-2 py-1.5 rounded-xl bg-surface-secondary">
          <Avatar size="sm" color="accent" variant="soft">
            <Avatar.Fallback>{initials}</Avatar.Fallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold truncate">{operatorName}</p>
            <p className="text-[10px] text-muted">Authenticated Session</p>
          </div>
          <Button
            isIconOnly
            size="sm"
            variant="danger-soft"
            onPress={onLogout}
            aria-label="Lock Admin Portal"
          >
            <LogOut className="h-3.5 w-3.5" />
          </Button>
        </div>

        <Link
          href="/pos"
          className="flex items-center justify-between rounded-xl border border-separator px-3 py-2 text-xs font-medium text-muted hover:text-foreground hover:bg-surface-secondary transition-colors"
        >
          <span className="flex items-center gap-2">
            <Monitor className="h-3.5 w-3.5 text-accent" />
            <span>Tablet POS Terminal</span>
          </span>
          <span className="text-[10px] font-mono">/pos</span>
        </Link>
      </div>
    </div>
  );
}

export default function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [authState, setAuthState] = useState<'loading' | 'unauthenticated' | 'authenticated'>(
    'loading'
  );
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
        root.dataset.theme = 'dark';
      } else {
        root.classList.remove('dark');
        root.classList.add('light');
        root.dataset.theme = 'light';
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
      <div className="flex min-h-screen items-center justify-center bg-background text-foreground">
        <div className="flex flex-col items-center gap-3">
          <Spinner size="lg" color="accent" />
          <span className="text-xs font-semibold tracking-wider uppercase text-muted">
            Loading FILTR Admin Portal...
          </span>
        </div>
      </div>
    );
  }

  if (authState === 'unauthenticated') {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 bg-background text-foreground">
        <div className="fixed top-5 right-5">
          <Button
            isIconOnly
            variant="outline"
            onPress={() => applyTheme(isDark ? 'light' : 'dark')}
            aria-label="Toggle theme"
          >
            {isDark ? <Sun className="h-4 w-4 text-accent" /> : <Moon className="h-4 w-4" />}
          </Button>
        </div>

        <Card className="w-full max-w-md border border-border p-2 sm:p-4 shadow-overlay">
          <Card.Header className="flex flex-col items-center text-center gap-2 pb-2">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-accent text-accent-foreground shadow-surface">
              <ShieldCheck className="h-6 w-6 stroke-[2.25]" />
            </div>
            <div>
              <Card.Title className="text-xl font-bold tracking-tight">
                FILTR Coffee Admin
              </Card.Title>
              <Card.Description className="text-xs mt-1">
                Laptop Operations, Analytics & Finance Portal • Android v2.5.4 Synced
              </Card.Description>
            </div>
          </Card.Header>

          <Card.Content className="pt-4">
            <form onSubmit={handleLogin} className="space-y-4">
              <TextField value={loginOperator} onChange={setLoginOperator} fullWidth>
                <Label className="text-xs font-medium text-muted">
                  Operator Name (Optional)
                </Label>
                <Input variant="secondary" placeholder="e.g. Somesh (Owner)" />
              </TextField>

              <div className="space-y-1.5">
                <Label className="text-xs font-medium text-muted">Admin Passcode</Label>
                <div className="relative flex items-center">
                  <Lock className="pointer-events-none absolute left-3 h-4 w-4 text-muted z-10" />
                  <Input
                    type={showPasscode ? 'text' : 'password'}
                    value={passcode}
                    onChange={(e) => {
                      setPasscode(e.target.value);
                      setLoginError('');
                    }}
                    variant="secondary"
                    fullWidth
                    autoFocus
                    placeholder="Enter store Admin passcode"
                    className="pl-9 pr-10 font-mono"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPasscode((v) => !v)}
                    aria-label={showPasscode ? 'Hide passcode' : 'Show passcode'}
                    className="absolute right-3 text-muted hover:text-foreground transition"
                  >
                    {showPasscode ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {loginError && (
                <div className="rounded-xl border border-danger/30 bg-danger-soft px-3.5 py-2 text-xs font-medium text-danger">
                  {loginError}
                </div>
              )}

              <Button
                type="submit"
                variant="primary"
                fullWidth
                size="lg"
                isDisabled={submitting || !passcode.trim()}
              >
                {submitting ? 'Verifying...' : 'Unlock Admin Dashboard'}
              </Button>
            </form>
          </Card.Content>

          <Separator className="my-2" />

          <Card.Footer className="flex justify-center pt-1">
            <Link
              href="/pos"
              className="inline-flex items-center gap-1.5 text-xs font-medium text-muted hover:text-foreground transition-colors"
            >
              <Monitor className="h-3.5 w-3.5 text-accent" />
              <span>Open Counter POS Terminal (/pos)</span>
            </Link>
          </Card.Footer>
        </Card>
      </div>
    );
  }

  const allItems = NAV_GROUPS.flatMap((g) => g.items);
  const currentNavItem =
    allItems.find((it) =>
      it.href === '/'
        ? pathname === '/'
        : pathname === it.href || pathname.startsWith(`${it.href}/`)
    ) || allItems[0];

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
        <div className="min-h-screen flex bg-background text-foreground">
          {/* Desktop 260px Sidebar */}
          <aside className="hidden lg:block w-[260px] shrink-0 border-r border-separator sticky top-0 h-screen no-print">
            <AdminSidebar
              pathname={pathname}
              operatorName={operatorName}
              onCloseMobile={() => setMobileMenuOpen(false)}
              onLogout={handleLogout}
            />
          </aside>

          {/* Mobile Drawer */}
          {mobileMenuOpen && (
            <div
              onClick={() => setMobileMenuOpen(false)}
              className="fixed inset-0 z-50 bg-backdrop backdrop-blur-sm lg:hidden no-print"
            >
              <aside
                onClick={(e) => e.stopPropagation()}
                className="h-full w-72 border-r border-separator bg-surface shadow-overlay"
              >
                <AdminSidebar
                  pathname={pathname}
                  operatorName={operatorName}
                  onCloseMobile={() => setMobileMenuOpen(false)}
                  onLogout={handleLogout}
                />
              </aside>
            </div>
          )}

          {/* Main Workspace */}
          <div className="flex-1 flex flex-col min-w-0">
            <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-separator bg-background/85 backdrop-blur-md px-4 sm:px-6 lg:px-8 no-print">
              <div className="flex items-center gap-3 min-w-0">
                <Button
                  isIconOnly
                  size="sm"
                  variant="outline"
                  onPress={() => setMobileMenuOpen(true)}
                  aria-label="Open navigation menu"
                  className="lg:hidden"
                >
                  <Menu className="h-4 w-4" />
                </Button>
                <div className="min-w-0">
                  <div className="flex items-center gap-2.5">
                    <h1 className="text-base sm:text-lg font-bold tracking-tight truncate">
                      {currentNavItem.label}
                    </h1>
                    <Chip size="sm" variant="soft" color="success" className="hidden sm:inline-flex">
                      <span className="flex items-center gap-1.5">
                        <span className="h-1.5 w-1.5 rounded-full bg-success animate-pulse" />
                        Live Supabase
                      </span>
                    </Chip>
                  </div>
                  <p className="text-xs text-muted truncate hidden md:block">
                    {currentNavItem.subtitle}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Tooltip delay={300}>
                  <Tooltip.Trigger>
                    <Button
                      size="sm"
                      variant="outline"
                      onPress={() => setRefreshTick((t) => t + 1)}
                      aria-label="Refresh active view"
                    >
                      <RefreshCw className="h-3.5 w-3.5" />
                      <span className="hidden sm:inline">Refresh</span>
                    </Button>
                  </Tooltip.Trigger>
                  <Tooltip.Content>Re-fetch live Supabase data</Tooltip.Content>
                </Tooltip>

                <Tooltip delay={300}>
                  <Tooltip.Trigger>
                    <Button
                      isIconOnly
                      size="sm"
                      variant="outline"
                      onPress={() => applyTheme(isDark ? 'light' : 'dark')}
                      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
                    >
                      {isDark ? (
                        <Sun className="h-4 w-4 text-accent" />
                      ) : (
                        <Moon className="h-4 w-4" />
                      )}
                    </Button>
                  </Tooltip.Trigger>
                  <Tooltip.Content>
                    {isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
                  </Tooltip.Content>
                </Tooltip>
              </div>
            </header>

            <main className="flex-1 w-full max-w-[1600px] mx-auto px-4 sm:px-6 lg:px-8 py-6">
              {children}
            </main>
          </div>
        </div>
      </AdminContext.Provider>
    </ToastProvider>
  );
}

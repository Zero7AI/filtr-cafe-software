'use client';

import React, { useState } from 'react';
import {
  Coffee,
  ShoppingBag,
  Clock,
  Printer,
  Bluetooth,
  Lock,
  CheckCircle2,
  AlertCircle,
  KeyRound,
  Sun,
  Moon,
} from 'lucide-react';
import { connectBluetoothPrinter, isBluetoothSupported } from '@/lib/escpos';
import ChangePinModal from './ChangePinModal';

interface NavbarProps {
  activeTab: 'POS' | 'ORDERS';
  onSelectTab: (tab: 'POS' | 'ORDERS') => void;
  uncompletedCount: number;
  autoPrint: boolean;
  onToggleAutoPrint: () => void;
  onLogout: () => void;
  bluetoothName: string | null;
  onBluetoothConnected: (name: string) => void;
  theme: 'dark' | 'light';
  onToggleTheme: () => void;
}

export default function Navbar({
  activeTab,
  onSelectTab,
  uncompletedCount,
  autoPrint,
  onToggleAutoPrint,
  onLogout,
  bluetoothName,
  onBluetoothConnected,
  theme,
  onToggleTheme,
}: NavbarProps) {
  const [connectingBt, setConnectingBt] = useState(false);
  const [btError, setBtError] = useState<string | null>(null);
  const [isChangePinOpen, setIsChangePinOpen] = useState(false);

  const isDark = theme === 'dark';

  const handleBluetoothClick = async () => {
    if (!isBluetoothSupported()) {
      setBtError('Web Bluetooth is supported in Chrome on Android 6.0+, Windows & Mac.');
      setTimeout(() => setBtError(null), 4000);
      return;
    }
    setConnectingBt(true);
    setBtError(null);
    const res = await connectBluetoothPrinter();
    setConnectingBt(false);
    if (res.success && res.name) {
      onBluetoothConnected(res.name);
    } else if (res.error) {
      setBtError(res.error);
      setTimeout(() => setBtError(null), 4000);
    }
  };

  return (
    <header
      className={`sticky top-0 z-40 flex h-18 w-full items-center justify-between border-b px-3 sm:px-6 transition-colors duration-150 ${
        isDark
          ? 'border-zinc-800 bg-zinc-950 text-white'
          : 'border-[#E4E4E7] bg-white text-[#09090B] shadow-sm'
      }`}
    >
      {/* Brand & Mode */}
      <div className="flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-500 text-zinc-950 shadow-md shadow-amber-500/20">
          <Coffee className="h-6 w-6 stroke-[2.5]" />
        </div>
        <div>
          <span
            className={`text-xl font-black tracking-tight flex items-center gap-1.5 ${
              isDark ? 'text-white' : 'text-[#09090B]'
            }`}
          >
            FILTR <span className="text-amber-500">COFFEE</span>
          </span>
          <span
            className={`text-[11px] font-medium tracking-wide block -mt-0.5 ${
              isDark ? 'text-zinc-400' : 'text-[#71717A]'
            }`}
          >
            Tablet POS & Kitchen
          </span>
        </div>
      </div>

      {/* Main Navigation Tabs */}
      <div
        className={`flex items-center rounded-2xl border p-1.5 shadow-inner transition-colors ${
          isDark
            ? 'bg-zinc-900 border-zinc-800'
            : 'bg-[#F4F4F5] border-[#E4E4E7]'
        }`}
      >
        <button
          type="button"
          onClick={() => onSelectTab('POS')}
          className={`flex items-center gap-2 rounded-xl px-5 py-2 text-sm font-bold transition-all duration-150 ${
            activeTab === 'POS'
              ? 'bg-amber-500 text-zinc-950 shadow-lg shadow-amber-500/20'
              : isDark
              ? 'text-zinc-400 hover:text-white hover:bg-zinc-800/60'
              : 'text-[#71717A] hover:text-[#09090B] hover:bg-white/80'
          }`}
        >
          <ShoppingBag className="h-4 w-4" />
          <span>New Order</span>
        </button>

        <button
          type="button"
          onClick={() => onSelectTab('ORDERS')}
          className={`relative flex items-center gap-2 rounded-xl px-5 py-2 text-sm font-bold transition-all duration-150 ${
            activeTab === 'ORDERS'
              ? 'bg-amber-500 text-zinc-950 shadow-lg shadow-amber-500/20'
              : isDark
              ? 'text-zinc-400 hover:text-white hover:bg-zinc-800/60'
              : 'text-[#71717A] hover:text-[#09090B] hover:bg-white/80'
          }`}
        >
          <Clock className="h-4 w-4" />
          <span>Live Orders</span>
          {uncompletedCount > 0 && (
            <span
              className={`ml-1 flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-[11px] font-black ${
                activeTab === 'ORDERS'
                  ? 'bg-zinc-950 text-amber-400'
                  : 'bg-amber-500 text-zinc-950'
              }`}
            >
              {uncompletedCount}
            </span>
          )}
        </button>
      </div>

      {/* Right Controls: Theme, BT, Auto-Print, PIN, Lock */}
      <div className="flex items-center gap-2 sm:gap-3">
        {/* Light / Dark Theme Toggle Button */}
        <button
          type="button"
          onClick={onToggleTheme}
          title={isDark ? 'Switch to Light Theme' : 'Switch to Dark Theme'}
          className={`flex h-10 w-10 items-center justify-center rounded-xl border transition ${
            isDark
              ? 'border-zinc-800 bg-zinc-900 text-amber-400 hover:bg-zinc-800 hover:text-amber-300'
              : 'border-[#E4E4E7] bg-white text-zinc-700 hover:bg-[#F4F4F5] hover:text-zinc-900 shadow-sm'
          }`}
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </button>

        {/* Bluetooth Thermal Printer Connect */}
        <button
          type="button"
          onClick={handleBluetoothClick}
          disabled={connectingBt}
          title={bluetoothName ? `Connected to ${bluetoothName}` : 'Connect Bluetooth Thermal Printer'}
          className={`flex items-center gap-1.5 rounded-xl border px-2.5 sm:px-3 py-2 text-xs font-semibold transition ${
            bluetoothName
              ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-400'
              : isDark
              ? 'border-zinc-700 bg-zinc-900 text-zinc-300 hover:bg-zinc-800'
              : 'border-[#E4E4E7] bg-white text-zinc-700 hover:bg-[#F4F4F5]'
          }`}
        >
          <Bluetooth className={`h-4 w-4 ${connectingBt ? 'animate-pulse text-amber-400' : ''}`} />
          <span>{bluetoothName ? bluetoothName.slice(0, 10) : 'BT Printer'}</span>
          {bluetoothName && <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400 ml-0.5" />}
        </button>

        {/* Auto-print toggle */}
        <button
          type="button"
          onClick={onToggleAutoPrint}
          title="Toggle Auto-print receipt on order placement"
          className={`flex items-center gap-1.5 rounded-xl border px-2.5 sm:px-3 py-2 text-xs font-semibold transition ${
            autoPrint
              ? 'border-amber-500/40 bg-amber-500/10 text-amber-500'
              : isDark
              ? 'border-zinc-800 bg-zinc-900 text-zinc-500'
              : 'border-[#E4E4E7] bg-white text-[#71717A]'
          }`}
        >
          <Printer className="h-4 w-4" />
          <span className="hidden md:inline">Auto-Print:</span>
          <span className="font-bold">{autoPrint ? 'ON' : 'OFF'}</span>
        </button>

        {/* Change PIN */}
        <button
          type="button"
          onClick={() => setIsChangePinOpen(true)}
          title="Change Staff Login PIN"
          className={`flex items-center gap-1.5 rounded-xl border px-2.5 sm:px-3 py-2 text-xs font-semibold transition ${
            isDark
              ? 'border-zinc-800 bg-zinc-900 text-zinc-300 hover:bg-zinc-800 hover:border-zinc-700 hover:text-white'
              : 'border-[#E4E4E7] bg-white text-[#09090B] hover:bg-[#F4F4F5] hover:border-zinc-300 shadow-sm'
          }`}
        >
          <KeyRound className="h-4 w-4 text-amber-500" />
          <span className="hidden sm:inline">Change PIN</span>
        </button>

        {/* Lock / Logout */}
        <button
          type="button"
          onClick={onLogout}
          title="Lock Tablet POS"
          className={`flex h-10 w-10 items-center justify-center rounded-xl border transition ${
            isDark
              ? 'border-zinc-800 bg-zinc-900 text-zinc-400 hover:bg-rose-500/10 hover:border-rose-500/30 hover:text-rose-400'
              : 'border-[#E4E4E7] bg-white text-[#71717A] hover:bg-rose-50 hover:border-rose-200 hover:text-rose-600 shadow-sm'
          }`}
        >
          <Lock className="h-4 w-4" />
        </button>
      </div>

      {btError && (
        <div className="absolute top-20 right-4 z-50 flex items-center gap-2 rounded-xl border border-rose-500 bg-rose-950 px-4 py-2 text-xs font-bold text-rose-200 shadow-2xl">
          <AlertCircle className="h-4 w-4 text-rose-400 shrink-0" />
          <span>{btError}</span>
        </div>
      )}

      {/* Change PIN Modal */}
      <ChangePinModal
        isOpen={isChangePinOpen}
        onClose={() => setIsChangePinOpen(false)}
        theme={theme}
      />
    </header>
  );
}

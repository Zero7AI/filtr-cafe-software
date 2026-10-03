'use client';

import React, { useState, useEffect } from 'react';
import { Lock, Coffee, Delete, Sun, Moon } from 'lucide-react';
import { verifyStaffPin } from '@/lib/pin';

interface PinAuthProps {
  onSuccess: () => void;
  theme?: 'dark' | 'light';
  onToggleTheme?: () => void;
}

export default function PinAuth({ onSuccess, theme = 'dark', onToggleTheme }: PinAuthProps) {
  const [pin, setPin] = useState<string>('');
  const [error, setError] = useState<string>('');

  const isDark = theme === 'dark';

  const handleKeyPress = (num: string) => {
    if (pin.length < 4) {
      const nextPin = pin + num;
      setPin(nextPin);
      setError('');
      if (nextPin.length === 4) {
        verifyPin(nextPin);
      }
    }
  };

  const handleBackspace = () => {
    setPin((prev) => prev.slice(0, -1));
    setError('');
  };

  const handleClear = () => {
    setPin('');
    setError('');
  };

  const verifyPin = (inputPin: string) => {
    if (verifyStaffPin(inputPin)) {
      try {
        localStorage.setItem('filtr_tablet_auth', 'true');
        localStorage.setItem('filtr_tablet_auth_time', Date.now().toString());
      } catch (e) {
        console.error(e);
      }
      onSuccess();
    } else {
      setError('Incorrect PIN. Please try again.');
      setTimeout(() => {
        setPin('');
      }, 500);
    }
  };

  // Allow physical keyboard input as well
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key >= '0' && e.key <= '9') {
        handleKeyPress(e.key);
      } else if (e.key === 'Backspace') {
        handleBackspace();
      } else if (e.key === 'Escape' || e.key === 'c' || e.key === 'C') {
        handleClear();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [pin]);

  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center px-4 transition-colors duration-200 ${
        isDark ? 'bg-zinc-950 text-white' : 'bg-[#FAFAFA] text-[#09090B]'
      }`}
    >
      {/* Optional Theme Toggle on Lock Screen */}
      {onToggleTheme && (
        <button
          type="button"
          onClick={onToggleTheme}
          title={isDark ? 'Switch to Light Theme' : 'Switch to Dark Theme'}
          className={`absolute top-5 right-5 flex h-10 w-10 items-center justify-center rounded-2xl border transition ${
            isDark
              ? 'border-zinc-800 bg-zinc-900 text-amber-400 hover:bg-zinc-800'
              : 'border-[#E4E4E7] bg-white text-zinc-700 hover:bg-[#F4F4F5] shadow-sm'
          }`}
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </button>
      )}

      <div
        className={`w-full max-w-sm rounded-3xl border p-8 shadow-2xl transition-colors ${
          isDark
            ? 'border-zinc-800 bg-zinc-900 text-white'
            : 'border-[#E4E4E7] bg-white text-[#09090B]'
        }`}
      >
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center">
          <div className="mb-3 flex h-16 w-16 items-center justify-center rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-500 shadow-md shadow-amber-500/10">
            <Coffee className="h-8 w-8 stroke-[2.5]" />
          </div>
          <h1 className="text-2xl font-black tracking-tight">FILTR COFFEE</h1>
          <p className={`text-xs font-medium uppercase tracking-widest mt-0.5 ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
            Tablet POS Terminal
          </p>
          <div
            className={`mt-4 flex items-center gap-1.5 text-xs px-3 py-1 rounded-full border ${
              isDark
                ? 'bg-zinc-800/80 border-zinc-700 text-zinc-400'
                : 'bg-[#F4F4F5] border-[#E4E4E7] text-zinc-600'
            }`}
          >
            <Lock className="h-3 w-3 text-amber-500" />
            <span>Enter 4-Digit Staff PIN</span>
          </div>
        </div>

        {/* PIN Indicators */}
        <div className="my-6 flex justify-center gap-4">
          {[0, 1, 2, 3].map((idx) => {
            const filled = pin.length > idx;
            return (
              <div
                key={idx}
                className={`h-4 w-4 rounded-full transition-all duration-200 ${
                  filled
                    ? 'bg-amber-500 scale-125 shadow-lg shadow-amber-500/40'
                    : isDark
                    ? 'bg-zinc-800 border border-zinc-700'
                    : 'bg-zinc-200 border border-zinc-300'
                }`}
              />
            );
          })}
        </div>

        {/* Error Feedback */}
        <div className="h-6 text-center text-xs font-medium text-rose-500">
          {error}
        </div>

        {/* Numeric Keypad (Tablet Touch Optimized) */}
        <div className="grid grid-cols-3 gap-3">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
            <button
              key={digit}
              type="button"
              onClick={() => handleKeyPress(digit)}
              className={`flex h-16 w-full items-center justify-center rounded-2xl text-2xl font-bold transition active:scale-95 ${
                isDark
                  ? 'bg-zinc-800/80 text-white hover:bg-zinc-700 active:bg-amber-500 active:text-zinc-950'
                  : 'bg-[#F4F4F5] text-[#09090B] hover:bg-zinc-200 active:bg-amber-500 active:text-white border border-[#E4E4E7]/60'
              }`}
            >
              {digit}
            </button>
          ))}
          <button
            type="button"
            onClick={handleClear}
            className={`flex h-16 w-full items-center justify-center rounded-2xl text-xs font-bold uppercase tracking-wider transition active:scale-95 ${
              isDark
                ? 'bg-zinc-800/40 text-zinc-400 hover:bg-zinc-800'
                : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
            }`}
          >
            Clear
          </button>
          <button
            type="button"
            onClick={() => handleKeyPress('0')}
            className={`flex h-16 w-full items-center justify-center rounded-2xl text-2xl font-bold transition active:scale-95 ${
              isDark
                ? 'bg-zinc-800/80 text-white hover:bg-zinc-700 active:bg-amber-500 active:text-zinc-950'
                : 'bg-[#F4F4F5] text-[#09090B] hover:bg-zinc-200 active:bg-amber-500 active:text-white border border-[#E4E4E7]/60'
            }`}
          >
            0
          </button>
          <button
            type="button"
            onClick={handleBackspace}
            className={`flex h-16 w-full items-center justify-center rounded-2xl transition active:scale-95 ${
              isDark
                ? 'bg-zinc-800/40 text-zinc-400 hover:bg-zinc-800'
                : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'
            }`}
          >
            <Delete className="h-6 w-6" />
          </button>
        </div>
      </div>
    </div>
  );
}

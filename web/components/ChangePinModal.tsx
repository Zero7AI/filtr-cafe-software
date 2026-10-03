'use client';

import React, { useState } from 'react';
import { KeyRound, X, Check, AlertCircle, Eye, EyeOff } from 'lucide-react';
import { verifyStaffPin, setStaffPin } from '@/lib/pin';

interface ChangePinModalProps {
  isOpen: boolean;
  onClose: () => void;
  theme?: 'dark' | 'light';
}

export default function ChangePinModal({ isOpen, onClose, theme = 'dark' }: ChangePinModalProps) {
  const [currentPin, setCurrentPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [showPins, setShowPins] = useState(false);

  if (!isOpen) return null;

  const isDark = theme === 'dark';

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!verifyStaffPin(currentPin)) {
      setError('Current PIN is incorrect');
      return;
    }

    if (!/^\d{4}$/.test(newPin)) {
      setError('New PIN must be exactly 4 digits');
      return;
    }

    if (newPin !== confirmPin) {
      setError('New PIN and Confirm PIN do not match');
      return;
    }

    const saved = setStaffPin(newPin);
    if (!saved) {
      setError('Failed to save PIN. Please try again.');
      return;
    }

    setSuccess(true);
    setTimeout(() => {
      setSuccess(false);
      setCurrentPin('');
      setNewPin('');
      setConfirmPin('');
      onClose();
    }, 1200);
  };

  const handleClose = () => {
    setError(null);
    setSuccess(false);
    setCurrentPin('');
    setNewPin('');
    setConfirmPin('');
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 px-4 animate-in fade-in duration-150">
      <div
        className={`relative w-full max-w-sm rounded-3xl border p-6 shadow-2xl transition-colors ${
          isDark
            ? 'border-zinc-800 bg-zinc-900 text-white'
            : 'border-[#E4E4E7] bg-white text-[#09090B]'
        }`}
      >
        {/* Close Button */}
        <button
          type="button"
          onClick={handleClose}
          className={`absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-xl transition ${
            isDark
              ? 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700 hover:text-white'
              : 'bg-[#F4F4F5] text-zinc-600 hover:bg-zinc-200 hover:text-zinc-900'
          }`}
        >
          <X className="h-4 w-4" />
        </button>

        {/* Header */}
        <div className="flex items-center gap-3 mb-5">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-500">
            <KeyRound className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-lg font-bold">Change Staff PIN</h2>
            <p className={`text-xs ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Set a new 4-digit tablet login PIN
            </p>
          </div>
        </div>

        {success ? (
          <div className="flex flex-col items-center justify-center py-6 text-center space-y-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-500">
              <Check className="h-6 w-6" />
            </div>
            <p className="text-sm font-bold text-emerald-500">PIN Changed Successfully!</p>
            <p className={`text-xs ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Use your new PIN the next time you log in.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSave} className="space-y-4">
            {/* Current PIN */}
            <div>
              <label
                className={`block text-xs font-semibold uppercase tracking-wider mb-1.5 ${
                  isDark ? 'text-zinc-400' : 'text-zinc-600'
                }`}
              >
                Current PIN
              </label>
              <input
                type={showPins ? 'text' : 'password'}
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={4}
                value={currentPin}
                onChange={(e) => {
                  const val = e.target.value.replace(/\D/g, '').slice(0, 4);
                  setCurrentPin(val);
                  setError(null);
                }}
                placeholder="4-digit PIN"
                className={`w-full rounded-xl border px-3.5 py-2.5 text-center text-lg font-mono tracking-widest focus:outline-none focus:border-amber-500 transition ${
                  isDark
                    ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-600'
                    : 'bg-[#FAFAFA] border-[#E4E4E7] text-[#09090B] placeholder-zinc-400'
                }`}
                autoFocus
              />
            </div>

            {/* New PIN */}
            <div>
              <label
                className={`block text-xs font-semibold uppercase tracking-wider mb-1.5 ${
                  isDark ? 'text-zinc-400' : 'text-zinc-600'
                }`}
              >
                New 4-Digit PIN
              </label>
              <input
                type={showPins ? 'text' : 'password'}
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={4}
                value={newPin}
                onChange={(e) => {
                  const val = e.target.value.replace(/\D/g, '').slice(0, 4);
                  setNewPin(val);
                  setError(null);
                }}
                placeholder="New 4 digits"
                className={`w-full rounded-xl border px-3.5 py-2.5 text-center text-lg font-mono tracking-widest focus:outline-none focus:border-amber-500 transition ${
                  isDark
                    ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-600'
                    : 'bg-[#FAFAFA] border-[#E4E4E7] text-[#09090B] placeholder-zinc-400'
                }`}
              />
            </div>

            {/* Confirm New PIN */}
            <div>
              <label
                className={`block text-xs font-semibold uppercase tracking-wider mb-1.5 ${
                  isDark ? 'text-zinc-400' : 'text-zinc-600'
                }`}
              >
                Confirm New PIN
              </label>
              <input
                type={showPins ? 'text' : 'password'}
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={4}
                value={confirmPin}
                onChange={(e) => {
                  const val = e.target.value.replace(/\D/g, '').slice(0, 4);
                  setConfirmPin(val);
                  setError(null);
                }}
                placeholder="Repeat new PIN"
                className={`w-full rounded-xl border px-3.5 py-2.5 text-center text-lg font-mono tracking-widest focus:outline-none focus:border-amber-500 transition ${
                  isDark
                    ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-600'
                    : 'bg-[#FAFAFA] border-[#E4E4E7] text-[#09090B] placeholder-zinc-400'
                }`}
              />
            </div>

            {/* Toggle show/hide PIN digits */}
            <div className={`flex items-center justify-between text-xs pt-0.5 ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
              <button
                type="button"
                onClick={() => setShowPins(!showPins)}
                className="flex items-center gap-1.5 hover:text-amber-500 transition"
              >
                {showPins ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                <span>{showPins ? 'Hide PINs' : 'Show digits'}</span>
              </button>
            </div>

            {/* Error Message */}
            {error && (
              <div className="flex items-center gap-2 rounded-xl bg-rose-950/80 border border-rose-800 px-3 py-2 text-xs text-rose-300">
                <AlertCircle className="h-4 w-4 shrink-0 text-rose-400" />
                <span>{error}</span>
              </div>
            )}

            {/* Action Buttons */}
            <div className="flex items-center gap-2 pt-2">
              <button
                type="button"
                onClick={handleClose}
                className={`flex-1 rounded-xl py-2.5 text-xs font-bold transition ${
                  isDark
                    ? 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700 hover:text-white'
                    : 'bg-[#F4F4F5] text-zinc-700 hover:bg-zinc-200 hover:text-zinc-900'
                }`}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={currentPin.length !== 4 || newPin.length !== 4 || confirmPin.length !== 4}
                className="flex-1 rounded-xl bg-amber-500 py-2.5 text-xs font-bold text-zinc-950 hover:bg-amber-400 disabled:opacity-50 disabled:cursor-not-allowed shadow-md shadow-amber-500/20 transition"
              >
                Save PIN
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

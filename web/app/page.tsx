'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { MenuItem, OrderWithItems } from '@/lib/types';
import {
  fetchMenuItems,
  fetchOrders,
  fetchReceiptConfig,
  defaultReceiptConfig,
  ReceiptDesignConfig,
} from '@/lib/supabase';
import { buildEscPosReceipt, printViaBluetooth, isBluetoothConnected } from '@/lib/escpos';
import PinAuth from '@/components/PinAuth';
import Navbar from '@/components/Navbar';
import PosOrderView from '@/components/PosOrderView';
import OrdersView from '@/components/OrdersView';
import Receipt from '@/components/Receipt';

export default function Home() {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);
  const [activeTab, setActiveTab] = useState<'POS' | 'ORDERS'>('POS');
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [uncompletedCount, setUncompletedCount] = useState<number>(0);
  const [autoPrint, setAutoPrint] = useState<boolean>(true);
  const [bluetoothName, setBluetoothName] = useState<string | null>(null);
  const [printOrderData, setPrintOrderData] = useState<OrderWithItems | null>(null);
  const [theme, setTheme] = useState<'dark' | 'light'>('dark');
  const [receiptConfig, setReceiptConfig] = useState<ReceiptDesignConfig>(defaultReceiptConfig);

  // Apply theme to DOM root and body for 100% Android 5.1 compatibility
  const applyTheme = (nextTheme: 'dark' | 'light') => {
    setTheme(nextTheme);
    if (typeof document !== 'undefined') {
      const root = document.documentElement;
      if (nextTheme === 'dark') {
        root.classList.add('dark');
        root.classList.remove('light');
        try {
          root.style.colorScheme = 'dark';
        } catch (e) {}
        document.body.style.backgroundColor = '#09090b';
        document.body.style.color = '#ffffff';
      } else {
        root.classList.remove('dark');
        root.classList.add('light');
        try {
          root.style.colorScheme = 'light';
        } catch (e) {}
        document.body.style.backgroundColor = '#fafafa';
        document.body.style.color = '#09090b';
      }
    }
    try {
      localStorage.setItem('filtr_pos_theme', nextTheme);
    } catch (e) {
      console.error(e);
    }
  };

  const toggleTheme = () => {
    applyTheme(theme === 'dark' ? 'light' : 'dark');
  };

  // Toggle and persist auto-print
  const handleToggleAutoPrint = () => {
    setAutoPrint((prev) => {
      const next = !prev;
      try {
        localStorage.setItem('filtr_auto_print', String(next));
      } catch (e) {
        console.error(e);
      }
      return next;
    });
  };

  // Load saved theme, auto-print & auth state on initial mount
  useEffect(() => {
    try {
      const savedAuth = localStorage.getItem('filtr_tablet_auth');
      setIsAuthenticated(savedAuth === 'true');

      const savedTheme = localStorage.getItem('filtr_pos_theme') as 'dark' | 'light';
      if (savedTheme === 'light' || savedTheme === 'dark') {
        applyTheme(savedTheme);
      } else {
        applyTheme('dark');
      }

      const savedAutoPrint = localStorage.getItem('filtr_auto_print');
      if (savedAutoPrint !== null) {
        setAutoPrint(savedAutoPrint === 'true');
      }
    } catch (e) {
      setIsAuthenticated(false);
      applyTheme('dark');
    }
  }, []);

  // Fetch initial data when authenticated
  useEffect(() => {
    if (!isAuthenticated) return;

    fetchMenuItems().then(setMenuItems);
    fetchReceiptConfig().then(setReceiptConfig);

    fetchOrders('UNCOMPLETED', 200, true).then((orders) => {
      setUncompletedCount(orders.length);
    });

    // Poll uncompleted orders count every 15s to keep badge fresh
    const timer = setInterval(() => {
      fetchOrders('UNCOMPLETED', 200, true).then((orders) => {
        setUncompletedCount(orders.length);
      });
    }, 15000);

    return () => clearInterval(timer);
  }, [isAuthenticated]);

  // Logout / Lock POS
  const handleLogout = () => {
    try {
      localStorage.removeItem('filtr_tablet_auth');
    } catch (e) {
      console.error(e);
    }
    setIsAuthenticated(false);
  };

  // Printing execution logic with dynamic mobile receipt design
  const handlePrint = useCallback(
    async (orderWithItems: OrderWithItems) => {
      setPrintOrderData(orderWithItems);

      // 1. If Web Bluetooth printer is connected, send ESC/POS bytes directly!
      if (isBluetoothConnected()) {
        try {
          const rawBytes = buildEscPosReceipt(orderWithItems, '80mm', receiptConfig);
          const success = await printViaBluetooth(rawBytes);
          if (success) {
            console.log('Printed successfully via Web Bluetooth');
            return;
          }
        } catch (err) {
          console.error('Bluetooth print failed, falling back to window.print():', err);
        }
      }

      // 2. Standard browser thermal print dialog fallback (allow React to render)
      setTimeout(() => {
        window.print();
      }, 350);
    },
    [receiptConfig]
  );

  // When order is completed in POS view
  const handleOrderCompleted = (orderWithItems: OrderWithItems, shouldPrint: boolean) => {
    // Increment uncompleted count optimistically
    setUncompletedCount((prev) => prev + 1);

    // CRITICAL FOR TABLET: Decouple in-app pop-ups from blocking OS print dialogs!
    // On Android 5.1, window.print() launches the OS print preview which blocks modal rendering.
    // 1. If Web Bluetooth printer is connected: silently print in the background.
    // 2. If Bluetooth is NOT connected: ONLY trigger window.print() if explicitly requested (shouldPrint).
    //    Never trigger window.print() automatically on normal order punch!
    if (isBluetoothConnected() && (shouldPrint || autoPrint)) {
      handlePrint(orderWithItems);
    } else if (shouldPrint && !isBluetoothConnected()) {
      handlePrint(orderWithItems);
    }
  };

  // Loading state while checking localStorage auth
  if (isAuthenticated === null) {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-zinc-950 text-amber-500">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-amber-500 border-t-transparent" />
      </div>
    );
  }

  // Not authenticated: render PIN lock screen
  if (!isAuthenticated) {
    return (
      <PinAuth
        onSuccess={() => setIsAuthenticated(true)}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
    );
  }

  const isDark = theme === 'dark';

  return (
    <div
      className={`min-h-screen flex flex-col transition-colors duration-150 ${
        isDark
          ? 'bg-zinc-950 text-white selection:bg-amber-500 selection:text-zinc-950'
          : 'bg-[#FAFAFA] text-[#09090B] selection:bg-amber-500 selection:text-white'
      }`}
    >
      {/* Top Tablet Navigation Bar */}
      <div className="no-print">
        <Navbar
          activeTab={activeTab}
          onSelectTab={setActiveTab}
          uncompletedCount={uncompletedCount}
          autoPrint={autoPrint}
          onToggleAutoPrint={handleToggleAutoPrint}
          onLogout={handleLogout}
          bluetoothName={bluetoothName}
          onBluetoothConnected={setBluetoothName}
          theme={theme}
          onToggleTheme={toggleTheme}
        />
      </div>

      {/* Main Content View (POS vs Live Orders) */}
      <main className="flex-1 overflow-hidden no-print">
        {activeTab === 'POS' ? (
          <PosOrderView
            menuItems={menuItems}
            autoPrint={autoPrint}
            onOrderCompleted={handleOrderCompleted}
            theme={theme}
            receiptConfig={receiptConfig}
          />
        ) : (
          <OrdersView
            onPrintOrder={handlePrint}
            onRefreshOrdersCount={setUncompletedCount}
            theme={theme}
            receiptConfig={receiptConfig}
          />
        )}
      </main>

      {/* Hidden Printable Thermal Receipt Container for window.print() */}
      <Receipt
        orderData={printOrderData}
        paperWidth="80mm"
        receiptConfig={receiptConfig}
      />
    </div>
  );
}

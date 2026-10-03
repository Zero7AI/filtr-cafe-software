import { OrderWithItems } from './types';
import { ReceiptDesignConfig, defaultReceiptConfig } from './supabase';

// Standard ESC/POS Commands
const ESC = 0x1b;
const GS = 0x1d;

const CMD_INIT = new Uint8Array([ESC, 0x40]);
const CMD_ALIGN_CENTER = new Uint8Array([ESC, 0x61, 0x01]);
const CMD_ALIGN_LEFT = new Uint8Array([ESC, 0x61, 0x00]);
const CMD_ALIGN_RIGHT = new Uint8Array([ESC, 0x61, 0x02]);
const CMD_BOLD_ON = new Uint8Array([ESC, 0x45, 0x01]);
const CMD_BOLD_OFF = new Uint8Array([ESC, 0x45, 0x00]);
const CMD_SIZE_NORMAL = new Uint8Array([GS, 0x21, 0x00]);
const CMD_SIZE_DOUBLE = new Uint8Array([GS, 0x21, 0x11]);
const CMD_SIZE_DOUBLE_HEIGHT = new Uint8Array([GS, 0x21, 0x01]);
const CMD_CUT_PARTIAL = new Uint8Array([GS, 0x56, 0x01]);

function encodeText(str: string): Uint8Array {
  if (typeof TextEncoder !== 'undefined') {
    return new TextEncoder().encode(str);
  }
  // Safe UTF-8 fallback for Android 5.1 / legacy Chrome
  const utf8: number[] = [];
  for (let i = 0; i < str.length; i++) {
    let charcode = str.charCodeAt(i);
    if (charcode < 0x80) utf8.push(charcode);
    else if (charcode < 0x800) {
      utf8.push(0xc0 | (charcode >> 6), 0x80 | (charcode & 0x3f));
    } else if (charcode < 0xd800 || charcode >= 0xe000) {
      utf8.push(
        0xe0 | (charcode >> 12),
        0x80 | ((charcode >> 6) & 0x3f),
        0x80 | (charcode & 0x3f)
      );
    } else {
      i++;
      charcode = 0x10000 + (((charcode & 0x3ff) << 10) | (str.charCodeAt(i) & 0x3ff));
      utf8.push(
        0xf0 | (charcode >> 18),
        0x80 | ((charcode >> 12) & 0x3f),
        0x80 | ((charcode >> 6) & 0x3f),
        0x80 | (charcode & 0x3f)
      );
    }
  }
  return new Uint8Array(utf8);
}

function concatBuffers(buffers: Uint8Array[]): Uint8Array {
  const totalLength = buffers.reduce((acc, b) => acc + b.length, 0);
  const result = new Uint8Array(totalLength);
  let offset = 0;
  for (const b of buffers) {
    result.set(b, offset);
    offset += b.length;
  }
  return result;
}

export function buildEscPosReceipt(
  data: OrderWithItems,
  paperWidth: '80mm' | '58mm' = '80mm',
  config: ReceiptDesignConfig = defaultReceiptConfig
): Uint8Array {
  const lineWidth = paperWidth === '58mm' ? 32 : 48;
  const divider = '='.repeat(lineWidth);
  const dashDivider = '-'.repeat(lineWidth);

  const order = data.order;
  const items = data.items;
  const token = order.order_id.split('-').pop() || '0000';
  const dateStr = new Date(order.timestamp).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });

  const parts: Uint8Array[] = [];

  // 1. Initialize
  parts.push(CMD_INIT);

  // 2. Header
  parts.push(CMD_ALIGN_CENTER);
  parts.push(CMD_SIZE_DOUBLE);
  parts.push(CMD_BOLD_ON);
  parts.push(encodeText('FILTR COFFEE\n'));
  parts.push(CMD_SIZE_NORMAL);
  parts.push(CMD_BOLD_OFF);
  if (config.headerSubtitle) {
    parts.push(encodeText(`${config.headerSubtitle}\n`));
  }
  if (config.tagline) {
    parts.push(encodeText(`${config.tagline}\n`));
  }
  parts.push(encodeText(`${divider}\n`));

  // 3. Token & Order ID
  parts.push(CMD_SIZE_DOUBLE);
  parts.push(CMD_BOLD_ON);
  parts.push(encodeText(`TOKEN #${token}\n`));
  parts.push(CMD_SIZE_NORMAL);
  parts.push(CMD_BOLD_OFF);
  parts.push(encodeText(`Order ID: ${order.order_id}\n`));
  parts.push(encodeText(`Date: ${dateStr}\n`));
  if (order.customer_name && order.customer_name !== 'Walk-in') {
    parts.push(encodeText(`Customer: ${order.customer_name}\n`));
  }
  if (order.contact_number) {
    parts.push(encodeText(`Phone: ${order.contact_number}\n`));
  }
  parts.push(encodeText(`${dashDivider}\n`));

  // 4. Items Table
  parts.push(CMD_ALIGN_LEFT);
  // Column header
  if (paperWidth === '58mm') {
    // 32 chars: Item (16) Qty (4) Amt (12)
    parts.push(encodeText('Item             Qty      Total\n'));
  } else {
    // 48 chars: Item (26) Rate (6) Qty (4) Total (12)
    parts.push(encodeText('Item                      Rate  Qty        Total\n'));
  }
  parts.push(encodeText(`${dashDivider}\n`));

  // Items rows
  for (const item of items) {
    const name = item.item_name;
    const qty = `x${item.quantity}`;
    const rate = `₹${item.item_price}`;
    const total = `₹${item.subtotal}`;

    if (paperWidth === '58mm') {
      const shortName = name.slice(0, 16).padEnd(16);
      const shortQty = qty.padStart(4);
      const shortTot = total.padStart(12);
      parts.push(encodeText(`${shortName}${shortQty}${shortTot}\n`));
    } else {
      const longName = name.slice(0, 24).padEnd(25);
      const longRate = rate.padStart(6);
      const longQty = qty.padStart(5);
      const longTot = total.padStart(12);
      parts.push(encodeText(`${longName}${longRate}${longQty}${longTot}\n`));
    }
  }

  parts.push(encodeText(`${dashDivider}\n`));

  // 5. Totals & Payment
  parts.push(CMD_ALIGN_RIGHT);
  parts.push(CMD_BOLD_ON);
  parts.push(CMD_SIZE_DOUBLE_HEIGHT);
  parts.push(encodeText(`TOTAL: ₹${order.total_amount}\n`));
  parts.push(CMD_SIZE_NORMAL);
  parts.push(CMD_BOLD_OFF);
  parts.push(encodeText(`Payment Mode: ${order.payment_mode}\n`));
  parts.push(encodeText(`Payment Status: ${order.is_paid ? 'PAID' : 'UNPAID'}\n`));
  if (order.notes) {
    parts.push(CMD_ALIGN_LEFT);
    parts.push(encodeText(`Notes: ${order.notes}\n`));
  }

  // 6. Footer
  parts.push(CMD_ALIGN_CENTER);
  parts.push(encodeText(`${divider}\n`));
  if (config.footerLine1) {
    parts.push(encodeText(`${config.footerLine1}\n`));
  }
  if (config.footerLine2) {
    parts.push(encodeText(`${config.footerLine2}\n`));
  }
  if (config.footerLine3) {
    parts.push(encodeText(`${config.footerLine3}\n`));
  }
  parts.push(encodeText('\n\n\n\n'));

  // 7. Cut paper
  parts.push(CMD_CUT_PARTIAL);

  return concatBuffers(parts);
}

// ==================== WEB BLUETOOTH PRINTER CLIENT ====================
let bluetoothDevice: any = null;
let writeCharacteristic: any = null;

export function isBluetoothSupported(): boolean {
  return typeof navigator !== 'undefined' && 'bluetooth' in navigator;
}

export async function connectBluetoothPrinter(): Promise<{ success: boolean; name?: string; error?: string }> {
  if (!isBluetoothSupported()) {
    return { success: false, error: 'Web Bluetooth is not supported in this browser version.' };
  }

  try {
    const device = await (navigator as any).bluetooth.requestDevice({
      acceptAllDevices: true,
      optionalServices: [
        '000018f0-0000-1000-8000-00805f9b34fb', // Standard Thermal Printer Service
        'e7810a71-73ae-499d-8c15-faa9aef0c3f2',
        '49535343-fe7d-4ae5-8fa9-9fafd205e455',
        '0000ffe0-0000-1000-8000-00805f9b34fb', // Common Chinese POS printer service
      ],
    });

    if (!device) {
      return { success: false, error: 'No printer device selected' };
    }

    // Set 6-second connection timeout to avoid hanging on older Android devices
    const timeout = new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error('Bluetooth connection timed out (6s)')), 6000)
    );

    const server = await Promise.race([device.gatt.connect(), timeout]);
    const services = await server.getPrimaryServices();

    for (const service of services) {
      try {
        const characteristics = await service.getCharacteristics();
        for (const char of characteristics) {
          if (char.properties.write || char.properties.writeWithoutResponse) {
            writeCharacteristic = char;
            bluetoothDevice = device;

            // Handle accidental disconnection
            device.addEventListener('gattserverdisconnected', () => {
              console.log('Bluetooth thermal printer disconnected');
              bluetoothDevice = null;
              writeCharacteristic = null;
            });

            return { success: true, name: device.name || 'Bluetooth Thermal Printer' };
          }
        }
      } catch (svcErr) {
        console.warn('Skipping service:', svcErr);
      }
    }

    return { success: false, error: 'No writable thermal printer characteristic found' };
  } catch (err: any) {
    console.error('Bluetooth connection failed:', err);
    if (err.name === 'NotFoundError' || err.message?.includes('cancelled')) {
      return { success: false, error: 'Selection cancelled' };
    }
    return { success: false, error: err.message || 'Connection failed' };
  }
}

export async function disconnectBluetoothPrinter(): Promise<void> {
  try {
    if (bluetoothDevice && bluetoothDevice.gatt && bluetoothDevice.gatt.connected) {
      bluetoothDevice.gatt.disconnect();
    }
  } catch (e) {
    console.error('Error disconnecting Bluetooth printer:', e);
  } finally {
    bluetoothDevice = null;
    writeCharacteristic = null;
  }
}

export async function printViaBluetooth(data: Uint8Array): Promise<boolean> {
  if (!writeCharacteristic) return false;
  try {
    // Send in 40-byte chunks to fit standard legacy Android Bluetooth ATT MTU safely
    const chunkSize = 40;
    for (let i = 0; i < data.length; i += chunkSize) {
      const chunk = data.slice(i, i + chunkSize);
      const writePromise = writeCharacteristic.writeValue(chunk);
      const timeout = new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('BLE write timeout')), 2500)
      );
      await Promise.race([writePromise, timeout]);
      // Small 15ms pause between packets to give printer buffer time to flush
      await new Promise((r) => setTimeout(r, 15));
    }
    return true;
  } catch (err) {
    console.error('Bluetooth print error:', err);
    return false;
  }
}

export function isBluetoothConnected(): boolean {
  return !!(bluetoothDevice && bluetoothDevice.gatt && bluetoothDevice.gatt.connected);
}

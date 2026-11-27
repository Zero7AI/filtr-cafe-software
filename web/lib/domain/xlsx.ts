import {
  BillLedgerRow,
  formatBillCell,
  SkuPriceHistoryMatrix,
} from './inventory';

/**
 * Pure TypeScript zero-dependency OOXML (.xlsx) workbook generator.
 * Produces a valid Store-mode ZIP archive with 2 worksheets:
 *  1. "SKU Price History"
 *  2. "Bill Ledger"
 */
export function buildSkuPriceHistoryXlsx(
  matrix: SkuPriceHistoryMatrix,
  ledger: BillLedgerRow[],
  includeDateInCells: boolean = true
): Uint8Array {
  const entries: Array<{ name: string; data: Uint8Array }> = [
    { name: '[Content_Types].xml', data: utf8(contentTypesXml()) },
    { name: '_rels/.rels', data: utf8(rootRelsXml()) },
    { name: 'xl/_rels/workbook.xml.rels', data: utf8(workbookRelsXml()) },
    { name: 'xl/workbook.xml', data: utf8(workbookXml()) },
    { name: 'xl/styles.xml', data: utf8(stylesXml()) },
    { name: 'xl/worksheets/sheet1.xml', data: utf8(buildPriceHistorySheetXml(matrix, includeDateInCells)) },
    { name: 'xl/worksheets/sheet2.xml', data: utf8(buildBillLedgerSheetXml(ledger)) },
  ];
  return createZipStore(entries);
}

const encoder = new TextEncoder();
function utf8(s: string): Uint8Array {
  return encoder.encode(s);
}

function colLetter(zeroBasedIdx: number): string {
  let n = zeroBasedIdx;
  let res = '';
  while (n >= 0) {
    res = String.fromCharCode(65 + (n % 26)) + res;
    n = Math.floor(n / 26) - 1;
  }
  return res;
}

function xmlEscape(text: string): string {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function inlineCell(colIdx: number, rowNum: number, value: string, styleId: number): string {
  const ref = `${colLetter(colIdx)}${rowNum}`;
  return `<c r="${ref}" s="${styleId}" t="inlineStr"><is><t>${xmlEscape(value)}</t></is></c>`;
}

function numCell(colIdx: number, rowNum: number, value: number, styleId: number): string {
  const ref = `${colLetter(colIdx)}${rowNum}`;
  const formatted =
    Math.abs(value - Math.round(value)) < 0.0001
      ? String(Math.round(value))
      : value.toFixed(2);
  return `<c r="${ref}" s="${styleId}"><v>${formatted}</v></c>`;
}

function buildPriceHistorySheetXml(
  matrix: SkuPriceHistoryMatrix,
  includeDateInCells: boolean
): string {
  const billColsCount = Math.max(1, matrix.maxBillColumns);
  const totalCols = 4 + billColsCount;
  const parts: string[] = [
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
    '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">',
    '<sheetViews><sheetView workbookViewId="0"><pane xSplit="1" ySplit="1" topLeftCell="B2" activePane="bottomRight" state="frozen"/></sheetView></sheetViews>',
    '<cols>',
    '<col min="1" max="1" width="30" customWidth="1"/>',
    '<col min="2" max="2" width="16" customWidth="1"/>',
    '<col min="3" max="4" width="20" customWidth="1"/>',
    `<col min="5" max="${totalCols}" width="16" customWidth="1"/>`,
    '</cols>',
    '<sheetData>',
  ];

  const headers = ['SKU Name', 'Unit / Weight', 'Latest Price (INR)', 'Price Change vs First (%)'];
  for (let b = 1; b <= billColsCount; b++) headers.push(`Bill ${b}`);
  parts.push('<row r="1">');
  headers.forEach((h, i) => parts.push(inlineCell(i, 1, h, 1)));
  parts.push('</row>');

  matrix.rows.forEach((row, idx) => {
    const rNum = idx + 2;
    parts.push(`<row r="${rNum}">`);
    parts.push(inlineCell(0, rNum, row.skuName, 4));
    parts.push(inlineCell(1, rNum, row.unitWeightLabel, 0));
    parts.push(numCell(2, rNum, row.latestPrice, 4));

    const pct = row.priceChangeVsFirstPct;
    const pctStyle = pct !== null && pct > 0 ? 2 : pct !== null && pct < 0 ? 5 : 0;
    const pctText =
      pct !== null ? `${pct > 0 ? '+' : ''}${pct.toFixed(1)}%` : '0.0%';
    parts.push(inlineCell(3, rNum, pctText, pctStyle));

    for (let bIdx = 0; bIdx < billColsCount; bIdx++) {
      const bill = row.bills[bIdx];
      if (!bill) {
        parts.push(inlineCell(4 + bIdx, rNum, '', 0));
      } else if (bill.isUnitAnomaly) {
        parts.push(inlineCell(4 + bIdx, rNum, formatBillCell(bill, includeDateInCells), 3));
      } else if (includeDateInCells) {
        parts.push(inlineCell(4 + bIdx, rNum, formatBillCell(bill, true), 0));
      } else {
        parts.push(numCell(4 + bIdx, rNum, bill.price, 0));
      }
    }
    parts.push('</row>');
  });

  parts.push('</sheetData></worksheet>');
  return parts.join('');
}

function buildBillLedgerSheetXml(ledger: BillLedgerRow[]): string {
  const headers = [
    'Bill ID',
    'Date',
    'Supplier',
    'SKU Name (Raw)',
    'Matched SKU',
    'Quantity',
    'Unit',
    'Unit Price (INR)',
    'Subtotal (INR)',
    'Normalized Rate (INR)',
    'Status',
    'Notes',
  ];
  const parts: string[] = [
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
    '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">',
    '<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>',
    '<cols>',
    '<col min="1" max="1" width="18" customWidth="1"/>',
    '<col min="2" max="2" width="22" customWidth="1"/>',
    '<col min="3" max="5" width="26" customWidth="1"/>',
    '<col min="6" max="11" width="15" customWidth="1"/>',
    '<col min="12" max="12" width="30" customWidth="1"/>',
    '</cols>',
    '<sheetData>',
    '<row r="1">',
    ...headers.map((h, i) => inlineCell(i, 1, h, 1)),
    '</row>',
  ];

  ledger.forEach((l, idx) => {
    const rNum = idx + 2;
    const statusStyle = l.statusLabel === 'CHECK UNIT' ? 3 : 0;
    parts.push(`<row r="${rNum}">`);
    parts.push(inlineCell(0, rNum, l.orderId, 0));
    parts.push(inlineCell(1, rNum, l.dateFormatted, 0));
    parts.push(inlineCell(2, rNum, l.supplierName, 0));
    parts.push(inlineCell(3, rNum, l.rawItemName, 0));
    parts.push(inlineCell(4, rNum, l.matchedSkuName, 4));
    parts.push(numCell(5, rNum, l.quantity, 0));
    parts.push(inlineCell(6, rNum, l.unit, 0));
    parts.push(numCell(7, rNum, l.unitPrice, 0));
    parts.push(numCell(8, rNum, l.subtotal, 0));
    parts.push(numCell(9, rNum, l.normalizedPrice, 4));
    parts.push(inlineCell(10, rNum, l.statusLabel, statusStyle));
    parts.push(inlineCell(11, rNum, l.notes, 0));
    parts.push('</row>');
  });

  parts.push('</sheetData></worksheet>');
  return parts.join('');
}

function contentTypesXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;
}

function rootRelsXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;
}

function workbookRelsXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;
}

function workbookXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="SKU Price History" sheetId="1" r:id="rId1"/>
    <sheet name="Bill Ledger" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>`;
}

function stylesXml(): string {
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="5">
    <font><sz val="11"/><color rgb="FF18181B"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFB91C1C"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFB45309"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF15803D"/><name val="Calibri"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF09090B"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEE2E2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEF3C7"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="6">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"><font><b/></font></xf>
    <xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>`;
}

// CRC32 + Store ZIP Writer
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) c = (c & 1) ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[i] = c >>> 0;
  }
  return t;
})();

function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    c = CRC_TABLE[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  }
  return (c ^ 0xffffffff) >>> 0;
}

function createZipStore(entries: Array<{ name: string; data: Uint8Array }>): Uint8Array {
  const localParts: Uint8Array[] = [];
  const centralParts: Uint8Array[] = [];
  let offset = 0;

  for (const entry of entries) {
    const nameBytes = utf8(entry.name);
    const crc = crc32(entry.data);
    const size = entry.data.length;

    const localHeader = new Uint8Array(30 + nameBytes.length);
    const lv = new DataView(localHeader.buffer);
    lv.setUint32(0, 0x04034b50, true);
    lv.setUint16(4, 20, true);
    lv.setUint16(6, 0x0800, true); // UTF-8
    lv.setUint16(8, 0, true); // Store
    lv.setUint16(10, 0, true);
    lv.setUint16(12, 0x5421, true);
    lv.setUint32(14, crc, true);
    lv.setUint32(18, size, true);
    lv.setUint32(22, size, true);
    lv.setUint16(26, nameBytes.length, true);
    lv.setUint16(28, 0, true);
    localHeader.set(nameBytes, 30);

    localParts.push(localHeader, entry.data);

    const cd = new Uint8Array(46 + nameBytes.length);
    const cv = new DataView(cd.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint16(8, 0x0800, true);
    cv.setUint16(10, 0, true);
    cv.setUint16(12, 0, true);
    cv.setUint16(14, 0x5421, true);
    cv.setUint32(16, crc, true);
    cv.setUint32(20, size, true);
    cv.setUint32(24, size, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint16(30, 0, true);
    cv.setUint16(32, 0, true);
    cv.setUint16(34, 0, true);
    cv.setUint16(36, 0, true);
    cv.setUint32(38, 0, true);
    cv.setUint32(42, offset, true);
    cd.set(nameBytes, 46);
    centralParts.push(cd);

    offset += localHeader.length + size;
  }

  const cdSize = centralParts.reduce((s, p) => s + p.length, 0);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(4, 0, true);
  ev.setUint16(6, 0, true);
  ev.setUint16(8, entries.length, true);
  ev.setUint16(10, entries.length, true);
  ev.setUint32(12, cdSize, true);
  ev.setUint32(16, offset, true);
  ev.setUint16(20, 0, true);

  const totalLen = offset + cdSize + eocd.length;
  const out = new Uint8Array(totalLen);
  let pos = 0;
  for (const p of localParts) {
    out.set(p, pos);
    pos += p.length;
  }
  for (const p of centralParts) {
    out.set(p, pos);
    pos += p.length;
  }
  out.set(eocd, pos);
  return out;
}

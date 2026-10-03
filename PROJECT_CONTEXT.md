# FILTR Billing & Point of Sale (POS) - Project Context & Documentation

## Overview
**FILTR Billing** is a modern Jetpack Compose Android POS, Menu Management, Kitchen Display System (KDS), and Voice-enabled Order Management system designed for **FILTR Coffee**.

It provides offline-first local database storage (Room) synced seamlessly with Google Sheets via a custom Google Apps Script Webhook endpoint.

---

## Technical Stack & Architecture
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3 (Dark/Light mode support)
- **Local Persistence**: Room Database (`orders`, `order_items`, `menu_items`, `upi_accounts`)
- **State Management**: ViewModel with Kotlin `StateFlow` and Coroutines
- **Networking**: OkHttp & Retrofit for Google Sheets Webhook communication
- **AI Integration**: Gemini 2.5 Flash for AI Voice Order parsing
- **Remote Data Sync**: Google Apps Script (Apps Script Web Application v4.0)

---

## Core Capabilities & Features

### 1. Smart Order Creation & Billing
- **Interactive POS Grid**: Filter menu items by category ("All", "Espresso", "Filter Coffee", "Manual Brews", "Cold Coffee", "Snacks", "Bakery").
- **Custom Item Customizations**: Options for milk choice, sugar level, temperature preference, extra shots, etc.
- **Dynamic Order ID Generator**: Sequence-based format `FLT-YYYYMMDD-XXXX` (e.g., `FLT-20260805-1001`). Syncs sequence automatically with Google Sheets to prevent duplicate Order IDs across multiple devices or app reinstalls.
- **Customer & Payment Info**: Customer name, phone number, notes, payment status (`PAID` / `UNPAID`), payment mode (`CASH`, `UPI`), and custom UPI QR code generation based on configured UPI accounts.

### 2. Voice Order Assistant
- Natural voice ordering powered by Gemini 2.5 Flash.
- Auto-parses spoken orders into menu item matches, quantities, and customer instructions.

### 3. Kitchen Display System (KDS) & Order Management
- Real-time status toggling for Kitchen (`PREPARING` / `READY`) and Delivery (`PENDING` / `DELIVERED`).
- Item-level completion tracking with completion timestamps and turnaround time calculations.
- Deletion mechanism: Permanently deletes the order and its items from both the local Room database and Google Sheets (`Orders_Billing`, `Order_Items_Detail`, and `Filtered_Orders_Export` tabs).

### 4. Menu & Item Master Management
- Add, edit, disable, or delete menu items.
- Protects historical integrity: items with existing order records are marked unavailable rather than hard-deleted.
- One-tap "Sync Item Master to Google Sheets".

### 5. Google Sheets Sync Engine & Webhook Integration
- Auto-syncs new or updated orders in the background.
- Background retry loop every 60 seconds for offline/failed orders.
- Manual sync triggers for single orders, pending orders, today's orders, item master, and date range analytics.

---

## Webhook API Actions (Google Apps Script)

| Action (`action`) | HTTP Method | Description |
| :--- | :--- | :--- |
| `syncOrder` | POST / GET | Upserts single order into `Orders_Billing` and `Order_Items_Detail` |
| `deleteOrder` | POST / GET | Completely removes order and associated item rows from all sheets |
| `getTodayMaxOrderId` | GET / POST | Fetches highest sequence ID for today to ensure global sequence continuation |
| `syncItemMaster` | POST | Syncs full menu item master list into `Item_Master` tab |
| `syncBatch` / `syncAll` | POST | Syncs multiple orders safely |
| `filterRangeAnalytics` | POST / GET | Calculates revenue, cash/UPI split, and populates `Date_Range_Analytics` |

---

## Complete Google Apps Script (v4.0 - Deployable Code)

Below is the complete, full version of the Google Apps Script code to paste into **Google Sheets -> Extensions -> Apps Script -> Deploy as Web App** (Execute as: **Me**, Access: **Anyone**):

```javascript
// FILTR COFFEE - Google Apps Script Integration Code (v4.0 - Cloud Sequential & Sync)
// Paste into Google Sheets -> Extensions -> Apps Script -> Save -> Deploy as Web App (Execute as: Me, Access: Anyone)

function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var action = e && e.parameter ? e.parameter.action : "";
    if (action === "getTodayMaxOrderId" || action === "getLatestOrderInfo" || action === "getMaxSequence" || action === "get_max_sequence") {
      var datePrefix = e.parameter.date || e.parameter.datePrefix || "";
      var res = getTodayMaxOrderId(ss, datePrefix);
      return ContentService.createTextOutput(JSON.stringify(res))
        .setMimeType(ContentService.MimeType.JSON);
    }
    if (action === "deleteOrder" || action === "delete_order" || action === "delete") {
      var delOrderId = e.parameter ? (e.parameter.orderId || e.parameter.order_id || "") : "";
      if (delOrderId) {
        deleteOrderById(ss, delOrderId);
        return responseSuccess("Order deleted successfully");
      }
    }
    return ContentService.createTextOutput(JSON.stringify({result: "success", status: "Filtr Coffee Sync Webhook Active"}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({result: "error", message: err.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var postData = e.postData ? e.postData.contents : "";
    var data = {};
    if (postData) {
      try {
        data = JSON.parse(postData);
      } catch (pErr) {
        data = {};
      }
    }

    var action = (e && e.parameter && e.parameter.action) ? e.parameter.action : (data.action || "");

    if (action === "deleteOrder" || action === "delete_order" || action === "delete") {
      var delOrderId = data.orderId || data.order_id || (data.order && data.order.orderId) || (e && e.parameter ? (e.parameter.orderId || e.parameter.order_id) : "") || "";
      if (delOrderId) {
        deleteOrderById(ss, delOrderId);
        return responseSuccess("Order deleted successfully");
      }
    }

    if (action === "getTodayMaxOrderId" || action === "getLatestOrderInfo" || action === "getMaxSequence" || action === "get_max_sequence") {
      var res = getTodayMaxOrderId(ss, data.date || data.datePrefix || (e && e.parameter ? e.parameter.date : "") || "");
      return ContentService.createTextOutput(JSON.stringify(res))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (action === "syncItemMaster" || action === "sync_menu") {
      syncItemMaster(ss, data.menuItems || []);
      return responseSuccess("Item master synced successfully");
    }

    if (action === "syncBatch" || action === "syncPending" || action === "sync_batch") {
      syncBatchOrders(ss, data.orders || []);
      return responseSuccess("Batch orders synced successfully");
    }

    if (action === "syncAll" || action === "sync_all") {
      syncAllOrders(ss, data.orders || []);
      return responseSuccess("All orders synced successfully");
    }

    if (action === "filterRangeAnalytics" || action === "filter_range_analytics") {
      syncRangeAnalyticsSheet(ss, data.startDate, data.endDate, data.orders || []);
      return responseSuccess("Range analytics synced successfully");
    }

    if (action === "syncOrder" || action === "sync_order" || (data && (data.orderId || (data.order && data.order.orderId)))) {
      var singleOrder = data.order || data;
      if (singleOrder && singleOrder.orderId) {
        syncSingleOrder(ss, singleOrder);
        return responseSuccess("Order synced successfully");
      }
    }

    return responseSuccess("Webhook received request");
  } catch (err) {
    return responseError(err.toString());
  }
}

function responseSuccess(msg) {
  return ContentService.createTextOutput(JSON.stringify({result: "success", message: msg}))
    .setMimeType(ContentService.MimeType.JSON);
}

function responseError(msg) {
  return ContentService.createTextOutput(JSON.stringify({result: "error", message: msg}))
    .setMimeType(ContentService.MimeType.JSON);
}

function normalizeId(id) {
  if (id === null || id === undefined) return "";
  var s = String(id).trim().toLowerCase();
  if (s.indexOf("'") === 0) s = s.substring(1).trim();
  s = s.replace(/^order\s*#?/i, "").replace(/^flt-?/i, "").replace(/^#/i, "").trim();
  return s;
}

function syncSingleOrder(ss, data) {
  if (!data || !data.orderId) return;

  var targetOrderId = normalizeId(data.orderId);
  if (!targetOrderId) return;

  var billingSheet = getOrCreateSheet(ss, "Orders_Billing", [
    "Order ID", "Timestamp", "Customer Name", "Contact Number", "Total Amount", "Payment Status", "Payment Mode", "Kitchen Status", "Delivery Status", "Notes"
  ]);
  
  var itemsSheet = getOrCreateSheet(ss, "Order_Items_Detail", [
    "Order ID", "Item Name", "Category", "Unit Price", "Quantity", "Subtotal", "Completed Qty", "Item Status", "Completed Timestamp", "Turnaround Time"
  ]);

  var formattedOrderId = "'" + String(data.orderId).trim();

  var nowStr = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm:ss");
  var orderTimestamp = data.timestamp || "";
  var billingRowIndex = findRowByOrderId(billingSheet, targetOrderId, 1);

  if (!orderTimestamp && billingRowIndex > 1) {
    var existingTs = billingSheet.getRange(billingRowIndex, 2).getDisplayValue();
    if (existingTs && existingTs !== "null" && existingTs !== "undefined") {
      orderTimestamp = existingTs;
    }
  }

  if (!orderTimestamp) {
    orderTimestamp = nowStr;
  }

  var rowData = [
    formattedOrderId,
    orderTimestamp,
    data.customerName || "Walk-in Customer",
    data.contactNumber || "N/A",
    Number(data.totalAmount || 0),
    (data.isPaid === "YES" || data.isPaid === true || data.isPaid === "PAID") ? "PAID" : "UNPAID",
    data.paymentMode || ((data.isPaid === "YES" || data.isPaid === true || data.isPaid === "PAID") ? "CASH" : "UNPAID"),
    (data.isMade === "YES" || data.isMade === true || data.isMade === "READY") ? "READY" : "PREPARING",
    (data.isDelivered === "YES" || data.isDelivered === true || data.isDelivered === "DELIVERED") ? "DELIVERED" : "PENDING",
    data.notes || ""
  ];

  // Upsert into Orders_Billing (In-Place Update or Append)
  if (billingRowIndex > 1) {
    billingSheet.getRange(billingRowIndex, 1, 1, rowData.length).setValues([rowData]);
  } else {
    billingSheet.appendRow(rowData);
  }

  // Upsert into Order_Items_Detail (In-Place Row/Cell Update or Append)
  var items = data.items || [];
  
  // Find all existing item rows in Order_Items_Detail for this Order ID
  var existingItemRowsMap = {};
  var itemsLastRow = itemsSheet.getLastRow();
  if (itemsLastRow > 1) {
    var itemsData = itemsSheet.getRange(2, 1, itemsLastRow - 1, 2).getDisplayValues();
    for (var r = 0; r < itemsData.length; r++) {
      var rowOrderId = normalizeId(itemsData[r][0]);
      if (rowOrderId === targetOrderId) {
        var rowItemName = String(itemsData[r][1] || "").trim().toLowerCase();
        if (rowItemName) {
          existingItemRowsMap[rowItemName] = r + 2; // Row index in sheet (1-based)
        }
      }
    }
  }

  for (var i = 0; i < items.length; i++) {
    var item = items[i];
    var name = item.itemName || item.name || "Item";
    var cat = item.category || "General";
    var unitPrice = Number(item.unitPrice !== undefined ? item.unitPrice : (item.price || 0));
    var qty = Number(item.quantity !== undefined ? item.quantity : (item.qty || 1));
    var subtotal = Number(item.subtotal !== undefined ? item.subtotal : (unitPrice * qty));
    var completedQty = Number(item.completedQuantity !== undefined ? item.completedQuantity : (item.isCompleted ? qty : 0));
    var completedTs = item.completedTimestamp || "";
    var itemStatus = item.itemStatus || (completedQty >= qty ? "READY" : (completedQty > 0 ? "PARTIAL" : "PREPARING"));
    var turnaroundTime = item.turnaroundTime || "";

    var itemRowData = [
      formattedOrderId,
      name,
      cat,
      unitPrice,
      qty,
      subtotal,
      completedQty,
      itemStatus,
      completedTs,
      turnaroundTime
    ];

    var normalizedNameKey = String(name).trim().toLowerCase();
    var existingRowIdx = existingItemRowsMap[normalizedNameKey];

    if (existingRowIdx && existingRowIdx > 1) {
      // Update exact row in-place without deleting or re-adding!
      itemsSheet.getRange(existingRowIdx, 1, 1, itemRowData.length).setValues([itemRowData]);
    } else {
      // Append new row if item wasn't previously present
      itemsSheet.appendRow(itemRowData);
    }
  }
}

function syncBatchOrders(ss, orders) {
  for (var o = 0; o < orders.length; o++) {
    syncSingleOrder(ss, orders[o]);
  }
}

function syncAllOrders(ss, orders) {
  var billingSheet = getOrCreateSheet(ss, "Orders_Billing", [
    "Order ID", "Timestamp", "Customer Name", "Contact Number", "Total Amount", "Payment Status", "Payment Mode", "Kitchen Status", "Delivery Status", "Notes"
  ]);
  
  var itemsSheet = getOrCreateSheet(ss, "Order_Items_Detail", [
    "Order ID", "Item Name", "Category", "Unit Price", "Quantity", "Subtotal", "Completed Qty", "Item Status", "Completed Timestamp", "Turnaround Time"
  ]);

  for (var o = 0; o < orders.length; o++) {
    syncSingleOrder(ss, orders[o]);
  }

  cleanBlankRows(billingSheet);
  cleanBlankRows(itemsSheet);
}

function deleteOrderById(ss, orderId) {
  var targetOrderId = normalizeId(orderId);
  if (!targetOrderId) return;

  var billingSheet = ss.getSheetByName("Orders_Billing");
  var itemsSheet = ss.getSheetByName("Order_Items_Detail");
  var filteredSheet = ss.getSheetByName("Filtered_Orders_Export");

  if (billingSheet) removeAllRowsByOrderId(billingSheet, targetOrderId, 1);
  if (itemsSheet) removeAllRowsByOrderId(itemsSheet, targetOrderId, 1);
  if (filteredSheet) removeAllRowsByOrderId(filteredSheet, targetOrderId, 1);

  if (billingSheet) cleanBlankRows(billingSheet);
  if (itemsSheet) cleanBlankRows(itemsSheet);
  if (filteredSheet) cleanBlankRows(filteredSheet);
}

function syncItemMaster(ss, menuItems) {
  var menuSheet = getOrCreateSheet(ss, "Item_Master", [
    "Item ID", "Item Name", "Category", "Price (₹)", "Description", "Status"
  ]);

  clearSheetData(menuSheet);

  for (var i = 0; i < menuItems.length; i++) {
    var m = menuItems[i];
    menuSheet.appendRow([
      m.id || (i + 1),
      m.name,
      m.category,
      Number(m.price || 0),
      m.description || "",
      m.isAvailable ? "Available" : "Unavailable"
    ]);
  }
}

function getOrCreateSheet(ss, sheetName, headers) {
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold").setBackground("#09090B").setFontColor("#FFFFFF");
  } else {
    var lastRow = sheet.getLastRow();
    if (lastRow === 0) {
      sheet.appendRow(headers);
      sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold").setBackground("#09090B").setFontColor("#FFFFFF");
    } else {
      if (sheet.getMaxColumns() < headers.length) {
        sheet.insertColumnsAfter(sheet.getMaxColumns(), headers.length - sheet.getMaxColumns());
      }
      sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
      sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold").setBackground("#09090B").setFontColor("#FFFFFF");
    }
  }
  return sheet;
}

function clearSheetData(sheet) {
  var lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    sheet.deleteRows(2, lastRow - 1);
  }
}

function findRowByOrderId(sheet, targetOrderId, colIndex) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return -1;
  var displayValues = sheet.getRange(1, colIndex, lastRow, 1).getDisplayValues();
  for (var r = 1; r < displayValues.length; r++) {
    var cellText = normalizeId(displayValues[r][0]);
    if (cellText && targetOrderId && cellText === targetOrderId) {
      return r + 1;
    }
  }
  return -1;
}

function removeAllRowsByOrderId(sheet, targetOrderId, colIndex) {
  if (!sheet) return;
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return;

  var normTarget = normalizeId(targetOrderId);
  if (!normTarget) return;

  var displayValues = sheet.getRange(1, colIndex, lastRow, 1).getDisplayValues();

  for (var r = displayValues.length - 1; r >= 1; r--) {
    var rawVal = displayValues[r][0];
    var cellText = normalizeId(rawVal);
    if (cellText) {
      var isExactMatch = (cellText === normTarget);
      var isSubMatch = (cellText.length >= 3 && normTarget.length >= 3 && (cellText.indexOf(normTarget) >= 0 || (normTarget.indexOf(cellText) >= 0 && cellText.indexOf("-") >= 0)));
      if (isExactMatch || isSubMatch) {
        sheet.deleteRow(r + 1);
      }
    }
  }
}

function cleanBlankRows(sheet) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return;
  var displayValues = sheet.getRange(1, 1, lastRow, 1).getDisplayValues();
  for (var r = displayValues.length - 1; r >= 1; r--) {
    var cellText = normalizeId(displayValues[r][0]);
    if (!cellText || cellText === "null" || cellText === "undefined") {
      sheet.deleteRow(r + 1);
    }
  }
}

function syncRangeAnalyticsSheet(ss, startDate, endDate, orders) {
  var rangeSheet = getOrCreateSheet(ss, "Date_Range_Analytics", [
    "Filter Start Date", "Filter End Date", "Total Orders", "Total Revenue (₹)", "Cash Revenue (₹)", "UPI Revenue (₹)", "Unpaid Revenue (₹)", "Calculated At"
  ]);

  var orderList = orders || [];

  if (orderList.length === 0) {
    var billingSheet = ss.getSheetByName("Orders_Billing");
    if (billingSheet && billingSheet.getLastRow() > 1) {
      var billingData = billingSheet.getRange(2, 1, billingSheet.getLastRow() - 1, 10).getDisplayValues();
      var startClean = (startDate || "").replace(/-/g, "").trim();
      var endClean = (endDate || "").replace(/-/g, "").trim();

      for (var r = 0; r < billingData.length; r++) {
        var row = billingData[r];
        var rawOrderId = normalizeId(row[0]);
        if (!rawOrderId) continue;

        var dateMatch = rawOrderId.match(/(\d{8})/);
        var orderDateNum = dateMatch ? dateMatch[1] : "";

        var isMatch = true;
        if (startClean && orderDateNum && orderDateNum < startClean) isMatch = false;
        if (endClean && orderDateNum && orderDateNum > endClean) isMatch = false;

        if (isMatch) {
          orderList.push({
            orderId: row[0].replace(/^'/, ''),
            timestamp: row[1] || "",
            customerName: row[2] || "Walk-in Customer",
            contactNumber: row[3] || "N/A",
            totalAmount: parseFloat(row[4]) || 0,
            isPaid: row[5] === "PAID" ? "YES" : "NO",
            paymentMode: row[6] || "UNPAID",
            isMade: row[7] === "READY" ? "YES" : "NO",
            isDelivered: row[8] === "DELIVERED" ? "YES" : "NO",
            notes: row[9] || ""
          });
        }
      }
    }
  }

  var totalRev = 0;
  var cashRev = 0;
  var upiRev = 0;
  var unpaidRev = 0;

  for (var i = 0; i < orderList.length; i++) {
    var amt = Number(orderList[i].totalAmount || 0);
    totalRev += amt;
    var mode = String(orderList[i].paymentMode || "").toUpperCase();
    var isPaid = String(orderList[i].isPaid || "").toUpperCase();

    if (isPaid === "YES" || isPaid === "PAID" || mode === "CASH" || mode === "UPI") {
      if (mode === "CASH") {
        cashRev += amt;
      } else if (mode === "UPI") {
        upiRev += amt;
      } else {
        cashRev += amt;
      }
    } else {
      unpaidRev += amt;
    }
  }

  rangeSheet.appendRow([
    startDate || "ALL",
    endDate || "ALL",
    orderList.length,
    totalRev,
    cashRev,
    upiRev,
    unpaidRev,
    new Date().toLocaleString()
  ]);

  var filteredOrdersSheet = getOrCreateSheet(ss, "Filtered_Orders_Export", [
    "Order ID", "Timestamp", "Customer Name", "Contact Number", "Total Amount", "Payment Status", "Payment Mode", "Kitchen Status"
  ]);
  clearSheetData(filteredOrdersSheet);

  for (var o = 0; o < orderList.length; o++) {
    var ord = orderList[o];
    filteredOrdersSheet.appendRow([
      "'" + String(ord.orderId).trim(),
      ord.timestamp,
      ord.customerName || "Walk-in Customer",
      ord.contactNumber || "N/A",
      Number(ord.totalAmount || 0),
      (ord.isPaid === "YES" || ord.isPaid === "PAID") ? "PAID" : "UNPAID",
      ord.paymentMode || ((ord.isPaid === "YES" || ord.isPaid === "PAID") ? "CASH" : "UNPAID"),
      (ord.isMade === "YES" || ord.isMade === "READY") ? "READY" : "PREPARING"
    ]);
  }
}

function onOpen() {
  var ui = SpreadsheetApp.getUi();
  ui.createMenu('☕ FILTR Store Analytics')
    .addItem('📊 Run Date Range Analytics', 'menuRunRangeAnalytics')
    .addItem('🔄 Refresh Today Analytics', 'menuRefreshTodayAnalytics')
    .addToUi();
}

function menuRunRangeAnalytics() {
  var ui = SpreadsheetApp.getUi();
  var startPrompt = ui.prompt('Date Range Analytics', 'Enter Start Date (YYYY-MM-DD): e.g. 2026-08-03', ui.ButtonSet.OK_CANCEL);
  if (startPrompt.getSelectedButton() !== ui.Button.OK) return;
  var startDate = startPrompt.getResponseText().trim();

  var endPrompt = ui.prompt('Date Range Analytics', 'Enter End Date (YYYY-MM-DD): e.g. 2026-08-04', ui.ButtonSet.OK_CANCEL);
  if (endPrompt.getSelectedButton() !== ui.Button.OK) return;
  var endDate = endPrompt.getResponseText().trim();

  var ss = SpreadsheetApp.getActiveSpreadsheet();
  syncRangeAnalyticsSheet(ss, startDate, endDate, []);
  ui.alert('Analytics generated successfully in Date_Range_Analytics & Filtered_Orders_Export tabs!');
}

function menuRefreshTodayAnalytics() {
  var ui = SpreadsheetApp.getUi();
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var todayStr = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd");
  syncRangeAnalyticsSheet(ss, todayStr, todayStr, []);
  ui.alert('Today analytics refreshed successfully!');
}

function getTodayMaxOrderId(ss, datePrefix) {
  var billingSheet = ss.getSheetByName("Orders_Billing");
  var itemsSheet = ss.getSheetByName("Order_Items_Detail");

  var maxSequence = 1000;
  var maxOrderId = "";
  var todayOrdersMap = {};

  if (!billingSheet) {
    return {
      result: "success",
      datePrefix: datePrefix,
      maxSequence: 1000,
      maxOrderId: "",
      todayOrders: []
    };
  }

  var lastRow = billingSheet.getLastRow();
  if (lastRow > 1) {
    var billingData = billingSheet.getRange(2, 1, lastRow - 1, 10).getDisplayValues();
    for (var r = 0; r < billingData.length; r++) {
      var row = billingData[r];
      var rawOrderId = normalizeId(row[0]);
      if (!rawOrderId) continue;

      var parts = rawOrderId.split("-");
      if (parts.length >= 2) {
        var seqNum = parseInt(parts[parts.length - 1].trim(), 10);
        if (!isNaN(seqNum)) {
          if (!datePrefix || rawOrderId.indexOf(datePrefix) >= 0) {
            if (seqNum > maxSequence) {
              maxSequence = seqNum;
              maxOrderId = rawOrderId;
            }
          }
        }
      }

      if (!datePrefix || rawOrderId.indexOf(datePrefix) >= 0) {
        todayOrdersMap[rawOrderId] = {
          orderId: row[0].replace(/^'/, ''),
          timestamp: row[1] || "",
          customerName: row[2] || "Walk-in Customer",
          contactNumber: row[3] || "N/A",
          totalAmount: parseFloat(row[4]) || 0,
          isPaid: row[5] === "PAID" ? "YES" : "NO",
          paymentMode: row[6] || "UNPAID",
          isMade: row[7] === "READY" ? "YES" : "NO",
          isDelivered: row[8] === "DELIVERED" ? "YES" : "NO",
          notes: row[9] || "",
          items: []
        };
      }
    }
  }

  if (itemsSheet && itemsSheet.getLastRow() > 1) {
    var itemsData = itemsSheet.getRange(2, 1, itemsSheet.getLastRow() - 1, 6).getDisplayValues();
    for (var i = 0; i < itemsData.length; i++) {
      var itemRow = itemsData[i];
      var itemOrderId = normalizeId(itemRow[0]);
      if (todayOrdersMap[itemOrderId]) {
        todayOrdersMap[itemOrderId].items.push({
          itemName: itemRow[1] || "Item",
          category: itemRow[2] || "General",
          unitPrice: parseFloat(itemRow[3]) || 0,
          quantity: parseInt(itemRow[4], 10) || 1,
          subtotal: parseFloat(itemRow[5]) || 0
        });
      }
    }
  }

  var todayOrdersList = [];
  for (var k in todayOrdersMap) {
    todayOrdersList.push(todayOrdersMap[k]);
  }

  return {
    result: "success",
    datePrefix: datePrefix,
    maxSequence: maxSequence,
    maxOrderId: maxOrderId,
    todayOrders: todayOrdersList
  };
}
```

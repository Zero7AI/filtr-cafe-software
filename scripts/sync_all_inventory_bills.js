// Comprehensive Inventory Sync & Backfill Script
// Syncs all bills after 17 September 2026 into Supabase inventory_orders, inventory_order_items, and whatsapp_messages.

const SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co";
const SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM";

const headers = {
  apikey: SERVICE_ROLE_KEY,
  Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
  "Content-Type": "application/json",
};

const ordersToSync = [
  {
    order: {
      order_id: "INV-20260917-001",
      timestamp: 1789663902293, // 17-Sep-2026 22:21 IST
      supplier_name: "WhatsApp Order",
      total_amount: 810.0,
      notes: "Via WhatsApp | Ice cream 2*405 - 810rs",
      sync_status: "SYNCED",
    },
    items: [
      {
        order_id: "INV-20260917-001",
        item_name: "Ice cream",
        quantity: 2,
        unit: "pcs",
        unit_price: 405.0,
        subtotal: 810.0,
      },
    ],
    messageIds: [90],
    parsedResult: {
      is_inventory: true,
      supplier_name: "WhatsApp Order",
      items: [
        {
          item_name: "Ice cream",
          quantity: 2,
          unit: "pcs",
          unit_price: 405.0,
          subtotal: 810.0,
        },
      ],
      total_amount: 810.0,
      notes: "Ice cream 2*405 - 810rs",
    },
  },
  {
    order: {
      order_id: "INV-20260918-001",
      timestamp: 1789710660000, // 18-Sep-2026 11:21 IST
      supplier_name: "The Beanster Company Raipur",
      total_amount: 13650.0,
      notes: "Via WhatsApp | Tax Invoice R26/725 dated 16-09-2026 | 20kg Roasted coffee beans @ ₹650/kg (₹13,000) + 5% GST (₹650) = ₹13,650",
      sync_status: "SYNCED",
    },
    items: [
      {
        order_id: "INV-20260918-001",
        item_name: "Roasted coffee beans",
        quantity: 20,
        unit: "kg",
        unit_price: 682.5,
        subtotal: 13650.0,
      },
    ],
    messageIds: [95, 96, 97],
    parsedResult: {
      is_inventory: true,
      supplier_name: "The Beanster Company Raipur",
      invoice_no: "R26/725",
      invoice_date: "16-09-2026",
      items: [
        {
          item_name: "Roasted coffee beans",
          quantity: 20,
          unit: "kg",
          unit_price: 682.5,
          subtotal: 13650.0,
        },
      ],
      total_amount: 13650.0,
      notes: "Invoice R26/725 dated 16-09-2026, GSTIN: 22CLFPM4979N1ZG",
    },
  },
  {
    order: {
      order_id: "INV-20260918-002",
      timestamp: 1789721678928, // 18-Sep-2026 14:24 IST
      supplier_name: "WhatsApp Order",
      total_amount: 270.0,
      notes: "Via WhatsApp | Ice 3 packet - 270rs",
      sync_status: "SYNCED",
    },
    items: [
      {
        order_id: "INV-20260918-002",
        item_name: "Ice",
        quantity: 3,
        unit: "packet",
        unit_price: 90.0,
        subtotal: 270.0,
      },
    ],
    messageIds: [98],
    parsedResult: {
      is_inventory: true,
      supplier_name: "WhatsApp Order",
      items: [
        {
          item_name: "Ice",
          quantity: 3,
          unit: "packet",
          unit_price: 90.0,
          subtotal: 270.0,
        },
      ],
      total_amount: 270.0,
      notes: "Ice 3 packet - 270rs",
    },
  },
  {
    order: {
      order_id: "INV-20260918-003",
      timestamp: 1789740569166, // 18-Sep-2026 19:39 IST
      supplier_name: "WhatsApp Order",
      total_amount: 1060.0,
      notes: "Via WhatsApp | Handwritten notepad slip dated 16/09/26 | TNT Vanilla (2x400=800), Caramel Sauce PAMAC 500ml (2x130=260)",
      sync_status: "SYNCED",
    },
    items: [
      {
        order_id: "INV-20260918-003",
        item_name: "TNT Vanilla",
        quantity: 2,
        unit: "pcs",
        unit_price: 400.0,
        subtotal: 800.0,
      },
      {
        order_id: "INV-20260918-003",
        item_name: "Caramel Sauce PAMAC (500ml)",
        quantity: 2,
        unit: "pcs",
        unit_price: 130.0,
        subtotal: 260.0,
      },
    ],
    messageIds: [99],
    parsedResult: {
      is_inventory: true,
      supplier_name: "WhatsApp Order",
      items: [
        {
          item_name: "TNT Vanilla",
          quantity: 2,
          unit: "pcs",
          unit_price: 400.0,
          subtotal: 800.0,
        },
        {
          item_name: "Caramel Sauce PAMAC (500ml)",
          quantity: 2,
          unit: "pcs",
          unit_price: 130.0,
          subtotal: 260.0,
        },
      ],
      total_amount: 1060.0,
      notes: "Handwritten notepad bill dated 16/09/26",
    },
  },
  {
    order: {
      order_id: "INV-20260919-001",
      timestamp: 1789808977735, // 19-Sep-2026 14:39 IST
      supplier_name: "Supermarket (Heritage School)",
      total_amount: 2345.0,
      notes: "Via WhatsApp | Tax Invoice 490209015-003085 dated 19/09/2026 | Paid via UPI",
      sync_status: "SYNCED",
    },
    items: [
      {
        order_id: "INV-20260919-001",
        item_name: "Balaji Masala 200g",
        quantity: 1,
        unit: "pcs",
        unit_price: 36.0,
        subtotal: 36.0,
      },
      {
        order_id: "INV-20260919-001",
        item_name: "Shaad Sunflower Oil 800g",
        quantity: 12,
        unit: "pcs",
        unit_price: 152.0,
        subtotal: 1824.0,
      },
      {
        order_id: "INV-20260919-001",
        item_name: "Lotus Biscoff C 75g",
        quantity: 1,
        unit: "pcs",
        unit_price: 27.0,
        subtotal: 27.0,
      },
      {
        order_id: "INV-20260919-001",
        item_name: "Yoga Bar Nuts N 35g",
        quantity: 1,
        unit: "pcs",
        unit_price: 25.0,
        subtotal: 25.0,
      },
      {
        order_id: "INV-20260919-001",
        item_name: "Giffy Lemon&S 750ml",
        quantity: 1,
        unit: "pcs",
        unit_price: 154.0,
        subtotal: 154.0,
      },
      {
        order_id: "INV-20260919-001",
        item_name: "PL Store King 3000",
        quantity: 1,
        unit: "pcs",
        unit_price: 279.0,
        subtotal: 279.0,
      },
    ],
    messageIds: [100],
    parsedResult: {
      is_inventory: true,
      supplier_name: "Supermarket (Heritage School)",
      invoice_no: "490209015-003085",
      invoice_date: "19/09/2026",
      items: [
        { item_name: "Balaji Masala 200g", quantity: 1, unit: "pcs", unit_price: 36.0, subtotal: 36.0 },
        { item_name: "Shaad Sunflower Oil 800g", quantity: 12, unit: "pcs", unit_price: 152.0, subtotal: 1824.0 },
        { item_name: "Lotus Biscoff C 75g", quantity: 1, unit: "pcs", unit_price: 27.0, subtotal: 27.0 },
        { item_name: "Yoga Bar Nuts N 35g", quantity: 1, unit: "pcs", unit_price: 25.0, subtotal: 25.0 },
        { item_name: "Giffy Lemon&S 750ml", quantity: 1, unit: "pcs", unit_price: 154.0, subtotal: 154.0 },
        { item_name: "PL Store King 3000", quantity: 1, unit: "pcs", unit_price: 279.0, subtotal: 279.0 },
      ],
      total_amount: 2345.0,
      notes: "Tax Invoice 490209015-003085 dated 19/09/2026, UPI Payment: 2345.00",
    },
  },
  {
    order: {
      order_id: "INV-20260919-002",
      timestamp: 1789838515158, // 19-Sep-2026 22:51 IST
      supplier_name: "KE ESTIMATE 25",
      total_amount: 1275.0,
      notes: "Via WhatsApp | Invoice 3091 dated 19-Sep-26 | Consignee: FILTR COFFEE, NEHRU NAGAR | Total 45 PKT",
      sync_status: "SYNCED",
    },
    items: [
      {
        order_id: "INV-20260919-002",
        item_name: "Parricot 8 Oz Double Wall Cups",
        quantity: 3,
        unit: "PKT",
        unit_price: 65.0,
        subtotal: 195.0,
      },
      {
        order_id: "INV-20260919-002",
        item_name: "Aam Tissue (120)",
        quantity: 20,
        unit: "PKT",
        unit_price: 9.0,
        subtotal: 180.0,
      },
      {
        order_id: "INV-20260919-002",
        item_name: "Thick Straw Black 8mm",
        quantity: 10,
        unit: "PKT",
        unit_price: 30.0,
        subtotal: 300.0,
      },
      {
        order_id: "INV-20260919-002",
        item_name: "Thick Straw Black 12mm",
        quantity: 10,
        unit: "PKT",
        unit_price: 40.0,
        subtotal: 400.0,
      },
      {
        order_id: "INV-20260919-002",
        item_name: "Dinearth 1D Tray Plate",
        quantity: 2,
        unit: "PKT",
        unit_price: 100.0,
        subtotal: 200.0,
      },
    ],
    messageIds: [101],
    parsedResult: {
      is_inventory: true,
      supplier_name: "KE ESTIMATE 25",
      invoice_no: "3091",
      invoice_date: "19-Sep-26",
      items: [
        { item_name: "Parricot 8 Oz Double Wall Cups", quantity: 3, unit: "PKT", unit_price: 65.0, subtotal: 195.0 },
        { item_name: "Aam Tissue (120)", quantity: 20, unit: "PKT", unit_price: 9.0, subtotal: 180.0 },
        { item_name: "Thick Straw Black 8mm", quantity: 10, unit: "PKT", unit_price: 30.0, subtotal: 300.0 },
        { item_name: "Thick Straw Black 12mm", quantity: 10, unit: "PKT", unit_price: 40.0, subtotal: 400.0 },
        { item_name: "Dinearth 1D Tray Plate", quantity: 2, unit: "PKT", unit_price: 100.0, subtotal: 200.0 },
      ],
      total_amount: 1275.0,
      notes: "Invoice 3091 dated 19-Sep-26 | FILTR COFFEE NEHRU NAGAR",
    },
  },
  {
    order: {
      order_id: "INV-20260920-001",
      timestamp: 1789854121999, // 20-Sep-2026 03:11 IST
      supplier_name: "SHRI-AMRAPUR",
      total_amount: 1350.0,
      notes: "Via WhatsApp | Estimate A008716 dated 19/09/2026 | Party: FILTR COFFEE | Credit bill",
      sync_status: "SYNCED",
    },
    items: [
      {
        order_id: "INV-20260920-001",
        item_name: "Nutralite Butter 100*5",
        quantity: 5,
        unit: "PKT",
        unit_price: 90.0,
        subtotal: 450.0,
      },
      {
        order_id: "INV-20260920-001",
        item_name: "French Fries 2.5kg 9mm-IB",
        quantity: 2,
        unit: "PCS",
        unit_price: 270.0,
        subtotal: 540.0,
      },
      {
        order_id: "INV-20260920-001",
        item_name: "Paneer Fresh 1kg",
        quantity: 1,
        unit: "PCS",
        unit_price: 360.0,
        subtotal: 360.0,
      },
    ],
    messageIds: [102],
    parsedResult: {
      is_inventory: true,
      supplier_name: "SHRI-AMRAPUR",
      invoice_no: "A008716",
      invoice_date: "19/09/2026",
      items: [
        { item_name: "Nutralite Butter 100*5", quantity: 5, unit: "PKT", unit_price: 90.0, subtotal: 450.0 },
        { item_name: "French Fries 2.5kg 9mm-IB", quantity: 2, unit: "PCS", unit_price: 270.0, subtotal: 540.0 },
        { item_name: "Paneer Fresh 1kg", quantity: 1, unit: "PCS", unit_price: 360.0, subtotal: 360.0 },
      ],
      total_amount: 1350.0,
      notes: "Estimate A008716 dated 19/09/2026 | FILTR COFFEE",
    },
  },
];

async function syncAll() {
  console.log("🚀 Starting inventory orders sync & recovery...");

  for (const entry of ordersToSync) {
    const { order, items, messageIds, parsedResult } = entry;
    console.log(`\n📦 Processing ${order.order_id} (₹${order.total_amount}) from ${order.supplier_name}...`);

    // 1. Upsert order header
    const orderResp = await fetch(`${SUPABASE_URL}/rest/v1/inventory_orders`, {
      method: "POST",
      headers: {
        ...headers,
        Prefer: "resolution=merge-duplicates,return=representation",
      },
      body: JSON.stringify(order),
    });

    if (!orderResp.ok) {
      const err = await orderResp.text();
      console.error(`❌ Failed to insert order ${order.order_id}:`, err);
      continue;
    }
    console.log(`  ✅ Header upserted: ${order.order_id}`);

    // 2. Delete existing items for clean replace
    await fetch(`${SUPABASE_URL}/rest/v1/inventory_order_items?order_id=eq.${order.order_id}`, {
      method: "DELETE",
      headers,
    });

    // 3. Insert items
    const itemsResp = await fetch(`${SUPABASE_URL}/rest/v1/inventory_order_items`, {
      method: "POST",
      headers: {
        ...headers,
        Prefer: "return=representation",
      },
      body: JSON.stringify(items),
    });

    if (!itemsResp.ok) {
      const err = await itemsResp.text();
      console.error(`❌ Failed to insert items for ${order.order_id}:`, err);
    } else {
      const insertedItems = await itemsResp.json();
      console.log(`  ✅ Inserted ${insertedItems.length} line item(s).`);
    }

    // 4. Update corresponding whatsapp_messages
    for (const msgId of messageIds) {
      const msgResp = await fetch(`${SUPABASE_URL}/rest/v1/whatsapp_messages?id=eq.${msgId}`, {
        method: "PATCH",
        headers: {
          ...headers,
          Prefer: "return=representation",
        },
        body: JSON.stringify({
          status: "saved",
          inventory_order_id: order.order_id,
          parsed_result: parsedResult,
          error_message: null,
        }),
      });

      if (msgResp.ok) {
        console.log(`  ✅ WhatsApp message #${msgId} linked to ${order.order_id} (status: saved)`);
      } else {
        const err = await msgResp.text();
        console.error(`  ❌ Failed to update WhatsApp message #${msgId}:`, err);
      }
    }
  }

  console.log("\n✨ Sync completed! Verifying database state...");
  const verifyResp = await fetch(
    `${SUPABASE_URL}/rest/v1/inventory_orders?select=*,inventory_order_items(*)&order=order_id.asc`,
    { headers }
  );
  const allOrders = await verifyResp.json();
  console.log(`Total inventory orders in DB: ${allOrders.length}`);
  allOrders.filter(o => o.order_id >= "INV-20260917").forEach(o => {
    console.log(`- ${o.order_id} | ${o.supplier_name} | ₹${o.total_amount} | ${o.inventory_order_items?.length} items`);
  });
}

syncAll().catch(console.error);

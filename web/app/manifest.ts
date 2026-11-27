import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'FILTR Coffee — Admin & POS',
    short_name: 'FILTR Admin',
    description:
      'FILTR Coffee Admin Command Center, Live KDS, P&L Analytics, Inventory, Attendance & Tablet POS',
    start_url: '/admin',
    display: 'standalone',
    background_color: '#09090b',
    theme_color: '#f59e0b',
    orientation: 'any',
    icons: [
      {
        src: '/icons/icon-192.svg',
        sizes: '192x192',
        type: 'image/svg+xml',
        purpose: 'any',
      },
      {
        src: '/icons/icon-512.svg',
        sizes: '512x512',
        type: 'image/svg+xml',
        purpose: 'maskable',
      },
    ],
  };
}

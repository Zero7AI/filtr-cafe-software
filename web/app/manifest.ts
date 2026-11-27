import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'FILTR Coffee — Admin Analytics',
    short_name: 'FILTR Admin',
    description:
      'FILTR Coffee Admin Command Center: Sales Analytics, P&L, SKU Price History, Exact Ingredient Usage, Live KDS, Kitchen Stock & Attendance',
    start_url: '/',
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

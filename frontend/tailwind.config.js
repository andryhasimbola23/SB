/** @type {import('tailwindcss').Config} */

/*
 * CONSTITUTION DE COULEUR — 3 teintes d'accent AU MAXIMUM :
 *   - vert  (brand)  #009640 — actions primaires, états actifs / sélection
 *   - rouge (danger) #E30613 — destructif et statuts d'erreur
 *   - ambre (warning)        — attention / en attente
 * Tout le reste est neutre (slate + tokens surface / ink / line).
 * Toute nouvelle teinte (bleu, violet, émeraude…) est interdite :
 * elle se superposerait à l'une des trois catégories ci-dessus.
 * Les survols d'action vivent dans `index.css` (.act-quiet, .act-danger…).
 */
const brandGreen = '#009640';
const brandRed = '#E30613';

export default {
    content: [
        './index.html',
        './src/**/*.{js,ts,jsx,tsx}',
    ],
    darkMode: 'class', // activé via <html class="dark">
    theme: {
        extend: {
            colors: {
                // --- Marque (rampes complètes) ---
                brand: {
                    50: '#e6f4ec',
                    100: '#c4e6d4',
                    200: '#8fd2af',
                    300: '#4dbb84',
                    400: '#1aa25f',
                    500: brandGreen,
                    600: '#007c35',
                    700: '#00632b',
                    800: '#004a20',
                    900: '#003116',
                },
                danger: {
                    50: '#fdeaea',
                    100: '#fbcfcf',
                    200: '#f79f9f',
                    300: '#f26f6f',
                    400: '#ee3f3f',
                    500: brandRed,
                    600: '#c00511',
                    700: '#97040d',
                    800: '#6e030a',
                    900: '#450206',
                },
                // --- Sémantique : piloté par variables CSS (clair/sombre) ---
                surface: 'var(--surface)',
                'surface-2': 'var(--surface-2)',
                'surface-3': 'var(--surface-3)',
                ink: {
                    DEFAULT: 'var(--text)',
                    muted: 'var(--text-muted)',
                    subtle: 'var(--text-subtle)',
                    inverse: 'var(--text-inverse)',
                },
                line: {
                    DEFAULT: 'var(--border)',
                    strong: 'var(--border-strong)',
                },
            },
            fontFamily: {
                sans: ['Montserrat', 'Segoe UI', 'Roboto', 'Helvetica', 'Arial', 'sans-serif'],
                mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
            },
            borderRadius: {
                card: '14px',
                panel: '10px',
                control: '8px',
            },
            boxShadow: {
                soft: '0 1px 2px rgba(16, 24, 40, 0.04), 0 1px 3px rgba(16, 24, 40, 0.06)',
                card: '0 1px 2px rgba(16, 24, 40, 0.05), 0 8px 20px -8px rgba(16, 24, 40, 0.10)',
                panel: '0 12px 32px -8px rgba(16, 24, 40, 0.18), 0 4px 10px -4px rgba(16, 24, 40, 0.08)',
                brand: '0 4px 14px -4px rgba(0, 150, 64, 0.45)',
            },
            keyframes: {
                shimmer: {
                    '0%': { backgroundPosition: '-200% 0' },
                    '100%': { backgroundPosition: '200% 0' },
                },
            },
        },
    },
    plugins: [],
};

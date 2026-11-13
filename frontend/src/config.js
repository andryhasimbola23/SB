/**
 * Configuration centralisée de l'application.
 *
 * L'origine du backend n'est plus codée en dur dans chaque fichier : elle
 * provient de la variable d'environnement VITE_API_URL (voir `.env.example`),
 * ce qui permet un build de production (serveur distant, proxy, HTTPS) sans
 * toucher au code. Sans variable, on retombe sur le backend local de dev.
 */
export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

/** Racine de l'API REST (`axios` et les appels directs). */
export const API_URL = `${API_BASE_URL}/api`;

import { Client } from '@stomp/stompjs';
import { API_BASE_URL } from '../config';

/**
 * Canal temps réel (STOMP over WebSocket) — miroir de `WebSocketConfig` côté
 * backend.
 *
 * Principe :
 *  - une connexion unique par session applicative, gérée par l'`AuthContext`
 *    (connexion au login, déconnexion au logout) ;
 *  - le JWT voyage en query paramètre (`/ws?token=...`), relu à **chaque**
 *    tentative : une reconnexion après ré-connexion utilise le dernier jeton
 *    présent dans le localStorage ;
 *  - reconnexion automatique toutes les 5 s (reconnectDelay) en cas de coupure
 *    réseau, redémarrage du serveur ou perte de sommeil de la machine ;
 *  - à chaque (re)connexion, les abonnements sont recréés et un événement
 *    `connexion` est diffusé : les consommateurs rafraîchissent alors l'état
 *    réel pour rattraper ce qui a été manqué pendant la coupure.
 *
 * Canaux :
 *  - `notifications` : { notification, nonLues } — la cloche ;
 *  - `demandes`      : { evenement, demandeId }  — rechargement des listes ;
 *  - `connexion`     : { etat: 'connecte' | 'deconnecte' }.
 */

const CANAUX = ['notifications', 'demandes', 'connexion'];

/** canal → Set de handlers (abonnements recréés à chaque reconnexion). */
const abonnes = new Map(CANAUX.map((canal) => [canal, new Set()]));

let client = null;

const urlServeur = () => `${API_BASE_URL.replace(/^http/, 'ws')}/ws`;

const diffuser = (canal, charge) => {
    const handlers = abonnes.get(canal);
    if (!handlers) return;
    handlers.forEach((handler) => {
        try {
            handler(charge);
        } catch (err) {
            // Un handler en erreur ne doit pas couper les autres ni la socket.
            console.error(`Handler temps réel « ${canal} » en erreur :`, err);
        }
    });
};

const lireJson = (message, canal) => {
    try {
        return JSON.parse(message.body);
    } catch (err) {
        console.error(`Message illisible sur le canal « ${canal} » :`, err);
        return null;
    }
};

/**
 * Abonne un handler à un canal. Retourne la fonction de désabonnement — à
 * appeler dans le cleanup d'un `useEffect`.
 */
export const souscrire = (canal, handler) => {
    const handlers = abonnes.get(canal);
    if (!handlers) {
        throw new Error(`Canal temps réel inconnu : ${canal}`);
    }
    handlers.add(handler);
    return () => handlers.delete(handler);
};

/** Ouvre la connexion (no-op si déjà en cours ou en train de reconnexion). */
export const connecterRealtime = () => {
    if (client) return;

    const moi = new Client({
        // Factory rappelée à chaque tentative : le token le plus récent est
        // toujours celui utilisé, sans reconnexion forcée après un login.
        webSocketFactory: () =>
            new WebSocket(
                `${urlServeur()}?token=${encodeURIComponent(localStorage.getItem('token') || '')}`
            ),
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
            moi.subscribe('/user/queue/notifications', (message) => {
                const charge = lireJson(message, 'notifications');
                if (charge) diffuser('notifications', charge);
            });
            moi.subscribe('/user/queue/demandes', (message) => {
                const charge = lireJson(message, 'demandes');
                if (charge) diffuser('demandes', charge);
                // Badge du menu (demandes en attente) : le même événement que
                // celui déclenché après une mutation locale.
                window.dispatchEvent(new Event('demandes:changed'));
            });
            // (Re)connexion : l'état serveur a pu bouger pendant la coupure —
            // les consommateurs rafraîchissent pour ne rien avoir manqué.
            window.dispatchEvent(new Event('demandes:changed'));
            diffuser('connexion', { etat: 'connecte' });
        },
        onWebSocketClose: () => diffuser('connexion', { etat: 'deconnecte' }),
        onWebSocketError: () => {
            // Pas de bruit : la reconnexion automatique s'en charge.
        },
        onStompError: (frame) => {
            console.error('Erreur STOMP :', frame.headers?.message || frame.body);
        },
    });

    client = moi;
    moi.activate();
};

/** Ferme la connexion (appelée au logout). Idempotent. */
export const deconnecterRealtime = () => {
    if (!client) return;
    const enCours = client;
    client = null;
    // deactivate() peut rejeter si la socket était déjà tombée : sans objet.
    Promise.resolve(enCours.deactivate()).catch(() => {});
};

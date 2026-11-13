import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    Bell, CheckCheck, ClipboardList, CheckCircle, XCircle,
    Package, Euro, Trash2, ClipboardCheck, BellOff,
} from 'lucide-react';
import notificationService from '../../services/notificationService';
import { souscrire } from '../../services/realtime';
import { formatDateTime } from '../../utils/format';

/** Icône et couleur par type d'événement. */
const TYPE_META = {
    NOUVELLE_DEMANDE: { icon: ClipboardList, tone: 'text-brand-500' },
    DEMANDE_APPROUVEE: { icon: CheckCircle, tone: 'text-brand-500' },
    DEMANDE_REJETEE: { icon: XCircle, tone: 'text-danger-500' },
    DEMANDE_LIVREE: { icon: Package, tone: 'text-brand-500' },
    BIEN_CEDER: { icon: Euro, tone: 'text-amber-500' },
    BIEN_REBUTE: { icon: Trash2, tone: 'text-danger-500' },
    CAMPAGNE_CLOTUREE: { icon: ClipboardCheck, tone: 'text-amber-500' },
};

// Filet de sécurité : si la socket temps réel est à terre, le badge reste juste.
const DELAI_RAFRAICHISSEMENT = 45_000;
const NB_AFFICHEES = 10;

/**
 * Cloche de notifications in-app (header, deux rôles).
 *
 * - badge = nombre de non-lues : mis à jour **instantanément** par le canal
 *   temps réel (`notifications`), avec en filet de sécurité le rafraîchissement
 *   toutes les 45 s et au focus de l'onglet — si la socket tombe, le badge
 *   reste juste ;
 * - le panneau liste les 10 plus récentes ; une notification reçue pendant
 *   l'ouverture est insérée en tête sans réouverture ;
 * - un clic marque lu et ouvre le lien métier s'il existe (demandes,
 *   mouvements, inventaires) ;
 * - les compteurs locaux suivent le serveur : pas d'optimisme à défaire.
 */
const NotificationBell = () => {
    const navigate = useNavigate();
    const [ouvert, setOuvert] = useState(false);
    const [nonLues, setNonLues] = useState(0);
    const [notifications, setNotifications] = useState([]);
    const [chargement, setChargement] = useState(false);
    const panneauRef = useRef(null);
    // Lu dans le handler temps réel (abonné une seule fois) sans fermer le
    // rendu courant : le panneau ouvert reçoit la notification en direct.
    const ouvertRef = useRef(false);

    useEffect(() => {
        ouvertRef.current = ouvert;
    }, [ouvert]);

    useEffect(() => {
        const rafraichir = () => {
            notificationService
                .getNonLuesCount()
                .then(setNonLues)
                .catch(() => {
                    // Serveur indisponible : le badge garde sa dernière valeur connue.
                });
        };

        rafraichir();
        const interval = setInterval(rafraichir, DELAI_RAFRAICHISSEMENT);
        const auFocus = () => rafraichir();
        window.addEventListener('focus', auFocus);

        // Temps réel : le compteur du serveur fait foi, la notification est
        // insérée en tête si le panneau est ouvert (sinon, il rechargera à
        // l'ouverture comme avant).
        const stopNotifications = souscrire('notifications', (evt) => {
            if (typeof evt?.nonLues === 'number') setNonLues(evt.nonLues);
            const notif = evt?.notification;
            if (notif && ouvertRef.current) {
                setNotifications((liste) =>
                    liste.some((n) => n.id === notif.id) ? liste : [notif, ...liste]);
            }
        });
        // (Re)connexion : on rattrape l'état réel du serveur (événements
        // possibles manqués pendant la coupure).
        const stopConnexion = souscrire('connexion', (evt) => {
            if (evt?.etat === 'connecte') rafraichir();
        });

        return () => {
            clearInterval(interval);
            window.removeEventListener('focus', auFocus);
            stopNotifications();
            stopConnexion();
        };
    }, []);

    // Fermeture au clic extérieur.
    useEffect(() => {
        if (!ouvert) return undefined;
        const auClic = (e) => {
            if (panneauRef.current && !panneauRef.current.contains(e.target)) {
                setOuvert(false);
            }
        };
        document.addEventListener('mousedown', auClic);
        return () => document.removeEventListener('mousedown', auClic);
    }, [ouvert]);

    const basculer = async () => {
        const prochain = !ouvert;
        setOuvert(prochain);
        if (prochain) {
            setChargement(true);
            try {
                setNotifications(await notificationService.getAll());
            } finally {
                setChargement(false);
            }
        }
    };

    const marquerLue = async (notif) => {
        if (notif.lue) {
            if (notif.lien) navigate(notif.lien);
            return;
        }
        try {
            const maj = await notificationService.marquerLue(notif.id);
            setNotifications((liste) =>
                liste.map((n) => (n.id === maj.id ? maj : n)));
            setNonLues((c) => Math.max(0, c - 1));
        } catch {
            // Échec du marquage : la navigation reste possible.
        }
        if (notif.lien) navigate(notif.lien);
    };

    const toutMarquerLue = async () => {
        try {
            await notificationService.toutMarquerLue();
            setNotifications((liste) =>
                liste.map((n) => ({ ...n, lue: true })));
            setNonLues(0);
        } catch {
            // Rien à afficher de bloquant : le prochain rafraîchissement remet au propre.
        }
    };

    const affichees = notifications.slice(0, NB_AFFICHEES);

    return (
        <div className="relative" ref={panneauRef}>
            <button
                onClick={basculer}
                className="btn-ghost btn-icon relative"
                aria-label={`Notifications${nonLues > 0 ? ` (${nonLues} non lues)` : ''}`}
                aria-expanded={ouvert}
                title="Notifications"
            >
                <Bell size={19} />
                {nonLues > 0 && (
                    <span
                        className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1
                                   rounded-full bg-danger-500 text-white text-[10px]
                                   font-bold flex items-center justify-center"
                    >
                        {nonLues > 9 ? '9+' : nonLues}
                    </span>
                )}
            </button>

            {ouvert && (
                <div
                    className="absolute right-0 mt-2 w-[calc(100vw-1.5rem)] sm:w-96 max-w-96
                               bg-surface border border-line rounded-panel shadow-xl z-50"
                >
                    <div className="flex items-center justify-between px-4 py-3 border-b border-line">
                        <p className="text-sm font-semibold text-ink">Notifications</p>
                        {notifications.some((n) => !n.lue) && (
                            <button
                                onClick={toutMarquerLue}
                                className="text-xs font-medium text-brand-600 dark:text-brand-300
                                           hover:underline flex items-center gap-1"
                            >
                                <CheckCheck size={13} />
                                Tout marquer comme lu
                            </button>
                        )}
                    </div>

                    <div className="max-h-96 overflow-y-auto scrollbar-thin">
                        {chargement && (
                            <p className="text-sm text-ink-muted text-center py-8">Chargement…</p>
                        )}

                        {!chargement && affichees.length === 0 && (
                            <div className="py-10 text-center">
                                <BellOff size={28} className="mx-auto text-ink-subtle mb-2" />
                                <p className="text-sm text-ink-muted">Aucune notification</p>
                            </div>
                        )}

                        {!chargement && affichees.map((notif) => {
                            const meta = TYPE_META[notif.type] || { icon: Bell, tone: 'text-ink-muted' };
                            const Icone = meta.icon;
                            return (
                                <button
                                    key={notif.id}
                                    onClick={() => marquerLue(notif)}
                                    className={`w-full text-left px-4 py-3 border-b border-line
                                                last:border-0 hover:bg-surface-2
                                                dark:hover:bg-slate-800/60 transition-colors flex gap-3
                                                ${notif.lue ? 'opacity-60' : ''}`}
                                >
                                    <Icone size={17} className={`${meta.tone} mt-0.5 shrink-0`} />
                                    <div className="min-w-0">
                                        <p className={`text-xs text-ink ${notif.lue ? 'font-medium' : 'font-semibold'}`}>
                                            {notif.titre}
                                        </p>
                                        <p className="text-xs text-ink-muted mt-0.5 line-clamp-2">
                                            {notif.message}
                                        </p>
                                        <p className="text-[10px] text-ink-subtle mt-1">
                                            {formatDateTime(notif.dateCreation)}
                                        </p>
                                    </div>
                                    {!notif.lue && (
                                        <span className="w-2 h-2 rounded-full bg-brand-500 mt-1.5 shrink-0" />
                                    )}
                                </button>
                            );
                        })}
                    </div>

                    {notifications.length > NB_AFFICHEES && (
                        <div className="px-4 py-2 border-t border-line text-center">
                            <span className="text-[11px] text-ink-subtle">
                                {notifications.length} notifications au total
                            </span>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default NotificationBell;

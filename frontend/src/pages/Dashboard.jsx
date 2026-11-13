import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
    Package, CheckCircle, Banknote, TrendingDown, Activity,
    AlertTriangle, Building2, Briefcase, Users, ArrowRight,
    ArrowRightLeft, Send, Trash2, Inbox, UserPlus, Truck,
    CalendarClock, ClipboardList,
} from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import dashboardService from '../services/dashboardService';
import demandeService from '../services/demandeService';
import { souscrire } from '../services/realtime';
import StatCard from '../components/dashboard/StatCard';
import Surface, { SurfaceLink, EmptyState } from '../components/ui/Surface';
import { DashboardSkeleton } from '../components/ui/Skeleton';
import Badge from '../components/ui/Badge';
import { tone as toneOf, TYPES_MOUVEMENT } from '../theme/toneStyles';
import {
    RepartitionCategorieChart, RepartitionServiceChart, StatutChart,
} from '../components/dashboard/Charts';
import {
    formatMontant as formatMontantBase, formatDate as formatDateBase,
} from '../utils/format';

const MOUVEMENT_TONE = {
    ENTREE: 'brand',
    TRANSFERT: 'neutral',
    AFFECTATION: 'brand',
    CESSION: 'warning',
    REBUT: 'danger',
};

const MOUVEMENT_ICON = {
    ENTREE: Package,
    TRANSFERT: ArrowRightLeft,
    AFFECTATION: UserPlus,
    CESSION: Send,
    REBUT: Trash2,
};

// Les fallbacks (« 0 Ar » et tiret long) sont propres au dashboard ;
// la logique de formatage vit dans utils/format.
const formatMontant = (montant) => formatMontantBase(montant, '0 Ar');

const formatDate = (dateStr) => formatDateBase(dateStr, '—');

/**
 * Compteur d'une carte COMPOSÉE (« Santé du parc ») : contrairement à
 * MiniStat, il vit à l'intérieur d'une Surface et peut mener vers une
 * page filtrée — le chiffre porte le ton, la souris passe sur la cellule.
 */
const StatCell = ({ to, icon: Icon, label, value, tone = 'neutral' }) => {
    const t = toneOf(tone);
    const contenu = (
        <>
            <span className={`w-9 h-9 rounded-panel flex items-center justify-center shrink-0 ${t.icon}`}>
                <Icon size={16} />
            </span>
            <div className="min-w-0">
                <p className={`text-xl font-bold leading-tight ${t.text}`}>{value}</p>
                <p className="section-label truncate">{label}</p>
            </div>
        </>
    );
    const base = 'flex items-center gap-3 p-3 rounded-panel transition-colors';

    return to ? (
        <Link
            to={to}
            className={`${base} hover:bg-surface-2 dark:hover:bg-slate-800/60 focus-visible:bg-surface-2 focus-visible:outline-none`}
        >
            {contenu}
        </Link>
    ) : (
        <div className={base}>{contenu}</div>
    );
};

const Dashboard = () => {
    const { user, isAdmin } = useAuth();
    const navigate = useNavigate();

    const [stats, setStats] = useState(null);
    const [repartitionCategorie, setRepartitionCategorie] = useState([]);
    const [repartitionService, setRepartitionService] = useState([]);
    const [derniersMouvements, setDerniersMouvements] = useState([]);
    const [dernieresAcquisitions, setDernieresAcquisitions] = useState([]);
    const [demandeStats, setDemandeStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        loadDashboard();
    }, []);

    const loadDashboard = async () => {
        try {
            setLoading(true);
            const [statsData, catData, servData, mvtData, acqData, demandeData] = await Promise.all([
                dashboardService.getStats(),
                dashboardService.getRepartitionCategorie(),
                dashboardService.getRepartitionService(),
                dashboardService.getDerniersMouvements(),
                dashboardService.getDernieresAcquisitions(),
                // Compteurs isolés : un échec ici ne doit pas vider le tableau de bord.
                demandeService.getStats().catch(() => null),
            ]);
            setStats(statsData);
            setRepartitionCategorie(catData);
            setRepartitionService(servData);
            setDerniersMouvements(mvtData);
            setDernieresAcquisitions(acqData);
            setDemandeStats(demandeData);
        } catch (err) {
            setError('Erreur lors du chargement du tableau de bord');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    /**
     * Temps réel : les compteurs de demandes (« À surveiller », cartes) sont
     * rafraîchis dès qu'une demande change, sans recharger tout le tableau de
     * bord — pas de squelette pour une mise à jour ciblée. À la (re)connexion,
     * on rattrape ce qui a pu être manqué.
     */
    useEffect(() => {
        const rafraichirDemandes = () => {
            demandeService.getStats()
                .then(setDemandeStats)
                .catch(() => {
                    // Sans compteur plutôt que faux compteur (cf. useDemandeBadge).
                });
        };
        const stopDemandes = souscrire('demandes', rafraichirDemandes);
        const stopConnexion = souscrire('connexion', (evt) => {
            if (evt?.etat === 'connecte') rafraichirDemandes();
        });
        return () => {
            stopDemandes();
            stopConnexion();
        };
    }, []);

    /**
     * « À surveiller » — les points qui appellent une décision humaine,
     * consolidés au même endroit : demandes en attente, campagnes ouvertes,
     * biens dégradés et parc vieillissant. Rien n'est affiché à zéro : une
     * liste d'alertes vides ne mérite pas une carte.
     */
    const alertes = [];
    if ((demandeStats?.enAttente || 0) > 0) {
        alertes.push({
            icon: Inbox,
            tone: 'warning',
            texte: `${demandeStats.enAttente} demande(s) d'immobilisation en attente de décision`,
            lien: '/demandes',
            cta: isAdmin() ? 'Traiter' : 'Suivre',
        });
    }
    if (isAdmin() && (stats?.campagnesEnCours || 0) > 0) {
        alertes.push({
            icon: ClipboardList,
            tone: 'warning',
            texte: `${stats.campagnesEnCours} campagne(s) d'inventaire en cours`,
            lien: '/inventaires',
            cta: 'Ouvrir',
        });
    }
    if ((stats?.totalMauvaisEtat || 0) > 0) {
        alertes.push({
            icon: AlertTriangle,
            tone: 'warning',
            texte: `${stats.totalMauvaisEtat} bien(s) en mauvais état`,
            // Le filtre voyage dans l'URL : la page cible affiche exactement
            // les biens annoncés, et non tout le parc.
            lien: '/immobilisations?etat=MAUVAIS',
            cta: 'Consulter',
        });
    }
    if ((stats?.totalHorsService || 0) > 0) {
        alertes.push({
            icon: TrendingDown,
            tone: 'danger',
            texte: `${stats.totalHorsService} bien(s) hors service`,
            lien: '/immobilisations?etat=HORS_SERVICE',
            cta: 'Consulter',
        });
    }
    if ((stats?.biensAnciens || 0) > 0) {
        alertes.push({
            icon: CalendarClock,
            tone: 'warning',
            texte: `${stats.biensAnciens} bien(s) de plus de 5 ans — renouvellement à envisager`,
            lien: '/immobilisations?anciens=1',
            cta: 'Voir le parc',
        });
    }

    if (loading) return <DashboardSkeleton />;

    return (
        <div className="space-y-6">
            {error && <div className="alert-danger alert">{error}</div>}

            {/* ---------- Bienvenue ---------- */}
            <section className="relative overflow-hidden rounded-card bg-gradient-to-br from-brand-600 via-brand-500 to-brand-700 shadow-brand p-5 sm:p-7 text-white">
                <div className="absolute top-0 right-0 w-40 h-40 sm:w-60 sm:h-60 bg-white/10 rounded-full -mr-16 -mt-16 blur-2xl" />
                <div className="absolute bottom-0 left-0 w-32 h-32 sm:w-44 sm:h-44 bg-white/5 rounded-full -ml-14 -mb-14 blur-2xl" />

                <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex min-w-0 items-center gap-4">
                        {/* Initiale du compte — même langage que l'avatar de l'en-tête. */}
                        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-white/15 text-xl font-bold tracking-wide text-white ring-2 ring-white/25 backdrop-blur-sm sm:h-16 sm:w-16 sm:text-2xl">
                            {user?.nomComplet?.charAt(0)?.toUpperCase() || 'U'}
                        </div>

                        <div className="min-w-0">
                            <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-white/70 sm:text-xs">
                                Bonjour
                            </p>
                            <h1 className="mt-1 truncate text-2xl font-bold tracking-tight sm:text-3xl">
                                {user?.nomComplet || 'Utilisateur'}
                            </h1>
                            <p className="mt-1.5 text-xs text-white/80 sm:text-sm">
                                Voici un aperçu de votre parc immobilier aujourd’hui.
                            </p>
                        </div>
                    </div>

                    <div className="flex shrink-0 items-center gap-2 self-start sm:flex-col sm:items-end sm:gap-1 sm:self-auto">
                        <p className="text-[10px] font-semibold uppercase tracking-wider text-white/60 sm:text-xs">
                            Rôle
                        </p>
                        <p className="rounded-panel bg-white/20 px-3 py-1 text-base font-bold backdrop-blur-sm ring-1 ring-white/20 sm:text-lg">
                            {isAdmin() ? 'Administrateur' : (user?.serviceNom || 'Sans service')}
                        </p>
                    </div>
                </div>
            </section>

            {/* ---------- Aucun périmètre ---------- */}
            {!isAdmin() && user && !user.serviceId && (
                <div className="alert-warning flex items-start gap-3">
                    <AlertTriangle size={18} className="mt-0.5 shrink-0" />
                    <div>
                        <p className="font-medium">Aucun service rattaché</p>
                        <p className="text-sm opacity-80">
                            Vous n’êtes affecté à aucun service : aucun bien ni mouvement ne vous est
                            visible pour le moment. Rapprochez-vous d’un administrateur.
                        </p>
                    </div>
                </div>
            )}

            {/* ---------- Indicateurs principaux ---------- */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <StatCard
                    icon={Package}
                    label="Total immobilisations"
                    value={stats?.totalImmobilisations || 0}
                    subtitle={`${stats?.totalActifs || 0} actifs`}
                    tone="brand"
                    loading={loading}
                />
                {isAdmin() ? (
                    <StatCard
                        // Billet plutôt que signe monétaire : le parc est
                        // valorisé en ariary, un « $ » affirmerait la mauvaise devise.
                        icon={Banknote}
                        label="Valeur du parc"
                        value={formatMontant(stats?.valeurTotaleParc)}
                        subtitle="Biens actifs"
                        tone="neutral"
                        loading={loading}
                    />
                ) : (
                    <StatCard
                        icon={CheckCircle}
                        label="Biens actifs"
                        value={stats?.totalActifs || 0}
                        subtitle="Immobilisations en service"
                        tone="brand"
                        loading={loading}
                    />
                )}
                {isAdmin() ? (
                    <StatCard
                        icon={TrendingDown}
                        label="VNC totale"
                        value={formatMontant(stats?.vncTotale)}
                        subtitle={
                            stats?.tauxAmortissementGlobal != null
                                ? `${stats.tauxAmortissementGlobal} % du parc amorti`
                                : 'Valeur comptable nette'
                        }
                        tone="neutral"
                        loading={loading}
                    />
                ) : (
                    <StatCard
                        icon={Activity}
                        label="Total catégories"
                        value={stats?.totalCategories || 0}
                        subtitle="Catégories disponibles"
                        tone="neutral"
                        loading={loading}
                    />
                )}
                <StatCard
                    icon={Activity}
                    label="Mouvements du mois"
                    value={stats?.mouvementsCeMois || 0}
                    subtitle={
                        isAdmin()
                            ? `${stats?.nouveauxBiensCeMois || 0} nouvelles acquisitions`
                            : 'Ce mois-ci'
                    }
                    tone="warning"
                    loading={loading}
                />
            </div>

            {/* ---------- Santé du parc ---------- */}
            {/* Une carte composée au lieu de huit cartouches identiques :
                trois compteurs cliquables, chacun vers la liste filtrée.
                Les demandes en attente remontent via « À surveiller ». */}
            <Surface
                title="Santé du parc"
                subtitle="État des biens déclarés — clic pour filtrer la liste"
            >
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    <StatCell
                        to="/immobilisations?etat=MAUVAIS"
                        icon={AlertTriangle}
                        label="Mauvais état"
                        value={stats?.totalMauvaisEtat || 0}
                        tone="warning"
                    />
                    <StatCell
                        to="/immobilisations?etat=HORS_SERVICE"
                        icon={TrendingDown}
                        label="Hors service"
                        value={stats?.totalHorsService || 0}
                        tone="danger"
                    />
                    {/* Sans filtre statut côté liste : la cellule n'est
                        pas cliquable plutôt que de mentir sur sa cible. */}
                    <StatCell
                        icon={Send}
                        label="Cédés"
                        value={stats?.totalCedes || 0}
                        tone="neutral"
                    />
                </div>
            </Surface>

            {/* ---------- Référentiel (ADMIN) ---------- */}
            {/* Un bandeau segmenté plutôt que quatre cartes répétées : le
                référentiel est un cadre de référence à gérer, pas des
                indicateurs à surveiller. */}
            {isAdmin() && (
                <Surface
                    title="Référentiel"
                    subtitle="Cadres de référence — clic pour gérer"
                    padded={false}
                >
                    <div className="flex flex-col sm:flex-row divide-y divide-line sm:divide-y-0 sm:divide-x">
                        {[
                            { icon: Building2, label: 'Agences', value: stats?.totalAgences, to: '/agences' },
                            { icon: Briefcase, label: 'Services', value: stats?.totalServices, to: '/services' },
                            { icon: Truck, label: 'Fournisseurs', value: stats?.totalFournisseurs, to: '/fournisseurs' },
                            { icon: Users, label: 'Utilisateurs', value: stats?.totalUtilisateurs, to: '/utilisateurs' },
                        ].map(({ icon: Icon, label, value, to }) => (
                            <Link
                                key={to}
                                to={to}
                                className="flex-1 flex items-center gap-3 p-4 hover:bg-surface-2 dark:hover:bg-slate-800/60 transition-colors"
                            >
                                <Icon size={17} className="text-ink-subtle shrink-0" />
                                <div className="min-w-0">
                                    <p className="text-lg font-bold text-ink leading-tight">{value || 0}</p>
                                    <p className="section-label truncate">{label}</p>
                                </div>
                            </Link>
                        ))}
                    </div>
                </Surface>
            )}

            {/* ---------- À surveiller ---------- */}
            {/* Masqué quand tout est au vert : une carte d'alertes vides
                pollue la lecture, « rien à signaler » tient en une ligne. */}
            {alertes.length > 0 && (
                <Surface
                    title="À surveiller"
                    subtitle="Points appelant une décision"
                    padded={false}
                >
                    <ul className="divide-y divide-line">
                        {alertes.map((alerte) => {
                            const Icon = alerte.icon;
                            return (
                                <li key={alerte.texte}>
                                    <Link
                                        to={alerte.lien}
                                        className="flex items-center gap-3 px-5 py-3 hover:bg-surface-2 focus-visible:bg-surface-2 focus-visible:outline-none transition-colors"
                                    >
                                        <div
                                            className={`w-9 h-9 rounded-panel flex items-center justify-center shrink-0 border border-line ${
                                                alerte.tone === 'danger'
                                                    ? 'bg-danger-50 dark:bg-danger-500/15'
                                                    : 'bg-amber-50 dark:bg-amber-500/15'
                                            }`}
                                        >
                                            <Icon
                                                size={15}
                                                className={
                                                    alerte.tone === 'danger'
                                                        ? 'text-danger-600 dark:text-danger-300'
                                                        : 'text-amber-600 dark:text-amber-300'
                                                }
                                            />
                                        </div>
                                        <p className="min-w-0 flex-1 text-sm text-ink truncate">
                                            {alerte.texte}
                                        </p>
                                        <span className="text-xs font-medium text-brand-600 dark:text-brand-300 shrink-0">
                                            {alerte.cta}
                                        </span>
                                        <ArrowRight size={14} className="text-ink-subtle shrink-0" />
                                    </Link>
                                </li>
                            );
                        })}
                    </ul>
                </Surface>
            )}

            {/* ---------- Graphiques ---------- */}
            <div className={`grid grid-cols-1 gap-5 ${isAdmin() ? 'lg:grid-cols-2' : ''}`}>
                <Surface title="Répartition par catégorie" subtitle="Nombre de biens par catégorie">
                    <RepartitionCategorieChart data={repartitionCategorie} />
                </Surface>
                <Surface title="Statut des immobilisations" subtitle="Répartition actifs / cédés / rebuts">
                    <StatutChart stats={stats} />
                </Surface>
                {/* Masqué pour un employé : il ne voit qu'un service, la
                    comparaison entre services n'aurait aucun sens. Pleine
                    largeur depuis le retrait du graphique par agence. */}
                {isAdmin() && (
                    <Surface
                        title="Répartition par service"
                        subtitle="Nombre de biens par service"
                        className="lg:col-span-2"
                    >
                        <RepartitionServiceChart data={repartitionService} />
                    </Surface>
                )}
            </div>

            {/* ---------- Listes ---------- */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                <Surface
                    title="Derniers mouvements"
                    subtitle="5 mouvements les plus récents"
                    padded={false}
                    actions={
                        <SurfaceLink onClick={() => navigate('/mouvements')}>
                            Voir tout <ArrowRight size={12} />
                        </SurfaceLink>
                    }
                >
                    {derniersMouvements.length === 0 ? (
                        <EmptyState icon={ArrowRightLeft} message="Aucun mouvement enregistré" />
                    ) : (
                        <ul className="divide-y divide-line">
                            {derniersMouvements.map((mvt) => {
                                const Icon = MOUVEMENT_ICON[mvt.typeMouvement] || Package;
                                const toneName = MOUVEMENT_TONE[mvt.typeMouvement] || 'neutral';
                                const meta = TYPES_MOUVEMENT[mvt.typeMouvement];
                                return (
                                    <li key={mvt.id}>
                                        {/* Lien en profondeur : la page cible ouvre
                                            la modale du mouvement, plutôt que de
                                            déposer sur une liste paginée où le bien
                                            visé peut être à la sixième page. */}
                                        <Link
                                            to={`/mouvements?mouvement=${mvt.id}`}
                                            className="block px-5 py-3 hover:bg-surface-2 focus-visible:bg-surface-2 focus-visible:outline-none transition-colors"
                                        >
                                            <div className="flex items-center gap-3">
                                                <div className="w-9 h-9 rounded-panel flex items-center justify-center shrink-0 border border-line bg-surface-2 dark:bg-slate-800/60">
                                                    <Icon size={15} className="text-ink-muted" />
                                                </div>
                                                <div className="min-w-0 flex-1">
                                                    <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                                                        <Badge tone={toneName} label={meta?.label || mvt.typeMouvement} dot={false} />
                                                        <span className="text-xs text-ink-subtle">
                                                            {formatDate(mvt.dateMouvement)}
                                                        </span>
                                                    </div>
                                                    <p className="text-sm text-ink font-medium truncate">
                                                        {mvt.immobilisationDesignation}
                                                    </p>
                                                    <p className="text-xs mono-code truncate">
                                                        {mvt.immobilisationCode}
                                                    </p>
                                                </div>
                                                <ArrowRight size={14} className="text-ink-subtle shrink-0" />
                                            </div>
                                        </Link>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </Surface>

                <Surface
                    title="Dernières acquisitions"
                    subtitle="5 biens les plus récents"
                    padded={false}
                    actions={
                        <SurfaceLink onClick={() => navigate('/immobilisations')}>
                            Voir tout <ArrowRight size={12} />
                        </SurfaceLink>
                    }
                >
                    {dernieresAcquisitions.length === 0 ? (
                        <EmptyState icon={Package} message="Aucune acquisition enregistrée" />
                    ) : (
                        <ul className="divide-y divide-line">
                            {dernieresAcquisitions.map((immo) => (
                                <li key={immo.id}>
                                    <Link
                                        to={`/immobilisations?immo=${immo.id}`}
                                        className="block px-5 py-3 hover:bg-surface-2 focus-visible:bg-surface-2 focus-visible:outline-none transition-colors"
                                    >
                                        <div className="flex items-center gap-3">
                                            <div className="w-9 h-9 rounded-panel bg-surface-3 flex items-center justify-center shrink-0">
                                                <Package size={15} className="text-ink-muted" />
                                            </div>
                                            <div className="min-w-0 flex-1">
                                                <p className="text-sm font-medium text-ink truncate">{immo.designation}</p>
                                                <p className="text-xs mono-code truncate">
                                                    {immo.codeUnique}
                                                </p>
                                            </div>
                                            {isAdmin() && (
                                                <div className="text-right shrink-0">
                                                    <p className="text-xs font-semibold text-ink">
                                                        {formatMontant(immo.valeurAchat)}
                                                    </p>
                                                    <p className="text-[10px] text-ink-subtle">
                                                        {formatDate(immo.dateAcquisition)}
                                                    </p>
                                                </div>
                                            )}
                                            <ArrowRight size={14} className="text-ink-subtle shrink-0" />
                                        </div>
                                    </Link>
                                </li>
                            ))}
                        </ul>
                    )}
                </Surface>
            </div>
        </div>
    );
};

export default Dashboard;

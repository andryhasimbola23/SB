import { useState, useEffect, useMemo, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
    ScrollText, ListFilter, RotateCcw, ShieldAlert, Download,
    Eye, History, X, Activity, CalendarDays, Loader2, Search, User, Box,
} from 'lucide-react';
import DetailItem from '../../components/ui/DetailItem';
import journalService from '../../services/journalService';
import utilisateurService from '../../services/utilisateurService';
import exportService from '../../services/exportService';
import Badge from '../../components/ui/Badge';
import Field from '../../components/ui/Field';
import Modal from '../../components/ui/Modal';
import ResponsiveTable, { RowAction, TableCard } from '../../components/ui/ResponsiveTable';
import Pagination from '../../components/ui/Pagination';
import { EmptyState } from '../../components/ui/Surface';
import { TableSkeleton } from '../../components/ui/Skeleton';
import { MiniStat } from '../../components/dashboard/StatCard';
import { usePagination } from '../../hooks/usePagination';
import { formatDateHeure } from '../../utils/format';

/** Ton de badge par action — palette alignée sur le reste de l'application. */
const ACTIONS = {
    CREATION: { label: 'Création', tone: 'brand' },
    MODIFICATION: { label: 'Modification', tone: 'neutral' },
    SUPPRESSION: { label: 'Suppression', tone: 'danger' },
    ENTREE: { label: 'Entrée', tone: 'brand' },
    TRANSFERT: { label: 'Transfert', tone: 'brand' },
    AFFECTATION: { label: 'Affectation', tone: 'brand' },
    CESSION: { label: 'Cession', tone: 'warning' },
    REBUT: { label: 'Rebut', tone: 'danger' },
    DECISION_DEMANDE: { label: 'Décision demande', tone: 'warning' },
    CLOTURE_CAMPAGNE: { label: 'Clôture campagne', tone: 'warning' },
    CONNEXION: { label: 'Connexion', tone: 'neutral' },
    CONNEXION_ECHOUEE: { label: 'Connexion échouée', tone: 'danger' },
    EXPORT_JOURNAL: { label: 'Export du journal', tone: 'neutral' },
};

const OPTIONS_ACTION = Object.entries(ACTIONS).map(([value, meta]) => ({ value, label: meta.label }));

const OPTIONS_ENTITE = [
    'Immobilisation',
    'Mouvement',
    "Demande d'immobilisation",
    "Campagne d'inventaire",
    'Session',
    "Journal d'audit",
    'Utilisateur',
].map((v) => ({ value: v, label: v }));

const PAR_PAGE = 10;

/**
 * Journal d'audit — page ADMIN, consultation enrichie.
 *
 * Trace des actions sensibles écrites par le métier : créations, modifications,
 * suppressions, mouvements, décisions de demande, clôtures de campagne,
 * sessions (connexions réussies ET échouées) et exports du journal lui-même.
 *
 * L'URL porte les filtres (source unique de vérité, même discipline que la
 * page des immobilisations) : la vue est partageable, le retour navigateur
 * rétablit l'état, et un lien de la barre latérale repart proprement. Le
 * journal reste append-only : ici on ne fait que LIRE, filtrer, exporter —
 * et l'export laisse lui-même une trace.
 */
const JournalAudit = () => {
    // ==== Filtres — dérivés de l'URL ====
    const [searchParams, setSearchParams] = useSearchParams();
    const filtreAction = searchParams.get('action') || '';
    const filtreEntite = searchParams.get('entite') || '';
    const filtreActeur = searchParams.get('acteur') || ''; // id utilisateur | 'sans-session'
    const filtreDu = searchParams.get('du') || '';
    const filtreAu = searchParams.get('au') || '';
    const recherche = searchParams.get('q') || '';
    const filtreBien = searchParams.get('bien') || ''; // entité filtrée par identifiant

    /** Écrit un filtre dans l'URL — `replace` : pas d'entrée d'historique par frappe ou bascule. */
    const majParam = useCallback((cle, valeur) => {
        setSearchParams((precedent) => {
            const suivant = new URLSearchParams(precedent);
            if (valeur) suivant.set(cle, valeur);
            else suivant.delete(cle);
            return suivant;
        }, { replace: true });
    }, [setSearchParams]);

    /** Efface tous les filtres — en une seule écriture d'URL (voir GestionImmobilisations). */
    const reinitialiser = useCallback(() => {
        setSearchParams({}, { replace: true });
    }, [setSearchParams]);

    // ==== Données ====
    const [entrees, setEntrees] = useState([]);
    const [utilisateurs, setUtilisateurs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    // Instant de chargement — base de calcul des fenêtres 24 h / 7 j / 15 min.
    // Figé à l'affichage plutôt que recalculé au rendu : les cartes de
    // synthèse ne « tiquent » pas et le rendu reste pur.
    const [horloge, setHorloge] = useState(0);
    const [exportEnCours, setExportEnCours] = useState(false);
    const [entreeDetail, setEntreeDetail] = useState(null);

    const chargerEntrees = useCallback(async () => {
        const lignes = await journalService.rechercher();
        setEntrees(lignes);
        setHorloge(Date.now());
    }, []);

    useEffect(() => {
        let vivant = true;
        (async () => {
            try {
                const [lignes, utilisateursChargees] = await Promise.all([
                    journalService.rechercher(),
                    // Le filtre acteur reste utilisable même si la liste des
                    // comptes venait à manquer : « Tous » + « Sans session ».
                    utilisateurService.getAll().catch(() => []),
                ]);
                if (!vivant) return;
                setEntrees(lignes);
                setUtilisateurs(utilisateursChargees);
                setHorloge(Date.now());
                setError('');
            } catch (err) {
                console.error(err);
                if (vivant) setError('Chargement du journal impossible');
            } finally {
                if (vivant) setLoading(false);
            }
        })();
        return () => {
            vivant = false;
        };
    }, []);

    // ==== Filtrage combiné (toujours cumulable) ====
    const filtres = useMemo(() => {
        const q = recherche.trim().toLowerCase();
        return entrees.filter((e) => {
            if (filtreAction !== '' && e.action !== filtreAction) return false;
            if (filtreEntite !== '' && e.entite !== filtreEntite) return false;
            if (filtreActeur === 'sans-session' && e.acteurId != null) return false;
            if (filtreActeur !== '' && filtreActeur !== 'sans-session' && String(e.acteurId) !== filtreActeur) return false;
            if (filtreBien !== '' && String(e.entiteId) !== filtreBien) return false;
            if (filtreDu !== '' && (e.dateAction || '').slice(0, 10) < filtreDu) return false;
            if (filtreAu !== '' && (e.dateAction || '').slice(0, 10) > filtreAu) return false;
            if (q !== '') {
                const foin = `${e.acteurNom || 'sans session'} ${e.description} ${e.action} ${e.entite} ${e.entiteId ?? ''}`.toLowerCase();
                if (!foin.includes(q)) return false;
            }
            return true;
        });
    }, [entrees, filtreAction, filtreEntite, filtreActeur, filtreBien, filtreDu, filtreAu, recherche]);

    // ==== Synthèse — calculée sur le journal complet (hors filtres) ====
    const stats = useMemo(() => {
        const il24h = horloge - 24 * 60 * 60 * 1000;
        const il7j = horloge - 7 * 24 * 60 * 60 * 1000;
        let total24 = 0;
        let total7 = 0;
        let echecs = 0;
        for (const e of entrees) {
            const t = Date.parse(e.dateAction);
            if (Number.isNaN(t)) continue;
            if (t >= il24h) total24 += 1;
            if (t >= il7j) total7 += 1;
            if (e.action === 'CONNEXION_ECHOUEE') echecs += 1;
        }
        return { total: entrees.length, total24, total7, echecs };
    }, [entrees, horloge]);

    // ==== Filtre acteur ====
    const optionsActeur = useMemo(
        () => [
            { value: '', label: 'Tous les acteurs' },
            ...utilisateurs.map((u) => ({
                value: String(u.id),
                label: `${u.prenom} ${u.nom}`.trim(),
            })),
            { value: 'sans-session', label: 'Sans session' },
        ],
        [utilisateurs]
    );

    // Nom affiché dans la trace d'export pour le filtre acteur courant.
    const nomActeurFiltre = useMemo(() => {
        if (filtreActeur === 'sans-session') return 'Sans session';
        if (filtreActeur === '') return null;
        const u = utilisateurs.find((x) => String(x.id) === filtreActeur);
        return u ? `${u.prenom} ${u.nom}`.trim() : null;
    }, [filtreActeur, utilisateurs]);

    const filtreActif = filtreAction !== '' || filtreEntite !== '' || filtreActeur !== ''
        || filtreDu !== '' || filtreAu !== '' || recherche.trim() !== '' || filtreBien !== '';

    const {
        currentItems,
        currentPage,
        totalPages,
        totalItems,
        handlePageChange,
    } = usePagination(filtres, PAR_PAGE);

    // ==== Export CSV — tracé (le journal audite sa propre extraction) ====
    const handleExport = async () => {
        if (filtres.length === 0 || exportEnCours) return;
        setExportEnCours(true);
        try {
            exportService.exportJournalCsv(filtres);
            await journalService.tracerExport({
                nombre: filtres.length,
                action: filtreAction || null,
                entite: filtreEntite || null,
                acteurNom: nomActeurFiltre,
                du: filtreDu || null,
                au: filtreAu || null,
                recherche: recherche.trim() || null,
                bien: filtreBien !== '' ? Number(filtreBien) : null,
            });
            // La trace d'export apparaît en tête du journal rechargé.
            await chargerEntrees();
        } catch (err) {
            console.error(err);
            setError('Export du journal impossible');
        } finally {
            setExportEnCours(false);
        }
    };

    /** Isole l'historique d'une entité — entité + identifiant, en une seule écriture d'URL. */
    const filtrerHistorique = useCallback((e) => {
        setEntreeDetail(null);
        if (e.entiteId == null) return;
        setSearchParams((precedent) => {
            const suivant = new URLSearchParams(precedent);
            suivant.set('bien', String(e.entiteId));
            suivant.set('entite', e.entite);
            return suivant;
        }, { replace: true });
    }, [setSearchParams]);

    const COLUMNS = [
        {
            key: 'date',
            label: 'Date & heure',
            primary: true,
            render: (e) => <span className="text-ink font-medium">{formatDateHeure(e.dateAction)}</span>,
        },
        {
            key: 'acteur',
            label: 'Acteur',
            render: (e) =>
                e.acteurNom ? (
                    <span className="text-sm text-ink truncate">{e.acteurNom}</span>
                ) : (
                    <span className="text-xs text-ink-subtle italic">Sans session</span>
                ),
        },
        {
            key: 'action',
            label: 'Action',
            render: (e) => (
                <Badge
                    tone={ACTIONS[e.action]?.tone || 'neutral'}
                    label={ACTIONS[e.action]?.label || e.action}
                />
            ),
        },
        {
            key: 'entite',
            label: 'Entité',
            hideBelow: 'md',
            render: (e) => (
                <div className="min-w-0">
                    <p className="text-sm text-ink truncate">{e.entite}</p>
                    {e.entiteId != null && (
                        <p className="text-xs text-ink-subtle">
                            #{e.entiteId}
                            {e.entite === 'Immobilisation' && (
                                <>
                                    {' · '}
                                    <Link
                                        to={`/immobilisations?immo=${e.entiteId}`}
                                        className="text-brand-600 hover:underline dark:text-brand-300"
                                        title="Ouvrir la fiche du bien"
                                    >
                                        Fiche
                                    </Link>
                                </>
                            )}
                        </p>
                    )}
                </div>
            ),
        },
        {
            key: 'description',
            label: 'Détail',
            hideBelow: 'lg',
            render: (e) => <span className="text-ink-muted truncate">{e.description}</span>,
        },
        {
            key: 'actions',
            label: '',
            align: 'right',
            render: (e) => (
                <RowAction icon={Eye} label="Voir le détail" onClick={() => setEntreeDetail(e)} variant="neutral" />
            ),
        },
    ];

    return (
        <div className="space-y-5">
            {/* En-tête + synthèse */}
            <div className="card p-4 sm:p-5">
                <h1 className="page-title flex items-center gap-2">
                    <ScrollText size={20} className="text-brand-500" />
                    Journal d'audit
                </h1>
                <p className="text-sm text-ink-muted mt-0.5">
                    Trace horodatée des actions sensibles — consultation seule, jamais de modification.
                </p>

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mt-4">
                    <MiniStat icon={ScrollText} label="Entrées totales" value={stats.total} tone="neutral" />
                    <MiniStat icon={Activity} label="Activité (24 h)" value={stats.total24} tone="brand" />
                    <MiniStat icon={CalendarDays} label="Activité (7 jours)" value={stats.total7} tone="neutral" />
                    {/* Carte cliquable : les échecs de connexion mènent droit au filtre. */}
                    <button
                        type="button"
                        onClick={() => majParam('action', 'CONNEXION_ECHOUEE')}
                        title="Filtrer les connexions échouées"
                        className="text-left"
                    >
                        <MiniStat icon={ShieldAlert} label="Connexions échouées" value={stats.echecs} tone="danger" />
                    </button>
                </div>
            </div>

            {error && <div className="alert-danger alert">{error}</div>}

            {/* Journal */}
            <TableCard>
                {/* Barre d'outils — deux lignes : recherche + actions, puis
                    filtres. Les critères vivent dans l'URL : copier le lien
                    réouvre exactement la même vue. */}
                <div className="p-3 sm:p-4 space-y-3">
                    <div className="flex flex-wrap sm:flex-nowrap items-center gap-2">
                        <div className="search-field min-w-[14rem]">
                            <input
                                type="text"
                                placeholder="Rechercher acteur, détail, action…"
                                value={recherche}
                                onChange={(e) => majParam('q', e.target.value)}
                                className="input pl-9"
                                aria-label="Rechercher dans le journal"
                            />
                            <Search
                                size={16}
                                className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
                                aria-hidden="true"
                            />
                        </div>

                        <div className="flex items-center gap-2 sm:shrink-0">
                            <button
                                type="button"
                                onClick={handleExport}
                                disabled={exportEnCours || filtres.length === 0}
                                className="btn-secondary btn-md"
                                aria-label="Exporter le journal filtré en CSV — l'export est tracé"
                                title={
                                    filtres.length === 0
                                        ? 'Aucune entrée à exporter'
                                        : `Exporter ${filtres.length} entrée(s) en CSV — l'export est tracé`
                                }
                            >
                                {exportEnCours ? (
                                    <Loader2 size={16} className="animate-spin" />
                                ) : (
                                    <Download size={16} />
                                )}
                                {/* Sous sm : icône seule — la ligne d'outils garde
                                    sa respiration sur petit écran. */}
                                <span className="hidden sm:inline">Export CSV</span>
                            </button>

                            <button
                                type="button"
                                onClick={reinitialiser}
                                disabled={!filtreActif}
                                className="btn-ghost btn-md"
                                aria-label="Réinitialiser les filtres"
                                title="Réinitialiser les filtres"
                            >
                                <RotateCcw size={15} />
                                <span className="hidden sm:inline">Réinitialiser</span>
                            </button>
                        </div>
                    </div>

                    {/* Filtres — sous sm, chaque critère occupe sa ligne (le
                        choix mérite une cible complète) ; au-delà, une seule
                        ligne de critères, les dates partageant la leur. */}
                    <div className="flex flex-wrap items-center gap-2 pt-3 border-t border-line/60">
                        <span className="section-label hidden md:inline mr-1">Filtres</span>

                        <Field
                            as="select"
                            aria-label="Filtrer par action"
                            value={filtreAction}
                            onChange={(e) => majParam('action', e.target.value)}
                            options={[{ value: '', label: 'Toutes les actions' }, ...OPTIONS_ACTION]}
                            className="w-full sm:w-40"
                        />
                        <Field
                            as="select"
                            aria-label="Filtrer par entité"
                            value={filtreEntite}
                            onChange={(e) => majParam('entite', e.target.value)}
                            options={[{ value: '', label: 'Toutes les entités' }, ...OPTIONS_ENTITE]}
                            className="w-full sm:w-44"
                        />
                        <Field
                            as="select"
                            aria-label="Filtrer par acteur"
                            value={filtreActeur}
                            onChange={(e) => majParam('acteur', e.target.value)}
                            options={optionsActeur}
                            className="w-full sm:w-44"
                        />
                        <div className="flex items-center gap-2 w-full sm:w-auto">
                            <input
                                type="date"
                                value={filtreDu}
                                onChange={(e) => majParam('du', e.target.value)}
                                max={filtreAu || undefined}
                                aria-label="Du"
                                className="input flex-1 sm:flex-none sm:w-36 min-w-0"
                            />
                            <input
                                type="date"
                                value={filtreAu}
                                onChange={(e) => majParam('au', e.target.value)}
                                min={filtreDu || undefined}
                                aria-label="Au"
                                className="input flex-1 sm:flex-none sm:w-36 min-w-0"
                            />
                        </div>

                        {filtreBien !== '' && (
                            <button
                                type="button"
                                onClick={() => majParam('bien', '')}
                                className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-control border border-brand-500/50 bg-brand-50/60 text-xs font-semibold text-brand-700 dark:border-brand-500/40 dark:bg-brand-500/10 dark:text-brand-300"
                                title="Retirer le filtre d'historique"
                            >
                                <History size={13} />
                                #{filtreBien}
                                <X size={12} />
                            </button>
                        )}

                        <span className="ml-auto text-xs text-ink-muted shrink-0">
                            {totalItems} / {entrees.length} entrée(s)
                        </span>
                    </div>
                </div>

                {loading ? (
                    <TableSkeleton rows={8} cols={6} />
                ) : (
                    <>
                        <ResponsiveTable
                            columns={COLUMNS}
                            data={currentItems}
                            rowKey={(e) => e.id}
                            empty={
                                <EmptyState
                                    icon={filtreActif ? ListFilter : ScrollText}
                                    title={filtreActif ? 'Aucune entrée pour ces filtres' : 'Journal vide'}
                                    message={
                                        filtreActif
                                            ? 'Aucune action ne correspond à la combinaison choisie.'
                                            : 'Les actions sensibles apparaîtront ici au fur et à mesure.'
                                    }
                                    action={
                                        filtreActif ? (
                                            <button onClick={reinitialiser} className="btn-secondary btn-md mt-2">
                                                <X size={16} />
                                                Réinitialiser les filtres
                                            </button>
                                        ) : null
                                    }
                                />
                            }
                        />
                        {totalItems > PAR_PAGE && (
                            <Pagination
                                currentPage={currentPage}
                                totalPages={totalPages}
                                onPageChange={handlePageChange}
                                totalItems={totalItems}
                                itemsPerPage={PAR_PAGE}
                            />
                        )}
                    </>
                )}
            </TableCard>

            {/* Détail d'une entrée — description entière, au-delà de la troncature de la colonne */}
            <Modal
                isOpen={entreeDetail != null}
                onClose={() => setEntreeDetail(null)}
                title="Détail de l'entrée"
                subtitle={entreeDetail ? `Entrée n° ${entreeDetail.id}` : undefined}
                footer={
                    <div className="flex flex-wrap justify-end gap-3">
                        {entreeDetail?.entiteId != null && (
                            <button
                                type="button"
                                onClick={() => filtrerHistorique(entreeDetail)}
                                className="btn-secondary btn-md"
                            >
                                <History size={15} />
                                Historique #{entreeDetail.entiteId}
                            </button>
                        )}
                        <button type="button" onClick={() => setEntreeDetail(null)} className="btn-primary btn-md">
                            Fermer
                        </button>
                    </div>
                }
            >
                {entreeDetail && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                        <DetailItem
                            icon={CalendarDays}
                            label="Date & heure"
                            value={formatDateHeure(entreeDetail.dateAction)}
                        />
                        <DetailItem icon={User} label="Acteur">
                            {entreeDetail.acteurNom || (
                                <span className="text-ink-subtle italic">Sans session</span>
                            )}
                        </DetailItem>
                        <DetailItem icon={Activity} label="Action">
                            <Badge
                                tone={ACTIONS[entreeDetail.action]?.tone || 'neutral'}
                                label={ACTIONS[entreeDetail.action]?.label || entreeDetail.action}
                            />
                        </DetailItem>
                        <DetailItem icon={Box} label="Entité">
                            {entreeDetail.entite}
                            {entreeDetail.entiteId != null && ` — n° ${entreeDetail.entiteId}`}
                            {entreeDetail.entite === 'Immobilisation' && entreeDetail.entiteId != null && (
                                <>
                                    {' · '}
                                    <Link
                                        to={`/immobilisations?immo=${entreeDetail.entiteId}`}
                                        className="text-brand-600 hover:underline dark:text-brand-300"
                                    >
                                        Ouvrir la fiche du bien
                                    </Link>
                                </>
                            )}
                        </DetailItem>
                        <div>
                            <p className="section-label mb-1">Détail</p>
                            <p className="text-ink-muted whitespace-pre-wrap">{entreeDetail.description}</p>
                        </div>
                    </div>
                )}
            </Modal>
        </div>
    );
};

export default JournalAudit;

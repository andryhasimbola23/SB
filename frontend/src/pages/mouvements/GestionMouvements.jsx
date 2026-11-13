import { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Eye, History } from 'lucide-react';
import mouvementService from '../../services/mouvementService';
import exportService from '../../services/exportService';
import Modal from '../../components/ui/Modal';
import Pagination from '../../components/ui/Pagination';
import ExportButton from '../../components/ui/ExportButton';
import DataToolbar, { FilterSelect } from '../../components/ui/DataToolbar';
import ResponsiveTable, { RowAction, TableCard } from '../../components/ui/ResponsiveTable';
import Badge from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/Surface';
import { MiniStat } from '../../components/dashboard/StatCard';
import { TableSkeleton } from '../../components/ui/Skeleton';
import DetailItem from '../../components/ui/DetailItem';
import { usePagination } from '../../hooks/usePagination';
import { Package, ArrowRightLeft, Send, Trash2, CalendarDays, Banknote, Receipt, User, Scale } from 'lucide-react';
import { TYPES_MOUVEMENT } from '../../theme/toneStyles';
import { formatMontant, formatDateTime as formatDate } from '../../utils/format';

const TYPE_TONES = {
    ENTREE: 'brand',
    TRANSFERT: 'neutral',
    AFFECTATION: 'brand',
    CESSION: 'warning',
    REBUT: 'danger',
};

const GestionMouvements = () => {
    const [mouvements, setMouvements] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [keyword, setKeyword] = useState('');
    const [filterType, setFilterType] = useState('');

    const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
    const [viewingMouvement, setViewingMouvement] = useState(null);
    const [searchParams, setSearchParams] = useSearchParams();

    useEffect(() => {
        loadMouvements();
    }, []);

    /**
     * Lien en profondeur depuis le tableau de bord : `?mouvement=<id>`.
     *
     * Le mouvement est chargé par son identifiant, indépendamment de la liste —
     * sinon il faudrait d'abord retrouver la page qui le contient, alors que la
     * cible peut être à la sixième page et filtrée hors écran. Le paramètre est
     * retiré de l'URL une fois la modale ouverte : un rechargement ne la rouvre
     * pas, et on peut la fermer sans qu'elle revienne.
     */
    useEffect(() => {
        const id = searchParams.get('mouvement');
        if (!id) return undefined;

        let vivant = true;
        (async () => {
            try {
                const mvt = await mouvementService.getById(id);
                if (!vivant) return;
                setError('');
                setViewingMouvement(mvt);
                setIsDetailsModalOpen(true);
            } catch (err) {
                if (!vivant) return;
                console.error(err);
                setError('Mouvement introuvable ou hors de votre périmètre');
            } finally {
                if (vivant) setSearchParams({}, { replace: true });
            }
        })();

        return () => {
            vivant = false;
        };
    }, [searchParams, setSearchParams]);

    const loadMouvements = async () => {
        try {
            setLoading(true);
            setMouvements(await mouvementService.getAll());
        } catch (err) {
            setError('Erreur lors du chargement');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const filteredMouvements = mouvements.filter((mvt) => {
        const q = keyword.trim().toLowerCase();
        const matchesKeyword =
            q === '' ||
            mvt.immobilisationCode?.toLowerCase().includes(q) ||
            mvt.immobilisationDesignation?.toLowerCase().includes(q);
        const matchesType = filterType === '' || mvt.typeMouvement === filterType;
        return matchesKeyword && matchesType;
    });

    const {
        currentItems,
        currentPage,
        totalPages,
        totalItems,
        handlePageChange,
    } = usePagination(filteredMouvements, 6);

    const handleOpenDetails = (mvt) => {
        setViewingMouvement(mvt);
        setIsDetailsModalOpen(true);
    };

    const stats = {
        total: mouvements.length,
        entrees: mouvements.filter((m) => m.typeMouvement === 'ENTREE').length,
        transferts: mouvements.filter((m) => m.typeMouvement === 'TRANSFERT').length,
        cessions: mouvements.filter((m) => m.typeMouvement === 'CESSION').length,
        rebuts: mouvements.filter((m) => m.typeMouvement === 'REBUT').length,
    };

    /* Détail lisible du mouvement selon son type */
    const DetailCell = ({ mvt }) => {
        if (mvt.typeMouvement === 'TRANSFERT') {
            return (
                <span className="truncate">
                    {mvt.ancienneLocalisation} <span className="text-ink-subtle">→</span>{' '}
                    <span className="text-ink font-medium">{mvt.nouvelleLocalisation}</span>
                </span>
            );
        }
        if (mvt.typeMouvement === 'CESSION' || mvt.typeMouvement === 'REBUT') {
            return <span>VNC : {formatMontant(mvt.valeurVnc)}</span>;
        }
        return <span className="text-ink-subtle">Enregistrement initial</span>;
    };

    const COLUMNS = [
        {
            key: 'date',
            label: 'Date',
            primary: true,
            render: (m) => <span className="text-ink font-medium">{formatDate(m.dateMouvement)}</span>,
        },
        {
            key: 'type',
            label: 'Type',
            render: (m) => <Badge tone={TYPE_TONES[m.typeMouvement]} label={TYPES_MOUVEMENT[m.typeMouvement]?.label || m.typeMouvement} />,
        },
        {
            key: 'immobilisation',
            label: 'Immobilisation',
            primary: true,
            render: (m) => (
                <div className="min-w-0">
                    <p className="text-sm font-medium text-ink truncate">{m.immobilisationDesignation}</p>
                    <p className="text-xs mono-code truncate">
                        {m.immobilisationCode}
                    </p>
                </div>
            ),
        },
        { key: 'detail', label: 'Détails', hideBelow: 'md', render: (m) => <DetailCell mvt={m} /> },
        {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            actions: true,
            render: (m) => <RowAction icon={Eye} label="Voir les détails" onClick={() => handleOpenDetails(m)} variant="brand" />,
        },
    ];

    const FILTERS = (
        <FilterSelect
            label="Type"
            value={filterType}
            onChange={setFilterType}
            options={[
                { value: '', label: 'Tous les types' },
                { value: 'ENTREE', label: 'Entrées' },
                { value: 'TRANSFERT', label: 'Transferts' },
                { value: 'CESSION', label: 'Cessions' },
                { value: 'REBUT', label: 'Rebuts' },
            ]}
        />
    );

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}

            {/* Compteurs */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                <MiniStat icon={History} label="Total" value={stats.total} tone="neutral" />
                <MiniStat icon={Package} label="Entrées" value={stats.entrees} tone="brand" />
                <MiniStat icon={ArrowRightLeft} label="Transferts" value={stats.transferts} tone="neutral" />
                <MiniStat icon={Send} label="Cessions" value={stats.cessions} tone="warning" />
                <MiniStat icon={Trash2} label="Rebuts" value={stats.rebuts} tone="danger" />
            </div>

            <DataToolbar
                keyword={keyword}
                onKeywordChange={setKeyword}
                onSearch={() => {}}
                placeholder="Rechercher par code ou désignation…"
                loading={loading}
                onRefresh={loadMouvements}
                filters={FILTERS}
                activeFilterCount={filterType ? 1 : 0}
                secondaryAction={
                    <ExportButton onExportExcel={exportService.exportMouvementsExcel} label="Exporter" />
                }
            />

            <TableCard>
                {loading ? (
                    <TableSkeleton rows={6} cols={5} />
                ) : (
                    <>
                        <ResponsiveTable
                            columns={COLUMNS}
                            data={currentItems}
                            rowKey={(m) => m.id}
                            empty={
                                <EmptyState
                                    icon={History}
                                    title="Aucun mouvement trouvé"
                                    message={
                                        keyword || filterType
                                            ? 'Aucun résultat pour ces critères.'
                                            : 'Les mouvements apparaîtront ici.'
                                    }
                                />
                            }
                        />
                        <Pagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}
                            totalItems={totalItems}
                            itemsPerPage={6}
                        />
                    </>
                )}
            </TableCard>

            {/* Détails */}
            <Modal
                isOpen={isDetailsModalOpen}
                onClose={() => setIsDetailsModalOpen(false)}
                title="Détails du mouvement"
                size="md"
            >
                {viewingMouvement && (
                    <div className="space-y-5">
                        {/* En-tête — même grammaire que la fiche
                            d'immobilisation : aplat neutre + code unique. */}
                        <div className="bg-surface-2 dark:bg-slate-800/60 rounded-panel p-3.5 border border-line flex items-start gap-3">
                            <div className="w-9 h-9 rounded-control bg-surface-3 flex items-center justify-center shrink-0">
                                <Package size={16} className="text-ink-muted" />
                            </div>
                            <div className="min-w-0">
                                <p className="section-label mb-1">Immobilisation</p>
                                <p className="text-sm font-semibold text-ink">
                                    {viewingMouvement.immobilisationDesignation}
                                </p>
                                <p className="text-xs mono-code mt-0.5">
                                    {viewingMouvement.immobilisationCode}
                                </p>
                            </div>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <DetailItem icon={ArrowRightLeft} label="Type">
                                <Badge
                                    tone={TYPE_TONES[viewingMouvement.typeMouvement]}
                                    label={TYPES_MOUVEMENT[viewingMouvement.typeMouvement]?.label || viewingMouvement.typeMouvement}
                                />
                            </DetailItem>
                            <DetailItem
                                icon={CalendarDays}
                                label="Date"
                                value={formatDate(viewingMouvement.dateMouvement)}
                            />
                        </div>

                        {viewingMouvement.demandeReference && (
                            <div className="bg-brand-50 dark:bg-brand-500/10 border border-brand-200 dark:border-brand-500/30 rounded-panel p-3.5">
                                <p className="section-label mb-1">Issue de la demande</p>
                                <Link
                                    to="/demandes"
                                    className="text-sm font-semibold text-brand-700 dark:text-brand-300 hover:underline"
                                >
                                    {viewingMouvement.demandeReference}
                                </Link>
                            </div>
                        )}

                        {viewingMouvement.typeMouvement === 'TRANSFERT' && (
                            <DetailItem icon={ArrowRightLeft} label="Affectation">
                                {viewingMouvement.ancienneLocalisation}{' '}
                                <span className="text-ink-subtle">→</span>{' '}
                                <span className="font-semibold">{viewingMouvement.nouvelleLocalisation}</span>
                            </DetailItem>
                        )}

                        {viewingMouvement.typeMouvement === 'AFFECTATION' && (
                            <div>
                                <p className="section-label mb-1">Servie dans</p>
                                <p className="text-sm text-ink">
                                    Le bien n'a pas bougé de service : seule la remise est tracée.
                                </p>
                            </div>
                        )}

                        {/* Chiffres — NEUTRES : une valeur n'est ni une action
                            ni un danger. Seule la plus/moins-value garde son
                            vert/rouge, sémantique (gain / perte). */}
                        {viewingMouvement.valeurVnc != null && (
                            <DetailItem
                                icon={Banknote}
                                label="VNC au moment du mouvement"
                                value={formatMontant(viewingMouvement.valeurVnc)}
                            />
                        )}

                        {viewingMouvement.prixVente != null && (
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <DetailItem
                                    icon={Receipt}
                                    label="Prix de vente"
                                    value={formatMontant(viewingMouvement.prixVente)}
                                />
                                {viewingMouvement.plusMoinsValue != null && (
                                    <DetailItem icon={Scale} label="Plus/moins-value">
                                        <span
                                            className={
                                                Number(viewingMouvement.plusMoinsValue) >= 0
                                                    ? 'text-brand-600 dark:text-brand-300'
                                                    : 'text-danger-600 dark:text-danger-300'
                                            }
                                        >
                                            {Number(viewingMouvement.plusMoinsValue).toLocaleString('fr-FR')} Ar
                                        </span>
                                    </DetailItem>
                                )}
                            </div>
                        )}

                        {viewingMouvement.commentaire && (
                            <div>
                                <p className="section-label mb-1">Commentaire</p>
                                <p className="text-sm text-ink-muted italic">« {viewingMouvement.commentaire} »</p>
                            </div>
                        )}

                        {viewingMouvement.utilisateurNom && (
                            <DetailItem icon={User} label="Effectué par" value={viewingMouvement.utilisateurNom} />
                        )}
                    </div>
                )}
            </Modal>
        </div>
    );
};

export default GestionMouvements;

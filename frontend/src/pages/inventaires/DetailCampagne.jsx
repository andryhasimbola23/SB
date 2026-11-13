import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Camera, Search, AlertTriangle, Trash2, Info } from 'lucide-react';
import inventaireService from '../../services/inventaireService';
import immobilisationService from '../../services/immobilisationService';
import exportService from '../../services/exportService';
import Modal from '../../components/ui/Modal';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import QrScanner from '../../components/ui/QrScanner';
import ExportButton from '../../components/ui/ExportButton';
import Badge from '../../components/ui/Badge';
import Field, { FormFooter } from '../../components/ui/Field';
import ResponsiveTable, { RowAction, TableCard } from '../../components/ui/ResponsiveTable';
import { EmptyState } from '../../components/ui/Surface';
import { ListSkeleton } from '../../components/ui/Skeleton';
import { extraireCodeUnique } from '../../utils/qr';

/** États constatés pendant l'inventaire → tons de la palette. */
const ETATS_CONSTATES = {
    PRESENT: { label: 'Présent', tone: 'brand' },
    MANQUANT: { label: 'Manquant', tone: 'danger' },
    MAUVAIS_ETAT: { label: 'Mauvais état', tone: 'warning' },
};

/** États physiques courants de l'immobilisation. */
const ETATS_IMMO = {
    NEUF: { label: 'Neuf', tone: 'brand' },
    BON: { label: 'Bon', tone: 'brand' },
    MAUVAIS: { label: 'Mauvais', tone: 'warning' },
    HORS_SERVICE: { label: 'Hors service', tone: 'danger' },
};

/** Choix cliquables de l'état constaté — 3 options, 3 tons. */
const ETAT_CHOICES = [
    { value: 'PRESENT', label: 'Présent', sub: '→ Bon', tone: 'brand' },
    { value: 'MAUVAIS_ETAT', label: 'Mauvais état', sub: '→ Mauvais', tone: 'warning' },
    { value: 'MANQUANT', label: 'Manquant', sub: '→ Hors service', tone: 'danger' },
];

const TONE_SELECTED = {
    brand: 'border-brand-500 bg-brand-50 dark:bg-brand-500/15 text-brand-700 dark:text-brand-300',
    warning: 'border-amber-500 bg-amber-50 dark:bg-amber-500/15 text-amber-700 dark:text-amber-300',
    danger: 'border-danger-500 bg-danger-50 dark:bg-danger-500/15 text-danger-700 dark:text-danger-300',
};

const DetailCampagne = () => {
    const { id } = useParams();
    const navigate = useNavigate();

    const [campagne, setCampagne] = useState(null);
    const [lignes, setLignes] = useState([]);
    const [immobilisations, setImmobilisations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const [isScannerOpen, setIsScannerOpen] = useState(false);
    const [isManualOpen, setIsManualOpen] = useState(false);
    const [searchKeyword, setSearchKeyword] = useState('');
    const [deleteId, setDeleteId] = useState(null);

    const [selectedImmo, setSelectedImmo] = useState(null);
    const [formData, setFormData] = useState({
        etatConstate: 'PRESENT',
        localisationConstatee: '',
        observation: '',
    });

    useEffect(() => {
        loadAll();
    }, [id]);

    const flash = (setter, msg, delay) => {
        setter(msg);
        setTimeout(() => setter(''), delay);
    };

    const loadAll = async () => {
        try {
            setLoading(true);
            const [campData, lignesData, immosData] = await Promise.all([
                inventaireService.getCampagneById(id),
                inventaireService.getLignesByCampagne(id),
                immobilisationService.getAll(),
            ]);
            setCampagne(campData);
            setLignes(lignesData);
            setImmobilisations(immosData);
        } catch (err) {
            setError('Erreur lors du chargement');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const openScan = (immo) => {
        let etatParDefaut = 'PRESENT';
        if (immo.etatPhysique === 'MAUVAIS') etatParDefaut = 'MAUVAIS_ETAT';
        if (immo.etatPhysique === 'HORS_SERVICE') etatParDefaut = 'MANQUANT';

        setSelectedImmo(immo);
        setFormData({
            etatConstate: etatParDefaut,
            localisationConstatee: `${immo.agenceNom} - ${immo.serviceNom}`,
            observation: '',
        });
        setIsManualOpen(true);
    };

    const handleScanSuccess = async (contenuScanne) => {
        setIsScannerOpen(false);
        // Le scan renvoie le payload COMPLET du QR (code + éventuel état et
        // statut) ; la recherche ne se fait que sur le code unique.
        const codeUnique = extraireCodeUnique(contenuScanne);
        const immo = immobilisations.find((i) => i.codeUnique === codeUnique);
        if (!immo) {
            flash(setError, `Aucune immobilisation trouvée pour le code : ${codeUnique}`, 4000);
            return;
        }
        openScan(immo);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            await inventaireService.scanner({
                campagneId: Number(id),
                immobilisationId: selectedImmo.id,
                etatConstate: formData.etatConstate,
                localisationConstatee: formData.localisationConstatee,
                observation: formData.observation,
            });
            flash(setSuccess, `${selectedImmo.designation} enregistré`, 3000);
            setIsManualOpen(false);
            setSelectedImmo(null);
            loadAll();
        } catch (err) {
            flash(setError, err.response?.data?.message || "Erreur lors de l'enregistrement", 4000);
        }
    };

    const handleDeleteLigne = async () => {
        try {
            await inventaireService.deleteLigne(deleteId);
            flash(setSuccess, 'Ligne supprimée', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 4000);
        } finally {
            setDeleteId(null);
            loadAll();
        }
    };

    const filteredImmobilisations = immobilisations.filter(
        (i) =>
            searchKeyword.trim() === '' ||
            i.codeUnique?.toLowerCase().includes(searchKeyword.toLowerCase()) ||
            i.designation?.toLowerCase().includes(searchKeyword.toLowerCase())
    );

    const scannedIds = new Set(lignes.map((l) => l.immobilisationId));

    const StatBox = ({ label, value, tone }) => {
        const tones = {
            neutral: 'text-ink',
            brand: 'text-brand-600 dark:text-brand-300',
            danger: 'text-danger-600 dark:text-danger-300',
            warning: 'text-amber-600 dark:text-amber-300',
        };
        return (
            <div className="text-center bg-surface-2 dark:bg-slate-800/50 rounded-panel py-2.5 px-2 border border-line">
                <p className="section-label">{label}</p>
                <p className={`text-lg font-bold ${tones[tone] || tones.neutral}`}>{value || 0}</p>
            </div>
        );
    };

    const LIGNES_COLUMNS = [
        {
            key: 'code',
            label: 'Code',
            primary: true,
            render: (l) => <span className="font-mono font-semibold text-ink">{l.immobilisationCode}</span>,
        },
        {
            key: 'designation',
            label: 'Désignation',
            primary: true,
            render: (l) => <span className="text-ink">{l.immobilisationDesignation}</span>,
        },
        {
            key: 'etat',
            label: 'État',
            render: (l) => {
                const meta = ETATS_CONSTATES[l.etatConstate] || { label: l.etatConstate, tone: 'neutral' };
                return <Badge tone={meta.tone} label={meta.label} />;
            },
        },
        {
            key: 'etatImmo',
            label: 'État immo',
            hideBelow: 'md',
            render: (l) => {
                const immo = immobilisations.find((i) => i.id === l.immobilisationId);
                if (!immo) return <span className="text-ink-subtle">—</span>;
                const meta = ETATS_IMMO[immo.etatPhysique] || { label: immo.etatPhysique, tone: 'neutral' };
                return <Badge tone={meta.tone} label={meta.label} dot={false} />;
            },
        },
        {
            key: 'dateScan',
            label: 'Date scan',
            hideBelow: 'lg',
            render: (l) => <span className="text-xs text-ink-muted">{new Date(l.dateScan).toLocaleString('fr-FR')}</span>,
        },
        {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            actions: true,
            render: (l) => <RowAction icon={Trash2} label="Supprimer" onClick={() => setDeleteId(l.id)} variant="delete" />,
        },
    ];

    if (loading) {
        return (
            <div className="space-y-6">
                <div className="card p-5 space-y-3">
                    <div className="h-4 bg-surface-3 rounded w-32 animate-pulse" />
                    <div className="h-6 bg-surface-3 rounded w-64 animate-pulse" />
                    <div className="h-4 bg-surface-3 rounded w-48 animate-pulse" />
                </div>
                <ListSkeleton items={3} />
            </div>
        );
    }

    if (!campagne) {
        return (
            <div className="card">
                <EmptyState icon={AlertTriangle} title="Campagne introuvable" message="Cette campagne n'existe pas ou a été supprimée." />
            </div>
        );
    }

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}
            {success && <div className="alert-brand alert">{success}</div>}

            {/* En-tête */}
            <div className="card p-4 sm:p-5">
                <button
                    onClick={() => navigate('/inventaires')}
                    className="inline-flex items-center gap-1.5 text-sm act-quiet mb-3"
                >
                    <ArrowLeft size={16} />
                    Retour aux campagnes
                </button>

                <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-3">
                    <div className="min-w-0">
                        <h1 className="page-title">{campagne.libelle}</h1>
                        <p className="text-sm text-ink-muted mt-1">
                            {campagne.agenceNom
                                ? `${campagne.agenceCode} - ${campagne.agenceNom}`
                                : 'Toutes les agences'}
                        </p>
                    </div>

                    <div className="flex flex-wrap gap-2 shrink-0">
                        {campagne.statut === 'EN_COURS' && (
                            <>
                                <button onClick={() => setIsScannerOpen(true)} className="btn-primary btn-md">
                                    <Camera size={16} />
                                    Scanner
                                </button>
                                <button
                                    onClick={() => {
                                        setSelectedImmo(null);
                                        setSearchKeyword('');
                                        setIsManualOpen(true);
                                    }}
                                    className="btn-neutral btn-md"
                                >
                                    <Search size={16} />
                                    Saisie
                                </button>
                            </>
                        )}
                        <ExportButton
                            onExportExcel={() => exportService.exportInventaireExcel(campagne.id)}
                            onExportPdf={() => exportService.exportInventairePdf(campagne.id)}
                            label="Exporter"
                        />
                    </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-2.5 mt-5 pt-4 border-t border-line">
                    <StatBox label="Total biens" value={campagne.totalImmobilisations} />
                    <StatBox label="Scannés" value={campagne.totalScannees} />
                    <StatBox label="Présents" value={campagne.totalPresentes} tone="brand" />
                    <StatBox label="Manquants" value={campagne.totalManquantes} tone="danger" />
                    <StatBox label="Mauvais état" value={campagne.totalMauvaisEtat} tone="warning" />
                </div>
            </div>

            {/* Info synchronisation */}
            <div className="alert-brand alert">
                <Info size={16} className="mt-0.5 shrink-0" />
                <p className="text-xs">
                    <strong>Synchronisation automatique :</strong> l'état que vous constatez sera appliqué
                    directement à l'immobilisation.
                </p>
            </div>

            {/* Lignes scannées */}
            <TableCard>
                <div className="px-4 sm:px-5 py-3.5 border-b border-line">
                    <h2 className="card-title">Immobilisations scannées ({lignes.length})</h2>
                </div>
                <ResponsiveTable
                    columns={LIGNES_COLUMNS}
                    data={lignes}
                    rowKey={(l) => l.id}
                    dense
                    empty={
                        <EmptyState
                            icon={Camera}
                            title="Aucune immobilisation scannée"
                            message="Cliquez sur « Scanner » pour commencer."
                        />
                    }
                />
            </TableCard>

            {/* Scanner QR */}
            {isScannerOpen && (
                <Modal isOpen={isScannerOpen} onClose={() => setIsScannerOpen(false)} title="Scanner un QR Code" size="md">
                    <QrScanner onScanSuccess={handleScanSuccess} />
                </Modal>
            )}

            {/* Saisie */}
            <Modal
                isOpen={isManualOpen}
                onClose={() => {
                    setIsManualOpen(false);
                    setSelectedImmo(null);
                }}
                title={selectedImmo ? 'Enregistrer un scan' : 'Sélectionner une immobilisation'}
                size="md"
            >
                {!selectedImmo ? (
                    <div className="space-y-4">
                        <div className="relative">
                            <Search
                                size={16}
                                className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
                            />
                            <input
                                type="text"
                                placeholder="Rechercher par code ou désignation…"
                                value={searchKeyword}
                                onChange={(e) => setSearchKeyword(e.target.value)}
                                autoFocus
                                className="input pl-9"
                                aria-label="Rechercher une immobilisation"
                            />
                        </div>

                        <div className="max-h-96 overflow-y-auto space-y-2 scrollbar-thin">
                            {filteredImmobilisations.slice(0, 30).map((immo) => {
                                const alreadyScanned = scannedIds.has(immo.id);
                                const etat = ETATS_IMMO[immo.etatPhysique] || {
                                    label: immo.etatPhysique || '-',
                                    tone: 'neutral',
                                };
                                return (
                                    <button
                                        key={immo.id}
                                        onClick={() => openScan(immo)}
                                        className="w-full text-left p-3 rounded-panel border border-line tile-hover"
                                    >
                                        <div className="flex items-center justify-between gap-3">
                                            <div className="min-w-0 flex-1">
                                                <p className="text-sm font-medium text-ink truncate">{immo.designation}</p>
                                                <p className="text-xs mono-code">
                                                    {immo.codeUnique}
                                                </p>
                                                <div className="mt-1.5">
                                                    <Badge tone={etat.tone} label={`État : ${etat.label}`} dot={false} />
                                                </div>
                                            </div>
                                            {alreadyScanned && (
                                                <Badge tone="brand" label="Scanné" dot={false} className="shrink-0" />
                                            )}
                                        </div>
                                    </button>
                                );
                            })}
                            {filteredImmobilisations.length === 0 && (
                                <p className="text-sm text-ink-muted text-center py-6">Aucun résultat</p>
                            )}
                        </div>
                    </div>
                ) : (
                    <form onSubmit={handleSubmit}>
                        <div className="bg-surface-2 dark:bg-slate-800/60 rounded-panel p-3.5 border border-line">
                            <p className="section-label mb-1">Immobilisation</p>
                            <p className="text-sm font-semibold text-ink">{selectedImmo.designation}</p>
                            <p className="text-xs mono-code mt-0.5">
                                {selectedImmo.codeUnique}
                            </p>
                            <div className="mt-2 flex items-center gap-2">
                                <span className="text-xs text-ink-muted">État actuel :</span>
                                <Badge
                                    tone={ETATS_IMMO[selectedImmo.etatPhysique]?.tone || 'neutral'}
                                    label={ETATS_IMMO[selectedImmo.etatPhysique]?.label || '-'}
                                    dot={false}
                                />
                            </div>
                        </div>

                        <div className="alert-warning alert mt-4">
                            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
                            <p className="text-xs">
                                <strong>Attention :</strong> l'état sélectionné ci-dessous sera appliqué à
                                l'immobilisation.
                            </p>
                        </div>

                        <div className="mt-5">
                            <p className="field-label">État constaté</p>
                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                                {ETAT_CHOICES.map((etat) => {
                                    const active = formData.etatConstate === etat.value;
                                    return (
                                        <button
                                            key={etat.value}
                                            type="button"
                                            onClick={() => setFormData((p) => ({ ...p, etatConstate: etat.value }))}
                                            aria-pressed={active}
                                            className={`p-2.5 rounded-panel border-2 text-xs font-medium transition-all ${
                                                active
                                                    ? TONE_SELECTED[etat.tone]
                                                    : 'border-line text-ink-muted hover:border-line-strong'
                                            }`}
                                        >
                                            <div>{etat.label}</div>
                                            <div className="text-[10px] opacity-70 mt-0.5">{etat.sub}</div>
                                        </button>
                                    );
                                })}
                            </div>
                        </div>

                        <div className="mt-4">
                            <Field
                                label="Localisation constatée"
                                value={formData.localisationConstatee}
                                onChange={(e) =>
                                    setFormData((p) => ({ ...p, localisationConstatee: e.target.value }))
                                }
                            />
                        </div>

                        <div className="mt-4">
                            <Field
                                label="Observation"
                                as="textarea"
                                value={formData.observation}
                                onChange={(e) => setFormData((p) => ({ ...p, observation: e.target.value }))}
                                rows={2}
                                placeholder="Remarques…"
                                className="resize-none"
                            />
                        </div>

                        <FormFooter
                            onCancel={() => setSelectedImmo(null)}
                            onCancelLabel="Retour"
                            submitLabel="Enregistrer & Synchroniser"
                        />
                    </form>
                )}
            </Modal>

            <ConfirmDialog
                isOpen={deleteId !== null}
                onClose={() => setDeleteId(null)}
                onConfirm={handleDeleteLigne}
                title="Supprimer la ligne"
                message="Êtes-vous sûr de vouloir supprimer cette ligne d'inventaire ? L'état de l'immobilisation restera inchangé."
                confirmLabel="Supprimer"
            />
        </div>
    );
};

export default DetailCampagne;

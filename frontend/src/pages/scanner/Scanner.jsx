import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    ScanLine, ExternalLink, CheckCircle, AlertTriangle,
    ClipboardList, History, Package, ClipboardCheck,
} from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';
import QrScanner from '../../components/ui/QrScanner';
import immobilisationService from '../../services/immobilisationService';
import inventaireService from '../../services/inventaireService';
import Modal from '../../components/ui/Modal';
import Field, { FormFooter } from '../../components/ui/Field';
import Badge from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/Surface';
import { extraireCodeUnique } from '../../utils/qr';
import { formatDateTime } from '../../utils/format';

/** États physiques → tons. */
const ETATS_IMMO = {
    NEUF: { label: 'Neuf', tone: 'brand' },
    BON: { label: 'Bon', tone: 'brand' },
    MAUVAIS: { label: 'Mauvais', tone: 'warning' },
    HORS_SERVICE: { label: 'Hors service', tone: 'danger' },
};

/** Choix d'état constaté (mêmes tons que l'inventaire). */
const ETAT_CHOICES = [
    { value: 'PRESENT', label: 'Présent', tone: 'brand' },
    { value: 'MAUVAIS_ETAT', label: 'Mauvais état', tone: 'warning' },
    { value: 'MANQUANT', label: 'Manquant', tone: 'danger' },
];

const TONE_SELECTED = {
    brand: 'border-brand-500 bg-brand-50 dark:bg-brand-500/15 text-brand-700 dark:text-brand-300',
    warning: 'border-amber-500 bg-amber-50 dark:bg-amber-500/15 text-amber-700 dark:text-amber-300',
    danger: 'border-danger-500 bg-danger-50 dark:bg-danger-500/15 text-danger-700 dark:text-danger-300',
};

const MAX_HISTORIQUE = 10;

/** Message temporaire (succès ou erreur) — pur helper, hors composant. */
const flash = (setter, msg, delay) => {
    setter(msg);
    setTimeout(() => setter(''), delay);
};

/**
 * Scanner global — accessible depuis l'en-tête, ouvert aux deux rôles.
 *
 * Scan continu : la caméra reste active, chaque étiquette scannée est
 * identifiée via `GET /immobilisations/code?code=…` (périmètre respecté).
 *
 * - **Employé** : identification + accès à la fiche du bien.
 * - **ADMIN** : identification + enregistrement direct d'un constat dans une
 *   campagne EN_COURS (l'inventaire est administratif), sans repasser par la
 *   page de campagne.
 *
 * Les 10 derniers scans de la session restent affichés (utile pour vérifier
 * qu'une étiquette a bien été prise en compte, même en cas de doute).
 */
const Scanner = () => {
    const navigate = useNavigate();
    const { isAdmin } = useAuth();

    const [historique, setHistorique] = useState([]); // { code, immo?, ok, at, note }
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const [campagnesActives, setCampagnesActives] = useState([]);
    const [campagneChoisie, setCampagneChoisie] = useState('');

    const [constatImmo, setConstatImmo] = useState(null);
    const [formData, setFormData] = useState({ etatConstate: 'PRESENT', localisationConstatee: '', observation: '' });
    const [enregistrement, setEnregistrement] = useState(false);

    // Campagnes EN_COURS : uniquement pour l'ADMIN (l'inventaire est administratif).
    useEffect(() => {
        if (!isAdmin()) return;
        inventaireService
            .getAllCampagnes()
            .then((campagnes) => {
                const actives = campagnes.filter((c) => c.statut === 'EN_COURS');
                setCampagnesActives(actives);
                if (actives.length > 0) setCampagneChoisie(String(actives[0].id));
            })
            .catch(() => {
                // Campagnes indisponibles : le scan identification reste utilisable.
            });
    }, [isAdmin]);

    const handleScanSuccess = useCallback(
        async (contenuScanne) => {
            const code = extraireCodeUnique(contenuScanne);
            if (!code) return;

            try {
                const immo = await immobilisationService.getByCode(code);
                setHistorique((h) =>
                    [{ code, immo, ok: true, at: new Date().toISOString() }, ...h].slice(0, MAX_HISTORIQUE)
                );
            } catch (err) {
                const inconnu = err.response?.status === 404;
                setHistorique((h) =>
                    [
                        {
                            code,
                            ok: false,
                            at: new Date().toISOString(),
                            note: inconnu ? 'Code inconnu ou hors périmètre' : 'Erreur de lecture',
                        },
                        ...h,
                    ].slice(0, MAX_HISTORIQUE)
                );
                flash(
                    setError,
                    inconnu
                        ? `Aucun bien trouvé pour le code ${code} (ou hors de votre périmètre)`
                        : 'Erreur lors de la lecture du bien',
                    4000
                );
            }
        },
        []
    );

    /** Ouvre le constat pour le dernier bien scanné (ADMIN, campagne choisie). */
    const ouvrirConstat = (immo) => {
        setConstatImmo(immo);
        setFormData({
            etatConstate: 'PRESENT',
            localisationConstatee: `${immo.agenceNom || ''} - ${immo.serviceNom || ''}`.replace(/ - $/, ''),
            observation: '',
        });
    };

    const handleSubmitConstat = async (e) => {
        e.preventDefault();
        if (!constatImmo || !campagneChoisie) return;
        setEnregistrement(true);
        try {
            await inventaireService.scanner({
                campagneId: Number(campagneChoisie),
                immobilisationId: constatImmo.id,
                etatConstate: formData.etatConstate,
                localisationConstatee: formData.localisationConstatee,
                observation: formData.observation,
            });
            flash(setSuccess, `Constat enregistré pour ${constatImmo.designation}`, 3000);
            setConstatImmo(null);
        } catch (err) {
            flash(setError, err.response?.data?.message || "Erreur lors de l'enregistrement", 4000);
        } finally {
            setEnregistrement(false);
        }
    };

    const dernierScan = historique[0];

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}
            {success && <div className="alert-brand alert">{success}</div>}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-start">
                {/* Scanner */}
                <div className="card p-4 sm:p-5">
                    <div className="flex items-center justify-between gap-3 mb-4">
                        <div>
                            <h1 className="page-title flex items-center gap-2">
                                <ScanLine size={20} className="text-brand-500" />
                                Scanner
                            </h1>
                            <p className="text-sm text-ink-muted mt-0.5">
                                Identification immédiate d'un bien par son étiquette.
                            </p>
                        </div>
                        <Badge tone="brand" label={`Scans : ${historique.filter((h) => h.ok).length}`} dot={false} />
                    </div>

                    <QrScanner onScanSuccess={handleScanSuccess} />
                </div>

                {/* Résultat + historique */}
                <div className="space-y-4">
                    {/* Dernier scan */}
                    {dernierScan?.ok ? (
                        <div className="card p-4 sm:p-5">
                            <p className="section-label mb-2">Dernier scan</p>
                            <div className="flex items-start justify-between gap-3">
                                <div className="min-w-0">
                                    <p className="text-base font-semibold text-ink truncate">
                                        {dernierScan.immo.designation}
                                    </p>
                                    <p className="text-xs mono-code mt-0.5">
                                        {dernierScan.immo.codeUnique}
                                    </p>
                                    <div className="flex flex-wrap items-center gap-2 mt-2">
                                        <StatutBadgeLite statut={dernierScan.immo.statut} />
                                        <Badge
                                            tone={ETATS_IMMO[dernierScan.immo.etatPhysique]?.tone || 'neutral'}
                                            label={ETATS_IMMO[dernierScan.immo.etatPhysique]?.label || '-'}
                                            dot={false}
                                        />
                                        {dernierScan.immo.serviceNom && (
                                            <Badge tone="neutral" label={dernierScan.immo.serviceNom} dot={false} />
                                        )}
                                    </div>
                                </div>
                            </div>

                            <div className="flex flex-wrap gap-2 mt-4">
                                <button
                                    onClick={() => navigate(`/immobilisations?immo=${dernierScan.immo.id}`)}
                                    className="btn-primary btn-md"
                                >
                                    <ExternalLink size={16} />
                                    Voir la fiche
                                </button>
                                {isAdmin() && campagnesActives.length > 0 && (
                                    <button
                                        onClick={() => ouvrirConstat(dernierScan.immo)}
                                        className="btn-neutral btn-md"
                                    >
                                        <ClipboardCheck size={16} />
                                        Enregistrer un constat
                                    </button>
                                )}
                            </div>
                        </div>
                    ) : dernierScan ? (
                        <div className="card p-4 sm:p-5">
                            <p className="section-label mb-2">Dernier scan</p>
                            <div className="alert-danger alert mb-0">
                                <AlertTriangle size={16} className="mt-0.5 shrink-0" />
                                <div>
                                    <p className="text-sm font-medium">{dernierScan.note}</p>
                                    <p className="text-xs font-mono mt-0.5">{dernierScan.code}</p>
                                </div>
                            </div>
                        </div>
                    ) : (
                        <div className="card">
                            <EmptyState
                                icon={ScanLine}
                                title="Aucun scan pour l'instant"
                                message="Scannez une étiquette : le bien reconnu s'affiche ici."
                            />
                        </div>
                    )}

                    {/* Choix de campagne (ADMIN) */}
                    {isAdmin() && campagnesActives.length > 0 && (
                        <div className="card p-4 sm:p-5">
                            <p className="section-label mb-2 flex items-center gap-1.5">
                                <ClipboardList size={13} />
                                Campagne d'inventaire active
                            </p>
                            <Field
                                as="select"
                                aria-label="Campagne d'inventaire"
                                value={campagneChoisie}
                                onChange={(e) => setCampagneChoisie(e.target.value)}
                                options={campagnesActives.map((c) => ({
                                    value: String(c.id),
                                    label: c.agenceNom ? `${c.libelle} — ${c.agenceNom}` : c.libelle,
                                }))}
                            />
                            <p className="text-xs text-ink-muted mt-2">
                                Un constat peut être enregistré directement depuis la carte du dernier scan.
                            </p>
                        </div>
                    )}

                    {/* Historique */}
                    {historique.length > 1 && (
                        <div className="card p-4 sm:p-5">
                            <p className="section-label mb-2 flex items-center gap-1.5">
                                <History size={13} />
                                Historique de la session ({historique.length})
                            </p>
                            <ul className="space-y-1.5 max-h-64 overflow-y-auto scrollbar-thin">
                                {historique.map((h) => (
                                    <li
                                        key={`${h.code}-${h.at}`}
                                        className="flex items-center justify-between gap-3 text-xs py-1.5 border-b border-line last:border-0"
                                    >
                                        <div className="min-w-0 flex items-center gap-2">
                                            {h.ok ? (
                                                <CheckCircle size={14} className="text-brand-500 shrink-0" />
                                            ) : (
                                                <AlertTriangle size={14} className="text-danger-500 shrink-0" />
                                            )}
                                            <div className="min-w-0">
                                                <p className="font-mono text-ink truncate">{h.code}</p>
                                                <p className="text-ink-muted truncate">
                                                    {h.ok ? h.immo.designation : h.note}
                                                </p>
                                            </div>
                                        </div>
                                        <span className="text-ink-subtle shrink-0">{formatDateTime(h.at)}</span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            </div>

            {/* Modale de constat (ADMIN) */}
            <Modal
                isOpen={constatImmo !== null}
                onClose={() => setConstatImmo(null)}
                title="Enregistrer un constat"
                size="md"
            >
                {constatImmo && (
                    <form onSubmit={handleSubmitConstat}>
                        <div className="bg-surface-2 dark:bg-slate-800/60 rounded-panel p-3.5 border border-line">
                            <p className="section-label mb-1">Immobilisation</p>
                            <p className="text-sm font-semibold text-ink flex items-center gap-2">
                                <Package size={14} />
                                {constatImmo.designation}
                            </p>
                            <p className="text-xs mono-code mt-0.5">
                                {constatImmo.codeUnique}
                            </p>
                        </div>

                        <div className="mt-4">
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
                                            {etat.label}
                                        </button>
                                    );
                                })}
                            </div>
                        </div>

                        <div className="mt-4">
                            <Field
                                label="Localisation constatée"
                                value={formData.localisationConstatee}
                                onChange={(e) => setFormData((p) => ({ ...p, localisationConstatee: e.target.value }))}
                            />
                        </div>

                        <div className="mt-4">
                            <Field
                                label="Observation"
                                as="textarea"
                                rows={2}
                                value={formData.observation}
                                onChange={(e) => setFormData((p) => ({ ...p, observation: e.target.value }))}
                                placeholder="Remarques…"
                                className="resize-none"
                            />
                        </div>

                        <FormFooter
                            onCancel={() => setConstatImmo(null)}
                            submitLabel="Enregistrer & Synchroniser"
                            submitting={enregistrement}
                        />
                    </form>
                )}
            </Modal>
        </div>
    );
};

/** Petit badge de statut (ACTIF / CEDE / REBUTE) — tons alignés sur Badge. */
const StatutBadgeLite = ({ statut }) => {
    const map = {
        ACTIF: { label: 'Actif', tone: 'brand' },
        CEDE: { label: 'Cédé', tone: 'warning' },
        REBUTE: { label: 'Rebuté', tone: 'danger' },
    };
    const meta = map[statut] || { label: statut || '-', tone: 'neutral' };
    return <Badge tone={meta.tone} label={meta.label} dot={false} />;
};

export default Scanner;

import { useState, useEffect } from 'react';
import { ArrowRightLeft, Send, Trash2, AlertTriangle } from 'lucide-react';
import Modal from './Modal';
import Field from './Field';
import mouvementService from '../../services/mouvementService';
import serviceService from '../../services/serviceService';
import agenceService from '../../services/agenceService';

/**
 * Palette volontairement réduite :
 * TRANSFERT → neutre, CESSION → ambre (sortie de parc), REBUT → rouge.
 */
const TYPES_MOUVEMENT = [
    {
        value: 'TRANSFERT',
        label: 'Transfert',
        icon: ArrowRightLeft,
        iconClass: 'bg-slate-100 dark:bg-slate-700/60 text-slate-600 dark:text-slate-300',
        description: 'Changer le service ou l’agence du bien',
    },
    {
        value: 'CESSION',
        label: 'Cession',
        icon: Send,
        iconClass: 'bg-amber-50 dark:bg-amber-500/15 text-amber-600 dark:text-amber-300',
        description: 'Vendre le bien (avec calcul de la plus/moins-value)',
    },
    {
        value: 'REBUT',
        label: 'Mise au rebut',
        icon: Trash2,
        iconClass: 'bg-danger-50 dark:bg-danger-500/15 text-danger-600 dark:text-danger-300',
        description: 'Déclarer le bien comme inutilisable',
    },
];

const MouvementModal = ({ isOpen, onClose, immobilisation, onSuccess }) => {
    const [typeMouvement, setTypeMouvement] = useState('TRANSFERT');
    const [services, setServices] = useState([]);
    const [agences, setAgences] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [formData, setFormData] = useState({
        nouveauServiceId: '',
        nouvelleAgenceId: '',
        prixVente: '',
        numeroFacture: '',
        commentaire: '',
    });
    const [formErrors, setFormErrors] = useState({});

    useEffect(() => {
        if (isOpen) {
            loadServicesAndAgences();
            resetForm();
        }
    }, [isOpen]);

    const resetForm = () => {
        setTypeMouvement('TRANSFERT');
        setFormData({
            nouveauServiceId: '',
            nouvelleAgenceId: '',
            prixVente: '',
            numeroFacture: '',
            commentaire: '',
        });
        setFormErrors({});
        setError('');
    };

    const loadServicesAndAgences = async () => {
        try {
            const [servData, agData] = await Promise.all([
                serviceService.getAll(),
                agenceService.getAll(),
            ]);
            setServices(servData.filter((s) => s.actif));
            setAgences(agData.filter((a) => a.actif));
        } catch (err) {
            console.error(err);
        }
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
        if (formErrors[name]) setFormErrors((prev) => ({ ...prev, [name]: '' }));
    };

    const validateForm = () => {
        const errors = {};

        if (typeMouvement === 'TRANSFERT') {
            if (!formData.nouveauServiceId && !formData.nouvelleAgenceId) {
                errors.nouveauServiceId = 'Sélectionnez au moins un nouveau service ou une nouvelle agence';
            }
        }

        if (typeMouvement === 'CESSION') {
            if (!formData.prixVente || parseFloat(formData.prixVente) < 0) {
                errors.prixVente = 'Le prix de vente est obligatoire';
            }
        }

        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validateForm()) return;

        setLoading(true);
        setError('');

        try {
            const payload = {
                immobilisationId: immobilisation.id,
                typeMouvement,
                commentaire: formData.commentaire,
                nouveauServiceId: formData.nouveauServiceId ? Number(formData.nouveauServiceId) : null,
                nouvelleAgenceId: formData.nouvelleAgenceId ? Number(formData.nouvelleAgenceId) : null,
                prixVente: formData.prixVente ? parseFloat(formData.prixVente) : null,
                numeroFacture: formData.numeroFacture || null,
            };

            await mouvementService.create(payload);
            onSuccess();
            onClose();
        } catch (err) {
            const msg = err.response?.data?.message || 'Erreur lors de l’enregistrement';
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    if (!immobilisation) return null;

    const selectedType = TYPES_MOUVEMENT.find((t) => t.value === typeMouvement);
    const TypeIcon = selectedType?.icon || ArrowRightLeft;

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Nouveau mouvement" size="lg">
            <form onSubmit={handleSubmit} className="space-y-6">
                {/* Bien concerné */}
                <div className="bg-surface-2 dark:bg-slate-800/60 rounded-panel p-3.5 border border-line">
                    <p className="section-label mb-1">Immobilisation concernée</p>
                    <p className="text-sm font-semibold text-ink">{immobilisation.designation}</p>
                    <p className="text-xs mono-code mt-0.5">
                        {immobilisation.codeUnique}
                    </p>
                </div>

                {error && <div className="alert-danger alert">{error}</div>}

                {/* Choix du type */}
                <div>
                    <h3 className="text-sm font-semibold text-ink mb-3 flex items-center gap-2">
                        <span className="w-1 h-4 bg-brand-500 rounded"></span>
                        Type de mouvement
                    </h3>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                        {TYPES_MOUVEMENT.map((type) => {
                            const Icon = type.icon;
                            const isActive = typeMouvement === type.value;
                            return (
                                <button
                                    key={type.value}
                                    type="button"
                                    onClick={() => {
                                        setTypeMouvement(type.value);
                                        setFormErrors({});
                                    }}
                                    aria-pressed={isActive}
                                    className={`flex sm:flex-col items-center sm:items-start gap-3 sm:gap-0 sm:p-3 p-3 rounded-panel border-2 transition-all text-left ${
                                        isActive
                                            ? 'border-brand-500 bg-brand-50/60 dark:bg-brand-500/10'
                                            : 'border-line hover:border-line-strong hover:bg-surface-2'
                                    }`}
                                >
                                    <div className={`w-9 h-9 rounded-panel flex items-center justify-center shrink-0 ${type.iconClass}`}>
                                        <Icon size={16} />
                                    </div>
                                    <p className={`text-sm font-semibold sm:mt-2 ${isActive ? 'text-brand-600 dark:text-brand-300' : 'text-ink'}`}>
                                        {type.label}
                                    </p>
                                </button>
                            );
                        })}
                    </div>
                    <p className="text-xs text-ink-muted mt-2 italic">{selectedType?.description}</p>
                </div>

                {/* TRANSFERT */}
                {typeMouvement === 'TRANSFERT' && (
                    <div>
                        <h3 className="text-sm font-semibold text-ink mb-3 flex items-center gap-2">
                            <span className="w-1 h-4 bg-brand-500 rounded"></span>
                            Nouvelle affectation
                        </h3>

                        <div className="p-3 bg-surface-2 dark:bg-slate-800/60 rounded-panel mb-4 text-xs border border-line">
                            <p className="text-ink-muted mb-0.5">Actuellement</p>
                            <p className="text-ink font-medium">
                                {immobilisation.agenceNom} — {immobilisation.serviceNom}
                            </p>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <Field
                                label="Nouvelle agence"
                                as="select"
                                name="nouvelleAgenceId"
                                value={formData.nouvelleAgenceId}
                                onChange={handleChange}
                            >
                                <select
                                    id="nouvelleAgenceId"
                                    name="nouvelleAgenceId"
                                    value={formData.nouvelleAgenceId}
                                    onChange={handleChange}
                                    className="input"
                                >
                                    <option value="">-- Laisser inchangée --</option>
                                    {agences.map((a) => (
                                        <option key={a.id} value={a.id}>
                                            {a.codeAgence} — {a.nomAgence}
                                        </option>
                                    ))}
                                </select>
                            </Field>

                            <Field
                                label="Nouveau service"
                                error={formErrors.nouveauServiceId}
                                required={false}
                            >
                                <select
                                    id="nouveauServiceId"
                                    name="nouveauServiceId"
                                    value={formData.nouveauServiceId}
                                    onChange={handleChange}
                                    className={`input ${formErrors.nouveauServiceId ? 'input-error' : ''}`}
                                >
                                    <option value="">-- Laisser inchangé --</option>
                                    {services.map((s) => (
                                        <option key={s.id} value={s.id}>
                                            {s.code} — {s.nomService}
                                        </option>
                                    ))}
                                </select>
                            </Field>
                        </div>
                    </div>
                )}

                {/* CESSION */}
                {typeMouvement === 'CESSION' && (
                    <div>
                        <h3 className="text-sm font-semibold text-ink mb-3 flex items-center gap-2">
                            <span className="w-1 h-4 bg-brand-500 rounded"></span>
                            Informations de cession
                        </h3>

                        <div className="alert-warning alert mb-4">
                            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
                            <p className="text-xs">
                                La VNC sera calculée automatiquement et la plus/moins-value sera déterminée
                                selon le prix de vente.
                            </p>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <Field
                                label="Prix de vente (Ar)"
                                required
                                type="number"
                                name="prixVente"
                                value={formData.prixVente}
                                onChange={handleChange}
                                step="0.01"
                                min="0"
                                placeholder="ex : 500000"
                                error={formErrors.prixVente}
                            />
                            <Field
                                label="N° Facture"
                                hint="Optionnel"
                                type="text"
                                name="numeroFacture"
                                value={formData.numeroFacture}
                                onChange={handleChange}
                                placeholder="ex : FAC-CESSION-2026-001"
                            />
                        </div>
                    </div>
                )}

                {/* REBUT */}
                {typeMouvement === 'REBUT' && (
                    <div>
                        <h3 className="text-sm font-semibold text-ink mb-3 flex items-center gap-2">
                            <span className="w-1 h-4 bg-brand-500 rounded"></span>
                            Mise au rebut
                        </h3>

                        <div className="alert-danger alert">
                            <AlertTriangle size={16} className="mt-0.5 shrink-0" />
                            <p className="text-xs">
                                Cette action changera le statut du bien en « Rebuté ». Il ne pourra plus être
                                réaffecté. Le bien reste dans l’inventaire pour traçabilité historique.
                            </p>
                        </div>
                    </div>
                )}

                {/* Commentaire */}
                <Field
                    label="Commentaire / Motif"
                    as="textarea"
                    name="commentaire"
                    value={formData.commentaire}
                    onChange={handleChange}
                    rows={3}
                    placeholder="Ex : Transfert suite à une réorganisation du service…"
                    className="resize-none"
                />

                {/* Actions */}
                <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-3 pt-5 mt-5 border-t border-line">
                    <button type="button" onClick={onClose} className="btn-secondary btn-md">
                        Annuler
                    </button>
                    <button
                        type="submit"
                        disabled={loading}
                        className="btn-primary btn-md disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <TypeIcon size={14} />
                        {loading ? 'Enregistrement…' : 'Enregistrer le mouvement'}
                    </button>
                </div>
            </form>
        </Modal>
    );
};

export default MouvementModal;

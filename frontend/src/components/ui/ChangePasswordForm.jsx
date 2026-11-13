import { useState } from 'react';
import { KeyRound, Eye, EyeOff } from 'lucide-react';
import Field, { FormFooter } from './Field';
import authService from '../../services/authService';

/**
 * Changement du mot de passe de l'utilisateur CONNECTÉ — le FORMULAIRE seul,
 * sans enveloppe de modale : il vit dans l'onglet « Mot de passe » de
 * ProfilModal.
 *
 * L'ancien endpoint /utilisateurs/{id}/change-password était réservé à
 * l'ADMIN tout en exigeant l'ancien mot de passe : un employé ne pouvait
 * donc jamais changer le sien. Ce module s'appuie sur /auth/change-password,
 * qui s'applique au porteur du jeton.
 */
const ChangePasswordForm = () => {
    const [ancien, setAncien] = useState('');
    const [nouveau, setNouveau] = useState('');
    const [confirmation, setConfirmation] = useState('');
    const [voirMdp, setVoirMdp] = useState(false);
    const [errors, setErrors] = useState({});
    const [message, setMessage] = useState(null); // { type: 'success' | 'error', texte }
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();

        const errs = {};
        if (!ancien) errs.ancien = "L'ancien mot de passe est obligatoire";
        if (!nouveau || nouveau.length < 6) errs.nouveau = 'Au moins 6 caractères';
        if (nouveau !== confirmation) errs.confirmation = 'Les deux saisies ne correspondent pas';
        setErrors(errs);
        if (Object.keys(errs).length > 0) return;

        setLoading(true);
        setMessage(null);
        try {
            await authService.changeMyPassword(ancien, nouveau);
            setMessage({ type: 'success', texte: 'Mot de passe modifié avec succès.' });
            setAncien('');
            setNouveau('');
            setConfirmation('');
        } catch (err) {
            setMessage({
                type: 'error',
                texte: err.response?.data?.message || 'Erreur lors du changement de mot de passe.',
            });
        } finally {
            setLoading(false);
        }
    };

    const reinitialiser = () => {
        setAncien('');
        setNouveau('');
        setConfirmation('');
        setErrors({});
        setMessage(null);
        setVoirMdp(false);
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-4">
            {message && (
                <div className={`alert ${message.type === 'success' ? 'alert-brand' : 'alert-danger'}`} role="alert">
                    {message.texte}
                </div>
            )}

            <Field
                label="Mot de passe actuel"
                htmlFor="mdp-ancien"
                type={voirMdp ? 'text' : 'password'}
                required
                autoComplete="current-password"
                value={ancien}
                onChange={(e) => setAncien(e.target.value)}
                error={errors.ancien}
            />

            <div className="relative">
                <Field
                    label="Nouveau mot de passe"
                    htmlFor="mdp-nouveau"
                    type={voirMdp ? 'text' : 'password'}
                    required
                    autoComplete="new-password"
                    value={nouveau}
                    onChange={(e) => setNouveau(e.target.value)}
                    error={errors.nouveau}
                    hint="Au moins 6 caractères"
                />
                <button
                    type="button"
                    onClick={() => setVoirMdp((v) => !v)}
                    className="absolute right-2 top-[34px] btn-ghost btn-icon"
                    aria-label={voirMdp ? 'Masquer les mots de passe' : 'Afficher les mots de passe'}
                    tabIndex={-1}
                >
                    {voirMdp ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
            </div>

            <Field
                label="Confirmer le nouveau mot de passe"
                htmlFor="mdp-confirmation"
                type={voirMdp ? 'text' : 'password'}
                required
                autoComplete="new-password"
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
                error={errors.confirmation}
            />

            <FormFooter
                onCancel={reinitialiser}
                submitLabel={
                    <>
                        <KeyRound size={15} className="inline -mt-0.5 mr-1.5" />
                        Modifier
                    </>
                }
                submitting={loading}
            />
        </form>
    );
};

export default ChangePasswordForm;

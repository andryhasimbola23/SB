import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    User, Mail, Phone, Briefcase, Building2, Calendar,
    Camera, Loader2, Pencil, LogOut, KeyRound,
} from 'lucide-react';
import Modal from './Modal';
import Badge from './Badge';
import Field, { FormFooter } from './Field';
import ChangePasswordForm from './ChangePasswordForm';
import { useAuth } from '../../hooks/useAuth';
import authService from '../../services/authService';
import uploadService from '../../services/uploadService';
import { getPhotoUrl, formatDate } from '../../utils/format';

/**
 * « Mon profil » — la fiche du compte connecté, en deux onglets.
 *
 * - **Profil** : identité (photo cliquable, nom, rôle, compte actif),
 *   coordonnées (email, téléphone, service, agence, membre depuis) et
 *   auto-édition des seuls champs que le titulaire maîtrise — email,
 *   téléphone, photo. Nom, rôle, service et agence sont des prérogatives
 *   de l'ADMIN : l'interface ne propose même pas de les modifier.
 * - **Mot de passe** : le formulaire historique, déplacé tel quel.
 *
 * Le footer embarque la déconnexion — elle n'existait que dans la sidebar.
 * Les données fraîches viennent de GET /api/auth/profil à chaque ouverture :
 * l'objet `user` du contexte ne porte ni email ni photo.
 */
const ProfilModal = ({ open, onClose }) => {
    const { logout } = useAuth();
    const navigate = useNavigate();

    const [onglet, setOnglet] = useState('profil');
    const [profil, setProfil] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const [edition, setEdition] = useState(false);
    const [form, setForm] = useState({ email: '', telephone: '' });
    const [formErrors, setFormErrors] = useState({});
    const [message, setMessage] = useState(null); // { type, texte }
    const [sauvegardeEnCours, setSauvegardeEnCours] = useState(false);
    const [uploadEnCours, setUploadEnCours] = useState(false);

    const fichierRef = useRef(null);

    // Chargement de la fiche à chaque ouverture — retour au repos (onglet
    // profil, lecture seule) quel que soit l'état précédent.
    useEffect(() => {
        if (!open) return undefined;
        let vivant = true;
        (async () => {
            setLoading(true);
            setOnglet('profil');
            setEdition(false);
            setMessage(null);
            setError('');
            try {
                const donnees = await authService.getProfil();
                if (!vivant) return;
                setProfil(donnees);
                setForm({ email: donnees.email || '', telephone: donnees.telephone || '' });
            } catch (err) {
                console.error(err);
                if (vivant) setError('Impossible de charger votre profil');
            } finally {
                if (vivant) setLoading(false);
            }
        })();
        return () => {
            vivant = false;
        };
    }, [open]);

    const initiales = profil
        ? `${profil.prenom?.charAt(0) || ''}${profil.nom?.charAt(0) || ''}`.toUpperCase()
        : 'U';

    const handleSave = async (e) => {
        e.preventDefault();
        const errs = {};
        if (!form.email.trim()) errs.email = "L'email est obligatoire";
        else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errs.email = 'Email invalide';
        if (form.telephone.length > 20) errs.telephone = '20 caractères maximum';
        setFormErrors(errs);
        if (Object.keys(errs).length > 0) return;

        setSauvegardeEnCours(true);
        setMessage(null);
        try {
            const maj = await authService.updateProfil({
                email: form.email.trim(),
                telephone: form.telephone.trim() || null,
            });
            setProfil(maj);
            setEdition(false);
            setMessage({ type: 'success', texte: 'Coordonnées mises à jour.' });
        } catch (err) {
            setMessage({
                type: 'error',
                texte: err.response?.data?.message || 'Enregistrement impossible.',
            });
        } finally {
            setSauvegardeEnCours(false);
        }
    };

    const handlePhoto = async (e) => {
        const fichier = e.target.files?.[0];
        e.target.value = ''; // permet de rechoisir le même fichier plus tard
        if (!fichier) return;
        setUploadEnCours(true);
        setMessage(null);
        try {
            const { url } = await uploadService.uploadPhoto(fichier);
            // L'upload stocke le fichier ; c'est le PATCH qui l'ATTACHE au
            // compte — sans cela, la photo disparaitrait au rechargement.
            const maj = await authService.updateProfil({ photo: url });
            setProfil(maj);
            setMessage({ type: 'success', texte: 'Photo de profil mise à jour.' });
        } catch (err) {
            setMessage({
                type: 'error',
                texte: err.response?.data?.message || "Échec de l'envoi de la photo.",
            });
        } finally {
            setUploadEnCours(false);
        }
    };

    const handleDeconnexion = () => {
        onClose();
        logout();
        navigate('/login');
    };

    const ONGLETS = [
        { cle: 'profil', label: 'Profil', icon: User },
        { cle: 'motdepasse', label: 'Mot de passe', icon: KeyRound },
    ];

    return (
        <Modal
            isOpen={open}
            onClose={onClose}
            title="Mon profil"
            subtitle={profil ? `@${profil.nomUtilisateur}` : undefined}
            size="md"
            footer={
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <button
                        type="button"
                        onClick={handleDeconnexion}
                        className="btn btn-md inline-flex items-center gap-2 text-danger-600 hover:text-danger-700 dark:text-danger-400 dark:hover:text-danger-300"
                    >
                        <LogOut size={15} />
                        Se déconnecter
                    </button>
                    <button type="button" onClick={onClose} className="btn-primary btn-md">
                        Fermer
                    </button>
                </div>
            }
        >
            {/* Bascule d'onglets — segmented control, même grammaire que la
                vue grille/liste des listes. */}
            <div className="flex items-center gap-1 bg-surface-2 dark:bg-slate-800 rounded-control p-1 border border-line w-fit mb-5">
                {ONGLETS.map(({ cle, label, icon: Icon }) => (
                    <button
                        key={cle}
                        type="button"
                        onClick={() => setOnglet(cle)}
                        aria-pressed={onglet === cle}
                        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-control text-sm transition-colors ${
                            onglet === cle
                                ? 'bg-surface text-ink shadow-soft font-medium'
                                : 'text-ink-muted hover:text-ink'
                        }`}
                    >
                        <Icon size={15} />
                        {label}
                    </button>
                ))}
            </div>

            {onglet === 'motdepasse' ? (
                <ChangePasswordForm />
            ) : loading ? (
                <div className="flex items-center justify-center py-10 text-ink-muted">
                    <Loader2 size={20} className="animate-spin mr-2" />
                    Chargement du profil…
                </div>
            ) : error || !profil ? (
                <div className="alert alert-danger" role="alert">
                    {error || 'Profil indisponible'}
                </div>
            ) : (
                <div className="space-y-5">
                    {message && (
                        <div className={`alert ${message.type === 'success' ? 'alert-brand' : 'alert-danger'}`} role="alert">
                            {message.texte}
                        </div>
                    )}

                    {/* Identité */}
                    <div className="flex items-center gap-4">
                        <button
                            type="button"
                            onClick={() => fichierRef.current?.click()}
                            className="relative group shrink-0"
                            title="Changer ma photo"
                            aria-label="Changer ma photo de profil"
                        >
                            {profil.photo ? (
                                <img
                                    src={getPhotoUrl(profil.photo)}
                                    alt=""
                                    className="w-16 h-16 rounded-full object-cover bg-surface-2"
                                />
                            ) : (
                                <div className="w-16 h-16 rounded-full bg-brand-500 text-white flex items-center justify-center text-xl font-semibold">
                                    {initiales}
                                </div>
                            )}
                            <span className="absolute -bottom-0.5 -right-0.5 w-6 h-6 rounded-full bg-surface border border-line flex items-center justify-center text-ink-muted group-hover:text-ink transition-colors">
                                {uploadEnCours ? (
                                    <Loader2 size={12} className="animate-spin" />
                                ) : (
                                    <Camera size={12} />
                                )}
                            </span>
                        </button>
                        <input
                            ref={fichierRef}
                            type="file"
                            accept="image/*"
                            className="hidden"
                            onChange={handlePhoto}
                        />

                        <div className="min-w-0">
                            <p className="text-lg font-semibold text-ink truncate">
                                {profil.prenom} {profil.nom}
                            </p>
                            <p className="text-sm text-ink-muted truncate">@{profil.nomUtilisateur}</p>
                            <div className="flex flex-wrap items-center gap-2 mt-2">
                                <Badge
                                    tone={profil.role === 'ADMIN' ? 'brand' : 'neutral'}
                                    label={profil.role === 'ADMIN' ? 'Administrateur' : 'Employé'}
                                />
                                <Badge
                                    tone={profil.actif ? 'brand' : 'danger'}
                                    label={profil.actif ? 'Compte actif' : 'Compte désactivé'}
                                />
                            </div>
                        </div>
                    </div>

                    {/* Coordonnées — lecture, ou édition des seuls champs du titulaire */}
                    {edition ? (
                        <form onSubmit={handleSave} className="space-y-4">
                            <Field
                                label="Email"
                                htmlFor="profil-email"
                                type="email"
                                required
                                value={form.email}
                                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                                error={formErrors.email}
                            />
                            <Field
                                label="Téléphone"
                                htmlFor="profil-telephone"
                                value={form.telephone}
                                onChange={(e) => setForm((f) => ({ ...f, telephone: e.target.value }))}
                                error={formErrors.telephone}
                                hint="20 caractères maximum — laisser vide pour retirer"
                            />
                            <FormFooter
                                onCancel={() => {
                                    setEdition(false);
                                    setFormErrors({});
                                    setForm({ email: profil.email || '', telephone: profil.telephone || '' });
                                }}
                                submitting={sauvegardeEnCours}
                            />
                        </form>
                    ) : (
                        <>
                            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-3 text-sm">
                                <div className="flex items-start gap-2.5">
                                    <Mail size={15} className="text-ink-subtle mt-0.5 shrink-0" />
                                    <div className="min-w-0">
                                        <dt className="section-label">Email</dt>
                                        <dd className="text-ink truncate">{profil.email || '—'}</dd>
                                    </div>
                                </div>
                                <div className="flex items-start gap-2.5">
                                    <Phone size={15} className="text-ink-subtle mt-0.5 shrink-0" />
                                    <div className="min-w-0">
                                        <dt className="section-label">Téléphone</dt>
                                        <dd className="text-ink truncate">{profil.telephone || '—'}</dd>
                                    </div>
                                </div>
                                <div className="flex items-start gap-2.5">
                                    <Briefcase size={15} className="text-ink-subtle mt-0.5 shrink-0" />
                                    <div className="min-w-0">
                                        <dt className="section-label">Service</dt>
                                        <dd className="text-ink truncate">{profil.serviceNom || 'Aucun rattachement'}</dd>
                                    </div>
                                </div>
                                <div className="flex items-start gap-2.5">
                                    <Building2 size={15} className="text-ink-subtle mt-0.5 shrink-0" />
                                    <div className="min-w-0">
                                        <dt className="section-label">Agence</dt>
                                        <dd className="text-ink truncate">{profil.agenceNom || 'Aucune'}</dd>
                                    </div>
                                </div>
                                <div className="flex items-start gap-2.5">
                                    <Calendar size={15} className="text-ink-subtle mt-0.5 shrink-0" />
                                    <div className="min-w-0">
                                        <dt className="section-label">Membre depuis</dt>
                                        <dd className="text-ink">{formatDate(profil.dateCreation)}</dd>
                                    </div>
                                </div>
                            </dl>

                            <button
                                type="button"
                                onClick={() => setEdition(true)}
                                className="btn-secondary btn-md"
                            >
                                <Pencil size={15} />
                                Modifier mes coordonnées
                            </button>
                        </>
                    )}
                </div>
            )}
        </Modal>
    );
};

export default ProfilModal;

import { useState, useRef } from 'react';
import { Camera, X, User } from 'lucide-react';
import uploadService from '../../services/uploadService';
import { getPhotoUrl } from '../../utils/format';

const PhotoUpload = ({ value, onChange, size = 'lg', userId = null }) => {
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState('');
    const fileInputRef = useRef(null);

    const sizes = {
        sm: { container: 'w-20 h-20', icon: 20 },
        md: { container: 'w-28 h-28', icon: 24 },
        lg: { container: 'w-32 h-32', icon: 28 },
    };

    const currentSize = sizes[size] || sizes.lg;

    const handleFileSelect = async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;

        // Validation côté client
        if (!file.type.startsWith('image/')) {
            setError('Seuls les fichiers images sont autorisés');
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            setError('La taille ne doit pas dépasser 5 MB');
            return;
        }

        setError('');
        setUploading(true);

        try {
            const { url } = await uploadService.uploadPhoto(file, userId);
            onChange(url);
        } catch (err) {
            setError('Erreur lors de l\'upload');
            console.error(err);
        } finally {
            setUploading(false);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
        }
    };

    const handleRemove = () => {
        onChange(null);
    };

    return (
        <div className="flex flex-col items-center gap-3">
            <div className="relative">
                {/* Photo ou placeholder */}
                <div
                    className={`${currentSize.container} rounded-full overflow-hidden border-4 border-surface dark:border-slate-700 shadow-card bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center cursor-pointer hover:opacity-90 transition`}
                    onClick={() => !uploading && fileInputRef.current?.click()}
                >
                    {uploading ? (
                        <div className="text-white text-xs font-medium">Chargement...</div>
                    ) : value ? (
                        <img
                            src={getPhotoUrl(value)}
                            alt="Photo de profil"
                            className="w-full h-full object-cover"
                        />
                    ) : (
                        <User size={currentSize.icon} className="text-white" />
                    )}
                </div>

                {/* Bouton appareil photo (au survol) */}
                {!uploading && (
                    <button
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        className="absolute bottom-0 right-0 w-9 h-9 rounded-full bg-brand-500 text-white flex items-center justify-center shadow-card hover:bg-brand-600 transition border-2 border-surface dark:border-slate-700"
                        title="Changer la photo"
                    >
                        <Camera size={16} />
                    </button>
                )}

                {/* Bouton supprimer */}
                {value && !uploading && (
                    <button
                        type="button"
                        onClick={handleRemove}
                        className="absolute top-0 right-0 w-7 h-7 rounded-full bg-danger-500 text-white flex items-center justify-center shadow-card hover:bg-danger-600 transition border-2 border-surface dark:border-slate-700"
                        title="Supprimer la photo"
                    >
                        <X size={14} />
                    </button>
                )}
            </div>

            {/* Input caché */}
            <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileSelect}
                className="hidden"
            />

            {/* Texte d'aide */}
            {!value && !uploading && (
                <p className="text-xs text-ink-muted text-center">
                    Cliquez pour ajouter une photo
                </p>
            )}

            {/* Erreur */}
            {error && (
                <p className="text-xs text-danger-500 font-medium text-center">{error}</p>
            )}
        </div>
    );
};

export default PhotoUpload;
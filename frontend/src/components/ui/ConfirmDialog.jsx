import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, X } from 'lucide-react';

/**
 * Dialogue de confirmation (suppression / action irréversible).
 * Adapté : feuille en bas sur mobile, fenêtre centrée au-dessus de `sm`.
 */
const ConfirmDialog = ({ isOpen, onClose, onConfirm, title, message, confirmLabel = 'Confirmer' }) => {
    useEffect(() => {
        const handleEscape = (e) => {
            if (e.key === 'Escape' && isOpen) onClose?.();
        };
        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    return createPortal(
        <div
            className="fixed inset-0 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-sm sm:p-4 z-[99999] animate-fade-in"
            onClick={onClose}
            role="alertdialog"
            aria-modal="true"
        >
            <div
                className="relative bg-surface w-full sm:max-w-md rounded-t-2xl sm:rounded-card shadow-panel p-5 sm:p-6 animate-slide-up"
                onClick={(e) => e.stopPropagation()}
            >
                <button
                    onClick={onClose}
                    className="btn-ghost btn-icon sm:hidden absolute right-3 top-3"
                    aria-label="Fermer"
                >
                    <X size={18} />
                </button>

                <div className="flex items-center gap-3 mb-3">
                    <div className="w-11 h-11 rounded-full bg-danger-50 dark:bg-danger-500/15 flex items-center justify-center shrink-0">
                        <AlertTriangle size={22} className="text-danger-500" />
                    </div>
                    <h3 className="text-base sm:text-lg font-semibold text-ink">{title}</h3>
                </div>

                <p className="text-sm text-ink-muted leading-relaxed mb-6">{message}</p>

                <div className="flex flex-col-reverse sm:flex-row justify-end gap-3">
                    <button onClick={onClose} className="btn-secondary btn-md">
                        Annuler
                    </button>
                    <button onClick={onConfirm} className="btn-danger btn-md">
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>,
        document.body
    );
};

export default ConfirmDialog;

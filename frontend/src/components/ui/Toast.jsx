import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { CheckCircle, AlertCircle, XCircle, Info, X } from 'lucide-react';

const CONFIG = {
    success: { icon: CheckCircle, tone: 'brand', label: 'Succès' },
    error: { icon: XCircle, tone: 'danger', label: 'Erreur' },
    warning: { icon: AlertCircle, tone: 'warning', label: 'Attention' },
    info: { icon: Info, tone: 'neutral', label: 'Information' },
};

const TONES = {
    brand: { icon: 'bg-brand-50 dark:bg-brand-500/15 text-brand-600 dark:text-brand-300' },
    danger: { icon: 'bg-danger-50 dark:bg-danger-500/15 text-danger-600 dark:text-danger-300' },
    warning: { icon: 'bg-amber-50 dark:bg-amber-500/15 text-amber-600 dark:text-amber-300' },
    neutral: { icon: 'bg-slate-100 dark:bg-slate-700/60 text-slate-600 dark:text-slate-300' },
};

/** Notification éphémère, positionnée en haut à droite (centrée sur mobile).
 *
 * `duration <= 0` rend le toast « collant » : il reste affiché jusqu'à ce que
 * l'appelant le remplace (utile pour un toast de progression) ou que l'utilisateur
 * le ferme. */
const Toast = ({ type = 'success', message, duration = 3000, onClose }) => {
    const [visible, setVisible] = useState(true);

    useEffect(() => {
        if (!duration || duration <= 0) return undefined;
        const timer = setTimeout(() => {
            setVisible(false);
            setTimeout(onClose, 300);
        }, duration);
        return () => clearTimeout(timer);
    }, [duration, onClose]);

    const cfg = CONFIG[type] || CONFIG.info;
    const Icon = cfg.icon;
    const t = TONES[cfg.tone];

    return createPortal(
        <div
            role="status"
            aria-live="polite"
            className={`fixed left-4 right-4 top-16 sm:left-auto sm:right-6 sm:top-20 sm:max-w-md z-[100000] card p-3.5 flex items-start gap-3 transition-all duration-300 ${
                visible ? 'opacity-100 translate-y-0 sm:translate-x-0' : 'opacity-0 -translate-y-2 sm:translate-x-full'
            }`}
        >
            <div className={`w-9 h-9 rounded-panel flex items-center justify-center shrink-0 ${t.icon}`}>
                <Icon size={18} />
            </div>
            <p className="text-sm text-ink flex-1 leading-snug pt-1.5">{message}</p>
            <button
                onClick={() => {
                    setVisible(false);
                    setTimeout(onClose, 300);
                }}
                className="btn-ghost btn-icon shrink-0 mt-0.5"
                aria-label="Fermer la notification"
            >
                <X size={16} />
            </button>
        </div>,
        document.body
    );
};

export default Toast;

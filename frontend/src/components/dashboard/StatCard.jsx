import { tone as toneOf } from '../../theme/toneStyles';
import { Skeleton } from '../ui/Skeleton';

/**
 * Carte de statistique du tableau de bord.
 * Surface neutre + icône teintée (vert / ambre / rouge / gris).
 * Plus de bleu ni de violet : le gris remplace toute teinte décorative.
 */
const StatCard = ({ icon: Icon, label, value, subtitle, tone = 'brand', loading = false }) => {
    const t = toneOf(tone);

    return (
        <div className="card p-4 sm:p-5 hover:shadow-card hover:-translate-y-0.5 transition-all duration-200">
            <div className="flex items-start justify-between mb-3">
                <div className={`w-11 h-11 rounded-panel flex items-center justify-center ${t.icon}`}>
                    {Icon && <Icon size={20} />}
                </div>
            </div>

            <p className="section-label mb-1.5">{label}</p>

            {loading ? (
                <Skeleton className="h-7 w-24" />
            ) : (
                <p className="text-2xl font-bold text-ink leading-tight">{value}</p>
            )}

            {subtitle && <p className="text-xs text-ink-muted mt-1.5">{subtitle}</p>}
        </div>
    );
};

/** Version compacte pour les indicateurs secondaires. */
export const MiniStat = ({ icon: Icon, label, value, tone = 'neutral' }) => {
    const t = toneOf(tone);

    return (
        <div className="card p-3 flex items-center gap-3 hover:shadow-card hover:-translate-y-0.5 transition-all duration-200">
            <div className={`w-9 h-9 rounded-panel flex items-center justify-center shrink-0 ${t.icon}`}>
                {Icon && <Icon size={16} />}
            </div>
            <div className="min-w-0">
                <p className="section-label truncate">{label}</p>
                <p className="text-lg font-bold text-ink leading-tight">{value}</p>
            </div>
        </div>
    );
};

export default StatCard;

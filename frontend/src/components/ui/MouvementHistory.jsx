import { ArrowRightLeft, Package, Send, Trash2, UserPlus, CheckCircle, Clock } from 'lucide-react';
import { tone as toneOf, TYPES_MOUVEMENT } from '../../theme/toneStyles';
import { formatMontant, formatDateHeure as formatDate } from '../../utils/format';

const TYPE_ICONS = {
    ENTREE: Package,
    TRANSFERT: ArrowRightLeft,
    AFFECTATION: UserPlus,
    CESSION: Send,
    REBUT: Trash2,
};

const MouvementHistory = ({ mouvements }) => {
    // Palette unique : vert / ambre / rouge / neutre (jamais de bleu ni de violet)
    const getTypeConfig = (type) => {
        const meta = TYPES_MOUVEMENT[type] || TYPES_MOUVEMENT.ENTREE;
        return {
            icon: TYPE_ICONS[type] || Package,
            label: meta.label,
            tone: toneOf(meta.tone),
        };
    };

    // Extraire la mention du changement de code du commentaire
    const extractCodeChange = (commentaire) => {
        if (!commentaire) return null;
        const match = commentaire.match(/\[Code unique changé\s*:\s*(.+?)\s*→\s*(.+?)\]/);
        return match ? { ancien: match[1], nouveau: match[2] } : null;
    };

    // Nettoyer le commentaire (retirer la mention technique)
    const cleanCommentaire = (commentaire) => {
        if (!commentaire) return '';
        return commentaire.replace(/\n?\[Code unique changé.*?\]/, '').trim();
    };

    if (!mouvements || mouvements.length === 0) {
        return (
            <div className="empty-state bg-surface-2 dark:bg-slate-800/50 rounded-panel border border-dashed border-line-strong">
                <div className="empty-state-icon">
                    <Clock size={22} />
                </div>
                <p className="text-sm text-ink-muted">Aucun mouvement enregistré</p>
            </div>
        );
    }

    return (
        <div className="space-y-3">
            {mouvements.map((mvt) => {
                const config = getTypeConfig(mvt.typeMouvement);
                const Icon = config.icon;
                const codeChange = extractCodeChange(mvt.commentaire);
                const commentaireNettoye = cleanCommentaire(mvt.commentaire);

                return (
                    <div key={mvt.id} className="flex gap-3 p-3 bg-surface border border-line rounded-panel hover:shadow-soft transition">
                        {/* Icône */}
                        <div className={`w-10 h-10 rounded-panel flex items-center justify-center shrink-0 border ${config.tone.bg} ${config.tone.border} ${config.tone.text}`}>
                            <Icon size={16} />
                        </div>

                        {/* Contenu */}
                        <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between gap-2 mb-1 flex-wrap">
                                <span className={`badge ${config.tone.badge}`}>
                                    {config.label}
                                </span>
                                <span className="text-xs text-ink-muted">{formatDate(mvt.dateMouvement)}</span>
                            </div>

                            {/* Transfert */}
                            {mvt.typeMouvement === 'TRANSFERT' && (
                                <div className="space-y-1.5 mt-1">
                                    <div className="flex items-center gap-2 text-xs text-ink-muted">
                                        <span className="truncate">{mvt.ancienneLocalisation || '-'}</span>
                                        <span className="text-ink-subtle">→</span>
                                        <span className="truncate font-medium text-ink">{mvt.nouvelleLocalisation || '-'}</span>
                                    </div>
                                    {codeChange && (
                                        <div className="p-2 bg-surface-2 dark:bg-slate-800/60 border border-line rounded-control text-xs">
                                            <p className="font-semibold text-ink mb-0.5">🔄 Nouveau code généré</p>
                                            <div className="flex items-center gap-2 text-ink-muted font-mono">
                                                <span className="line-through opacity-60">{codeChange.ancien}</span>
                                                <span>→</span>
                                                <span className="font-bold text-brand-600 dark:text-brand-300">{codeChange.nouveau}</span>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Cession */}
                            {mvt.typeMouvement === 'CESSION' && (
                                <div className="text-xs text-ink-muted mt-1 space-y-0.5">
                                    <p>
                                        VNC : <span className="font-medium text-ink">{formatMontant(mvt.valeurVnc)}</span>
                                        {mvt.prixVente && (
                                            <>
                                                {' '}| Prix de vente : <span className="font-medium text-ink">{formatMontant(mvt.prixVente)}</span>
                                            </>
                                        )}
                                    </p>
                                    {mvt.plusMoinsValue != null && (
                                        <p className={Number(mvt.plusMoinsValue) >= 0 ? 'text-brand-600 dark:text-brand-300' : 'text-danger-600 dark:text-danger-300'}>
                                            {Number(mvt.plusMoinsValue) >= 0 ? 'Plus-value' : 'Moins-value'} :{' '}
                                            <span className="font-semibold">
                                                {Number(mvt.plusMoinsValue).toLocaleString('fr-FR')} Ar
                                            </span>
                                        </p>
                                    )}
                                </div>
                            )}

                            {/* Rebut */}
                            {mvt.typeMouvement === 'REBUT' && mvt.valeurVnc != null && (
                                <p className="text-xs text-ink-muted mt-1">
                                    VNC au moment du rebut : <span className="font-medium text-ink">{formatMontant(mvt.valeurVnc)}</span>
                                </p>
                            )}

                            {/* Commentaire nettoyé */}
                            {commentaireNettoye && (
                                <p className="text-xs text-ink-subtle italic mt-1 line-clamp-2">
                                    « {commentaireNettoye} »
                                </p>
                            )}

                            {/* Utilisateur */}
                            {mvt.utilisateurNom && (
                                <p className="text-xs text-ink-subtle mt-1 flex items-center gap-1">
                                    <CheckCircle size={11} />
                                    Par {mvt.utilisateurNom}
                                </p>
                            )}
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

export default MouvementHistory;
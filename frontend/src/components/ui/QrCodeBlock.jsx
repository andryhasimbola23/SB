import { useState } from 'react';
import { QrCode, RefreshCw, Printer, Download } from 'lucide-react';
import immobilisationService from '../../services/immobilisationService';
import { printQrLabel } from '../../utils/printQrLabels';
import { getPhotoUrl } from '../../utils/format';

const QrCodeBlock = ({ immobilisation, onChange }) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const getUrl = (url) => getPhotoUrl(url);

    const handleGenerate = async () => {
        setLoading(true);
        setError('');
        try {
            const updated = await immobilisationService.genererQrCode(immobilisation.id);
            onChange(updated);
        } catch (err) {
            setError('Erreur lors de la génération');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    /**
     * Impression individuelle — déléguée au gabarit partagé
     * (`utils/printQrLabels`) : étiquette 80 × 80 mm avec le logo officiel,
     * le QR Code et le code unique, imprimée seule (aucune UI applicative).
     */
    const handlePrint = () => {
        if (!immobilisation.qrCode) return;
        printQrLabel(immobilisation);
    };

    const handleDownload = () => {
        const qrUrl = getUrl(immobilisation.qrCode);
        if (!qrUrl) return;
        const link = document.createElement('a');
        link.href = qrUrl;
        link.download = `qrcode-${immobilisation.codeUnique.replace(/\//g, '_')}.png`;
        link.click();
    };

    return (
        <div>
            <h3 className="text-sm font-semibold text-ink mb-3 flex items-center gap-2">
                <span className="w-1 h-4 bg-brand-500 rounded"></span>
                QR Code
            </h3>

            {error && (
                <div className="alert-danger alert text-xs mb-3">{error}</div>
            )}

            {immobilisation.qrCode ? (
                <div className="flex flex-col md:flex-row gap-5 items-start p-4 bg-surface-2 dark:bg-slate-800/50 rounded-panel border border-line">
                    {/* QR Code */}
                    <div className="bg-white p-3 rounded-panel shadow-soft border border-line mx-auto md:mx-0 shrink-0">
                        <img
                            src={getUrl(immobilisation.qrCode)}
                            alt="QR Code"
                            className="w-36 h-36 md:w-40 md:h-40 object-contain"
                        />
                    </div>

                    {/* Actions */}
                    <div className="flex-1 min-w-0 space-y-3">
                        <div>
                            <p className="section-label mb-1.5">Contenu du QR Code</p>
                            <p className="text-sm font-mono font-semibold text-ink bg-surface px-3 py-2 rounded-control border border-line break-all">
                                {immobilisation.codeUnique}
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            <button
                                type="button"
                                onClick={handlePrint}
                                className="btn-primary btn-md"
                            >
                                <Printer size={14} />
                                Imprimer l'étiquette
                            </button>
                            <button
                                type="button"
                                onClick={handleDownload}
                                className="btn-secondary btn-md"
                            >
                                <Download size={14} />
                                Télécharger
                            </button>
                            <button
                                type="button"
                                onClick={handleGenerate}
                                disabled={loading}
                                className="btn-danger-soft btn-md"
                            >
                                <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
                                {loading ? 'Génération…' : 'Régénérer'}
                            </button>
                        </div>
                    </div>
                </div>
            ) : (
                <div className="p-8 bg-surface-2 dark:bg-slate-800/50 rounded-panel border border-dashed border-line-strong flex flex-col items-center justify-center text-center">
                    <div className="empty-state-icon mb-1">
                        <QrCode size={26} />
                    </div>
                    <p className="text-sm text-ink-muted mb-4">
                        Aucun QR Code généré pour cette immobilisation
                    </p>
                    <button
                        type="button"
                        onClick={handleGenerate}
                        disabled={loading}
                        className="btn-primary btn-md"
                    >
                        <QrCode size={16} className={loading ? 'animate-soft-pulse' : ''} />
                        {loading ? 'Génération en cours…' : 'Générer le QR Code'}
                    </button>
                </div>
            )}
        </div>
    );
};

export default QrCodeBlock;
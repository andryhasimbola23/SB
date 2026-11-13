import { useState } from 'react';
import { Download, FileSpreadsheet, FileText, Loader2 } from 'lucide-react';

/**
 * Bouton d'export Excel / PDF.
 * - Un seul format : bouton direct.
 * - Deux formats : menu déroulant (feuille plein écran sur mobile).
 */
const ExportButton = ({ onExportExcel, onExportPdf, label = 'Exporter', size = 'md' }) => {
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [loadingType, setLoadingType] = useState(null);

    const sizes = {
        sm: 'btn-sm',
        md: 'btn-md',
        lg: 'btn-lg',
    };

    const handleExport = async (type) => {
        setLoading(true);
        setLoadingType(type);
        try {
            if (type === 'excel' && onExportExcel) await onExportExcel();
            else if (type === 'pdf' && onExportPdf) await onExportPdf();
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
            setLoadingType(null);
            setOpen(false);
        }
    };

    // Format unique : bouton direct
    if (onExportExcel && !onExportPdf) {
        return (
            <button
                onClick={() => handleExport('excel')}
                disabled={loading}
                className={`btn btn-brand-soft ${sizes[size]}`}
            >
                {loading ? <Loader2 size={14} className="animate-spin" /> : <FileSpreadsheet size={14} />}
                {loading ? 'Export…' : 'Excel'}
            </button>
        );
    }

    if (onExportPdf && !onExportExcel) {
        return (
            <button
                onClick={() => handleExport('pdf')}
                disabled={loading}
                className={`btn btn-danger-soft ${sizes[size]}`}
            >
                {loading ? <Loader2 size={14} className="animate-spin" /> : <FileText size={14} />}
                {loading ? 'Export…' : 'PDF'}
            </button>
        );
    }

    // Menu déroulant
    return (
        <div className="relative">
            <button
                onClick={() => setOpen(!open)}
                disabled={loading}
                className={`btn btn-secondary ${sizes[size]}`}
            >
                {loading ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
                {loading ? 'Export…' : label}
            </button>

            {open && (
                <>
                    <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
                    <div className="absolute right-0 mt-2 w-52 card rounded-panel shadow-panel z-20 overflow-hidden animate-slide-down">
                        {onExportExcel && (
                            <button
                                onClick={() => handleExport('excel')}
                                disabled={loading}
                                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-ink hover:bg-surface-2 transition-colors text-left"
                            >
                                {loadingType === 'excel' ? (
                                    <Loader2 size={14} className="animate-spin text-brand-500" />
                                ) : (
                                    <FileSpreadsheet size={14} className="text-brand-500" />
                                )}
                                <span>Exporter en Excel</span>
                            </button>
                        )}
                        {onExportPdf && (
                            <button
                                onClick={() => handleExport('pdf')}
                                disabled={loading}
                                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-ink hover:bg-surface-2 transition-colors text-left border-t border-line"
                            >
                                {loadingType === 'pdf' ? (
                                    <Loader2 size={14} className="animate-spin text-danger-500" />
                                ) : (
                                    <FileText size={14} className="text-danger-500" />
                                )}
                                <span>Exporter en PDF</span>
                            </button>
                        )}
                    </div>
                </>
            )}
        </div>
    );
};

export default ExportButton;

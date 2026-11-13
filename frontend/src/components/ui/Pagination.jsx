import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';

/**
 * Pagination.
 * - Mobile : bouton précédent/suivant + indicateur "3 / 12".
 * - `sm+`  : boutons numérotés complets avec ellipses.
 */
const Pagination = ({ currentPage, totalPages, onPageChange, totalItems, itemsPerPage }) => {
    if (totalPages <= 1) return null;

    const startItem = (currentPage - 1) * itemsPerPage + 1;
    const endItem = Math.min(currentPage * itemsPerPage, totalItems);

    const getPageNumbers = () => {
        const pages = [];
        const maxVisible = 5;

        if (totalPages <= maxVisible) {
            for (let i = 1; i <= totalPages; i++) pages.push(i);
        } else {
            pages.push(1);

            let start = Math.max(2, currentPage - 1);
            let end = Math.min(totalPages - 1, currentPage + 1);

            if (currentPage <= 3) {
                start = 2;
                end = 4;
            }
            if (currentPage >= totalPages - 2) {
                start = totalPages - 3;
                end = totalPages - 1;
            }

            if (start > 2) pages.push('...');
            for (let i = start; i <= end; i++) pages.push(i);
            if (end < totalPages - 1) pages.push('...');

            pages.push(totalPages);
        }
        return pages;
    };

    const pageNumbers = getPageNumbers();

    const navBtn = (disabled) =>
        `inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-control transition-colors ${
            disabled
                ? 'text-ink-subtle cursor-not-allowed'
                : 'text-ink hover:bg-surface-3'
        }`;

    return (
        <nav
            className="flex flex-wrap items-center justify-between gap-3 px-4 sm:px-6 py-3.5 bg-surface border-t border-line"
            aria-label="Pagination"
        >
            {/* Info */}
            <p className="text-xs sm:text-sm text-ink-muted order-2 sm:order-1">
                <span className="font-semibold text-ink">{startItem}</span>–
                <span className="font-semibold text-ink">{endItem}</span> sur{' '}
                <span className="font-semibold text-ink">{totalItems}</span>
            </p>

            {/* Navigation */}
            <div className="flex items-center gap-1 order-1 sm:order-2 w-full sm:w-auto justify-center sm:justify-end">
                <button
                    onClick={() => onPageChange(1)}
                    disabled={currentPage === 1}
                    className={`${navBtn(currentPage === 1)} sm:hidden`}
                    aria-label="Première page"
                >
                    <ChevronsLeft size={16} />
                </button>
                <button
                    onClick={() => onPageChange(currentPage - 1)}
                    disabled={currentPage === 1}
                    className={navBtn(currentPage === 1)}
                >
                    <ChevronLeft size={16} />
                    <span className="hidden sm:inline">Précédent</span>
                </button>

                <span className="sm:hidden px-3 text-sm font-medium text-ink">
                    {currentPage} / {totalPages}
                </span>

                <div className="hidden sm:flex items-center gap-1 mx-1">
                    {pageNumbers.map((page, index) =>
                        page === '...' ? (
                            <span key={`e-${index}`} className="px-1.5 text-ink-subtle text-sm">
                                …
                            </span>
                        ) : (
                            <button
                                key={page}
                                onClick={() => onPageChange(page)}
                                aria-current={currentPage === page ? 'page' : undefined}
                                className={`min-w-[32px] h-8 px-2 text-sm font-medium rounded-control transition-colors ${
                                    currentPage === page
                                        ? 'bg-brand-500 text-white shadow-sm'
                                        : 'text-ink hover:bg-surface-3'
                                }`}
                            >
                                {page}
                            </button>
                        )
                    )}
                </div>

                <button
                    onClick={() => onPageChange(currentPage + 1)}
                    disabled={currentPage === totalPages}
                    className={navBtn(currentPage === totalPages)}
                >
                    <span className="hidden sm:inline">Suivant</span>
                    <ChevronRight size={16} />
                </button>
                <button
                    onClick={() => onPageChange(totalPages)}
                    disabled={currentPage === totalPages}
                    className={`${navBtn(currentPage === totalPages)} sm:hidden`}
                    aria-label="Dernière page"
                >
                    <ChevronsRight size={16} />
                </button>
            </div>
        </nav>
    );
};

export default Pagination;

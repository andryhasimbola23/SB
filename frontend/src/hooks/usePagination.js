import { useState, useMemo } from 'react';

export const usePagination = (items, itemsPerPage = 6) => {
    const [currentPage, setCurrentPage] = useState(1);

    const totalPages = Math.ceil(items.length / itemsPerPage);

    // Réinitialiser la page si elle dépasse le nombre total de pages
    if (currentPage > totalPages && totalPages > 0) {
        setCurrentPage(1);
    }

    const currentItems = useMemo(() => {
        const startIndex = (currentPage - 1) * itemsPerPage;
        return items.slice(startIndex, startIndex + itemsPerPage);
    }, [items, currentPage, itemsPerPage]);

    const handlePageChange = (page) => {
        if (page >= 1 && page <= totalPages) {
            setCurrentPage(page);
        }
    };

    return {
        currentItems,
        currentPage,
        totalPages,
        totalItems: items.length,
        itemsPerPage,
        handlePageChange,
    };
};
const SKEL = 'bg-surface-3 dark:bg-slate-700/50 animate-pulse rounded';

export const Skeleton = ({ className = '' }) => <div className={`${SKEL} ${className}`} />;

/** Squelette d'une carte d'immobilisation (vue grille). */
export const ImmobilisationCardSkeleton = () => (
    <div className="card overflow-hidden">
        <div className={`${SKEL} h-44 rounded-none`} />
        <div className="p-4 space-y-3">
            <div className="flex justify-between gap-3">
                <div className="flex-1 space-y-2">
                    <div className={`${SKEL} h-4 w-3/4`} />
                    <div className={`${SKEL} h-3 w-1/2`} />
                </div>
                <div className={`${SKEL} h-6 w-16 rounded-full`} />
            </div>
            <div className="grid grid-cols-4 gap-3">
                {[1, 2, 3, 4].map((i) => (
                    <div key={i} className="space-y-1.5">
                        <div className={`${SKEL} h-2 w-10`} />
                        <div className={`${SKEL} h-3 w-16`} />
                    </div>
                ))}
            </div>
        </div>
    </div>
);

/** Squelette de tableau (en-tête + lignes). */
export const TableSkeleton = ({ rows = 5, cols = 6 }) => (
    <div className="card overflow-hidden">
        <div className="bg-surface-2 border-b border-line px-5 py-3.5">
            <div className="flex gap-4">
                {Array.from({ length: Math.min(cols, 5) }).map((_, i) => (
                    <div key={i} className={`${SKEL} h-2.5 w-24`} />
                ))}
            </div>
        </div>
        <div className="divide-y divide-line">
            {Array.from({ length: rows }).map((_, r) => (
                <div key={r} className="px-5 py-4 flex items-center gap-4">
                    {Array.from({ length: Math.min(cols, 5) }).map((_, c) => (
                        <div key={c} className={`${SKEL} h-4 flex-1`} />
                    ))}
                </div>
            ))}
        </div>
    </div>
);

/** Squelette d'une carte de statistique. */
export const StatCardSkeleton = () => (
    <div className="card p-5 space-y-3">
        <div className={`${SKEL} w-11 h-11 rounded-panel`} />
        <div className={`${SKEL} h-3 w-24`} />
        <div className={`${SKEL} h-7 w-32`} />
    </div>
);

/** Squelette de la modale de détails. */
export const DetailsModalSkeleton = () => (
    <div className="space-y-6">
        <div className="flex flex-col md:flex-row gap-5 pb-5 border-b border-line">
            <div className={`${SKEL} md:w-44 md:h-44 h-40 rounded-card`} />
            <div className="flex-1 space-y-3">
                <div className="flex gap-2">
                    <div className={`${SKEL} h-6 w-20 rounded-full`} />
                    <div className={`${SKEL} h-6 w-16 rounded-full`} />
                </div>
                <div className={`${SKEL} h-6 w-3/4`} />
                <div className={`${SKEL} h-8 w-56`} />
                <div className="grid grid-cols-2 gap-4">
                    <div className={`${SKEL} h-16 rounded-panel`} />
                    <div className={`${SKEL} h-16 rounded-panel`} />
                </div>
            </div>
        </div>
        {[1, 2, 3].map((i) => (
            <div key={i} className="space-y-2">
                <div className={`${SKEL} h-4 w-32`} />
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                    {[1, 2, 3].map((j) => (
                        <div key={j} className={`${SKEL} h-12`} />
                    ))}
                </div>
            </div>
        ))}
    </div>
);

/** Squelette de liste de cartes. */
export const ListSkeleton = ({ items = 5 }) => (
    <div className="space-y-3">
        {Array.from({ length: items }).map((_, i) => (
            <div key={i} className="card p-5 space-y-3">
                <div className="flex justify-between gap-3">
                    <div className="flex-1 space-y-2">
                        <div className={`${SKEL} h-4 w-2/3`} />
                        <div className={`${SKEL} h-3 w-1/3`} />
                    </div>
                    <div className={`${SKEL} h-6 w-20 rounded-full`} />
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    {[1, 2, 3, 4].map((j) => (
                        <div key={j} className={`${SKEL} h-3`} />
                    ))}
                </div>
            </div>
        ))}
    </div>
);

/** Squelette du dashboard complet. */
export const DashboardSkeleton = () => (
    <div className="space-y-6">
        <div className={`${SKEL} rounded-card h-32`} />

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
                <StatCardSkeleton key={i} />
            ))}
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {[1, 2, 3, 4, 5, 6].map((i) => (
                <div key={i} className="card p-3 h-16" />
            ))}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <div className="card p-5 h-80" />
            <div className="card p-5 h-80" />
        </div>
    </div>
);

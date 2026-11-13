// Spinner de chargement personnalisé
export const Spinner = ({ size = 'md', color = 'green' }) => {
    const sizes = {
        sm: 'w-4 h-4 border-2',
        md: 'w-6 h-6 border-2',
        lg: 'w-10 h-10 border-3',
        xl: 'w-16 h-16 border-4',
    };

    const colors = {
        green: 'border-brand-500 border-t-transparent',
        red: 'border-danger-500 border-t-transparent',
        white: 'border-white border-t-transparent',
        slate: 'border-slate-400 border-t-transparent',
    };

    return (
        <div
            className={`${sizes[size]} ${colors[color]} rounded-full animate-spin-slow`}
            role="status"
            aria-label="Chargement"
        ></div>
    );
};

// Loader plein écran avec message
export const FullPageLoader = ({ message = 'Chargement...' }) => (
    <div className="fixed inset-0 bg-surface/85 backdrop-blur-sm z-50 flex flex-col items-center justify-center animate-fade-in">
        <Spinner size="xl" />
        <p className="mt-4 text-ink-muted font-medium animate-soft-pulse">{message}</p>
    </div>
);

// Loader inline (pour les boutons)
export const ButtonLoader = () => (
    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
);

// Points de chargement (style moderne)
export const DotsLoader = () => (
    <div className="flex items-center gap-1">
        <div className="w-2 h-2 rounded-full bg-brand-500 animate-bounce" style={{ animationDelay: '0ms' }}></div>
        <div className="w-2 h-2 rounded-full bg-brand-500 animate-bounce" style={{ animationDelay: '150ms' }}></div>
        <div className="w-2 h-2 rounded-full bg-brand-500 animate-bounce" style={{ animationDelay: '300ms' }}></div>
    </div>
);

// Barre de chargement (style top-bar comme YouTube)
export const LoadingBar = ({ isLoading }) => {
    if (!isLoading) return null;

    return (
        <div className="fixed top-0 left-0 right-0 h-0.5 bg-transparent z-[100] overflow-hidden">
            <div className="h-full bg-gradient-to-r from-brand-500 via-brand-400 to-brand-500 bg-[length:200%_100%] animate-shimmer"></div>
        </div>
    );
};
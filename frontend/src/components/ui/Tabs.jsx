import { useRef, useState, useEffect } from 'react';

/**
 * Onglets adaptatifs : défilement horizontal + snap sur mobile.
 * L'onglet actif est automatiquement amené dans le champ de vision.
 */
const Tabs = ({ tabs, defaultTab = 0, onChange }) => {
    const [activeTab, setActiveTab] = useState(defaultTab);
    const scrollRef = useRef(null);
    const itemRefs = useRef([]);

    useEffect(() => {
        const container = scrollRef.current;
        const item = itemRefs.current[activeTab];
        if (!container || !item) return;

        const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        item.scrollIntoView({
            behavior: prefersReduced ? 'auto' : 'smooth',
            inline: 'center',
            block: 'nearest',
        });
    }, [activeTab]);

    const select = (index) => {
        setActiveTab(index);
        onChange?.(index);
    };

    return (
        <div>
            {/* Barre d'onglets */}
            <div
                ref={scrollRef}
                role="tablist"
                className="flex border-b border-line overflow-x-auto hide-scrollbar snap-x"
            >
                {tabs.map((tab, index) => {
                    const Icon = tab.icon;
                    const isActive = activeTab === index;
                    return (
                        <button
                            key={tab.key ?? index}
                            ref={(el) => (itemRefs.current[index] = el)}
                            role="tab"
                            aria-selected={isActive}
                            onClick={() => select(index)}
                            className={`relative flex items-center gap-2 px-4 py-3 text-sm font-medium whitespace-nowrap snap-start transition-colors shrink-0 ${
                                isActive
                                    ? 'text-brand-600 dark:text-brand-300'
                                    : 'text-ink-muted hover:text-ink'
                            }`}
                        >
                            {Icon && <Icon size={16} />}
                            {tab.label}
                            {tab.count != null && (
                                <span
                                    className={`min-w-[20px] h-5 px-1.5 rounded-full text-[10px] font-bold flex items-center justify-center ${
                                        isActive
                                            ? 'bg-brand-50 dark:bg-brand-500/20 text-brand-700 dark:text-brand-300'
                                            : 'bg-surface-3 text-ink-muted'
                                    }`}
                                >
                                    {tab.count}
                                </span>
                            )}
                            {isActive && (
                                <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-500 rounded-full" />
                            )}
                        </button>
                    );
                })}
            </div>

            {/* Contenu */}
            <div className="pt-5">{tabs[activeTab]?.content}</div>
        </div>
    );
};

export default Tabs;

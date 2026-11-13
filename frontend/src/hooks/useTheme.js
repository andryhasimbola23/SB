import { useState, useEffect } from 'react';

export const useTheme = () => {
    const [theme, setTheme] = useState(() => {
        // Lire depuis localStorage au premier rendu
        const saved = localStorage.getItem('theme');
        if (saved) return saved;

        // Sinon, détecter la préférence système
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return 'dark';
        }
        return 'light';
    });

    useEffect(() => {
        const root = document.documentElement;
        if (theme === 'dark') {
            root.classList.add('dark');
        } else {
            root.classList.remove('dark');
        }
        localStorage.setItem('theme', theme);
    }, [theme]);

    const toggleTheme = () => {
        setTheme((prev) => (prev === 'light' ? 'dark' : 'light'));
    };

    return { theme, toggleTheme, isDark: theme === 'dark' };
};
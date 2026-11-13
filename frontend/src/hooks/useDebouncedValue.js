import { useState, useEffect } from 'react';

/**
 * Valeur retardée : la valeur retournée ne bouge qu'après `delay` ms sans
 * nouvelle écriture. Sert à déclencher la recherche à la frappe sans appeler
 * l'API à chaque lettre.
 *
 * @param {*}     value  valeur à délayer (la saisie de l'utilisateur)
 * @param {number} delay délai en ms (300 par défaut)
 */
const useDebouncedValue = (value, delay = 300) => {
    const [debounced, setDebounced] = useState(value);

    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delay);
        return () => clearTimeout(timer);
    }, [value, delay]);

    return debounced;
};

export default useDebouncedValue;

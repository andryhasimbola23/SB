import { useEffect, useRef } from 'react';
import useDebouncedValue from './useDebouncedValue';

/**
 * Recherche instantanée : déclenche `search(keyword)` dès que la saisie se
 * stabilise, sans attendre Entrée ni le bouton « Rechercher ».
 *
 * - le premier rendu est ignoré : le chargement initial reste seul maître ;
 * - le handler est lu via une ref : son identité change à chaque rendu sans
 *   pour autant relancer une recherche ;
 * - la valeur débouncée est passée en argument, ce qui évite tout closure
 *   périmée sur l'état du champ ;
 * - Entrée et le bouton continuent d'appeler le handler directement, sans
 *   attendre le délai.
 *
 * @param {string}   keyword saisie courante
 * @param {Function} search  handler de recherche (reçoit le terme stabilisé)
 * @param {number}   delay   délai en ms (300 par défaut)
 */
const useAutoSearch = (keyword, search, delay = 300) => {
    const debouncedKeyword = useDebouncedValue(keyword, delay);
    const searchRef = useRef(search);
    const firstRunRef = useRef(true);

    // Toujours la dernière version du handler — déclaré avant l'effet
    // déclencheur pour que la ref soit fraîche au moment de l'appel.
    useEffect(() => {
        searchRef.current = search;
    });

    useEffect(() => {
        if (firstRunRef.current) {
            firstRunRef.current = false;
            return;
        }
        searchRef.current?.(debouncedKeyword);
    }, [debouncedKeyword]);
};

export default useAutoSearch;

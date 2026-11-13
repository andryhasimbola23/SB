import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    Plus, Trash2, Package, Camera, X, Tag, Building2, Briefcase,
    Filter, CalendarClock, Store,
    Eye, FileText, TrendingDown, ArrowRightLeft, Grid3x3, List, Pencil,
    Printer, Loader2, AlignLeft, CalendarCheck, Banknote, Percent,
    Activity, ShieldCheck, Save,
} from 'lucide-react';
import immobilisationService from '../../services/immobilisationService';
import serviceService from '../../services/serviceService';
import agenceService from '../../services/agenceService';
import categorieService from '../../services/categorieService';
import fournisseurService from '../../services/fournisseurService';
import uploadService from '../../services/uploadService';
import mouvementService from '../../services/mouvementService';
import exportService from '../../services/exportService';
import Modal from '../../components/ui/Modal';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import Pagination from '../../components/ui/Pagination';
import AmortissementTable from '../../components/ui/AmortissementTable';
import QrCodeBlock from '../../components/ui/QrCodeBlock';
import MouvementHistory from '../../components/ui/MouvementHistory';
import MouvementModal from '../../components/ui/MouvementModal';
import ExportButton from '../../components/ui/ExportButton';
import Field, { FormFooter } from '../../components/ui/Field';
import Section from '../../components/ui/Section';
import Badge, { StatutBadge } from '../../components/ui/Badge';
import DetailItem from '../../components/ui/DetailItem';
import { EmptyState } from '../../components/ui/Surface';
import { RowAction } from '../../components/ui/ResponsiveTable';
import FilterDropdown from '../../components/ui/FilterDropdown';
import Toast from '../../components/ui/Toast';
import { ListSkeleton } from '../../components/ui/Skeleton';
import { DotsLoader } from '../../components/ui/Loader';
import { printQrLabelSheet } from '../../utils/printQrLabels';
import { genererQrCodesEnLot } from '../../utils/genererQrCodes';
import { formatMontant, formatDate, getPhotoUrl } from '../../utils/format';
import { usePagination } from '../../hooks/usePagination';
import { useAuth } from '../../hooks/useAuth';
import useAutoSearch from '../../hooks/useAutoSearch';

const TYPES_MATERIEL = [
    'UCB', 'UCP', 'ARM', 'CFR', 'CHS', 'CTL', 'ECR', 'IMP',
    'MOT', 'OND', 'PA', 'RAK', 'ROU', 'SCN', 'SWT', 'TAB', 'TEL',
];

/**
 * États physiques.
 * NEUF et BON sont des tons positifs — on distingue les deux par l'intensité,
 * pas en ajoutant une couleur supplémentaire.
 */
const ETATS_PHYSIQUES = [
    { value: 'NEUF', label: 'Neuf', tone: 'brand' },
    { value: 'BON', label: 'Bon état', tone: 'brand' },
    { value: 'MAUVAIS', label: 'Mauvais état', tone: 'warning' },
    { value: 'HORS_SERVICE', label: 'Hors service', tone: 'danger' },
];

const STATUTS = [
    { value: 'ACTIF', label: 'Actif', tone: 'brand' },
    { value: 'CEDE', label: 'Cédé', tone: 'warning' },
    { value: 'REBUTE', label: 'Mis au rebut', tone: 'danger' },
];

const etatMeta = (v) => ETATS_PHYSIQUES.find((e) => e.value === v) || { label: v || '-', tone: 'neutral' };

/**
 * Seuil « parc de plus de 5 ans » — calqué sur la formule du tableau de bord
 * (`countActifsAnciens` : biens actifs mis en service avant le seuil).
 *
 * Calculé une fois au chargement du module plutôt qu'au rendu : l'écart de
 * quelques secondes avec le calcul serveur est sans effet sur un horizon de
 * cinq ans, et le rendu reste pur.
 */
const SEUIL_PARC_ANCIEN = new Date();
SEUIL_PARC_ANCIEN.setFullYear(SEUIL_PARC_ANCIEN.getFullYear() - 5);

/**
 * Résume une liste d'échecs de génération pour l'affichage dans un toast :
 * on n'affiche jamais des dizaines de codes uniques.
 */
const resumerEchecs = (echecs, max = 3) => {
    const codes = echecs.slice(0, max).map((e) => e.codeUnique || e.id).join(', ');
    const reste = echecs.length - Math.min(echecs.length, max);
    return reste > 0 ? `${codes} et ${reste} autre(s)` : codes;
};

/** Bilan lisible d'une impression groupée, génération automatique comprise. */
const formulerBilan = ({ nbEtiquettes, nbGeneres, echecs, nbSansQr }) => {
    const morceaux = [`${nbEtiquettes} étiquette(s) QR Code prête(s) à imprimer`];
    if (nbGeneres > 0) morceaux[0] += `, dont ${nbGeneres} générée(s) à la volée`;
    if (nbSansQr > 0) morceaux.push(`${nbSansQr} bien(s) resté(s) sans QR`);
    if (echecs.length > 0) {
        morceaux.push(`${echecs.length} échec(s) de génération : ${resumerEchecs(echecs)}`);
    }
    return `${morceaux.join(' — ')}.`;
};

const GestionImmobilisations = () => {
    const { isAdmin, user } = useAuth();

    const [immobilisations, setImmobilisations] = useState([]);
    const [services, setServices] = useState([]);
    const [agences, setAgences] = useState([]);
    const [categories, setCategories] = useState([]);
    const [fournisseurs, setFournisseurs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [keyword, setKeyword] = useState('');
    const [filterService, setFilterService] = useState('');

    // Liste complète en cache : elle restaure les résultats instantanément
    // quand le champ de recherche est vidé, sans rechargement ni squelette.
    const fullListRef = useRef([]);
    // Compteur de requête : seules les réponses les plus récentes s'affichent.
    const searchSeqRef = useRef(0);

    // Filtres d'état et d'ancienneté — l'URL est la source unique de vérité :
    // le tableau de bord arrive avec le filtre de l'alerte cliquée
    // (?etat=MAUVAIS, ?anciens=1), un lien de la barre latérale ou le bouton
    // retour repart de zéro, et le lien redevient partageable. Dérivés de
    // l'URL, ces filtres ne peuvent jamais diverger de celle-ci — ni rester
    // actifs après une navigation externe (le composant reste monté entre
    // deux liens vers la même route).
    const [searchParams, setSearchParams] = useSearchParams();
    const filterEtat = searchParams.get('etat') || '';
    const filterAnciens = searchParams.get('anciens') === '1';
    const filterType = searchParams.get('type') || '';

    // Écriture des filtres dans l'URL — `replace` : aucune entrée
    // d'historique par bascule de filtre, le retour navigateur doit sauter
    // les micro-toggles et se poser sur l'état de provenance.
    const setFilterEtat = useCallback((valeur) => {
        setSearchParams((precedent) => {
            const suivant = new URLSearchParams(precedent);
            if (valeur) suivant.set('etat', valeur);
            else suivant.delete('etat');
            return suivant;
        }, { replace: true });
    }, [setSearchParams]);

    const setFilterAnciens = useCallback((valeur) => {
        setSearchParams((precedent) => {
            const suivant = new URLSearchParams(precedent);
            if (valeur) suivant.set('anciens', '1');
            else suivant.delete('anciens');
            return suivant;
        }, { replace: true });
    }, [setSearchParams]);

    const setFilterType = useCallback((valeur) => {
        setSearchParams((precedent) => {
            const suivant = new URLSearchParams(precedent);
            if (valeur) suivant.set('type', valeur);
            else suivant.delete('type');
            return suivant;
        }, { replace: true });
    }, [setSearchParams]);

    // Effacement global des filtres — en une seule écriture d'URL : deux
    // appels successifs à `setSearchParams` dans le même gestionnaire
    // travailleraient chacun sur l'URL de départ, et le second annulerait le
    // premier.
    const resetFiltres = useCallback(() => {
        setSearchParams((precedent) => {
            const suivant = new URLSearchParams(precedent);
            suivant.delete('etat');
            suivant.delete('anciens');
            suivant.delete('type');
            return suivant;
        }, { replace: true });
        setFilterService('');
    }, [setSearchParams]);

    const [viewMode, setViewMode] = useState('grid');

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingImmo, setEditingImmo] = useState(null);
    const [uploadingPhoto, setUploadingPhoto] = useState(false);

    const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);
    const [viewingImmo, setViewingImmo] = useState(null);

    const [amortissementPlan, setAmortissementPlan] = useState(null);
    const [loadingAmortissement, setLoadingAmortissement] = useState(false);

    const [mouvements, setMouvements] = useState([]);
    const [loadingMouvements, setLoadingMouvements] = useState(false);
    const [isMouvementModalOpen, setIsMouvementModalOpen] = useState(false);

    const EMPTY_FORM = {
        designation: '',
        dateAcquisition: '',
        dateMiseService: '',
        valeurAchat: '',
        statut: 'ACTIF',
        etatPhysique: 'BON',
        photo: null,
        marque: '',
        tauxAmortissement: '',
        typeMateriel: 'UCB',
        description: '',
        facture: '',
        serviceId: services.length > 0 ? services[0].id : '',
        agenceId: agences.length > 0 ? agences[0].id : '',
        categorieId: '',
        fournisseurId: '',
    };

    const [formData, setFormData] = useState(EMPTY_FORM);
    const [formErrors, setFormErrors] = useState({});
    const [deleteId, setDeleteId] = useState(null);

    // Sélection multiple pour l'impression groupée des QR Codes.
    // Un Set d'identifiants survit à la pagination et aux changements de filtre :
    // la sélection n'est « perdue » que si le bien quitte réellement les résultats.
    const [selectedIds, setSelectedIds] = useState(() => new Set());

    // Mode sélection à la demande : au repos, AUCUNE case à cocher n'est
    // affichée (ni ligne « tout sélectionner », ni cases par carte/ligne).
    // Le mode s'active par l'icône imprimante de la barre d'outils et ne
    // se referme qu'à l'impression, par « Annuler » ou la touche Échap.
    const [modeSelection, setModeSelection] = useState(false);

    // Nombre de QR Codes en cours de génération à la volée avant impression.
    // Sert à la fois à bloquer le bouton et à afficher le spinner.
    const [qrEnCours, setQrEnCours] = useState(0);

    // Notification éphémère (impression groupée, sélection vide, etc.)
    const [toast, setToast] = useState(null);

    /**
     * Filtrage combiné (service + état + type + ancienneté) — la liste paginée
     * doit porter sur le résultat filtré, sinon la pagination se calcule sur
     * l'ensemble et masque l'effet des filtres.
     *
     * Le critère « plus de 5 ans » reproduit la formule du tableau de bord
     * (`countActifsAnciens`) : bien actif dont la mise en service est
     * antérieure à cinq ans — les chiffres des deux écrans coïncident alors.
     */
    const filteredImmobilisations = useMemo(() => {
        const seuil = SEUIL_PARC_ANCIEN.getTime();
        return immobilisations.filter((i) => {
            if (filterService !== '' && String(i.serviceId) !== filterService) return false;
            if (filterEtat !== '' && i.etatPhysique !== filterEtat) return false;
            if (filterType !== '' && i.typeMateriel !== filterType) return false;
            if (filterAnciens) {
                if (i.statut !== 'ACTIF') return false;
                if (!i.dateMiseService || Date.parse(i.dateMiseService) >= seuil) return false;
            }
            return true;
        });
    }, [immobilisations, filterService, filterEtat, filterType, filterAnciens]);

    // Options du filtre état — dérivées des états physiques déjà affichés
    // sur les cartes : le vocabulaire des filtres et de la liste est le même.
    const etatOptions = useMemo(
        () => [
            { value: '', label: 'Tous les états' },
            ...ETATS_PHYSIQUES.map((e) => ({ value: e.value, label: e.label })),
        ],
        []
    );

    // Tout filtre actif (hors recherche texte) — l'état vide et son bouton de
    // réinitialisation en dépendent.
    const filtreActif = filterService !== '' || filterEtat !== '' || filterType !== '' || filterAnciens;

    // Options du filtre type — le vocabulaire de la page (même constantes que
    // le formulaire « Type ») : le filtre et la création parlent le même code.
    // Recherche activée (17 codes > 8) : sur mobile, la feuille en bas d'écran
    // rend la liste longue à parcourir au doigt.
    const typeOptions = useMemo(
        () => [
            { value: '', label: 'Tous les types' },
            ...TYPES_MATERIEL.map((t) => ({ value: t, label: t })),
        ],
        []
    );

    // Options du filtre service — mémoïsées pour ne pas recréer le tableau
    // à chaque rendu (le menu déroulant dépend de cette référence).
    const serviceOptions = useMemo(
        () => [
            { value: '', label: 'Tous les services' },
            ...services.map((s) => ({ value: String(s.id), label: `${s.code} - ${s.nomService}` })),
        ],
        [services]
    );

    const {
        currentItems,
        currentPage,
        totalPages,
        totalItems,
        itemsPerPage,
        handlePageChange,
    } = usePagination(filteredImmobilisations, 9);

    /* ---------- Sélection multiple (impression groupée des QR Codes) ---------- */

    // Biens sélectionnés qui font partie des résultats actuels : la sélection
    // est « purgée » au moment de l'impression, jamais stockée périmée.
    const selection = useMemo(
        () => filteredImmobilisations.filter((immo) => selectedIds.has(immo.id)),
        [filteredImmobilisations, selectedIds]
    );

    // La case « Tout sélectionner » porte sur tous les résultats filtrés,
    // toutes pages confondues.
    const allSelected =
        filteredImmobilisations.length > 0 &&
        selection.length === filteredImmobilisations.length;

    const toggleSelection = (id) =>
        setSelectedIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });

    const handleToggleAll = () => {
        setSelectedIds(
            allSelected
                ? new Set()
                : new Set(filteredImmobilisations.map((immo) => immo.id))
        );
    };

    /**
     * Imprime une planche A4 (3 colonnes) : une étiquette par bien sélectionné,
     * composée du logo AUXIMAD, de son QR Code et de son code unique.
     *
     * Les biens sélectionnés qui n'ont pas encore de QR Code le sont générés à
     * la volée (par lots bornés à 5 appels simultanés) puis fusionnés dans la
     * liste — voir `utils/genererQrCodes`. Un échec n'empêche jamais d'imprimer
     * les biens qui, eux, sont étiquetables.
     */
    const handlePrintSelected = async () => {
        if (selection.length === 0) {
            setToast({
                type: 'warning',
                message: 'Aucune immobilisation sélectionnée : cochez au moins un bien avant d’imprimer.',
            });
            return;
        }
        // Double clic pendant la génération : on laisse le premier appel aller à son terme.
        if (qrEnCours > 0) return;

        /* ---------- 1. Génération à la volée des QR Codes manquants ---------- */
        const majParId = new Map();
        const aGenerer = selection.filter((immo) => !immo.qrCode);
        let echecsGenerations = [];

        if (aGenerer.length > 0) {
            setQrEnCours(aGenerer.length);
            // Toast « collant » (duration 0) : mis à jour à chaque bien traité.
            const progression = (faits, total) =>
                setToast({
                    type: 'info',
                    duration: 0,
                    message: `Génération des QR Code(s)… ${faits} / ${total}`,
                });
            progression(0, aGenerer.length);

            let resultat;
            try {
                resultat = await genererQrCodesEnLot(aGenerer, {
                    genererUn: immobilisationService.genererQrCode,
                    concurrence: 5,
                    onProgress: progression,
                });
            } catch (err) {
                // Filet de sécurité : le helper capte déjà chaque bien, ceci ne
                // se produit que sur une défaillance imprévue du module.
                console.error('Erreur lors de la génération des QR Codes', err);
                resultat = {
                    regeneres: [],
                    echecs: aGenerer.map((immo) => ({
                        id: immo.id,
                        codeUnique: immo.codeUnique,
                        message: err?.message || 'Erreur inconnue',
                    })),
                };
            } finally {
                setQrEnCours(0);
            }

            resultat.regeneres.forEach((dto) => majParId.set(dto.id, dto));
            echecsGenerations = resultat.echecs;

            // Fusion dans la liste : la carte affiche désormais son QR et une
            // seconde impression ne régénérera rien. On complète plutôt qu'on ne
            // remplace, pour ne perdre aucun champ de la ligne d'origine.
            if (majParId.size > 0) {
                setImmobilisations((prev) =>
                    prev.map((immo) =>
                        majParId.has(immo.id)
                            ? { ...immo, ...majParId.get(immo.id) }
                            : immo
                    )
                );
            }

            if (majParId.size === 0) {
                setToast({
                    type: 'error',
                    message: `Impossible de générer les QR Code(s) : ${resumerEchecs(echecsGenerations)}. Rien à imprimer.`,
                });
                return;
            }
        }

        /* ---------- 2. Planche ---------- */
        // On repart de la sélection *fusionnée* : les biens régénérés viennent
        // d'être mis à jour, les autres conservent leur QR existant. Le filtre
        // reste en place comme filet de sécurité (échec de génération).
        const biensEtiquetables = selection
            .map((immo) => majParId.get(immo.id) ?? immo)
            .filter((immo) => immo.qrCode);
        const nbSansQr = selection.length - biensEtiquetables.length;

        if (biensEtiquetables.length === 0) {
            setToast({
                type: 'error',
                message: 'Aucun QR Code disponible pour la sélection : rien à imprimer.',
            });
            return;
        }

        if (!printQrLabelSheet(biensEtiquetables)) {
            setToast({
                type: 'error',
                message:
                    'La fenêtre d’impression a été bloquée par le navigateur. Autorisez les fenêgles contextuelles puis réessayez.' +
                    (majParId.size > 0
                        ? ` Les ${majParId.size} QR Code(s) générés ont bien été enregistrés : une nouvelle tentative ne les régénérera pas.`
                        : ''),
            });
            return;
        }

        /* ---------- 3. Bilan ---------- */
        setToast({
            type: echecsGenerations.length > 0 || nbSansQr > 0 ? 'warning' : 'success',
            message: formulerBilan({
                nbEtiquettes: biensEtiquetables.length,
                nbGeneres: majParId.size,
                echecs: echecsGenerations,
                nbSansQr,
            }),
        });
        // Impression faite : retour au repos. Le mode sélection se referme et
        // la sélection est vidée (une prochaine impression sera une nouvelle
        // intention). Sur un échec plus haut, le mode reste ouvert pour
        // corriger la sélection sans tout recommencer.
        setSelectedIds(new Set());
        setModeSelection(false);
    };

    /** Affiche un message, puis l'efface automatiquement. */
    const flash = useCallback((setter, msg, delay) => {
        setter(msg);
        setTimeout(() => setter(''), delay);
    }, []);

    /**
     * Ouvre la fiche d'un bien et charge ses pièces annexes : l'historique de
     * ses mouvements et, pour un administrateur, son plan d'amortissements.
     *
     * Mémoïsée sur `user` — et non sur `isAdmin()`, recréée à chaque rendu —
     * pour que l'effet de lien en profondeur puisse la déclarer en dépendance
     * stable et rester tranquille.
     */
    const handleOpenDetails = useCallback(async (immo) => {
        setViewingImmo(immo);
        setIsDetailsModalOpen(true);
        setAmortissementPlan(null);
        setMouvements([]);
        setLoadingAmortissement(true);
        setLoadingMouvements(true);

        const admin = user?.role === 'ADMIN';

        try {
            const promises = [mouvementService.getByImmobilisation(immo.id)];
            if (admin) promises.push(immobilisationService.getAmortissements(immo.id));
            const results = await Promise.all(promises);
            setMouvements(results[0]);
            if (admin && results[1]) setAmortissementPlan(results[1]);
        } catch (err) {
            console.error('Erreur lors du chargement des détails', err);
        } finally {
            setLoadingAmortissement(false);
            setLoadingMouvements(false);
        }
    }, [user]);

    useEffect(() => {
        loadAll();
    }, []);

    // Échap referme le mode sélection — la sortie la plus attendue pour un
    // mode « temporaire ». L'écouteur n'existe qu'en mode actif.
    useEffect(() => {
        if (!modeSelection) return undefined;
        const gererTouche = (e) => {
            if (e.key === 'Escape') setModeSelection(false);
        };
        window.addEventListener('keydown', gererTouche);
        return () => window.removeEventListener('keydown', gererTouche);
    }, [modeSelection]);

    /**
     * Lien en profondeur depuis le tableau de bord : `?immo=<id>`.
     *
     * Le bien est chargé par son identifiant, indépendamment de la liste —
     * sinon il faudrait d'abord retrouver la page qui le contient, alors que la
     * cible peut être à la quatrième page et filtrée hors écran. `handleOpenDetails`
     * fait ensuite les mêmes chargements annexes que depuis la liste (mouvements,
     * amortissements). Le paramètre est retiré de l'URL une fois la modale ouverte :
     * un rechargement ne la rouvre pas, et on peut la fermer sans qu'elle revienne.
     */
    useEffect(() => {
        const id = searchParams.get('immo');
        if (!id) return undefined;

        let vivant = true;
        (async () => {
            try {
                const immo = await immobilisationService.getById(id);
                if (!vivant) return;
                setError('');
                await handleOpenDetails(immo);
            } catch (err) {
                if (!vivant) return;
                console.error(err);
                flash(setError, 'Immobilisation introuvable ou hors de votre périmètre', 6000);
            } finally {
                // Seul le paramètre `immo` est consommé : les filtres
                // éventuels (?etat, ?anciens) doivent survivre à l'ouverture
                // d'une fiche — sinon un simple clic depuis le tableau de bord
                // effacerait le filtre choisi.
                if (vivant) {
                    setSearchParams((precedent) => {
                        const suivant = new URLSearchParams(precedent);
                        suivant.delete('immo');
                        return suivant;
                    }, { replace: true });
                }
            }
        })();

        return () => {
            vivant = false;
        };
    }, [handleOpenDetails, flash, searchParams, setSearchParams]);

    const loadAll = async () => {
        try {
            setLoading(true);
            const [immoData, servData, agData, catData, fourData] = await Promise.all([
                immobilisationService.getAll(),
                serviceService.getAll(),
                agenceService.getAll(),
                categorieService.getAll(),
                fournisseurService.getAll(),
            ]);
            // Invalide d'éventuelles recherches en vol avant d'écrire la liste.
            searchSeqRef.current += 1;
            fullListRef.current = immoData;
            setImmobilisations(immoData);
            setServices(servData.filter((s) => s.actif));
            setAgences(agData.filter((a) => a.actif));
            setCategories(catData.filter((c) => c.actif));
            setFournisseurs(fourData.filter((f) => f.actif));
            // Une recherche en cours reste appliquée sur les données rechargées.
            if (keyword.trim()) await handleSearch(keyword);
        } catch (err) {
            setError('Erreur lors du chargement');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    /**
     * Recherche — appelée automatiquement à la frappe (useAutoSearch, ce
     * champ n'utilisant pas DataToolbar), par Entrée ou par le bouton
     * « Rechercher » de la barre d'outils.
     *
     * @param {string} terme terme recherché ; défaut = état courant du champ
     */
    const handleSearch = async (terme = keyword) => {
        const k = (terme ?? '').trim();
        handlePageChange(1);

        if (!k) {
            // Champ vidé : restauration immédiate depuis le cache.
            searchSeqRef.current += 1;
            setImmobilisations(fullListRef.current);
            return;
        }

        const seq = ++searchSeqRef.current;
        try {
            const resultats = await immobilisationService.search(k);
            // Réponse obsolète (une frappe plus récente a pris le relais).
            if (seq === searchSeqRef.current) setImmobilisations(resultats);
        } catch {
            if (seq === searchSeqRef.current) flash(setError, 'Erreur lors de la recherche', 5000);
        }
    };

    // Recherche instantanée à la frappe (300 ms de stabilisation).
    useAutoSearch(keyword, handleSearch);

    const handleOpenCreate = () => {
        setEditingImmo(null);
        setFormData({ ...EMPTY_FORM });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const handleOpenEdit = (immo) => {
        setEditingImmo(immo);
        setFormData({
            designation: immo.designation || '',
            dateAcquisition: immo.dateAcquisition ? immo.dateAcquisition.substring(0, 10) : '',
            dateMiseService: immo.dateMiseService ? immo.dateMiseService.substring(0, 10) : '',
            valeurAchat: immo.valeurAchat || '',
            statut: immo.statut || 'ACTIF',
            etatPhysique: immo.etatPhysique || 'BON',
            photo: immo.photo || null,
            marque: immo.marque || '',
            tauxAmortissement: immo.tauxAmortissement || '',
            typeMateriel: immo.typeMateriel || 'UCB',
            description: immo.description || '',
            facture: immo.facture || '',
            serviceId: immo.serviceId || '',
            agenceId: immo.agenceId || '',
            categorieId: immo.categorieId || '',
            fournisseurId: immo.fournisseurId || '',
        });
        setFormErrors({});
        setIsModalOpen(true);
    };

    const handleCategorieChange = (e) => {
        const catId = e.target.value;
        const selectedCat = categories.find((c) => String(c.id) === String(catId));
        setFormData((prev) => ({
            ...prev,
            categorieId: catId,
            tauxAmortissement: selectedCat?.tauxAmortissementParDefaut ?? prev.tauxAmortissement,
        }));
        if (formErrors.categorieId) setFormErrors((prev) => ({ ...prev, categorieId: '' }));
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
        if (formErrors[name]) setFormErrors((prev) => ({ ...prev, [name]: '' }));
    };

    const handlePhotoUpload = async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;

        if (!file.type.startsWith('image/')) {
            flash(setError, 'Seuls les fichiers images sont autorisés', 4000);
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            flash(setError, 'La taille ne doit pas dépasser 5 MB', 4000);
            return;
        }

        setUploadingPhoto(true);
        setError('');
        try {
            const { url } = await uploadService.immobilisationPhoto(file);
            setFormData((prev) => ({ ...prev, photo: url }));
        } catch {
            flash(setError, "Erreur lors de l'upload de la photo", 4000);
        } finally {
            setUploadingPhoto(false);
        }
    };

    const validateForm = () => {
        const errors = {};
        if (!formData.designation.trim()) errors.designation = 'La désignation est obligatoire';
        if (!formData.dateAcquisition) errors.dateAcquisition = "La date d'acquisition est obligatoire";
        if (!formData.dateMiseService) errors.dateMiseService = 'La date de mise en service est obligatoire';
        if (!formData.valeurAchat) errors.valeurAchat = "La valeur d'achat est obligatoire";
        else if (parseFloat(formData.valeurAchat) <= 0) errors.valeurAchat = 'La valeur doit être supérieure à 0';
        if (!formData.marque.trim()) errors.marque = 'La marque est obligatoire';
        if (!formData.tauxAmortissement) errors.tauxAmortissement = 'Le taux est obligatoire';
        if (!formData.typeMateriel) errors.typeMateriel = 'Le type est obligatoire';
        if (!formData.categorieId) errors.categorieId = 'La catégorie est obligatoire';

        if (!editingImmo) {
            if (!formData.serviceId) errors.serviceId = 'Le service est obligatoire';
            if (!formData.agenceId) errors.agenceId = "L'agence est obligatoire";
        }

        setFormErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validateForm()) return;

        try {
            setError('');
            const payload = {
                ...formData,
                valeurAchat: parseFloat(formData.valeurAchat),
                tauxAmortissement: parseFloat(formData.tauxAmortissement),
                dateAcquisition: formData.dateAcquisition + 'T00:00:00',
                dateMiseService: formData.dateMiseService + 'T00:00:00',
                categorieId: Number(formData.categorieId),
                fournisseurId: formData.fournisseurId ? Number(formData.fournisseurId) : null,
            };

            if (!editingImmo) {
                payload.serviceId = Number(formData.serviceId);
                payload.agenceId = Number(formData.agenceId);
            }

            if (editingImmo) {
                await immobilisationService.update(editingImmo.id, payload);
                flash(setSuccess, 'Immobilisation modifiée avec succès', 5000);
            } else {
                const created = await immobilisationService.create(payload);
                flash(setSuccess, `Immobilisation créée : ${created.codeUnique}`, 5000);
            }
            setIsModalOpen(false);
            loadAll();
        } catch (err) {
            flash(setError, err.response?.data?.message || "Erreur lors de l'enregistrement", 5000);
        }
    };

    const handleDelete = async () => {
        try {
            await immobilisationService.delete(deleteId);
            flash(setSuccess, 'Immobilisation supprimée avec succès', 3000);
        } catch (err) {
            flash(setError, err.response?.data?.message || 'Erreur lors de la suppression', 5000);
        } finally {
            setDeleteId(null);
            loadAll();
        }
    };

    const handleMouvementSuccess = async () => {
        try {
            const updated = await immobilisationService.getById(viewingImmo.id);
            setViewingImmo(updated);
            setMouvements(await mouvementService.getByImmobilisation(viewingImmo.id));
            if (isAdmin()) setAmortissementPlan(await immobilisationService.getAmortissements(viewingImmo.id));
            loadAll();
            flash(setSuccess, 'Mouvement enregistré avec succès', 3000);
        } catch (err) {
            console.error('Erreur lors du rechargement', err);
        }
    };

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}
            {success && <div className="alert-brand alert">{success}</div>}

            {/* ---------- Barre d'outils ---------- */}
            {/* Une carte, deux lignes logiques : recherche et actions en haut,
                filtres regroupés en dessous. Les huit contrôles empilés sur une
                unique ligne devenaient illisibles dès la moindre largeur
                intermédiaire — d'où cette répartition. */}
            <div className="card p-3 sm:p-4">
                <div className="flex flex-wrap sm:flex-nowrap items-center gap-2">
                    {/* Recherche — absorbe le reste de la ligne ; largeur
                        minimale pour que les actions passent en dessous sur
                        petit écran plutôt que d'être comprimées. */}
                    <div className="search-field min-w-[14rem]">
                        <div className="relative">
                            <input
                                type="text"
                                placeholder="Rechercher un bien…"
                                value={keyword}
                                onChange={(e) => setKeyword(e.target.value)}
                                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                                className="input pl-9"
                                aria-label="Rechercher une immobilisation"
                            />
                            <svg
                                className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
                                width="16" height="16" viewBox="0 0 24 24" fill="none"
                                stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"
                            >
                                <circle cx="11" cy="11" r="8" />
                                <path d="m21 21-4.3-4.3" />
                            </svg>
                        </div>
                    </div>

                    {/* Actions de la ligne de recherche : bascule de vue,
                        impression des QR Codes, export, création. */}
                    <div className="flex flex-wrap sm:flex-nowrap items-center gap-2 sm:shrink-0">
                        {/* Bascule de vue — visible partout. Elle a du sens même
                            sur mobile : la grille n'y compte qu'une seule colonne,
                            la vue liste y est donc bien plus dense. Les boutons
                            passent en p-2.5 sous sm pour une cible tactile de 36 px. */}
                        <div className="flex items-center shrink-0 bg-surface-2 dark:bg-slate-800 rounded-control p-1 border border-line">
                            {[
                                { mode: 'grid', icon: Grid3x3, label: 'Grille' },
                                { mode: 'list', icon: List, label: 'Liste' },
                            ].map(({ mode, icon: Icon, label }) => (
                                <button
                                    key={mode}
                                    onClick={() => setViewMode(mode)}
                                    title={label}
                                    aria-label={label}
                                    aria-pressed={viewMode === mode}
                                    className={`p-2.5 sm:p-2 rounded-control transition-colors ${
                                        viewMode === mode
                                            ? 'bg-surface text-brand-600 dark:text-brand-300 shadow-soft'
                                            : 'text-ink-muted hover:text-ink'
                                    }`}
                                >
                                    <Icon size={16} />
                                </button>
                            ))}
                        </div>

                        {/* Impression des QR Codes — une simple icône, sans bloc
                            dédié : au repos elle active le mode sélection, en
                            mode elle imprime les biens retenus. Le compteur
                            s'affiche en pastille, le spinner signale la
                            génération à la volée des QR Codes manquants. */}
                        {filteredImmobilisations.length > 0 && (
                            <button
                                type="button"
                                onClick={() => {
                                    // Mode sélection à la demande : au repos
                                    // l'icône ACTIVE le mode ; en mode elle
                                    // lance l'impression des biens retenus.
                                    if (modeSelection) handlePrintSelected();
                                    else setModeSelection(true);
                                }}
                                disabled={qrEnCours > 0}
                                aria-pressed={modeSelection}
                                title={
                                    qrEnCours > 0
                                        ? `Génération des QR Code(s)… ${qrEnCours} restant(s)`
                                        : modeSelection
                                            ? `Imprimer les ${selection.length} bien(s) sélectionné(s)`
                                            : 'Sélectionner et imprimer les QR Codes'
                                }
                                aria-label={
                                    qrEnCours > 0
                                        ? 'Génération des QR Codes en cours'
                                        : modeSelection
                                            ? 'Imprimer les biens sélectionnés'
                                            : 'Activer le mode sélection pour imprimer les QR Codes'
                                }
                                className={`btn btn-icon relative border min-h-10 min-w-10 sm:min-h-9 sm:min-w-9 ${
                                    qrEnCours > 0
                                        ? 'border-brand-500/50 bg-brand-50/60 text-brand-600 dark:border-brand-500/40 dark:bg-brand-500/10 dark:text-brand-300'
                                        : modeSelection || selection.length > 0
                                            ? 'border-brand-500/50 bg-brand-50/60 text-brand-700 dark:border-brand-500/40 dark:bg-brand-500/10 dark:text-brand-300'
                                            : 'border-line bg-surface-2 text-ink-muted dark:bg-slate-800 hover:text-ink hover:border-brand-500/40'
                                }`}
                            >
                                {qrEnCours > 0 ? (
                                    <Loader2 size={16} className="animate-spin" />
                                ) : (
                                    <Printer size={16} />
                                )}
                                {selection.length > 0 && qrEnCours === 0 && (
                                    <span className="absolute -top-1.5 -right-1.5 min-w-[18px] h-[18px] px-1 rounded-full bg-brand-500 text-white text-[10px] font-bold leading-[18px] text-center">
                                        {selection.length}
                                    </span>
                                )}
                            </button>
                        )}

                        <ExportButton
                            onExportExcel={exportService.exportImmobilisationsExcel}
                            label="Exporter"
                        />

                        {isAdmin() && (
                            <button onClick={handleOpenCreate} className="btn-primary btn-md w-full sm:w-auto">
                                <Plus size={16} />
                                Nouveau
                            </button>
                        )}
                    </div>
                </div>

                {/* Ligne 2 — filtres dédiée, séparée d'un filet : regrouper
                    les critères les uns avec les autres plutôt que de les
                    mêler aux actions. Le bouton d'effacement évite d'attendre
                    l'état vide pour défaire un filtre. */}
                <div className="flex flex-wrap items-center gap-2 mt-3 pt-3 border-t border-line/60">
                    <span className="section-label hidden md:inline mr-1">Filtres</span>

                    {/* Filtre par état — ADMIN et employé : chacun reçoit ses
                        propres alertes « À surveiller » dans son périmètre.
                        Menu ancré à GAUCHE : ce filtre est le premier de la
                        barre, un ancrage à droite ferait déborder le panneau
                        de 256 px par-dessus la sidebar. */}
                    <FilterDropdown
                        icon={Package}
                        label="Filtrer par état"
                        value={filterEtat}
                        onChange={setFilterEtat}
                        options={etatOptions}
                        placeholder="Tous les états"
                        block
                        className="flex-1 min-w-[6.5rem] sm:flex-none"
                    />

                    {/* Filtre par type de matériel (UCB, ARM, CHS…) — ADMIN et
                        employé : chacun filtre dans son propre périmètre, comme
                        l'état. Même gabarit responsive que ses voisins :
                        largeur partagée sur une ligne à débordement en mobile,
                        largeur naturelle dès `sm`. Le menu se déroule à gauche
                        (ancre de premier plan) et devient une feuille en bas
                        d'écran sur mobile, avec recherche intégrée. */}
                    <FilterDropdown
                        icon={Tag}
                        label="Filtrer par type"
                        value={filterType}
                        onChange={setFilterType}
                        options={typeOptions}
                        searchable={typeOptions.length > 8}
                        placeholder="Tous les types"
                        block
                        className="flex-1 min-w-[6.5rem] sm:flex-none"
                    />

                    {/* Filtre par service — ADMIN seulement. Pour un employé, la
                        liste est déjà restreinte à son service par l'API : un
                        filtre unique ne servirait qu'à donner l'illusion d'un
                        choix. */}
                    {isAdmin() ? (
                        <FilterDropdown
                            icon={Filter}
                            label="Filtrer par service"
                            value={filterService}
                            onChange={setFilterService}
                            options={serviceOptions}
                            searchable={serviceOptions.length > 8}
                            placeholder="Tous les services"
                            block
                            className="flex-1 min-w-[6.5rem] sm:flex-none"
                        />
                    ) : (
                        <span
                            className="inline-flex items-center gap-2 px-3 py-2 rounded-control border border-line bg-surface-2 dark:bg-slate-800 text-sm text-ink-muted flex-1 min-w-[6.5rem] sm:flex-none"
                            title="Périmètre de consultation imposé"
                        >
                            <Briefcase size={15} className="shrink-0" />
                            <span className="truncate">
                                {user?.serviceNom || 'Aucun service rattaché'}
                            </span>
                        </span>
                    )}

                    <button
                        type="button"
                        onClick={() => setFilterAnciens(!filterAnciens)}
                        aria-pressed={filterAnciens}
                        title="Biens actifs de plus de 5 ans"
                        className={`inline-flex items-center gap-2 px-3 py-2 rounded-control border text-sm shrink-0 transition-colors ${
                            filterAnciens
                                ? 'border-line bg-surface text-ink shadow-soft dark:bg-slate-700'
                                : 'border-line bg-surface-2 text-ink-muted hover:text-ink dark:bg-slate-800'
                        }`}
                    >
                        <CalendarClock size={15} />
                        <span className="hidden sm:inline">Plus de 5 ans</span>
                    </button>

                    {filtreActif && (
                        <button
                            type="button"
                            onClick={resetFiltres}
                            className="text-xs font-semibold act-danger inline-flex items-center gap-1"
                        >
                            <X size={13} />
                            Effacer les filtres
                        </button>
                    )}
                </div>
            </div>

            {/* ---------- Barre de sélection (mode à la demande) ---------- */}
            {/* Aucune case à cocher au repos : la sélection n'existe que
                lorsqu'on veut réellement étiqueter. Le mode s'active par
                l'icône imprimante de la barre d'outils et se referme à
                l'impression, par « Annuler » ou la touche Échap. */}
            {!loading && modeSelection && filteredImmobilisations.length > 0 && (
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-3 py-2.5 rounded-control border border-brand-500/40 bg-brand-50/50 dark:border-brand-500/30 dark:bg-brand-500/10">
                    <p className="text-sm font-medium text-ink inline-flex items-center gap-2">
                        Sélectionnez les biens à étiqueter
                        <span aria-live="polite" className="inline-flex items-center gap-1.5">
                            <Badge
                                tone={selection.length > 0 ? 'brand' : 'neutral'}
                                label={`${selection.length} / ${filteredImmobilisations.length}`}
                                dot={false}
                            />
                            <span className="text-xs text-ink-muted font-normal">retenus</span>
                        </span>
                    </p>

                    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 ml-auto">
                        <label className="flex items-center gap-2 cursor-pointer select-none text-sm text-ink-muted hover:text-ink transition-colors">
                            <input
                                type="checkbox"
                                checked={allSelected}
                                onChange={handleToggleAll}
                                aria-label="Sélectionner toutes les immobilisations"
                                className="w-4 h-4 rounded border-line-strong text-brand-500 focus:ring-brand-500/30 cursor-pointer"
                            />
                            <span className="font-medium">Tout sélectionner</span>
                        </label>

                        {selection.length > 0 && (
                            <button
                                type="button"
                                onClick={() => setSelectedIds(new Set())}
                                className="text-xs font-semibold act-danger inline-flex items-center gap-1"
                            >
                                <X size={13} />
                                Effacer
                            </button>
                        )}

                        <button
                            type="button"
                            onClick={() => setModeSelection(false)}
                            title="Fermer le mode sélection (Échap)"
                            className="text-xs font-semibold text-ink-muted hover:text-ink inline-flex items-center gap-1 transition-colors"
                        >
                            <X size={13} />
                            Annuler
                        </button>
                    </div>
                </div>
            )}

            {/* ---------- Liste ---------- */}
            {loading ? (
                <ListSkeleton items={6} />
            ) : filteredImmobilisations.length === 0 ? (
                <div className="card">
                    <EmptyState
                        icon={Package}
                        title={
                            !isAdmin() && !user?.serviceId
                                ? 'Aucun périmètre de consultation'
                                : filtreActif
                                  ? 'Aucune immobilisation ne correspond aux filtres'
                                  : 'Aucune immobilisation trouvée'
                        }
                        message={
                            !isAdmin() && !user?.serviceId
                                ? 'Vous n’êtes rattaché à aucun service : aucun bien ne vous est visible.'
                                : filtreActif
                                  ? 'Aucun bien ne correspond aux filtres sélectionnés.'
                                  : isAdmin()
                                    ? 'Cliquez sur « Nouveau » pour en ajouter une.'
                                    : 'Aucun bien à afficher.'
                        }
                        action={
                            !isAdmin() && !user?.serviceId ? null : filtreActif ? (
                                <button
                                    onClick={resetFiltres}
                                    className="btn-secondary btn-md mt-2"
                                >
                                    <X size={16} />
                                    Réinitialiser le filtre
                                </button>
                            ) : isAdmin() ? (
                                <button onClick={handleOpenCreate} className="btn-primary btn-md mt-2">
                                    <Plus size={16} />
                                    Nouvelle immobilisation
                                </button>
                            ) : null
                        }
                    />
                </div>
            ) : (
                <>
                    {viewMode === 'grid' ? (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-5">
                            {currentItems.map((immo, index) => {
                                const etat = etatMeta(immo.etatPhysique);
                                const selectionnee = selectedIds.has(immo.id);
                                return (
                                    <article
                                        key={immo.id}
                                        onClick={() => handleOpenDetails(immo)}
                                        className={`group card overflow-hidden cursor-pointer transition-all duration-200 animate-fade-in-up hover:shadow-card hover:-translate-y-0.5 ${
                                            selectionnee
                                                ? 'ring-2 ring-brand-500 ring-offset-2 ring-offset-surface dark:ring-offset-slate-900 shadow-card'
                                                : ''
                                        }`}
                                        style={{ animationDelay: `${index * 40}ms` }}
                                    >
                                        {/* Visuel */}
                                        <div className="relative h-44 bg-gradient-to-br from-surface-3 to-surface-2 dark:from-slate-800 dark:to-slate-900 overflow-hidden">
                                            {/* Sélection pour l'impression groupée — UNIQUEMENT en
                                                mode sélection. `z-10` : l'overlay de survol
                                                (inset-0) recouvre le visuel et capterait le clic. */}
                                            {modeSelection && (
                                                <div
                                                    className={`absolute top-3 left-3 z-10 rounded-control p-1 border transition-colors duration-150 ${
                                                        selectionnee
                                                            ? 'bg-brand-500 border-brand-600 text-white shadow-brand'
                                                            : 'bg-surface/95 border-line text-ink-muted dark:bg-slate-800/95 dark:border-slate-600'
                                                    }`}
                                                    onClick={(e) => e.stopPropagation()}
                                                >
                                                    <input
                                                        type="checkbox"
                                                        checked={selectionnee}
                                                        onChange={() => toggleSelection(immo.id)}
                                                        aria-label={`Sélectionner ${immo.codeUnique}`}
                                                        className={`w-4 h-4 block rounded cursor-pointer border ${
                                                            selectionnee
                                                                ? 'border-white/60 accent-current'
                                                                : 'border-line-strong dark:border-slate-500 accent-brand-500'
                                                        }`}
                                                    />
                                                </div>
                                            )}

                                            {immo.photo ? (
                                                <img
                                                    src={getPhotoUrl(immo.photo)}
                                                    alt={immo.designation}
                                                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                                                />
                                            ) : (
                                                <div className="w-full h-full flex items-center justify-center">
                                                    <Package size={48} className="text-ink-subtle" />
                                                </div>
                                            )}

                                            <span className="absolute top-3 left-12 px-2.5 py-1 bg-black/60 backdrop-blur-md text-white text-[10px] font-bold rounded-full tracking-wider">
                                                {immo.typeMateriel}
                                            </span>
                                            <div className="absolute top-3 right-3">
                                                <StatutBadge statut={immo.statut} />
                                            </div>

                                            <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-end justify-center pb-4">
                                                <span className="text-white text-xs font-medium inline-flex items-center gap-1.5">
                                                    <Eye size={14} />
                                                    Voir les détails
                                                </span>
                                            </div>
                                        </div>

                                        {/* Contenu */}
                                        <div className="p-4">
                                            <div className="flex items-center justify-between gap-2 mb-2">
                                                <span className="text-[10px] mono-code-chip px-2 py-0.5 rounded truncate max-w-[60%]">
                                                    {immo.codeUnique}
                                                </span>
                                                <Badge tone={etat.tone} label={etat.label} dot={false} />
                                            </div>

                                            <h3 className="text-sm font-semibold text-ink truncate">
                                                {immo.designation}
                                            </h3>
                                            <p className="text-xs text-ink-muted mb-3 truncate">
                                                {immo.marque} · {immo.categorieCode}
                                            </p>

                                            <div className="flex items-center gap-2 mb-3 text-xs text-ink-muted">
                                                <Building2 size={12} className="shrink-0 text-ink-subtle" />
                                                <span className="truncate">{immo.agenceNom}</span>
                                                <span className="text-ink-subtle">•</span>
                                                <Briefcase size={12} className="shrink-0 text-ink-subtle" />
                                                <span className="truncate">{immo.serviceNom}</span>
                                            </div>

                                            <div className="flex items-center justify-between pt-3 border-t border-line">
                                                <div>
                                                    <p className="section-label">{isAdmin() ? 'Valeur' : 'Acquisition'}</p>
                                                    {isAdmin() ? (
                                                        <p className="text-sm font-bold text-ink">
                                                            {formatMontant(immo.valeurAchat)}
                                                        </p>
                                                    ) : (
                                                        <p className="text-sm font-semibold text-ink-muted">
                                                            {formatDate(immo.dateAcquisition)}
                                                        </p>
                                                    )}
                                                </div>

                                                {isAdmin() && (
                                                    <div className="flex items-center gap-1">
                                                        <RowAction
                                                            icon={Pencil}
                                                            label="Modifier"
                                                            variant="edit"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                handleOpenEdit(immo);
                                                            }}
                                                        />
                                                        <RowAction
                                                            icon={Trash2}
                                                            label="Supprimer"
                                                            variant="delete"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                setDeleteId(immo.id);
                                                            }}
                                                        />
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </article>
                                );
                            })}
                        </div>
                    ) : (
                        <div className="space-y-2">
                            {currentItems.map((immo, index) => {
                                const etat = etatMeta(immo.etatPhysique);
                                const selectionnee = selectedIds.has(immo.id);
                                return (
                                    <div
                                        key={immo.id}
                                        onClick={() => handleOpenDetails(immo)}
                                        className={`card p-3 flex items-center gap-3 sm:gap-4 cursor-pointer transition-all animate-fade-in-up hover:shadow-card ${
                                            selectionnee
                                                ? 'border-brand-500/60 bg-brand-50/40 dark:border-brand-500/40 dark:bg-brand-500/10 shadow-card'
                                                : 'hover:border-brand-500/40'
                                        }`}
                                        style={{ animationDelay: `${index * 30}ms` }}
                                    >
                                        {/* Sélection pour l'impression groupée — devant le bien,
                                            uniquement en mode sélection. */}
                                        {modeSelection && (
                                            <div
                                                className={`rounded-control p-1 border shrink-0 transition-colors duration-150 ${
                                                    selectionnee
                                                        ? 'bg-brand-500 border-brand-600 text-white'
                                                        : 'bg-surface border-line text-ink-muted dark:bg-slate-800 dark:border-slate-600'
                                                }`}
                                                onClick={(e) => e.stopPropagation()}
                                            >
                                                <input
                                                    type="checkbox"
                                                    checked={selectionnee}
                                                    onChange={() => toggleSelection(immo.id)}
                                                    aria-label={`Sélectionner ${immo.codeUnique}`}
                                                    className={`w-4 h-4 block rounded cursor-pointer border ${
                                                        selectionnee
                                                            ? 'border-white/60 accent-current'
                                                            : 'border-line-strong dark:border-slate-500 accent-brand-500'
                                                    }`}
                                                />
                                            </div>
                                        )}

                                        <div className="w-14 h-14 rounded-control overflow-hidden bg-surface-3 flex-shrink-0">
                                            {immo.photo ? (
                                                <img
                                                    src={getPhotoUrl(immo.photo)}
                                                    alt={immo.designation}
                                                    className="w-full h-full object-cover"
                                                />
                                            ) : (
                                                <div className="w-full h-full flex items-center justify-center">
                                                    <Package size={22} className="text-ink-subtle" />
                                                </div>
                                            )}
                                        </div>

                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2 mb-0.5">
                                                <h3 className="text-sm font-semibold text-ink truncate">{immo.designation}</h3>
                                                <span className="hidden sm:inline-flex text-[10px] mono-code-chip px-1.5 py-0.5 rounded">
                                                    {immo.typeMateriel}
                                                </span>
                                            </div>
                                            <p className="text-xs font-mono text-ink-muted truncate">{immo.codeUnique}</p>
                                        </div>

                                        <div className="hidden md:flex items-center gap-2 flex-shrink-0">
                                            <StatutBadge statut={immo.statut} />
                                            <Badge tone={etat.tone} label={etat.label} dot={false} />
                                        </div>

                                        {isAdmin() && (
                                            <div className="hidden lg:block text-right flex-shrink-0">
                                                <p className="text-sm font-bold text-ink">{formatMontant(immo.valeurAchat)}</p>
                                                <p className="text-[10px] text-ink-subtle">{formatDate(immo.dateAcquisition)}</p>
                                            </div>
                                        )}

                                        <div className="flex items-center gap-1 flex-shrink-0">
                                            <RowAction icon={Eye} label="Voir les détails" variant="brand" onClick={(e) => { e.stopPropagation(); handleOpenDetails(immo); }} />
                                            {isAdmin() && (
                                                <>
                                                    <span className="hidden sm:block">
                                                        <RowAction icon={Pencil} label="Modifier" variant="edit" onClick={(e) => { e.stopPropagation(); handleOpenEdit(immo); }} />
                                                    </span>
                                                    <span className="hidden sm:block">
                                                        <RowAction icon={Trash2} label="Supprimer" variant="delete" onClick={(e) => { e.stopPropagation(); setDeleteId(immo.id); }} />
                                                    </span>
                                                </>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}

                    <div className="card overflow-hidden">
                        <Pagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}
                            totalItems={totalItems}
                            itemsPerPage={itemsPerPage}
                        />
                    </div>
                </>
            )}

            {/* ---------- Détails ---------- */}
            <Modal
                isOpen={isDetailsModalOpen}
                onClose={() => setIsDetailsModalOpen(false)}
                title="Détails de l'immobilisation"
                size="lg"
            >
                {viewingImmo && (
                    <div className="space-y-6">
                        <div className="flex flex-col md:flex-row gap-5 pb-5 border-b border-line">
                            {/* Photo — carte ombrée, léger zoom au survol ;
                                sans photo, un emplacement assumé plutôt qu'un
                                rectangle vide. */}
                            <div className="md:w-48 h-48 flex-shrink-0 group rounded-card overflow-hidden bg-surface-2 dark:bg-slate-800 border border-line shadow-card">
                                {viewingImmo.photo ? (
                                    <img
                                        src={getPhotoUrl(viewingImmo.photo)}
                                        alt={viewingImmo.designation}
                                        className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                                    />
                                ) : (
                                    <div className="w-full h-full flex flex-col items-center justify-center gap-2">
                                        <Package size={36} className="text-ink-subtle" />
                                        <span className="text-[10px] uppercase font-bold tracking-wider text-ink-subtle">
                                            Aucune photo
                                        </span>
                                    </div>
                                )}
                            </div>

                            <div className="flex-1 min-w-0">
                                <div className="flex flex-wrap gap-2 mb-3">
                                    <StatutBadge statut={viewingImmo.statut} />
                                    <Badge
                                        tone={etatMeta(viewingImmo.etatPhysique).tone}
                                        label={etatMeta(viewingImmo.etatPhysique).label}
                                        dot={false}
                                    />
                                </div>

                                <h2 className="text-lg sm:text-xl font-bold text-ink mb-2">{viewingImmo.designation}</h2>

                                <div className="inline-flex items-center gap-1.5 text-sm font-semibold mono-code-chip px-3 py-1 rounded-control">
                                    {viewingImmo.codeUnique}
                                </div>

                                {/* Chiffres clés — tuiles NEUTRES d'un seul
                                    gabarit : l'amortissement n'est pas un
                                    danger, il n'a pas de rouge (règle de
                                    palette). */}
                                <div className={`grid gap-3 mt-4 ${isAdmin() ? 'grid-cols-2' : 'grid-cols-1'}`}>
                                    {isAdmin() && (
                                        <div className="rounded-panel p-3 bg-surface-2 dark:bg-slate-800/60 border border-line">
                                            <p className="section-label mb-1">Valeur d'achat</p>
                                            <p className="text-lg font-bold text-ink tabular-nums">
                                                {formatMontant(viewingImmo.valeurAchat)}
                                            </p>
                                        </div>
                                    )}
                                    <div className="rounded-panel p-3 bg-surface-2 dark:bg-slate-800/60 border border-line">
                                        <p className="section-label mb-1">Taux d'amortissement</p>
                                        <p className="text-lg font-bold text-ink tabular-nums flex items-center gap-1.5">
                                            <TrendingDown size={15} className="text-ink-muted" />
                                            {viewingImmo.tauxAmortissement} %
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Identification enrichie : les dates d'acquisition et
                            de mise en service ainsi que le fournisseur
                            existaient dans le DTO mais n'étaient jamais
                            affichés — la fiche gagne en réalisme sans perdre
                            sa grille (6 lignes = 2 rangées de 3). */}
                        <Section title="Identification">
                            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                                <DetailItem icon={Tag} label="Type" value={viewingImmo.typeMateriel} />
                                <DetailItem icon={Package} label="Marque" value={viewingImmo.marque} />
                                <DetailItem icon={Store} label="Fournisseur" value={viewingImmo.fournisseurNom} />
                                <DetailItem icon={FileText} label="N° Facture" value={viewingImmo.facture} />
                                <DetailItem
                                    icon={CalendarClock}
                                    label="Acquisition"
                                    value={formatDate(viewingImmo.dateAcquisition)}
                                />
                                <DetailItem
                                    icon={CalendarClock}
                                    label="Mise en service"
                                    value={formatDate(viewingImmo.dateMiseService)}
                                />
                            </div>
                            {viewingImmo.description && (
                                <div className="mt-4 rounded-panel bg-surface-2 dark:bg-slate-800/60 border border-line p-3.5">
                                    <p className="section-label mb-1.5">Description</p>
                                    <p className="text-sm text-ink-muted leading-relaxed whitespace-pre-wrap">
                                        {viewingImmo.description}
                                    </p>
                                </div>
                            )}
                        </Section>

                        <Section title="Affectation">
                            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                                <DetailItem
                                    icon={Building2}
                                    label="Agence"
                                    value={viewingImmo.agenceNom ? `${viewingImmo.agenceCode} - ${viewingImmo.agenceNom}` : '-'}
                                />
                                <DetailItem icon={Briefcase} label="Service" value={viewingImmo.serviceNom || '-'} />
                                <DetailItem icon={Tag} label="Catégorie" value={viewingImmo.categorieNom || '-'} />
                            </div>
                        </Section>

                        <QrCodeBlock immobilisation={viewingImmo} onChange={(updated) => setViewingImmo(updated)} />

                        {isAdmin() && (
                            <Section title="Amortissement">
                                {loadingAmortissement ? (
                                    <div className="empty-state bg-surface-2 dark:bg-slate-800/50 rounded-panel border border-dashed border-line-strong py-10">
                                        <DotsLoader />
                                        <p className="text-sm text-ink-muted">Chargement…</p>
                                    </div>
                                ) : (
                                    <AmortissementTable
                                        lignes={amortissementPlan?.lignes || []}
                                        vncActuelle={amortissementPlan?.vncActuelle}
                                    />
                                )}
                            </Section>
                        )}

                        <Section title="Historique des mouvements">
                            <div className="flex items-center justify-between gap-3 mb-3">
                                <span className="text-xs text-ink-muted">{mouvements.length} mouvement(s)</span>
                                {isAdmin() && viewingImmo.statut === 'ACTIF' && (
                                    <button onClick={() => setIsMouvementModalOpen(true)} className="btn-brand-soft btn-sm">
                                        <ArrowRightLeft size={12} />
                                        Nouveau
                                    </button>
                                )}
                            </div>
                            {loadingMouvements ? (
                                <div className="empty-state bg-surface-2 dark:bg-slate-800/50 rounded-panel border border-dashed border-line-strong py-10">
                                    <DotsLoader />
                                    <p className="text-sm text-ink-muted">Chargement…</p>
                                </div>
                            ) : (
                                <MouvementHistory mouvements={mouvements} />
                            )}
                        </Section>

                        <div className="flex flex-wrap justify-between gap-3 pt-4 border-t border-line">
                            <ExportButton
                                onExportPdf={() => exportService.exportFicheImmobilisationPdf(viewingImmo.id)}
                                label="Exporter la fiche"
                            />
                            <div className="flex gap-3">
                                <button onClick={() => setIsDetailsModalOpen(false)} className="btn-secondary btn-md">
                                    Fermer
                                </button>
                                {isAdmin() && (
                                    <button
                                        onClick={() => {
                                            setIsDetailsModalOpen(false);
                                            handleOpenEdit(viewingImmo);
                                        }}
                                        className="btn-primary btn-md"
                                    >
                                        <Pencil size={14} />
                                        Modifier
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                )}
            </Modal>

            {/* ---------- Création / modification ---------- */}
            {isAdmin() && (
                <Modal
                    isOpen={isModalOpen}
                    onClose={() => setIsModalOpen(false)}
                    title={editingImmo ? "Modifier l'immobilisation" : 'Nouvelle immobilisation'}
                    subtitle={
                        editingImmo
                            ? 'Mettez à jour la fiche, les valeurs ou le rattachement du bien.'
                            : 'Renseignez le bien : il recevra son code unique à l’enregistrement.'
                    }
                    icon={Package}
                    size="lg"
                    footer={
                        <FormFooter
                            embedded
                            form="immobilisation-form"
                            onCancel={() => setIsModalOpen(false)}
                            submitLabel={editingImmo ? 'Enregistrer' : 'Créer l’immobilisation'}
                            submitIcon={editingImmo ? Save : Plus}
                            hint="Les champs marqués * sont obligatoires."
                        />
                    }
                >
                    <form id="immobilisation-form" onSubmit={handleSubmit} className="space-y-6">
                        {/* En-tête fiche — la même grammaire que la modale de
                            détail : tuile d'icône, badges, titre, code en chip
                            mono NEUTRE (un code n'est jamais vert). */}
                        {editingImmo && (
                            <div className="flex items-start gap-3.5 pb-4 border-b border-line">
                                <div className="w-12 h-12 rounded-control bg-surface-3 flex items-center justify-center shrink-0">
                                    <Package size={20} className="text-ink-muted" />
                                </div>
                                <div className="min-w-0 flex-1">
                                    <div className="flex flex-wrap gap-2 mb-2">
                                        <StatutBadge statut={formData.statut || editingImmo.statut} />
                                        <Badge
                                            tone={etatMeta(formData.etatPhysique || editingImmo.etatPhysique).tone}
                                            label={etatMeta(formData.etatPhysique || editingImmo.etatPhysique).label}
                                            dot={false}
                                        />
                                    </div>
                                    <h2 className="text-lg font-bold text-ink truncate mb-1.5">
                                        {editingImmo.designation}
                                    </h2>
                                    <span className="inline-flex items-center gap-1.5 text-sm font-semibold mono-code-chip px-3 py-1 rounded-control">
                                        {editingImmo.codeUnique}
                                    </span>
                                </div>
                            </div>
                        )}

                        {/* En création : pas d'identité encore — la même
                            grammaire, avec l'info utile sur le code. */}
                        {!editingImmo && (
                            <div className="flex items-start gap-3.5 pb-4 border-b border-line">
                                <div className="w-12 h-12 rounded-control bg-surface-3 flex items-center justify-center shrink-0">
                                    <Package size={20} className="text-ink-muted" />
                                </div>
                                <div className="min-w-0 flex-1 pt-1.5">
                                    <p className="section-label mb-1">Code unique</p>
                                    <p className="text-sm text-ink-muted leading-relaxed">
                                        Attribué automatiquement à l'enregistrement.
                                    </p>
                                </div>
                            </div>
                        )}

                        <Section title="Photo du bien">
                            <div className="flex items-center gap-4">
                                {formData.photo ? (
                                    <div className="relative group w-24 h-24">
                                        <div className="w-full h-full rounded-card overflow-hidden border border-line shadow-card">
                                            <img
                                                src={getPhotoUrl(formData.photo)}
                                                alt="Aperçu"
                                                className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                                            />
                                        </div>
                                        <button
                                            type="button"
                                            onClick={() => setFormData((p) => ({ ...p, photo: null }))}
                                            className="absolute -top-2 -right-2 w-6 h-6 rounded-full bg-danger-500 text-white flex items-center justify-center shadow-card hover:bg-danger-600 transition"
                                            aria-label="Retirer la photo"
                                        >
                                            <X size={12} />
                                        </button>
                                    </div>
                                ) : (
                                    <label className="w-24 h-24 rounded-panel border-2 border-dashed border-line-strong flex flex-col items-center justify-center cursor-pointer tile-hover">
                                        <Camera size={20} className="text-ink-subtle mb-1" />
                                        <span className="text-xs text-ink-muted">Ajouter</span>
                                        <input type="file" accept="image/*" onChange={handlePhotoUpload} className="hidden" />
                                    </label>
                                )}
                                <div className="text-xs text-ink-muted">
                                    <p>Format JPG, PNG · Max 5 MB</p>
                                    {uploadingPhoto && (
                                        <p className="text-brand-600 dark:text-brand-300 mt-1 animate-soft-pulse">
                                            Chargement…
                                        </p>
                                    )}
                                </div>
                            </div>
                        </Section>

                        <Section title="Informations">
                            <Field
                                icon={Package}
                                label="Désignation"
                                required
                                name="designation"
                                value={formData.designation}
                                onChange={handleChange}
                                error={formErrors.designation}
                            />

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
                                <Field icon={Tag} label="Type" required error={formErrors.typeMateriel}>
                                    <select
                                        id="typeMateriel"
                                        name="typeMateriel"
                                        value={formData.typeMateriel}
                                        onChange={handleChange}
                                        className="input"
                                    >
                                        {TYPES_MATERIEL.map((t) => (
                                            <option key={t} value={t}>{t}</option>
                                        ))}
                                    </select>
                                </Field>
                                <Field
                                    icon={Store}
                                    label="Marque"
                                    required
                                    name="marque"
                                    value={formData.marque}
                                    onChange={handleChange}
                                    error={formErrors.marque}
                                />
                            </div>

                            <div className="mt-4">
                                <Field
                                    icon={AlignLeft}
                                    label="Description"
                                    as="textarea"
                                    name="description"
                                    value={formData.description}
                                    onChange={handleChange}
                                    rows={2}
                                    className="resize-none"
                                />
                            </div>
                        </Section>

                        <Section title="Dates et valeurs">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <Field
                                    icon={CalendarClock}
                                    label="Date d'acquisition"
                                    required
                                    type="date"
                                    name="dateAcquisition"
                                    value={formData.dateAcquisition}
                                    onChange={handleChange}
                                    error={formErrors.dateAcquisition}
                                />
                                <Field
                                    icon={CalendarCheck}
                                    label="Date mise en service"
                                    required
                                    type="date"
                                    name="dateMiseService"
                                    value={formData.dateMiseService}
                                    onChange={handleChange}
                                    error={formErrors.dateMiseService}
                                />
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
                                <Field
                                    icon={Banknote}
                                    label="Valeur (Ar)"
                                    required
                                    type="number"
                                    name="valeurAchat"
                                    value={formData.valeurAchat}
                                    onChange={handleChange}
                                    step="0.01"
                                    error={formErrors.valeurAchat}
                                />
                                <Field
                                    icon={Percent}
                                    label="Taux (%)"
                                    required
                                    type="number"
                                    name="tauxAmortissement"
                                    value={formData.tauxAmortissement}
                                    onChange={handleChange}
                                    step="0.01"
                                    error={formErrors.tauxAmortissement}
                                />
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
                                <Field icon={Activity} label="État physique" required>
                                    <select
                                        id="etatPhysique"
                                        name="etatPhysique"
                                        value={formData.etatPhysique}
                                        onChange={handleChange}
                                        className="input"
                                    >
                                        {ETATS_PHYSIQUES.map((e) => (
                                            <option key={e.value} value={e.value}>{e.label}</option>
                                        ))}
                                    </select>
                                </Field>
                                <Field
                                    icon={FileText}
                                    label="N° Facture"
                                    name="facture"
                                    value={formData.facture}
                                    onChange={handleChange}
                                />
                            </div>
                        </Section>

                        <Section title="Affectation">
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <Field icon={Building2} label="Agence" required={!editingImmo} error={formErrors.agenceId}>
                                    <select
                                        id="agenceId"
                                        name="agenceId"
                                        value={formData.agenceId}
                                        onChange={handleChange}
                                        disabled={!!editingImmo}
                                        className={`input ${formErrors.agenceId ? 'input-error' : ''}`}
                                    >
                                        <option value="">-- Sélectionner --</option>
                                        {agences.map((ag) => (
                                            <option key={ag.id} value={ag.id}>
                                                {ag.codeAgence} - {ag.nomAgence}
                                            </option>
                                        ))}
                                    </select>
                                </Field>
                                <Field icon={Briefcase} label="Service" required={!editingImmo} error={formErrors.serviceId}>
                                    <select
                                        id="serviceId"
                                        name="serviceId"
                                        value={formData.serviceId}
                                        onChange={handleChange}
                                        disabled={!!editingImmo}
                                        className={`input ${formErrors.serviceId ? 'input-error' : ''}`}
                                    >
                                        <option value="">-- Sélectionner --</option>
                                        {services.map((s) => (
                                            <option key={s.id} value={s.id}>
                                                {s.code} - {s.nomService}
                                            </option>
                                        ))}
                                    </select>
                                </Field>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
                                <Field icon={Tag} label="Catégorie" required error={formErrors.categorieId}>
                                    <select
                                        id="categorieId"
                                        name="categorieId"
                                        value={formData.categorieId}
                                        onChange={handleCategorieChange}
                                        className={`input ${formErrors.categorieId ? 'input-error' : ''}`}
                                    >
                                        <option value="">-- Sélectionner --</option>
                                        {categories.map((c) => (
                                            <option key={c.id} value={c.id}>
                                                {c.codeComptable} - {c.nomCategorie}
                                            </option>
                                        ))}
                                    </select>
                                </Field>
                                <Field icon={Store} label="Fournisseur">
                                    <select
                                        id="fournisseurId"
                                        name="fournisseurId"
                                        value={formData.fournisseurId}
                                        onChange={handleChange}
                                        className="input"
                                    >
                                        <option value="">-- Aucun --</option>
                                        {fournisseurs.map((f) => (
                                            <option key={f.id} value={f.id}>{f.nomFournisseur}</option>
                                        ))}
                                    </select>
                                </Field>
                            </div>

                            {editingImmo && (
                                <div className="mt-4">
                                    <Field icon={ShieldCheck} label="Statut">
                                        <select
                                            id="statut"
                                            name="statut"
                                            value={formData.statut}
                                            onChange={handleChange}
                                            className="input"
                                        >
                                            {STATUTS.map((s) => (
                                                <option key={s.value} value={s.value}>{s.label}</option>
                                            ))}
                                        </select>
                                    </Field>
                                </div>
                            )}
                        </Section>
                    </form>
                </Modal>
            )}

            {isAdmin() && (
                <MouvementModal
                    isOpen={isMouvementModalOpen}
                    onClose={() => setIsMouvementModalOpen(false)}
                    immobilisation={viewingImmo}
                    onSuccess={handleMouvementSuccess}
                />
            )}

            <ConfirmDialog
                isOpen={deleteId !== null}
                onClose={() => setDeleteId(null)}
                onConfirm={handleDelete}
                title="Confirmer la suppression"
                message="Êtes-vous sûr de vouloir supprimer cette immobilisation ? Cette action est irréversible."
                confirmLabel="Supprimer"
            />

            {toast && (
                <Toast
                    type={toast.type}
                    message={toast.message}
                    duration={toast.duration}
                    onClose={() => setToast(null)}
                />
            )}
        </div>
    );
};

export default GestionImmobilisations;

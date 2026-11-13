import { useState, useEffect, useMemo } from 'react';
import {
    Inbox, History, CheckCircle, XCircle, Eye, Plus, Clock, Package,
    UserPlus, ArrowRight, Search, User, CalendarDays, AlignLeft, AlertCircle,
    Hash, Send, Tags,
} from 'lucide-react';
import DetailItem from '../../components/ui/DetailItem';
import demandeService from '../../services/demandeService';
import categorieService from '../../services/categorieService';
import serviceService from '../../services/serviceService';
import exportService from '../../services/exportService';
import { useAuth } from '../../hooks/useAuth';
import Modal from '../../components/ui/Modal';
import ConfirmDialog from '../../components/ui/ConfirmDialog';
import Pagination from '../../components/ui/Pagination';
import ExportButton from '../../components/ui/ExportButton';
import DataToolbar, { FilterSelect } from '../../components/ui/DataToolbar';
import ResponsiveTable, { RowAction, TableCard } from '../../components/ui/ResponsiveTable';
import Badge, { DemandeBadge, PrioriteBadge } from '../../components/ui/Badge';
import { TYPES_MOUVEMENT } from '../../theme/toneStyles';
import Field, { FieldGroup, FormFooter } from '../../components/ui/Field';
import Section from '../../components/ui/Section';
import { EmptyState } from '../../components/ui/Surface';
import { MiniStat } from '../../components/dashboard/StatCard';
import { TableSkeleton } from '../../components/ui/Skeleton';
import Toast from '../../components/ui/Toast';
import { usePagination } from '../../hooks/usePagination';
import { signalerDemandeChangee } from '../../hooks/useDemandeBadge';
import { souscrire } from '../../services/realtime';

const PAR_PAGE = 8;

const FILTRES_STATUT = [
    { value: '', label: 'Tous les statuts' },
    { value: 'EN_ATTENTE', label: 'En attente' },
    { value: 'APPROUVEE', label: 'Approuvées' },
    { value: 'REJETTEE', label: 'Rejetées' },
    { value: 'ANNULEE', label: 'Annulées' },
];

const FORM_VIDE = {
    designation: '',
    motif: '',
    quantite: 1,
    categorieId: '',
    priorite: 'NORMALE',
    besoinLe: '',
};

/** Message serveur exploitable : `message` métier, sinon les erreurs de champ. */
const erreurDe = (err, repli) => {
    const data = err?.response?.data;
    if (!data) return repli;
    if (typeof data.message === 'string') return data.message;
    if (data.errors) return Object.values(data.errors).join(' • ');
    return repli;
};

const GestionDemandes = () => {
    const { isAdmin } = useAuth();
    const admin = isAdmin();

    const [demandes, setDemandes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [toast, setToast] = useState(null);
    const [keyword, setKeyword] = useState('');
    const [filterStatut, setFilterStatut] = useState('');

    // Modale de création
    const [creerOuverte, setCreerOuverte] = useState(false);
    const [form, setForm] = useState(FORM_VIDE);
    const [formErreurs, setFormErreurs] = useState({});
    const [envoi, setEnvoi] = useState(false);
    const [categories, setCategories] = useState([]);

    // Modale de détail
    const [detail, setDetail] = useState(null);

    // Modale de décision (ADMIN)
    const [decision, setDecision] = useState(null); // demande visée
    const [formDecision, setFormDecision] = useState({
        verdict: 'APPROUVEE',
        commentaire: '',
        immobilisationId: null,
        destinationServiceId: '',
    });
    const [decisionErreur, setDecisionErreur] = useState('');

    // Biens livrables : l'administrateur choisit lequel remettre au lieu de saisir
    // un code à l'aveugle — le serveur refuserait de toute façon un bien non livrable.
    const [candidats, setCandidats] = useState([]);
    const [rechercheCandidat, setRechercheCandidat] = useState('');
    const [candidatsChargement, setCandidatsChargement] = useState(false);
    const [bienChoisi, setBienChoisi] = useState(null);
    const [services, setServices] = useState([]);

    // Confirmation d'annulation
    const [aAnnuler, setAAnnuler] = useState(null);

    /**
     * Chargement sans état posé de façon synchrone : toutes les mutations d'état
     * arrivent après l'attente, ce qui rend l'appel depuis un effet inoffensif
     * (aucun rendu cascadé au montage).
     */
    const charger = async () => {
        try {
            const liste = await demandeService.getAll();
            setDemandes(liste);
            setError('');
        } catch (err) {
            setError(erreurDe(err, 'Erreur lors du chargement des demandes'));
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    /** Réactive le squelette — réservé au bouton « actualiser » (événement, pas effet). */
    const recharger = async () => {
        setLoading(true);
        await charger();
    };

    useEffect(() => {
        let vivant = true;
        (async () => {
            try {
                const liste = await demandeService.getAll();
                if (!vivant) return;
                setDemandes(liste);
                setError('');
            } catch (err) {
                if (!vivant) return;
                setError(erreurDe(err, 'Erreur lors du chargement des demandes'));
                console.error(err);
            } finally {
                if (vivant) setLoading(false);
            }
        })();
        return () => {
            vivant = false;
        };
    }, []);

    /**
     * Temps réel : une demande créée ou décidée par un autre utilisateur
     * apparaît (ou change de statut) sans actualisation. `charger` ne pose
     * aucun état de façon synchrone et ne dépend d'aucune prop : le closure du
     * premier rendu reste valable pour tout l'abonnement.
     *
     * À la (re)connexion, on recharge aussi — les événements survenus pendant
     * la coupure ont été manqués.
     */
    useEffect(() => {
        const stopDemandes = souscrire('demandes', () => charger());
        const stopConnexion = souscrire('connexion', (evt) => {
            if (evt?.etat === 'connecte') charger();
        });
        return () => {
            stopDemandes();
            stopConnexion();
        };
    }, []);

    const filtreEnMemoire = demandes.filter((d) => {
        const q = keyword.trim().toLowerCase();
        const concordanceMotCle =
            q === '' ||
            d.reference?.toLowerCase().includes(q) ||
            d.designation?.toLowerCase().includes(q) ||
            d.motif?.toLowerCase().includes(q) ||
            d.demandeurNom?.toLowerCase().includes(q);
        const concordanceStatut = filterStatut === '' || d.statut === filterStatut;
        return concordanceMotCle && concordanceStatut;
    });

    const { currentItems, currentPage, totalPages, totalItems, handlePageChange } =
        usePagination(filtreEnMemoire, PAR_PAGE);

    const compteurs = useMemo(
        () => ({
            total: demandes.length,
            enAttente: demandes.filter((d) => d.statut === 'EN_ATTENTE').length,
            approuvees: demandes.filter((d) => d.statut === 'APPROUVEE').length,
            rejettees: demandes.filter((d) => d.statut === 'REJETTEE').length,
            annulees: demandes.filter((d) => d.statut === 'ANNULEE').length,
        }),
        [demandes]
    );

    // ============================================
    // CRÉATION
    // ============================================

    const ouvrirCreation = async () => {
        if (categories.length === 0) {
            try {
                setCategories(await categorieService.getAll());
            } catch (err) {
                console.error(err);
            }
        }
        setForm(FORM_VIDE);
        setFormErreurs({});
        setCreerOuverte(true);
    };

    const valider = () => {
        const errs = {};
        if (!form.designation.trim()) errs.designation = 'La désignation est obligatoire';
        else if (form.designation.trim().length > 150)
            errs.designation = '150 caractères maximum';
        if (!form.motif.trim()) errs.motif = 'Le motif est obligatoire : il explique le besoin';
        if (!Number.isInteger(Number(form.quantite)) || Number(form.quantite) < 1)
            errs.quantite = 'La quantité doit être au moins égale à 1';
        setFormErreurs(errs);
        return Object.keys(errs).length === 0;
    };

    const envoyerDemande = async (e) => {
        e.preventDefault();
        if (!valider()) return;

        setEnvoi(true);
        try {
            await demandeService.create({
                designation: form.designation.trim(),
                motif: form.motif.trim(),
                quantite: Number(form.quantite),
                categorieId: form.categorieId ? Number(form.categorieId) : null,
                priorite: form.priorite,
                besoinLe: form.besoinLe || null,
            });
            setCreerOuverte(false);
            setToast({ type: 'success', message: 'Demande envoyée à l’administrateur' });
            await charger();
            signalerDemandeChangee();
        } catch (err) {
            setFormErreurs({});
            setToast({ type: 'error', message: erreurDe(err, 'Envoi impossible') });
        } finally {
            setEnvoi(false);
        }
    };

    // ============================================
    // DÉCISION (ADMIN)
    // ============================================

    const ouvrirDecision = async (d, verdict) => {
        if (services.length === 0) {
            try {
                setServices((await serviceService.getAll()).filter((s) => s.actif));
            } catch (err) {
                console.error(err);
            }
        }
        setRechercheCandidat('');
        setBienChoisi(null);
        setCandidatsChargement(true);
        setDecision(d);
        setFormDecision({
            verdict,
            commentaire: d.commentaireTraitement || '',
            immobilisationId: null,
            // Pré-rempli du service de la demande : c'est l'adresse de livraison.
            // Vide pour un employé non affecté, où le choix doit être explicite.
            destinationServiceId: d.serviceId ?? '',
        });
        setDecisionErreur('');
    };

    /**
     * Biens livrables — rechargés à chaque ouverture et à chaque frappe.
     *
     * Le choix n'est qu'une intention : entre-temps un bien peut avoir été cédé,
     * rebuté, ou remis sur une autre demande. Le serveur revalide au moment de la
     * décision, et la liste est relue ensuite — un code affiché ici peut avoir
     * été réémis depuis par un transfert.
     *
     * L'indicateur de chargement est posé par l'événement qui déclenche la
     * requête (ouverture, frappe), pas par l'effet.
     */
    useEffect(() => {
        if (!decision) return undefined;
        let vivant = true;
        const minuteur = setTimeout(async () => {
            try {
                const liste = await demandeService.getCandidats({
                    categorieId: decision.categorieId,
                    q: rechercheCandidat.trim() || undefined,
                });
                if (vivant) setCandidats(liste);
            } catch (err) {
                if (vivant) setCandidats([]);
                console.error(err);
            } finally {
                if (vivant) setCandidatsChargement(false);
            }
        }, 250);
        return () => {
            vivant = false;
            clearTimeout(minuteur);
        };
    }, [decision, rechercheCandidat]);

    const choisirBien = (c) => {
        setBienChoisi(c);
        setFormDecision({ ...formDecision, immobilisationId: c.id });
    };

    const retirerBien = () => {
        setBienChoisi(null);
        setFormDecision({ ...formDecision, immobilisationId: null });
    };

    const confirmerDecision = async (e) => {
        e?.preventDefault?.();
        const refus = formDecision.verdict === 'REJETTEE';

        if (refus && !formDecision.commentaire.trim()) {
            setDecisionErreur('Le motif du refus est obligatoire.');
            return;
        }
        // Sans adresse, la livraison n'a nulle part où aller : le serveur refuse de
        // toute façon, autant le dire ici.
        if (!refus && formDecision.immobilisationId && formDecision.destinationServiceId === '') {
            setDecisionErreur('Choisissez le service de destination du bien.');
            return;
        }

        setEnvoi(true);
        try {
            await demandeService.decider(
                decision.id,
                formDecision.verdict,
                formDecision.commentaire.trim() || null,
                formDecision.immobilisationId,
                formDecision.destinationServiceId ? Number(formDecision.destinationServiceId) : null
            );
            setDecision(null);
            setToast({
                type: 'success',
                message:
                    formDecision.verdict === 'REJETTEE'
                        ? 'Demande refusée, motif transmis au demandeur'
                        : formDecision.immobilisationId
                          ? 'Bien remis : le service est mis à jour et le mouvement est enregistré'
                          : 'Demande approuvée',
            });
            await charger();
            signalerDemandeChangee();
        } catch (err) {
            setDecisionErreur(erreurDe(err, 'Décision impossible'));
        } finally {
            setEnvoi(false);
        }
    };

    // ============================================
    // ANNULATION (demandeur)
    // ============================================

    const confirmerAnnulation = async () => {
        if (!aAnnuler) return;
        try {
            await demandeService.annuler(aAnnuler.id);
            setAAnnuler(null);
            setToast({ type: 'info', message: 'Demande retirée' });
            await charger();
            signalerDemandeChangee();
        } catch (err) {
            setAAnnuler(null);
            setToast({ type: 'error', message: erreurDe(err, 'Annulation impossible') });
        }
    };

    // ============================================
    // RENDU
    // ============================================

    /**
     * Ce qui va se passer, annoncé avant le clic.
     *
     * Un bien déjà dans le service de destination ne bouge pas : on livre, on
     * n'a pas à réémettre un code ni à invalider un QR imprimés pour rien.
     */
    const destinationChoisie =
        services.find((s) => s.id === Number(formDecision.destinationServiceId)) || null;
    const memeService =
        bienChoisi != null && destinationChoisie != null && bienChoisi.serviceId === destinationChoisie.id;
    const typeLivraison = memeService ? 'AFFECTATION' : 'TRANSFERT';

    const date = (v, avecHeure = true) =>
        v
            ? new Date(v).toLocaleDateString('fr-FR', {
                  day: '2-digit',
                  month: '2-digit',
                  year: 'numeric',
                  ...(avecHeure ? { hour: '2-digit', minute: '2-digit' } : {}),
              })
            : '—';

    const COLONNES = [
        {
            key: 'demande',
            label: 'Demande',
            primary: true,
            render: (d) => (
                <div className="min-w-0">
                    <p className="text-sm font-medium text-ink truncate">{d.designation}</p>
                    <p className="text-xs mono-code truncate">
                        {d.reference}
                    </p>
                </div>
            ),
        },
        {
            key: 'statut',
            label: 'Statut',
            render: (d) => <DemandeBadge statut={d.statut} />,
        },
        {
            key: 'priorite',
            label: 'Priorité',
            render: (d) => <PrioriteBadge priorite={d.priorite} />,
        },
        ...(admin
            ? [
                  {
                      key: 'demandeur',
                      label: 'Demandeur',
                      primary: true,
                      render: (d) => (
                          <div className="min-w-0">
                              <p className="text-sm font-medium text-ink truncate">
                                  {d.demandeurNom || '—'}
                              </p>
                              <p className="text-xs text-ink-muted truncate">{d.serviceAffiche}</p>
                          </div>
                      ),
                  },
              ]
            : []),
        {
            key: 'besoin',
            label: 'Détail',
            hideBelow: 'md',
            render: (d) => (
                <span className="text-sm text-ink-muted">
                    {d.quantite} × {d.categorieNom || 'catégorie non précisée'}
                    {d.besoinLe ? ` · besoin le ${date(d.besoinLe, false)}` : ''}
                </span>
            ),
        },
        {
            key: 'date',
            label: 'Déposée le',
            hideBelow: 'lg',
            render: (d) => <span className="text-sm text-ink-muted">{date(d.dateCreation)}</span>,
        },
        {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            actions: true,
            render: (d) => (
                <div className="flex items-center justify-end gap-1">
                    <RowAction icon={Eye} label="Voir le détail" onClick={() => setDetail(d)} variant="brand" />
                    {admin && d.statut === 'EN_ATTENTE' && (
                        <>
                            <RowAction
                                icon={CheckCircle}
                                label="Approuver"
                                onClick={() => ouvrirDecision(d, 'APPROUVEE')}
                                variant="edit"
                            />
                            <RowAction
                                icon={XCircle}
                                label="Refuser"
                                onClick={() => ouvrirDecision(d, 'REJETTEE')}
                                variant="delete"
                            />
                        </>
                    )}
                    {!admin && d.statut === 'EN_ATTENTE' && (
                        <RowAction icon={History} label="Retirer" onClick={() => setAAnnuler(d)} variant="delete" />
                    )}
                </div>
            ),
        },
    ];

    return (
        <div className="space-y-5">
            {error && <div className="alert-danger alert">{error}</div>}

            {/* Compteurs */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                <MiniStat icon={Inbox} label="Total" value={compteurs.total} tone="neutral" />
                <MiniStat icon={Clock} label="En attente" value={compteurs.enAttente} tone="warning" />
                <MiniStat icon={CheckCircle} label="Approuvées" value={compteurs.approuvees} tone="brand" />
                <MiniStat icon={XCircle} label="Rejetées" value={compteurs.rejettees} tone="danger" />
                <MiniStat icon={History} label="Annulées" value={compteurs.annulees} tone="quiet" />
            </div>

            <DataToolbar
                keyword={keyword}
                onKeywordChange={setKeyword}
                onSearch={() => {}}
                placeholder={
                    admin
                        ? 'Rechercher par référence, objet ou demandeur…'
                        : 'Rechercher par référence ou objet…'
                }
                loading={loading}
                onRefresh={recharger}
                filters={
                    <FilterSelect
                        label="Statut"
                        value={filterStatut}
                        onChange={setFilterStatut}
                        options={FILTRES_STATUT}
                    />
                }
                activeFilterCount={filterStatut ? 1 : 0}
                secondaryAction={
                    <ExportButton onExportExcel={exportService.exportDemandesExcel} label="Exporter" />
                }
                actionLabel={admin ? undefined : 'Nouvelle demande'}
                actionIcon={Plus}
                onAction={admin ? undefined : ouvrirCreation}
            />

            <TableCard>
                {loading ? (
                    <TableSkeleton rows={6} cols={5} />
                ) : (
                    <>
                        <ResponsiveTable
                            columns={COLONNES}
                            data={currentItems}
                            rowKey={(d) => d.id}
                            empty={
                                <EmptyState
                                    icon={admin ? Inbox : Package}
                                    title={
                                        admin
                                            ? filterStatut || keyword
                                                ? 'Aucune demande trouvée'
                                                : 'Aucune demande'
                                            : filterStatut || keyword
                                              ? 'Aucune demande trouvée'
                                              : 'Aucune demande déposée'
                                    }
                                    message={
                                        filterStatut || keyword
                                            ? 'Aucun résultat pour ces critères.'
                                            : admin
                                              ? 'Les demandes envoyées par les employés apparaîtront ici.'
                                              : 'Exprimez un besoin de matériel : l’administrateur la traitera.'
                                    }
                                />
                            }
                        />
                        <Pagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}
                            totalItems={totalItems}
                            itemsPerPage={PAR_PAGE}
                        />
                    </>
                )}
            </TableCard>

            {/* ---------- Nouvelle demande ---------- */}
            <Modal
                isOpen={creerOuverte}
                onClose={() => setCreerOuverte(false)}
                title="Nouvelle demande d’immobilisation"
                subtitle="Votre demande sera transmise à l’administrateur."
                icon={Send}
                footer={
                    <FormFooter
                        embedded
                        form="demande-form"
                        onCancel={() => setCreerOuverte(false)}
                        onCancelLabel="Annuler"
                        submitLabel="Envoyer la demande"
                        submitIcon={Send}
                        submitting={envoi}
                        hint="Les champs marqués * sont obligatoires."
                    />
                }
            >
                <form id="demande-form" onSubmit={envoyerDemande} noValidate className="space-y-6">
                    <Section title="Objet et motif">
                        <Field
                            icon={Package}
                            label="Objet de la demande"
                            htmlFor="designation"
                            required
                            value={form.designation}
                            onChange={(e) => setForm({ ...form, designation: e.target.value })}
                            placeholder="Ex. : Chaise de bureau ergonomique"
                            maxLength={150}
                            error={formErreurs.designation}
                        />

                        <div className="mt-4">
                            <Field
                                icon={AlignLeft}
                                label="Motif"
                                htmlFor="motif"
                                as="textarea"
                                required
                                rows={3}
                                value={form.motif}
                                onChange={(e) => setForm({ ...form, motif: e.target.value })}
                                placeholder="Pourquoi ce matériel est-il nécessaire ?"
                                maxLength={2000}
                                error={formErreurs.motif}
                            />
                        </div>
                    </Section>

                    <Section
                        title="Détails du matériel"
                        description="Précisez la quantité et l’échéance pour aider à prioriser la demande."
                    >
                        <FieldGroup>
                            <Field
                                icon={Hash}
                                label="Quantité"
                                htmlFor="quantite"
                                type="number"
                                min="1"
                                value={form.quantite}
                                onChange={(e) => setForm({ ...form, quantite: e.target.value })}
                                error={formErreurs.quantite}
                            />

                            <Field icon={AlertCircle} label="Priorité" htmlFor="priorite">
                                <select
                                    id="priorite"
                                    className="input"
                                    value={form.priorite}
                                    onChange={(e) => setForm({ ...form, priorite: e.target.value })}
                                >
                                    <option value="NORMALE">Normale</option>
                                    <option value="URGENTE">Urgente</option>
                                </select>
                            </Field>

                            <Field icon={Tags} label="Catégorie" htmlFor="categorieId">
                                <select
                                    id="categorieId"
                                    className="input"
                                    value={form.categorieId}
                                    onChange={(e) => setForm({ ...form, categorieId: e.target.value })}
                                >
                                    <option value="">Non précisée</option>
                                    {categories.map((c) => (
                                        <option key={c.id} value={c.id}>
                                            {c.nomCategorie}
                                        </option>
                                    ))}
                                </select>
                            </Field>

                            <Field
                                icon={CalendarDays}
                                label="Matériel nécessaire avant le"
                                htmlFor="besoinLe"
                                type="date"
                                hint="Facultatif"
                                value={form.besoinLe}
                                onChange={(e) => setForm({ ...form, besoinLe: e.target.value })}
                            />
                        </FieldGroup>
                    </Section>
                </form>
            </Modal>

            {/* ---------- Détail ---------- */}
            <Modal
                isOpen={Boolean(detail)}
                onClose={() => setDetail(null)}
                title={detail?.reference || 'Détail de la demande'}
                size="md"
            >
                {detail && (
                    <div className="space-y-5">
                        <div className="bg-surface-2 dark:bg-slate-800/60 rounded-panel p-3.5 border border-line">
                            <div className="flex flex-wrap items-center gap-2 mb-2">
                                <DemandeBadge statut={detail.statut} />
                                <PrioriteBadge priorite={detail.priorite} />
                            </div>
                            <p className="text-sm font-semibold text-ink">{detail.designation}</p>
                            <p className="text-xs text-ink-muted mt-1">
                                {detail.quantite} unité{detail.quantite > 1 ? 's' : ''} ·{' '}
                                {detail.categorieNom || 'catégorie non précisée'}
                            </p>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <DetailItem
                                icon={User}
                                label="Demandeur"
                                value={detail.demandeurNom}
                                sub={detail.serviceAffiche}
                            />
                            <DetailItem icon={CalendarDays} label="Déposée le" value={date(detail.dateCreation)} />
                            <DetailItem
                                icon={Clock}
                                label="Matériel nécessaire avant"
                                value={detail.besoinLe ? date(detail.besoinLe, false) : 'Non précisé'}
                            />
                            <DetailItem
                                icon={CheckCircle}
                                label="Traitée le"
                                value={detail.dateTraitement ? date(detail.dateTraitement) : undefined}
                            />
                        </div>

                        <div>
                            <p className="section-label mb-1">Motif</p>
                            <p className="text-sm text-ink whitespace-pre-wrap">{detail.motif}</p>
                        </div>

                        {detail.statut === 'REJETTEE' && detail.commentaireTraitement && (
                            <div className="alert-danger alert">
                                <div>
                                    <p className="font-medium">Motif du refus</p>
                                    <p className="text-sm opacity-90 whitespace-pre-wrap">
                                        {detail.commentaireTraitement}
                                    </p>
                                    {detail.traiteeParNom && (
                                        <p className="text-xs opacity-70 mt-1">
                                            Décision de {detail.traiteeParNom}
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}

                        {detail.statut === 'APPROUVEE' && detail.commentaireTraitement && (
                            <div className="alert-brand alert">
                                <div>
                                    <p className="font-medium">Note de l’administrateur</p>
                                    <p className="text-sm opacity-90 whitespace-pre-wrap">
                                        {detail.commentaireTraitement}
                                    </p>
                                </div>
                            </div>
                        )}

                        {detail.immobilisationId && (
                            <div className="bg-surface-2 dark:bg-slate-800/60 rounded-panel p-3.5 border border-line">
                                <div className="flex flex-wrap items-center justify-between gap-2 mb-1">
                                    <p className="section-label">Bien livré</p>
                                    {detail.mouvementType && (
                                        <Badge
                                            tone={TYPES_MOUVEMENT[detail.mouvementType]?.tone || 'neutral'}
                                            label={
                                                TYPES_MOUVEMENT[detail.mouvementType]?.label ||
                                                detail.mouvementType
                                            }
                                        />
                                    )}
                                </div>
                                <p className="text-sm font-semibold text-ink">
                                    {detail.immobilisationDesignation}
                                </p>
                                <p className="text-xs mono-code">
                                    {detail.immobilisationCode}
                                </p>
                                <p className="text-xs text-ink-muted mt-1.5">
                                    {detail.mouvementType === 'AFFECTATION'
                                        ? 'Remis sans changer de service : la remise est tracée dans l’historique des mouvements.'
                                        : 'Le bien a changé de service : le transfert est tracé dans l’historique des mouvements.'}
                                </p>
                            </div>
                        )}
                    </div>
                )}
            </Modal>

            {/* ---------- Décision (ADMIN) ---------- */}
            <Modal
                isOpen={Boolean(decision)}
                onClose={() => setDecision(null)}
                title={decision ? `Traitement ${decision.reference}` : ''}
                subtitle={decision?.designation}
                size="md"
            >
                {decision && (
                    <form onSubmit={confirmerDecision} noValidate className="space-y-5">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <button
                                type="button"
                                onClick={() =>
                                    setFormDecision({ ...formDecision, verdict: 'APPROUVEE' })
                                }
                                className={`btn btn-md w-full justify-start border ${
                                    formDecision.verdict === 'APPROUVEE'
                                        ? 'bg-brand-50 dark:bg-brand-500/15 border-brand-500 text-brand-700 dark:text-brand-300'
                                        : 'bg-surface text-ink-muted border-line hover:bg-surface-3'
                                }`}
                            >
                                <CheckCircle size={17} />
                                Approuver
                            </button>
                            <button
                                type="button"
                                onClick={() =>
                                    setFormDecision({ ...formDecision, verdict: 'REJETTEE' })
                                }
                                className={`btn btn-md w-full justify-start border ${
                                    formDecision.verdict === 'REJETTEE'
                                        ? 'bg-danger-50 dark:bg-danger-500/15 border-danger-500 text-danger-700 dark:text-danger-300'
                                        : 'bg-surface text-ink-muted border-line hover:bg-surface-3'
                                }`}
                            >
                                <XCircle size={17} />
                                Refuser
                            </button>
                        </div>

                        <div>
                            <label htmlFor="commentaireDecision" className="field-label">
                                {formDecision.verdict === 'REJETTEE' ? (
                                    <>
                                        Motif du refus
                                        <span className="text-danger-500 ml-0.5">*</span>
                                    </>
                                ) : (
                                    'Note (facultative)'
                                )}
                            </label>
                            <textarea
                                id="commentaireDecision"
                                className={`input ${decisionErreur ? 'input-error' : ''}`}
                                rows={3}
                                value={formDecision.commentaire}
                                onChange={(e) =>
                                    setFormDecision({ ...formDecision, commentaire: e.target.value })
                                }
                                placeholder={
                                    formDecision.verdict === 'REJETTEE'
                                        ? 'Expliquez pourquoi la demande ne peut être satisfaite.'
                                        : 'Précisions utiles pour le demandeur.'
                                }
                                maxLength={2000}
                            />
                            {decisionErreur && (
                                <p className="field-error" role="alert">{decisionErreur}</p>
                            )}
                            <p className="text-xs text-ink-muted mt-1.5">
                                Le motif du refus est transmis au demandeur et obligatoire.
                            </p>
                        </div>

                        {formDecision.verdict === 'APPROUVEE' && (
                            <div className="space-y-4">
                                {/* Adresse de livraison : figée à la demande, sauf employé
                                    non affecté où le choix doit être explicite. */}
                                <Field label="Service de destination" required>
                                    <select
                                        id="destinationService"
                                        className="input"
                                        value={formDecision.destinationServiceId}
                                        onChange={(e) =>
                                            setFormDecision({
                                                ...formDecision,
                                                destinationServiceId: e.target.value,
                                            })
                                        }
                                    >
                                        <option value="">-- Choisir le service --</option>
                                        {services.map((s) => (
                                            <option key={s.id} value={s.id}>
                                                {s.code} — {s.nomService}
                                            </option>
                                        ))}
                                    </select>
                                </Field>
                                {!decision.serviceId && (
                                    <p className="text-xs text-ink-muted -mt-2">
                                        Le demandeur n’est rattaché à aucun service : sans destination
                                        choisie, aucune livraison n’a d’adresse.
                                    </p>
                                )}

                                <div>
                                    <label htmlFor="rechercheCandidat" className="field-label">
                                        Bien remis (facultatif)
                                    </label>
                                    <div className="relative">
                                        <Search
                                            size={15}
                                            className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-subtle pointer-events-none"
                                        />
                                        <input
                                            id="rechercheCandidat"
                                            className="input pl-9"
                                            value={rechercheCandidat}
                                            onChange={(e) => {
                                                setRechercheCandidat(e.target.value);
                                                setCandidatsChargement(true);
                                            }}
                                            placeholder="Code, désignation, marque ou service…"
                                        />
                                    </div>

                                    <ul className="mt-2 max-h-56 overflow-y-auto divide-y divide-line border border-line rounded-panel">
                                        {candidatsChargement && (
                                            <li className="px-3 py-4 text-sm text-ink-muted text-center">
                                                Recherche en cours…
                                            </li>
                                        )}
                                        {!candidatsChargement && candidats.length === 0 && (
                                            <li className="px-3 py-4 text-sm text-ink-muted text-center">
                                                Aucun bien livrable
                                                {rechercheCandidat ? ' pour cette recherche' : ''}.
                                            </li>
                                        )}
                                        {!candidatsChargement &&
                                            candidats.map((c) => {
                                                const actif = c.id === formDecision.immobilisationId;
                                                return (
                                                    <li key={c.id}>
                                                        <button
                                                            type="button"
                                                            onClick={() =>
                                                                actif ? retirerBien() : choisirBien(c)
                                                            }
                                                            className={`w-full text-left px-3 py-2.5 flex items-start gap-2.5 transition ${
                                                                actif
                                                                    ? 'bg-brand-50 dark:bg-brand-500/15'
                                                                    : 'hover:bg-surface-2 dark:hover:bg-slate-800/60'
                                                            }`}
                                                        >
                                                            <span className="mt-0.5 shrink-0">
                                                                {actif ? (
                                                                    <CheckCircle
                                                                        size={15}
                                                                        className="text-brand-600 dark:text-brand-300"
                                                                    />
                                                                ) : (
                                                                    <Package size={15} className="text-ink-subtle" />
                                                                )}
                                                            </span>
                                                            <span className="min-w-0">
                                                                <span className="block text-sm font-medium text-ink truncate">
                                                                    {c.designation}
                                                                    {c.marque ? ` — ${c.marque}` : ''}
                                                                </span>
                                                                <span className="block text-xs mono-code truncate">
                                                                    {c.codeUnique}
                                                                </span>
                                                                <span className="block text-xs text-ink-muted truncate">
                                                                    {c.serviceNom}
                                                                    {c.etatPhysique
                                                                        ? ` · ${c.etatPhysique.toLowerCase()}`
                                                                        : ''}
                                                                </span>
                                                            </span>
                                                        </button>
                                                    </li>
                                                );
                                            })}
                                    </ul>
                                    <p className="text-xs text-ink-muted mt-1.5">
                                        Un seul bien est remis à la fois : la quantité demandée reste
                                        informative.
                                    </p>
                                </div>

                                {/* Ce qui va réellement se passer, annoncé avant le clic. */}
                                {bienChoisi && destinationChoisie && (
                                    <div
                                        className={`rounded-panel border p-3.5 flex items-start gap-2.5 ${
                                            memeService
                                                ? 'bg-surface-2 dark:bg-slate-800/60 border-line'
                                                : 'bg-brand-50 dark:bg-brand-500/10 border-brand-200 dark:border-brand-500/30'
                                        }`}
                                    >
                                        <span className="mt-0.5 shrink-0">
                                            {memeService ? (
                                                <UserPlus
                                                    size={16}
                                                    className="text-brand-600 dark:text-brand-300"
                                                />
                                            ) : (
                                                <ArrowRight size={16} className="text-ink-subtle" />
                                            )}
                                        </span>
                                        <span className="text-sm text-ink">
                                            {bienChoisi.serviceNom}{' '}
                                            <span className="text-ink-subtle">→</span>{' '}
                                            <span className="font-semibold">{destinationChoisie.nomService}</span>
                                            <span className="block text-xs text-ink-muted mt-0.5">
                                                {typeLivraison === 'AFFECTATION'
                                                    ? 'Affectation : le bien ne bouge pas de service, seule la remise sera tracée.'
                                                    : 'Transfert : le service change, le code unique est réémis et le QR code invalidé.'}
                                            </span>
                                        </span>
                                    </div>
                                )}
                            </div>
                        )}

                        <FormFooter
                            onCancel={() => setDecision(null)}
                            onCancelLabel="Fermer"
                            submitLabel={
                                formDecision.verdict === 'APPROUVEE'
                                    ? 'Confirmer l’approbation'
                                    : 'Confirmer le refus'
                            }
                            submitting={envoi}
                            danger={formDecision.verdict === 'REJETTEE'}
                        />
                    </form>
                )}
            </Modal>

            {/* ---------- Confirmation d'annulation ---------- */}
            <ConfirmDialog
                isOpen={Boolean(aAnnuler)}
                onClose={() => setAAnnuler(null)}
                onConfirm={confirmerAnnulation}
                title="Retirer la demande ?"
                message={
                    aAnnuler
                        ? `La demande ${aAnnuler.reference} sera annulée et ne sera pas traitée par l’administrateur.`
                        : ''
                }
                confirmLabel="Retirer"
            />

            {toast && (
                <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
            )}
        </div>
    );
};

export default GestionDemandes;

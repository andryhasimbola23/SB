import {
    PieChart, Pie, Cell, ResponsiveContainer, Tooltip,
    BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import { useTheme } from '../../hooks/useTheme';

/**
 * Palette volontairement restreinte : vert de marque → neutre clair.
 *
 * Aucun rouge ni ambre ici : ces deux teintes portent un sens (danger, alerte)
 * et une simple catégorie ne doit pas en emprunter — l'ancienne palette
 * attribuait le rouge de la marque au deuxième libellé de catégorie. Le dernier
 * palier remplace l'ardoise quasi noire précédente, invisible sur une surface
 * sombre.
 */
const PALETTE = ['#009640', '#16a054', '#3bb975', '#6ecfa0', '#9ee2c3', '#cdeedd'];

const formatMontant = (montant) => {
    if (!montant) return '0 Ar';
    if (montant >= 1000000) return (montant / 1000000).toFixed(1) + ' M Ar';
    if (montant >= 1000) return (montant / 1000).toFixed(0) + ' K Ar';
    return montant + ' Ar';
};

/** Part en pourcentage, à une décimale, au format français (« 60,5 »). */
const pourcentage = (part, total) =>
    total > 0
        ? ((part / total) * 100).toLocaleString('fr-FR', {
              minimumFractionDigits: 1,
              maximumFractionDigits: 1,
          })
        : '0,0';

/** Résolution des couleurs de grille/texte selon le thème actif. */
const useChartTheme = () => {
    const { isDark } = useTheme();
    return {
        grid: isDark ? '#293449' : '#f1f5f9',
        axis: isDark ? '#94a3b8' : '#64748b',
        tooltipBg: isDark ? '#1e293b' : '#ffffff',
        tooltipBorder: isDark ? '#334155' : '#e2e8f0',
        text: isDark ? '#f1f5f9' : '#1e293b',
    };
};

const tooltipStyle = (t) => ({
    borderRadius: '10px',
    border: `1px solid ${t.tooltipBorder}`,
    backgroundColor: t.tooltipBg,
    color: t.text,
    fontSize: '12px',
    boxShadow: '0 4px 12px -4px rgba(0,0,0,0.15)',
});

const EmptyChart = () => (
    <div className="h-64 flex items-center justify-center text-ink-subtle text-sm">
        Aucune donnée disponible
    </div>
);

/**
 * Légende commune aux deux donuts, écrite en HTML.
 *
 * recharts positionne sa propre légende en absolu, sur une hauteur fixe de
 * 36 px — insuffisante pour des libellés de catégories de 38 caractères, qui
 * se replient sur deux ou trois lignes que rien ne réservait et débordent sur
 * le graphique. Ici chaque entrée prend autant de lignes qu'il lui faut, en
 * entier, et la hauteur suit le contenu.
 */
const LegendeDonut = ({ items, colonnes = 'sm:grid-cols-2' }) => (
    <ul className={`grid grid-cols-1 ${colonnes} gap-x-4 gap-y-1`}>
        {items.map((item) => (
            <li key={item.nom} className="flex items-start gap-2 min-w-0">
                <span
                    className="mt-1.5 h-2 w-2 shrink-0 rounded-full"
                    style={{ backgroundColor: item.couleur }}
                />
                <span className="min-w-0 flex-1">
                    <span className="block text-[11px] leading-snug text-ink-muted">
                        {item.nom}
                    </span>
                    <span className="block text-[11px] text-ink-subtle">
                        <span className="font-semibold text-ink">{item.valeur}</span> · {item.part} %
                    </span>
                </span>
            </li>
        ))}
    </ul>
);

/**
 * Donnette — répartition par catégorie.
 *
 * La légende est déléguée à `LegendeDonut` : les libellés de catégories
 * atteignent 38 caractères, ce que recharts ne réservait pas en hauteur.
 *
 * Les chiffres ont quitté les tranches : deux catégories ne comptent que 2
 * biens, ce qui donnait des arcs minuscules dont les étiquettes se
 * superposaient. Le total occupe le trou du donut, le détail descend dans la
 * légende avec le nombre et la part.
 */
export const RepartitionCategorieChart = ({ data }) => {
    const t = useChartTheme();
    if (!data || data.length === 0) return <EmptyChart />;

    const total = data.reduce((somme, d) => somme + (d.nombre || 0), 0);
    const lignes = data.map((d, index) => ({
        ...d,
        couleur: PALETTE[index % PALETTE.length],
        part: pourcentage(d.nombre, total),
    }));

    return (
        <div className="space-y-3">
            <div className="relative">
                <ResponsiveContainer width="100%" height={200}>
                    <PieChart>
                        <Pie
                            data={lignes}
                            dataKey="nombre"
                            nameKey="label"
                            cx="50%"
                            cy="50%"
                            outerRadius={78}
                            innerRadius={46}
                            paddingAngle={2}
                            stroke="none"
                        >
                            {lignes.map((d) => (
                                <Cell key={d.label} fill={d.couleur} />
                            ))}
                        </Pie>
                        <Tooltip
                            formatter={(value, _name, props) => [
                                `${value} bien(s) · ${formatMontant(props.payload.valeur)}`,
                                props.payload.label,
                            ]}
                            contentStyle={tooltipStyle(t)}
                        />
                    </PieChart>
                </ResponsiveContainer>

                {/* Le trou du donut porte le total. */}
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <p className="text-2xl font-bold leading-none text-ink">{total}</p>
                    <p className="section-label mt-1.5">biens</p>
                </div>
            </div>

            <LegendeDonut
                items={lignes.map((d) => ({
                    nom: d.label,
                    couleur: d.couleur,
                    valeur: d.nombre,
                    part: d.part,
                }))}
            />
        </div>
    );
};

/**
 * Barres horizontales — répartition par service.
 *
 * Horizontal parce que les noms de services sont longs : sur un axe vertical ils
 * imposaient une rotation illisible, et l'axe latéral avale la moitié de la
 * largeur. Le sigle (IT, SAG, RH…) occupe donc l'axe des catégories, le nom
 * complet revenant à l'infobulle — on lit le graphique d'un coup d'œil, et on
 * retrouve le détail sans le deviner.
 */
export const RepartitionServiceChart = ({ data }) => {
    const t = useChartTheme();
    if (!data || data.length === 0) return <EmptyChart />;

    // Repli sur le nom si le sigle n'est pas fourni.
    const lignes = data.map((d) => ({ ...d, axe: d.code || d.label }));

    return (
        <ResponsiveContainer width="100%" height={280}>
            <BarChart
                layout="vertical"
                data={lignes}
                margin={{ top: 5, right: 20, left: 10, bottom: 5 }}
            >
                <CartesianGrid strokeDasharray="3 3" stroke={t.grid} horizontal={false} />
                <XAxis
                    type="number"
                    tick={{ fontSize: 11, fill: t.axis }}
                    tickLine={false}
                    axisLine={{ stroke: t.grid }}
                    allowDecimals={false}
                />
                <YAxis
                    type="category"
                    dataKey="axe"
                    width={64}
                    tick={{ fontSize: 11, fill: t.axis }}
                    tickLine={false}
                    axisLine={false}
                />
                <Tooltip
                    formatter={(value, _name, props) => [
                        `${value} bien(s) · ${formatMontant(props.payload.valeur)}`,
                        props.payload.label,
                    ]}
                    contentStyle={tooltipStyle(t)}
                />
                <Bar dataKey="nombre" fill="#009640" radius={[0, 6, 6, 0]} />
            </BarChart>
        </ResponsiveContainer>
    );
};

/**
 * Barres horizontales — valeur du parc par agence (biens actifs).
 *
 * Trié par le serveur du plus valorisé au moins : la lecture descend le long
 * de l'axe, la question « où est l'argent du parc » trouve sa réponse en haut.
 * L'axe des montants porte le format ariary, l'infobulle ajoute le nombre de
 * biens — deux agences proches en valeur se départagent au volume.
 */
export const RepartitionAgenceChart = ({ data }) => {
    const t = useChartTheme();
    if (!data || data.length === 0) return <EmptyChart />;

    // Hauteur suivant le nombre d'agences : au-delà de 6, une hauteur fixe
    // aplatissait les barres jusqu'à l'illisible.
    const hauteur = Math.max(200, Math.min(data.length * 48, 380));

    return (
        <ResponsiveContainer width="100%" height={hauteur}>
            <BarChart
                layout="vertical"
                data={data}
                margin={{ top: 5, right: 20, left: 10, bottom: 5 }}
            >
                <CartesianGrid strokeDasharray="3 3" stroke={t.grid} horizontal={false} />
                <XAxis
                    type="number"
                    tick={{ fontSize: 11, fill: t.axis }}
                    tickLine={false}
                    axisLine={{ stroke: t.grid }}
                    tickFormatter={formatMontant}
                />
                <YAxis
                    type="category"
                    dataKey="label"
                    width={92}
                    tick={{ fontSize: 11, fill: t.axis }}
                    tickLine={false}
                    axisLine={false}
                />
                <Tooltip
                    formatter={(value, _name, props) => [
                        `${formatMontant(value)} · ${props.payload.nombre} bien(s)`,
                        props.payload.label,
                    ]}
                    contentStyle={tooltipStyle(t)}
                />
                <Bar dataKey="valeur" fill="#009640" radius={[0, 6, 6, 0]} />
            </BarChart>
        </ResponsiveContainer>
    );
};

/**
 * Donnut — statut des biens (vert = actif, ambre = cédé, rouge = rebut).
 *
 * Traité à l'identique de la répartition par catégorie : les chiffres ont
 * quitté les tranches, ce qui réglait leur chevauchement — deux statuts ne
 * comptent qu'un bien chacun, soit des arcs de 9° dont les étiquettes se
 * piétinaient mutuellement — et le total occupe le trou du donut. Même
 * gabarit, donc même hauteur que la carte voisine.
 */
export const StatutChart = ({ stats }) => {
    const t = useChartTheme();

    const donnees = [
        { nom: 'Actifs', valeur: stats?.totalActifs || 0, couleur: '#009640' },
        { nom: 'Cédés', valeur: stats?.totalCedes || 0, couleur: '#f59e0b' },
        { nom: 'Rebuts', valeur: stats?.totalRebutes || 0, couleur: '#E30613' },
    ].filter((d) => d.valeur > 0);

    if (donnees.length === 0) return <EmptyChart />;

    const total = donnees.reduce((somme, d) => somme + d.valeur, 0);
    const parts = donnees.map((d) => ({ ...d, part: pourcentage(d.valeur, total) }));

    return (
        <div className="space-y-3">
            <div className="relative">
                <ResponsiveContainer width="100%" height={200}>
                    <PieChart>
                        <Pie
                            data={parts}
                            dataKey="valeur"
                            nameKey="nom"
                            cx="50%"
                            cy="50%"
                            outerRadius={78}
                            innerRadius={46}
                            paddingAngle={2}
                            stroke="none"
                        >
                            {parts.map((d) => (
                                <Cell key={d.nom} fill={d.couleur} />
                            ))}
                        </Pie>
                        <Tooltip
                            formatter={(value, _name, props) => [
                                `${value} bien(s) · ${props.payload.part} %`,
                                props.payload.nom,
                            ]}
                            contentStyle={tooltipStyle(t)}
                        />
                    </PieChart>
                </ResponsiveContainer>

                {/* Le trou du donut porte le total. */}
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <p className="text-2xl font-bold leading-none text-ink">{total}</p>
                    <p className="section-label mt-1.5">biens</p>
                </div>
            </div>

            <LegendeDonut items={parts} colonnes="sm:grid-cols-3" />
        </div>
    );
};

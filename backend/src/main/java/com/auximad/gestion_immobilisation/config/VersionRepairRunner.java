package com.auximad.gestion_immobilisation.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Garantit l'absence de {@code version NULL} au démarrage de l'application.
 *
 * Les trois entités porteuses de {@code @Version} (Immobilisation,
 * DemandeImmobilisation, CampagneInventaire) ont reçu leur colonne `version`
 * après coup, via {@code ddl-auto=update} : les lignes d'origine — tout le jeu
 * de données de démonstration — la portent donc à {@code NULL}. Au premier
 * flush d'une telle ligne, Hibernate tente d'incrémenter ce verrou en
 * déboîtant la {@code Long} nulle et plante :
 *
 *   NullPointerException: Cannot invoke "java.lang.Long.longValue()"
 *                        because "current" is null
 *
 * qui remonte en 500 depuis le commit de la transaction. Une réparation en
 * base manuelle soigne la copie présente, mais rien ne survivrait à une
 * réimportation de jeu de données : c'est ce runner qui rétablit l'invariant
 * à chaque démarrage, avant la première requête.
 *
 * Un {@code @PostLoad} sur les entités ne suffit pas : l'instantané de version
 * comparé au flush est capturé au chargement dans l'{@code EntityEntry}, avant
 * tout callback JPA — corriger le champ après coup laisse l'instantané à
 * {@code NULL} et le flush échoue de la même façon.
 *
 * Les entités créées par l'application depuis F12 ont toujours une version
 * valide (initialisée à 0 à la persistance) : ce bloc ne touche qu'au vestige.
 */
@Component
@RequiredArgsConstructor
public class VersionRepairRunner implements CommandLineRunner {

    /** Les trois tables portant une colonne `version` gérée par JPA. */
    private static final String[] REPARATIONS = {
            "UPDATE immobilisation SET version = 0 WHERE version IS NULL",
            "UPDATE demande_immobilisation SET version = 0 WHERE version IS NULL",
            "UPDATE campagne_inventaire SET version = 0 WHERE version IS NULL"
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        int repares = 0;
        for (String sql : REPARATIONS) {
            repares += jdbcTemplate.update(sql);
        }
        if (repares > 0) {
            System.out.println(">>> " + repares
                    + " ligne(s) sans version renseignée réparée(s) (version = 0).");
        }
    }
}

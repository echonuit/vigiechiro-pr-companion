package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/// Applique les scripts de migration versionnés `src/main/resources/db/migration/V0x__*.sql`
/// et trace les versions appliquées dans la table `schema_version`.
///
/// Au premier lancement, la base est vide : `V01__schema.sql` crée toutes les tables (dont
/// `schema_version`), puis `V02__seed_taxons.sql` insère les données de référence. À la
/// réouverture d'une base existante, les versions déjà présentes sont ignorées (migration
/// idempotente, objectif disponibilité 5.2 : « base présente → réutilisée »).
///
/// Pour ajouter une migration : créer le fichier `V0n__xxx.sql` dans `db/migration/`
/// **et** ajouter son nom à [#MIGRATIONS] (l'ordre fait foi).
public class MigrationSchema {

    /// Migrations appliquées dans l'ordre. Le préfixe `V0n` porte le numéro de version.
    static final String[] MIGRATIONS = {
        "V01__schema.sql",
        "V02__seed_taxons.sql",
        "V03__perf_indexes.sql",
        "V04__groupe_hors_referentiel.sql",
        "V05__seed_taxons_officiels.sql",
        "V06__reparer_souches_referentiel.sql",
        "V07__renommer_median_freq_khz.sql",
        "V08__rattacher_fil_rouge_chiropteres.sql",
        "V09__horodatage_capture_sequence.sql",
        "V10__materiel_micro_passage.sql",
        "V11__saved_filter_view.sql",
        "V12__reglages.sql",
        "V13__observation_manuelle.sql",
        "V14__observation_douteuse.sql",
        "V15__vigiechiro_link.sql",
        "V16__groupe_referentiel_vigiechiro.sql",
        "V17__vigiechiro_link_verrouille.sql",
        "V18__depot_unite.sql",
        "V19__column_layout.sql",
        "V20__duree_reelle_sequences.sql",
        "V21__observation_ancrage_certitude.sql",
        "V22__participation_traitement.sql",
        "V23__empreintes_fichiers.sql",
        "V24__archivage_passage.sql",
        "V25__purge_originaux_declaree.sql",
        "V26__validation_expert.sql",
        "V27__verdict_par_fichier.sql",
        "V28__bascule_lexique_verdict.sql",
        "V29__point_synchronise.sql",
        "V30__depot_plan.sql",
        "V31__retrait_colonnes_mortes_archivage.sql"
    };

    private static final String DOSSIER = "/db/migration/";

    private final SourceDeDonnees source;

    public MigrationSchema(SourceDeDonnees source) {
        this.source = source;
    }

    /// Applique toutes les migrations non encore enregistrées dans `schema_version`.
    public void migrer() {
        Set<Integer> dejaAppliquees = versionsAppliquees();
        for (String fichier : MIGRATIONS) {
            int version = numeroVersion(fichier);
            if (dejaAppliquees.contains(version)) {
                continue;
            }
            executerScript(DOSSIER + fichier);
            enregistrerVersion(version);
        }
    }

    private Set<Integer> versionsAppliquees() {
        Set<Integer> versions = new HashSet<>();
        try (Connection connexion = source.getConnection();
                Statement st = connexion.createStatement();
                ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) {
                versions.add(rs.getInt(1));
            }
        } catch (SQLException tableAbsente) {
            // Premier lancement : la table schema_version n'existe pas encore. Aucune version appliquée.
            return Set.of();
        }
        return versions;
    }

    private void enregistrerVersion(int version) {
        String sql = "INSERT OR IGNORE INTO schema_version(version, applied_at) VALUES (?, ?)";
        try (Connection connexion = source.getConnection();
                PreparedStatement ps = connexion.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setString(2, LocalDateTime.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Impossible d'enregistrer la version " + version, e);
        }
    }

    private void executerScript(String ressource) {
        String contenu = lireRessource(ressource);
        try (Connection connexion = source.getConnection();
                Statement st = connexion.createStatement()) {
            for (String instruction : decouperInstructions(contenu)) {
                st.execute(instruction);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Échec du script de migration " + ressource, e);
        }
    }

    private int numeroVersion(String fichier) {
        String numero = fichier.substring(1, fichier.indexOf("__"));
        return Integer.parseInt(numero);
    }

    private static String lireRessource(String chemin) {
        try (InputStream in = MigrationSchema.class.getResourceAsStream(chemin)) {
            if (in == null) {
                throw new IllegalStateException("Migration introuvable : " + chemin);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible : " + chemin, e);
        }
    }

    /// Retire les lignes de commentaire pur (`--`) et découpe le script sur les `;`.
    private static String[] decouperInstructions(String sql) {
        StringBuilder sansCommentaires = new StringBuilder();
        for (String ligne : sql.split("\n")) {
            if (!ligne.strip().startsWith("--")) {
                sansCommentaires.append(ligne).append('\n');
            }
        }
        return Arrays.stream(sansCommentaires.toString().split(";"))
                .map(String::strip)
                .filter(instruction -> !instruction.isEmpty())
                .toArray(String[]::new);
    }
}

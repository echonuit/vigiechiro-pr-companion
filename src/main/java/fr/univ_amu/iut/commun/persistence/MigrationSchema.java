package fr.univ_amu.iut.commun.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Applique les scripts de migration versionnés `src/main/resources/db/migration/V0x__*.sql`
/// et trace les versions appliquées dans la table `schema_version`.
///
/// Au premier lancement, la base est vide : `V01__schema.sql` crée toutes les tables (dont
/// `schema_version`), puis `V02__seed_taxons.sql` insère les données de référence. À la
/// réouverture d'une base existante, les versions déjà présentes sont ignorées (migration
/// idempotente, objectif disponibilité 5.2 : « base présente → réutilisée »).
///
/// Chaque migration s'applique **en une transaction** avec l'inscription de sa version (#2728), et
/// laisse au passage l'**empreinte** de ce qu'elle a exécuté (#2729) : un script modifié après avoir
/// été appliqué est alors un refus explicite au démarrage, et non une divergence silencieuse entre
/// les bases qui l'ont subi et celles qui naissent après.
///
/// Pour ajouter une migration : créer le fichier `V0n__xxx.sql` dans `db/migration/`
/// **et** ajouter son nom à [#MIGRATIONS] (l'ordre fait foi). Un script publié ne se modifie plus.
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
        "V31__retrait_colonnes_mortes_archivage.sql",
        "V32__campagne.sql",
        "V33__passage_campagne.sql",
        "V34__passage_opportuniste.sql",
        "V35__site_tiers.sql",
        "V36__taxon_prioritaire_pna.sql",
        "V37__statut_recupere.sql",
        "V38__commune_du_point.sql",
        "V39__echec_definitif.sql",
        "V40__point_publie.sql",
        "V41__cause_du_refus.sql",
        "V42__avis_de_relecteur.sql",
        "V43__participation_relevee.sql"
    };

    private static final String DOSSIER = "/db/migration/";

    /// Longueur de l'extrait d'instruction cité dans le message d'échec : de quoi reconnaître la
    /// ligne dans le script sans en déverser le corps.
    private static final int LONGUEUR_EXTRAIT = 60;

    /// Dossier des sauvegardes, sous la racine du workspace. Le filet posé avant une montée de
    /// version y va, et non ailleurs : c'est là que la restauration propose de chercher.
    private static final String DOSSIER_SAUVEGARDES = "sauvegardes";

    /// Le nom dit ce que le fichier est et de quoi il précède : `vigiechiro-avant-migration-V39.db`.
    private static final String PREFIXE_FILET = "vigiechiro-avant-migration-";

    private final SourceDeDonnees source;
    private final UniteDeTravail uniteDeTravail;
    private final RegistreMigrations registre;

    public MigrationSchema(SourceDeDonnees source) {
        this.source = source;
        this.uniteDeTravail = new UniteDeTravail(source);
        this.registre = new RegistreMigrations(source);
    }

    /// Applique toutes les migrations non encore enregistrées dans `schema_version`, après avoir
    /// vérifié qu'aucune de celles qui le sont déjà n'a changé depuis (#2729).
    public void migrer() {
        Map<Integer, String> retenues = registre.lire();
        refuserSiUnScriptAppliqueAChange(retenues);
        List<String> enAttente = enAttente(retenues);
        if (enAttente.isEmpty()) {
            etalonnerLesEmpreintesInconnues(retenues);
            return;
        }
        // Le verrou ne se prend QUE s'il y a quelque chose à appliquer (#2731). Une commande de
        // lecture lancée pendant que l'IHM tourne ne migre rien : la faire échouer sur un verrou lui
        // coûterait plus que la protection ne lui rapporte.
        try (VerrouWorkspace verrou =
                VerrouWorkspace.pourOperationExclusive(source.workspace(), "la mise à jour de la base")) {
            poserLeFilet(retenues, enAttente);
            for (String fichier : enAttente) {
                appliquer(fichier, numeroVersion(fichier));
            }
            etalonnerLesEmpreintesInconnues(retenues);
        }
    }

    private List<String> enAttente(Map<Integer, String> retenues) {
        List<String> enAttente = new ArrayList<>();
        for (String fichier : MIGRATIONS) {
            if (!retenues.containsKey(numeroVersion(fichier))) {
                enAttente.add(fichier);
            }
        }
        return enAttente;
    }

    /// Met la base à l'abri **avant** de la faire évoluer, dans `<workspace>/sauvegardes` (#2729).
    ///
    /// Une montée de version est le seul moment où l'application transforme la base sans que
    /// l'utilisateur l'ait demandé : il ouvre l'application après une mise à jour, et le schéma
    /// change. Chaque migration est certes atomique (#2728), mais l'atomicité protège d'une panne, pas
    /// d'une migration qui **réussit** en faisant autre chose que prévu. Le filet, lui, protège des
    /// deux, et il se retrouve dans la liste des sauvegardes à restaurer.
    ///
    /// Rien à faire dans deux cas : aucune migration en attente, ou une base qui n'en portait encore
    /// aucune. Ce second cas est la **création** de la base, pas sa montée de version : il n'y a rien
    /// à mettre à l'abri.
    ///
    /// Si le filet ne peut pas être posé, on **ne migre pas**. Avancer sans lui reviendrait à ne le
    /// promettre que quand il ne sert à rien.
    private void poserLeFilet(Map<Integer, String> retenues, List<String> enAttente) {
        if (enAttente.isEmpty() || retenues.isEmpty()) {
            return;
        }
        Path dossier = source.workspace().racine().resolve(DOSSIER_SAUVEGARDES);
        String nom =
                PREFIXE_FILET + enAttente.get(0).substring(0, enAttente.get(0).indexOf("__"));
        try {
            new InstantaneBase(source).ecrireDans(dossier, nom);
        } catch (DataAccessException echec) {
            throw new DataAccessException(
                    "La base n'a pas pu être mise à l'abri dans " + dossier
                            + " avant sa mise à jour, la migration n'a donc pas eu lieu. Libérez de la"
                            + " place ou vérifiez les droits sur ce dossier, puis relancez.",
                    echec);
        }
    }

    /// Refuse de migrer si un script **déjà appliqué** ne correspond plus à son empreinte.
    ///
    /// Modifier un script après coup (rebase, correction bien intentionnée) fait diverger en silence
    /// les bases qui l'ont subi dans sa première version de celles qui naissent avec la seconde.
    /// Migrer par-dessus n'y changerait rien : la version est enregistrée, le script ne sera jamais
    /// rejoué, et le schéma obtenu ne correspondrait à aucune description. Mieux vaut s'arrêter en le
    /// disant.
    private void refuserSiUnScriptAppliqueAChange(Map<Integer, String> retenues) {
        List<String> derives = new ArrayList<>();
        for (String fichier : MIGRATIONS) {
            String retenue = retenues.get(numeroVersion(fichier));
            if (retenue != null && !retenue.equals(empreinte(fichier))) {
                derives.add(fichier);
            }
        }
        if (!derives.isEmpty()) {
            throw new RefusAvantEcriture(refus(derives));
        }
    }

    /// Donne son empreinte à chaque migration appliquée avant que les empreintes n'existent.
    ///
    /// C'est un **étalonnage** : il fige ce que les scripts disent aujourd'hui pour que toute dérive
    /// ultérieure se voie. Il ne peut rien dire du passé, puisque rien n'a gardé trace de ce qui
    /// avait été appliqué : un script déjà modifié avant ce premier lancement sera étalonné sur sa
    /// version modifiée, sans que personne puisse le savoir.
    private void etalonnerLesEmpreintesInconnues(Map<Integer, String> retenues) {
        Map<Integer, String> aEtalonner = new LinkedHashMap<>();
        for (String fichier : MIGRATIONS) {
            int version = numeroVersion(fichier);
            if (retenues.containsKey(version) && retenues.get(version) == null) {
                aEtalonner.put(version, empreinte(fichier));
            }
        }
        if (!aEtalonner.isEmpty()) {
            uniteDeTravail.executer(connexion -> registre.etalonner(connexion, aEtalonner));
        }
    }

    private static String refus(List<String> derives) {
        String constat = derives.size() == 1
                ? "La migration " + derives.get(0) + " a changé depuis qu'elle a été appliquée à cette base."
                : derives.size()
                        + " migrations ont changé depuis qu'elles ont été appliquées à cette base ("
                        + String.join(", ", derives)
                        + ").";
        return constat
                + " Un script appliqué ne se rejoue jamais : cette base porte ce que l'ancienne version"
                + " lui a fait, et migrer par-dessus donnerait un schéma que rien ne décrit. Rétablissez"
                + " le contenu d'origine du ou des scripts, ou repartez d'une base neuve après avoir"
                + " sauvegardé celle-ci.";
    }

    /// Applique un script et inscrit sa version **dans la même transaction** (#2728).
    ///
    /// Les deux écritures ont longtemps été portées par deux connexions en autocommit. Une coupure au
    /// milieu du script, ou entre le script et l'inscription, laissait alors un schéma partiellement
    /// modifié qu'aucune ligne de `schema_version` ne décrivait. Le lancement suivant rejouait le
    /// script depuis le début sur ce schéma bâtard et butait sur la première instruction non
    /// idempotente : aucun `IF NOT EXISTS` en V01, deux `ADD COLUMN` en V26. L'application ne
    /// redémarrait plus, et rien n'annonçait pourquoi.
    ///
    /// Le catalogue se prête à la transaction : SQLite sait annuler du DDL, et aucun des scripts ne
    /// porte de `PRAGMA`, de `VACUUM` ni de transaction explicite, les trois choses qui ne survivent
    /// pas à un `BEGIN`. Une migration future qui en aurait besoin devra donc le dire ici.
    private void appliquer(String fichier, int version) {
        String[] instructions = instructionsDe(fichier);
        uniteDeTravail.executer(connexion -> {
            try (Statement st = connexion.createStatement()) {
                for (int rang = 0; rang < instructions.length; rang++) {
                    executer(st, instructions, rang, fichier);
                }
            }
            registre.inscrire(connexion, version, empreinte(instructions));
        });
    }

    /// Exécute la `rang`-ième instruction du script en **situant** son échec : sans le rang ni
    /// l'extrait, un message de SQLite (« no such column ») laisse à relire tout le fichier pour
    /// trouver où.
    private static void executer(Statement st, String[] instructions, int rang, String fichier) {
        try {
            st.execute(instructions[rang]);
        } catch (SQLException echec) {
            throw new DataAccessException(
                    "Migration "
                            + fichier
                            + " annulée : l'instruction n°"
                            + (rang + 1)
                            + " sur "
                            + instructions.length
                            + " a échoué (« "
                            + extrait(instructions[rang])
                            + " »). La base est restée dans l'état d'avant cette migration.",
                    echec);
        }
    }

    private static String[] instructionsDe(String fichier) {
        return decouperInstructions(lireRessource(DOSSIER + fichier));
    }

    private static String empreinte(String fichier) {
        return empreinte(instructionsDe(fichier));
    }

    /// Empreinte SHA-256 de ce que le script **fait faire à la base** : ses instructions, telles que
    /// le découpage les produit, et non le fichier brut.
    ///
    /// La différence n'est pas cosmétique. Corriger une faute dans un commentaire, ou passer un
    /// fichier en fins de ligne Windows, ne change rien à ce que la base reçoit : faire échouer le
    /// démarrage pour cela serait un refus faux, et un refus faux use plus vite la confiance qu'une
    /// alerte manquée. En revanche, toucher à une instruction, en ajouter une ou en retirer une
    /// change l'empreinte.
    private static String empreinte(String[] instructions) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] condense = sha256.digest(String.join(";", instructions).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(condense);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 est exigé de toute plateforme Java", absent);
        }
    }

    /// Première ligne de l'instruction, tronquée : de quoi la reconnaître dans le script.
    private static String extrait(String instruction) {
        String premiereLigne = instruction.lines().findFirst().orElse("").strip();
        return premiereLigne.length() <= LONGUEUR_EXTRAIT
                ? premiereLigne
                : premiereLigne.substring(0, LONGUEUR_EXTRAIT) + "…";
    }

    private int numeroVersion(String fichier) {
        String numero = fichier.substring(1, fichier.indexOf("__"));
        return Integer.parseInt(numero);
    }

    /// Version de schéma la plus haute que **cette** version de l'application sache appliquer.
    ///
    /// Sert à refuser une sauvegarde écrite par une version plus récente (#2730) : ses tables et ses
    /// colonnes sont inconnues ici, et la migration ne les rattrapera pas puisque leurs scripts
    /// n'existent pas encore dans ce binaire. L'ordre du catalogue fait foi, garanti croissant par
    /// [MigrationSchemaTest].
    static int versionMaximale() {
        String dernier = MIGRATIONS[MIGRATIONS.length - 1];
        return Integer.parseInt(dernier.substring(1, dernier.indexOf("__")));
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

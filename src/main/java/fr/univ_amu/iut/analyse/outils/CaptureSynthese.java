package fr.univ_amu.iut.analyse.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.analyse.view.SyntheseController;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Rend l'écran **Synthèse de la nuit** (#2351) hors écran en PNG. La capture doit montrer **les
/// quatre messages** que la colonne « Activité » sait écrire, plus le bouclier PNA. Jamais de cellule
/// vide : l'absence a un sens, et il diffère selon le cas.
///
/// ## Ce que les nombres visent
///
/// Les contacts tombent dans les bandes du référentiel embarqué, déclinaison
/// `region:Provence-Alpes-Cote dAzur` / `printemps` : la classe est **calculée**, jamais décrétée.
///
/// | Taxon | q25 | q75 | q98 | confiance | contacts | ce que la colonne écrit |
/// |---|---|---|---|---|---|---|
/// | `Pipkuh` Pipistrelle de Kuhl | 23 | 261 | 1804 | Bonne | 718 | **Forte** |
/// | `Pippip` Pipistrelle commune | 9 | 221 | 1858 | Bonne | 120 | **Moyenne**, et le bouclier PNA |
/// | `Myodas` Murin des marais | 1 | 2 | 11 | **Faible** | 5 | **Forte *(indicatif)*** |
/// | `Cicorn` Cigale grise | — | — | — | **aucune déclinaison applicable** | 1 | « Pas de seuil pour ce contexte » |
/// | `Antcho` Antaxie catalane | — | — | — | **absente du référentiel** | 40 | « Non couvert par le référentiel » |
///
/// Le message ne dépend **pas du groupe taxonomique**, contrairement à ce que laisse croire la fiche :
/// `LigneSynthese.libelleClasse()` se décide sur `referentiel.couvre(codeTaxon)`, donc sur la présence
/// du code dans le CSV. Une cigale peut dire « Pas de seuil », et c'est ce qu'elle fait ici.
///
/// ## Ce qui contraint le carré et la nuit
///
/// Les deux constantes se tiennent, et les changer casse la démonstration. Le carré doit être en
/// **Provence** pour que 718 tombe en « Forte » : en Corse l'été, `q98` vaut 518 et l'écran dirait
/// « Très forte ». La nuit doit être au **printemps** parce que c'est l'un des 25 contextes où les cinq
/// états coexistent (#3051) ; en `Provence` / `ete`, aucun des 98 taxons n'est orphelin, et « Pas de
/// seuil pour ce contexte » ne serait montré par aucune capture.
///
/// ⚠️ Les quantiles cités par l'[ADR 2351], `Q75 = 480 · Q98 = 1 240`, n'existent dans **aucune**
/// déclinaison de Pipkuh : ils avaient été inventés pour la maquette. La fiche et `M-Synthese` portent
/// désormais ceux du référentiel réel ; l'ADR, immuable, garde les siens. Qui compare les trois lira
/// donc deux jeux de nombres.
///
/// Les codes de taxon suivent la **casse du référentiel** (`Pipkuh`, et non `PIPKUH`) : c'est le piège
/// relevé à la clôture du lot #2353, où une démo en majuscules montrait un écran sans son repère.
///
/// Ce que l'outil ne fait plus, et pourquoi il ne doit pas y revenir : voir #3018.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSynthese {

    private static final String ID_UTILISATEUR = "u-capture";
    private static final String SERIE = "SN-1";

    /// Carré des **Bouches-du-Rhône** : la région se déduit des deux premiers chiffres (`13`), elle n'est
    /// plus affirmée par un service bouchonné. Le choix n'est pas décoratif - voir la note sur 718.
    static final String CARRE = "130246";

    static final String POINT = "A1";
    static final String SITE = "Étang de Berre";

    /// Nuit de **printemps** (`SaisonActivite.PRINTEMPS` court du 1er avril au 15 juin) : la saison s'en
    /// déduit, et c'est elle qui choisit la déclinaison du référentiel. Le choix n'est pas décoratif -
    /// voir la note sur le printemps.
    private static final String NUIT = "2026-05-14";

    private CaptureSynthese() {}

    public static void main() throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException | SQLException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException, SQLException {
        Path workspace = Files.createTempDirectory("vc-capture-synthese");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        injecteur.getInstance(MigrationSchema.class).migrer();
        long idPassage = semerLaNuit(source);

        FXMLLoader loader = ChargeurFxml.chargeur(SyntheseController.class, "Synthese.fxml");
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        SyntheseController controleur = loader.getController();
        controleur.ouvrirSur(new ContextePassage(idPassage, 3, new ContexteSite(CARRE, POINT, SITE)));
        Path fichier = sortie.resolve("apercu-synthese.png");
        // 1100 × 700 : à 620 px, le bloc de mise en garde débordait de quelques pixels et ApercuFx
        // refusait la capture (ADR 0042). Il avait raison : un avertissement tronqué ne prévient
        // personne, et c'est justement ce que cette capture doit montrer comme lisible.
        ApercuFx.enregistrerPng(new Scene(vue, 1180, 700), fichier);
        System.out.println("Apercu de la synthese ecrit dans " + fichier.toAbsolutePath());
    }

    /// Injecteur de cet outil : la composition **complète** de l'application, surchargée par des
    /// exécuteurs synchrones. Exposé pour le garde-fou de câblage (`CablageInjecteursCaptureTest`).
    public static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), new ModuleCapture()));
    }

    /// Sème la nuit et rend l'identifiant de son passage. Partagée avec
    /// [CaptureSyntheseSansReferentiel] : les deux aperçus doivent montrer **la même nuit**, pour que
    /// leur seule différence soit le référentiel.
    ///
    /// Les observations sont réparties : deux par séquence pour la Pipistrelle de Kuhl (le tableau montre
    /// alors des contacts **supérieurs** au nombre de fichiers, comme sur une vraie nuit), une par
    /// séquence pour les autres.
    static long semerLaNuit(SourceDeDonnees source) throws SQLException {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        try (Connection cx = source.getConnection()) {
            long idSite = cle(
                    cx,
                    "INSERT INTO monitoring_site(square_number, friendly_name, protocol, created_at, user_id)"
                            + " VALUES (?, ?, 'PointFixeStandard', '2026-05-01', ?)",
                    CARRE,
                    SITE,
                    ID_UTILISATEUR);
            long idPoint = cle(cx, "INSERT INTO listening_point(code, site_id) VALUES (?, ?)", POINT, idSite);
            executer(cx, "INSERT INTO recorder(serial_number) VALUES ('" + SERIE + "')");
            long idPassage = cle(
                    cx,
                    "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                            + " workflow_status, point_id, recorder_id)"
                            + " VALUES (3, 2026, ?, '21:00', '05:00', 'Vérifié', ?, ?)",
                    NUIT,
                    idPoint,
                    SERIE);
            long idSession = cle(
                    cx, "INSERT INTO recording_session(root_path, passage_id) VALUES ('/ws/synthese', ?)", idPassage);
            long idOriginal = cle(
                    cx,
                    "INSERT INTO original_recording(file_name, file_path, session_id)"
                            + " VALUES ('a.wav', '/ws/synthese/bruts/a.wav', ?)",
                    idSession);
            long idResultats = cle(
                    cx,
                    "INSERT INTO identification_results(file_path, detected_format, imported_at, passage_id)"
                            + " VALUES ('/ws/synthese/obs.csv', 'Vu', ?, ?)",
                    NUIT,
                    idPassage);

            // 718 : le nombre fondateur de l'ADR 2351, produit et non plus affirmé (359 séquences × 2).
            semerTaxon(cx, idSession, idOriginal, idResultats, "Pipkuh", 718, 2);
            semerTaxon(cx, idSession, idOriginal, idResultats, "Pippip", 120, 1);
            // Murin des marais : sa déclinaison de printemps est de confiance « Faible », la classe se
            // marque donc indicative. 5 contacts tombent en « Forte » (q75 = 2, q98 = 11), ce qui place
            // « Forte (indicatif) » juste sous la « Forte » fiable de la Pipistrelle de Kuhl : on lit
            // alors ce que la mention ajoute, plutôt que d'avoir à l'expliquer.
            semerTaxon(cx, idSession, idOriginal, idResultats, "Myodas", 5, 1);
            // Cigale grise : sa seule déclinaison est `national` / `ete`, donc au printemps plus rien ne
            // la couvre. Le référentiel la connaît, ce contexte-là non : « Pas de seuil pour ce contexte ».
            semerTaxon(cx, idSession, idOriginal, idResultats, "Cicorn", 1, 1);
            // Antaxie catalane : connue de la base, absente du référentiel d'activité. Message différent
            // du précédent, et c'est tout l'enjeu : « Non couvert par le référentiel ».
            semerTaxon(cx, idSession, idOriginal, idResultats, "Antcho", 40, 1);
            return idPassage;
        }
    }

    /// Sème `contacts` observations d'un taxon, à raison de `parSequence` par fichier.
    private static void semerTaxon(
            Connection cx,
            long idSession,
            long idOriginal,
            long idResultats,
            String code,
            int contacts,
            int parSequence)
            throws SQLException {
        String insertSequence = "INSERT INTO listening_sequence(file_name, original_recording_id, file_path,"
                + " session_id, recorded_at) VALUES (?, ?, ?, ?, ?)";
        String insertObservation = "INSERT INTO observation(sequence_id, taxon_tadarida, prob_tadarida,"
                + " taxon_observer, prob_observer, results_id) VALUES (?, ?, 0.92, ?, 0.95, ?)";
        try (PreparedStatement sequence = cx.prepareStatement(insertSequence, Statement.RETURN_GENERATED_KEYS);
                PreparedStatement observation = cx.prepareStatement(insertObservation)) {
            sequence.setLong(2, idOriginal);
            sequence.setLong(4, idSession);
            observation.setString(2, code);
            observation.setString(3, code);
            observation.setLong(4, idResultats);
            int poses = 0;
            for (int rang = 0; poses < contacts; rang++) {
                String nom = code + "_" + rang + ".wav";
                sequence.setString(1, nom);
                sequence.setString(3, "/ws/synthese/transformes/" + nom);
                // Horodatage dans la nuit du 3 au 4 juillet : c'est lui qui donne la saison (ADR 2352).
                sequence.setString(5, NUIT + "T22:%02d:00".formatted(rang % 60));
                sequence.executeUpdate();
                long idSequence = premiereCle(sequence);
                observation.setLong(1, idSequence);
                for (int i = 0; i < parSequence && poses < contacts; i++, poses++) {
                    observation.executeUpdate();
                }
            }
        }
    }

    private static void executer(Connection cx, String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }

    private static long cle(Connection cx, String sql, Object... parametres) throws SQLException {
        try (PreparedStatement st = cx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < parametres.length; i++) {
                st.setObject(i + 1, parametres[i]);
            }
            st.executeUpdate();
            return premiereCle(st);
        }
    }

    private static long premiereCle(PreparedStatement st) throws SQLException {
        try (ResultSet cles = st.getGeneratedKeys()) {
            cles.next();
            return cles.getLong(1);
        }
    }

    /// Ce que la capture surcharge, et **rien de plus** : les espèces prioritaires, pour que le bouclier
    /// du Plan National d'Actions se voie sur une seule des quatre lignes.
    private static final class ModuleCapture extends AbstractModule {

        /// Espèces prioritaires de la démonstration. **Fidèle au Plan National d'Actions** : parmi les
        /// quatre espèces montrées, seule la Pipistrelle commune y figure. Une démo qui marquerait tout,
        /// ou rien, donnerait une image aussi fausse dans un sens que dans l'autre : c'est le défaut
        /// relevé à la clôture du lot #2353, où l'aperçu publié ne montrait aucun bouclier.
        @Provides
        EspecesPrioritaires especesPrioritaires() {
            return () -> Set.of("Pippip");
        }
    }
}

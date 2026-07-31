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
/// Rend l'écran **Synthèse de la nuit** (#2351) hors écran en PNG. La nuit montre les quatre cas que
/// l'écran sait rendre : une classe d'activité par espèce, **ses quantiles à côté**, une mention
/// *(indicatif)* sur une déclinaison peu fiable, et un orthoptère qui **dit** qu'il n'est pas couvert
/// plutôt que de laisser une cellule vide.
///
/// ## La nuit est semée, pas fabriquée (#3018)
///
/// Cet outil substituait autrefois un `ServiceSynthese` anonyme dont les trois lectures étaient
/// surchargées : l'écran affichait des lignes écrites en dur, et le DAO qu'on lui passait portait une
/// source **nulle**. Rien de ce qui est montré ne venait du produit.
///
/// ⚠️ Cette substitution cachait une **contradiction** : le contexte passé à l'écran citait le carré
/// `640380` (Nouvelle-Aquitaine) pendant que les seuils affirmaient « region Corse » et que le site
/// s'appelait « Étang de Biguglia ». Personne ne pouvait la voir, puisque le service bouchonné ignorait
/// le carré qu'on lui donnait. La région se **déduit** désormais du carré, et la saison de la nuit.
///
/// ## Pourquoi la Provence, et pourquoi 718
///
/// **718 contacts de Pipistrelle de Kuhl** n'est pas un nombre de démonstration : c'est la question
/// fondatrice de l'écran, citée par l'[ADR 2351], dessinée dans la maquette `M-Synthese` et reprise en
/// tête de la fiche utilisateur. Trois documents s'y appuient, la capture doit donc la **produire**.
///
/// Encore faut-il que 718 tombe bien en « Forte », ce qui dépend de la déclinaison : en Corse l'été,
/// `q98` vaut 518, et 718 la dépasse - l'écran dirait **Très forte**. Le carré est donc en
/// Bouches-du-Rhône (`13…`, donc `region:Provence-Alpes-Cote dAzur`), seule façon de garder à la fois
/// le nombre fondateur et la classe que le récit lui associe.
///
/// ⚠️ Les quantiles cités par ces trois documents - `Q75 = 480 · Q98 = 1 240` - n'existent **dans aucune
/// déclinaison** de Pipkuh : ils avaient été inventés pour la maquette. La fiche et la maquette portent
/// désormais ceux du référentiel réel ; l'ADR 2351, immuable, garde les siens.
///
/// ## Ce que les nombres visent
///
/// Les contacts sont choisis pour tomber dans les bandes du référentiel embarqué, déclinaison
/// `region:Provence-Alpes-Cote dAzur` / `ete` - la classe est donc **calculée**, plus décrétée :
///
/// | Taxon | q25 | q75 | q98 | confiance | contacts | classe attendue |
/// |---|---|---|---|---|---|---|
/// | `Pipkuh` Pipistrelle de Kuhl | 41 | 620 | 3842 | Très bonne | 718 | **Forte** |
/// | `Pippip` Pipistrelle commune | 13 | 254 | 2751 | Très bonne | 120 | **Moyenne**, et le bouclier PNA |
/// | `Cicorn` Cigale grise | 1 | 2 | 58 | **Faible** | 1 | **Moyenne**, *(indicatif)* |
/// | `Antcho` Antaxie catalane | — | — | — | **absente du référentiel** | 40 | aucune, et la cellule le dit |
///
/// La Cigale grise n'a qu'une seule déclinaison, `national` / `ete`, et sa confiance est *Faible* : la
/// mention *(indicatif)* ne dépend donc pas de la région retenue.
///
/// ⚠️ Les deux derniers **ne sont pas ceux de l'ancienne démonstration**, et c'est instructif. Elle
/// montrait une Barbastelle marquée *(indicatif)* et une Sauterelle verte « hors référentiel » : le
/// référentiel embarqué dit le contraire des deux. La Barbastelle a une déclinaison **nationale d'été
/// très fiable**, et `ReferentielActivite` s'arrête à la première fiable - elle n'est donc jamais
/// indicative ; la Sauterelle verte, elle, a bel et bien une déclinaison `region:Corse` / `ete`.
///
/// Deux cas d'écran affichés depuis des données que le produit ne peut pas produire. C'est ce que
/// permet un service bouchonné, et c'est pourquoi il fallait le retirer.
///
/// Les codes de taxon suivent la **casse du référentiel** (`Pipkuh`, et non `PIPKUH`) : c'est le piège
/// relevé à la clôture du lot #2353, où une démo en majuscules montrait un écran sans son repère.
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

    /// Nuit d'**été** : la saison s'en déduit, et c'est elle qui choisit la déclinaison du référentiel.
    private static final String NUIT = "2026-07-03";

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
        ApercuFx.enregistrerPng(new Scene(vue, 1100, 700), fichier);
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
            // Cigale grise : aucune de ses déclinaisons n'est fiable, la classe se marque donc indicative.
            // Un seul contact donne « Moyenne » et non « Faible » : q25 vaut 1, et la borne est stricte
            // (`contacts < q25`). Faible serait donc inatteignable ici - le référentiel a le dernier mot.
            semerTaxon(cx, idSession, idOriginal, idResultats, "Cicorn", 1, 1);
            // Antaxie catalane : connue de la base, absente du référentiel d'activité.
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

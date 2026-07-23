package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.importation.view.ActionImportTransformes.ModeImport;
import fr.univ_amu.iut.importation.view.ActionImportTransformes.PointRattachable;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Flux du geste IHM « importer un dossier de transformés déjà présents » (#2258). L'action compose des
/// dialogues **injectés** (double répondant) et lance le travail via une [SuiviOperation] **synchrone sans
/// fenêtre** : le geste se joue donc **hors du fil JavaFX**, sans ouvrir de `Stage` (aucun robot TestFX
/// nécessaire, c'est tout l'intérêt des ports). Base SQLite réelle + point préparé, comme
/// `ServiceImportReferenceTest`.
class ActionImportTransformesTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final String CARRE = "640380";
    private static final int FREQUENCE_ENTETE = 38_400; // Fe/10 d'un transformé
    private static final int TRAMES = 3_840;

    @TempDir
    Path racine;

    private SourceDeDonnees source;
    private Workspace workspace;
    private PointDEcoute point;

    private ServiceImportReferenceFabrique fabrique;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private SequenceDao sequenceDao;

    /// Modale de progression **synchrone et sans fenêtre** : exécute le travail immédiatement puis restitue
    /// succès / annulation / échec, comme la vraie modale une fois le travail fini, mais sans `Stage`.
    private final SuiviOperation suiviSynchrone = new SuiviOperation() {
        @Override
        public <T> void lancer(
                Window proprietaire,
                String titre,
                BiFunction<Consumer<Progression>, JetonAnnulation, T> travail,
                Consumer<T> succes,
                Runnable annule,
                Consumer<Throwable> echec) {
            try {
                succes.accept(travail.apply(progres -> {}, new JetonAnnulation()));
            } catch (RuntimeException erreur) {
                echec.accept(erreur);
            }
        }
    };

    private final Supplier<Window> proprietaire = () -> null;

    @BeforeEach
    void preparer() {
        workspace = new Workspace(racine.resolve("ws"));
        source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        Site site = siteDao.insert(new Site(null, CARRE, "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        point = pointDao.insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()));

        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        sequenceDao = new SequenceDao(source);

        fabrique = new ServiceImportReferenceFabrique(source, workspace);
    }

    @Test
    @DisplayName("Geste complet : dossier + point + référencer → passage créé, séquences pointant l'externe")
    void geste_complet_reference_cree_le_passage() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));
        AtomicReference<String> messageNotifie = new AtomicReference<>();
        AtomicReference<NiveauNotification> niveauNotifie = new AtomicReference<>();
        AtomicInteger rechargements = new AtomicInteger();

        ActionImportTransformes action = new ActionImportTransformes(
                fabrique.creer(),
                workspace,
                proprietaire,
                selecteurRendant(Optional.of(externe)),
                () -> List.of(new PointRattachable(point, CARRE)),
                (entete, question, options, libelle) -> Optional.of(options.get(0)),
                (entete, question, options, libelle) -> Optional.of(ModeImport.REFERENCER),
                (niveau, entete, message) -> {
                    niveauNotifie.set(niveau);
                    messageNotifie.set(message);
                },
                suiviSynchrone,
                rechargements::incrementAndGet);

        action.importer();

        assertThat(passageDao.findAll()).as("un passage a été créé").hasSize(1);
        Long idPassage = passageDao.findAll().get(0).id();
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        assertThat(sequenceDao.findBySession(idSession))
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.cheminFichier())
                        .as("mode référence : les séquences pointent le dossier externe")
                        .startsWith(externe.toString()));
        assertThat(niveauNotifie.get()).isEqualTo(NiveauNotification.INFORMATION);
        assertThat(messageNotifie.get()).contains("Passage #" + idPassage).contains("référencées");
        assertThat(rechargements.get()).as("l'écran est rafraîchi après succès").isEqualTo(1);
    }

    @Test
    @DisplayName("L'utilisateur renonce au dossier : rien ne se passe (aucun passage, aucun choix de point)")
    void renoncement_au_dossier_ne_fait_rien() {
        ActionImportTransformes action = new ActionImportTransformes(
                fabrique.creer(),
                workspace,
                proprietaire,
                selecteurRendant(Optional.empty()), // l'utilisateur annule
                () -> List.of(new PointRattachable(point, CARRE)),
                (entete, question, options, libelle) -> {
                    throw new AssertionError("aucun choix de point attendu si le dossier n'est pas choisi");
                },
                (entete, question, options, libelle) -> {
                    throw new AssertionError("aucune question de mode attendue si le dossier n'est pas choisi");
                },
                (niveau, entete, message) -> {
                    throw new AssertionError("aucune notification attendue si le dossier n'est pas choisi");
                },
                suiviSynchrone,
                () -> {
                    throw new AssertionError("aucun rechargement attendu si le dossier n'est pas choisi");
                });

        action.importer();

        assertThat(passageDao.findAll()).as("aucun passage créé").isEmpty();
    }

    @Test
    @DisplayName("L'utilisateur renonce au mode (Annuler) : dossier et point choisis, mais aucun passage")
    void renoncement_au_mode_ne_persiste_rien() throws IOException {
        Path externe = preparerDossierTransforme(racine.resolve("externe"));

        ActionImportTransformes action = new ActionImportTransformes(
                fabrique.creer(),
                workspace,
                proprietaire,
                selecteurRendant(Optional.of(externe)),
                () -> List.of(new PointRattachable(point, CARRE)),
                (entete, question, options, libelle) -> Optional.of(options.get(0)),
                (entete, question, options, libelle) -> Optional.empty(), // renoncement au mode
                (niveau, entete, message) -> {
                    throw new AssertionError("aucune notification attendue si l'utilisateur renonce au mode");
                },
                suiviSynchrone,
                () -> {
                    throw new AssertionError("aucun rechargement attendu si l'utilisateur renonce au mode");
                });

        action.importer();

        assertThat(passageDao.findAll())
                .as("renoncer au choix copier / référencer n'importe rien")
                .isEmpty();
    }

    /// Sélecteur double : rend `reponse` pour le choix du dossier, vide pour les autres formes (non
    /// sollicitées ici).
    private static SelecteurFichier selecteurRendant(Optional<Path> reponse) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return reponse;
            }

            @Override
            public Optional<Path> choisirFichier(
                    String titre, Optional<Path> dossierInitial, fr.univ_amu.iut.commun.view.FiltreFichier filtre) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> enregistrerFichier(
                    String titre, String nomPropose, fr.univ_amu.iut.commun.view.FiltreFichier filtre) {
                return Optional.empty();
            }
        };
    }

    /// Dossier **externe** de séquences déjà transformées : deux tranches d'un original horodaté portant la
    /// série (pour que le journal de repli déduise série et date).
    private static Path preparerDossierTransforme(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        String base = "Car640380-2026-Pass1-Z1-PaRecPR" + SERIE + "_20260422_203922_";
        ecrireWavTransforme(dossier.resolve(base + "000.wav"), 1);
        ecrireWavTransforme(dossier.resolve(base + "001.wav"), 2);
        return dossier;
    }

    private static void ecrireWavTransforme(Path fichier, int germe) throws IOException {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41 + germe * 7) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE_ENTETE);
        buf.putInt(FREQUENCE_ENTETE * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }

    /// Petite fabrique du service métier réel, pour ne pas répéter le montage de ses sept collaborateurs
    /// dans chaque test.
    private record ServiceImportReferenceFabrique(SourceDeDonnees source, Workspace workspace) {

        fr.univ_amu.iut.importation.model.ServiceImportReference creer() {
            return new fr.univ_amu.iut.importation.model.ServiceImportReference(
                    new PointDao(source),
                    new SiteDao(source),
                    new AgregatImportDao(source),
                    new UniteDeTravail(source),
                    workspace,
                    new HorlogeFigee(LocalDate.of(2026, 5, 31)),
                    new ServiceDisponibiliteAudio(new SessionDao(source), new SequenceDao(source), workspace));
        }
    }
}

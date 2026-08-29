package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les deux gestes d'écran de l'emport (#4727) : désigner, annoncer, confirmer, écrire.
///
/// Aucun TestFX ici : les trois dialogues passent par leurs porteurs injectables, et c'est
/// exactement la raison d'être de [SelecteurFichier] : un sélecteur natif fige un test headless, si
/// bien qu'un geste qui **commence** par lui n'était pas testable du tout.
class ActionsEmportTest {

    private static final ProfilVigieChiro RELECTEUR =
            new ProfilVigieChiro("507f1f77bcf86cd799439011", "chiro-pierre", "Observateur");

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private SelectionDao selectionDao;
    private SequenceDao sequenceDao;
    private ActionsEmport actions;
    private long idPassage;
    private long idSession;
    private long idOriginal;

    private final List<String> notifications = new ArrayList<>();
    private NiveauNotification dernierNiveau;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier.resolve("poste")));
        new MigrationSchema(source).migrer();
        selectionDao = new SelectionDao(source);
        sequenceDao = new SequenceDao(source);

        idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .position(43.5, 5.4)
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-06-20")
                .heures("20:00:00", "06:00:00")
                .statut(StatutWorkflow.TRANSFORME)
                .semerPassage()
                .idPassage();
        idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "/ws/sess", null, null, idPassage))
                .id();
        idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "o.wav", "/ws/o.wav", 5.0, 384000, null, idSession))
                .id();

        actions = new ActionsEmport(new ServiceEmport(
                selectionDao,
                sequenceDao,
                new SessionDao(source),
                new PassageDao(source),
                new PointDao(source),
                new SiteDao(source),
                new UniteDeTravail(source)));
        actions.notificateur().definir((niveau, entete, message) -> {
            dernierNiveau = niveau;
            notifications.add(entete + " | " + message);
        });
    }

    @Test
    @DisplayName("Emporter annonce le volume avant d'écrire, et l'écrit une fois confirmé")
    void emporter_annonce_le_volume_puis_ecrit() throws IOException {
        uneSelectionDeDeux();
        Path destination = dossier.resolve("nuit.zip");
        actions.selecteur().definir(selecteurQuiRepond(destination));
        List<String> annonces = new ArrayList<>();
        actions.confirmateur().definir(message -> {
            annonces.add(message);
            return true;
        });

        actions.emporter(idPassage);

        assertThat(annonces)
                .as("le volume s'annonce avant d'écrire, sinon on confirme à l'aveugle")
                .singleElement()
                .satisfies(annonce -> assertThat(annonce).contains("2 séquence").containsIgnoringCase("o"));
        assertThat(Files.exists(destination)).as("puis le paquet est écrit").isTrue();
        assertThat(dernierNiveau).isEqualTo(NiveauNotification.INFORMATION);
        assertThat(notifications)
                .as("le compte rendu dit ce qui a été écrit, pas seulement que ça s'est bien passé")
                .singleElement()
                .satisfies(
                        message -> assertThat(message).contains("Nuit emportée").contains("2 séquence"));
    }

    @Test
    @DisplayName("Refuser l'annonce de volume ne laisse aucun fichier derrière")
    void refuser_l_annonce_n_ecrit_rien() throws IOException {
        uneSelectionDeDeux();
        Path destination = dossier.resolve("nuit.zip");
        actions.selecteur().definir(selecteurQuiRepond(destination));
        actions.confirmateur().definir(message -> false);

        actions.emporter(idPassage);

        assertThat(Files.exists(destination))
                .as("un volume refusé n'écrit rien : c'est le cas qu'on ne pouvait pas vérifier avant le port")
                .isFalse();
    }

    @Test
    @DisplayName("Annuler le sélecteur n'écrit rien et ne demande aucune confirmation")
    void annuler_le_selecteur_n_ecrit_rien() {
        uneSelectionDeDeux();
        actions.selecteur().definir(selecteurQuiAnnule());
        List<String> annonces = new ArrayList<>();
        actions.confirmateur().definir(message -> {
            annonces.add(message);
            return true;
        });

        actions.emporter(idPassage);

        assertThat(annonces)
                .as("annuler la désignation arrête le geste avant même de peser")
                .isEmpty();
    }

    @Test
    @DisplayName("Un refus du service se dit en erreur, avec sa cause, plutôt qu'en échec muet")
    void un_refus_du_service_se_dit_avec_sa_cause() {
        // Aucune sélection sur cette nuit : le service refuse de composer.
        actions.selecteur().definir(selecteurQuiRepond(dossier.resolve("vide.zip")));
        actions.confirmateur().definir(message -> true);

        actions.emporter(idPassage);

        assertThat(dernierNiveau).isEqualTo(NiveauNotification.AVERTISSEMENT);
        assertThat(notifications)
                .as("un échec muet laisserait l'utilisateur croire que le geste a marché")
                .singleElement()
                .satisfies(message -> assertThat(message).contains("sélection"));
    }

    @Test
    @DisplayName("Ouvrir un paquet reçu crée la sélection figée, et le refus d'un paquet étranger se dit")
    void ouvrir_un_paquet_recu() throws IOException {
        uneSelectionDeDeux();
        Path paquet = dossier.resolve("recu.zip");
        actions.selecteur().definir(selecteurQuiRepond(paquet));
        actions.confirmateur().definir(message -> true);
        actions.emporter(idPassage);
        // Le relecteur n'a pas la sélection de l'expéditeur : on retire la nôtre pour jouer son poste.
        Long aRetirer = selectionDao.findByPassage(idPassage).orElseThrow().id();
        new UniteDeTravail(source).executer(c -> selectionDao.supprimerDansTransaction(c, aRetirer));

        actions.ouvrirPaquetRecu(Optional.of(RELECTEUR));

        assertThat(selectionDao.findByPassage(idPassage).orElseThrow().methode())
                .as("la nuit reçue porte une sélection figée")
                .isEqualTo(MethodeSelection.RECUE_D_UN_PAQUET);
        assertThat(dernierNiveau).isEqualTo(NiveauNotification.INFORMATION);
        assertThat(notifications)
                .as("le compte rendu dit combien de séquences, et qui les signera")
                .last(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("Paquet ouvert")
                .contains("2 séquence")
                .contains("chiro-pierre");
    }

    @Test
    @DisplayName("Le volume s'annonce en kilooctets dès qu'il dépasse le kilooctet, en octets sinon")
    void le_volume_s_annonce_dans_l_unite_qui_se_lit() throws IOException {
        List<SequenceDEcoute> nuit = creerNuitLourde();
        SelectionDEcoute selection =
                selectionDao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(0).id(), 0, false));
        actions.selecteur().definir(selecteurQuiRepond(dossier.resolve("lourd.zip")));
        List<String> annonces = new ArrayList<>();
        actions.confirmateur().definir(message -> {
            annonces.add(message);
            return false;
        });

        actions.emporter(idPassage);

        assertThat(annonces)
                .singleElement()
                .satisfies(annonce -> assertThat(annonce)
                        .as("5 000 octets d'audio font 4 Ko : une division changée en multiplication dirait 5 120 000")
                        .contains("4 Ko d'audio"));
    }

    @Test
    @DisplayName("Un paquet qu'aucune nuit locale ne reconnaît se refuse, avec le carré en cause")
    void un_paquet_etranger_se_refuse_en_le_disant() throws IOException {
        uneSelectionDeDeux();
        Path paquet = dossier.resolve("etranger.zip");
        actions.selecteur().definir(selecteurQuiRepond(paquet));
        actions.confirmateur().definir(message -> true);
        actions.emporter(idPassage);

        SourceDeDonnees vierge = new SourceDeDonnees(new Workspace(dossier.resolve("vierge")));
        new MigrationSchema(vierge).migrer();
        ActionsEmport chezVierge = new ActionsEmport(new ServiceEmport(
                new SelectionDao(vierge),
                new SequenceDao(vierge),
                new SessionDao(vierge),
                new PassageDao(vierge),
                new PointDao(vierge),
                new SiteDao(vierge),
                new UniteDeTravail(vierge)));
        List<String> refus = new ArrayList<>();
        chezVierge.notificateur().definir((niveau, entete, message) -> refus.add(entete + " | " + message));
        chezVierge.selecteur().definir(selecteurQuiRepond(paquet));

        chezVierge.ouvrirPaquetRecu(Optional.of(RELECTEUR));

        assertThat(refus)
                .as("le refus nomme le carré inconnu, sinon l'utilisateur ne sait pas quoi corriger")
                .singleElement()
                .satisfies(
                        message -> assertThat(message).contains("Paquet refusé").contains("040962"));
    }

    @Test
    @DisplayName("Annuler le choix d'un paquet à ouvrir ne rend aucun compte")
    void annuler_l_ouverture_ne_rend_aucun_compte() {
        actions.selecteur().definir(selecteurQuiAnnule());

        actions.ouvrirPaquetRecu(Optional.of(RELECTEUR));

        assertThat(notifications)
                .as("rien ne s'est passé, et le dire serait un bruit de plus")
                .isEmpty();
    }

    @Test
    @DisplayName("Une écriture qui échoue se dit, plutôt que de laisser croire à un emport réussi")
    void une_ecriture_qui_echoue_se_dit() {
        uneSelectionDeDeux();
        // Un dossier qui n'existe pas : l'écriture échouera après que le plan a été confirmé.
        actions.selecteur().definir(selecteurQuiRepond(dossier.resolve("absent").resolve("nuit.zip")));
        actions.confirmateur().definir(message -> true);

        actions.emporter(idPassage);

        assertThat(dernierNiveau).isEqualTo(NiveauNotification.AVERTISSEMENT);
        assertThat(notifications)
                .as("l'échec porte son entête propre : ce n'est pas un refus métier mais une écriture rompue")
                .singleElement()
                .satisfies(message -> assertThat(message).contains("Emport interrompu"));
    }

    // --- montage -----------------------------------------------------------

    private void uneSelectionDeDeux() {
        List<SequenceDEcoute> nuit = creerNuit(3);
        SelectionDEcoute selection =
                selectionDao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 2, idPassage));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(0).id(), 0, false));
        selectionDao.attacherSequence(
                new SequenceSelectionnee(selection.id(), nuit.get(1).id(), 1, false));
        selectionDao.marquerVerdict(selection.id(), nuit.get(0).id(), VerdictFichier.BON);
    }

    private List<SequenceDEcoute> creerNuitLourde() {
        try {
            Path bruts = Files.createDirectories(dossier.resolve("lourdes"));
            String nom = "Car040962-2026-Pass1-A1-900.wav";
            Path fichier = Files.writeString(bruts.resolve(nom), "x".repeat(5_000));
            return List.of(sequenceDao.insert(new SequenceDEcoute(
                    null, nom, idOriginal, 9, 0.0, 5.0, fichier.toString(), false, idSession, null, null)));
        } catch (IOException impossible) {
            throw new IllegalStateException("fixture", impossible);
        }
    }

    private List<SequenceDEcoute> creerNuit(int n) {
        List<SequenceDEcoute> sequences = new ArrayList<>();
        try {
            Path bruts = Files.createDirectories(dossier.resolve("sequences"));
            for (int t = 0; t < n; t++) {
                String nom = "Car040962-2026-Pass1-A1-" + String.format("%03d", t) + ".wav";
                Path fichier = Files.writeString(bruts.resolve(nom), "contenu " + t);
                sequences.add(sequenceDao.insert(new SequenceDEcoute(
                        null, nom, idOriginal, t, 0.0, 5.0, fichier.toString(), false, idSession, null, null)));
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("fixture", impossible);
        }
        return sequences;
    }

    private static SelecteurFichier selecteurQuiRepond(Path chemin) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(chemin);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> initial, FiltreFichier filtre) {
                return Optional.of(chemin);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                return Optional.of(chemin);
            }
        };
    }

    private static SelecteurFichier selecteurQuiAnnule() {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> initial, FiltreFichier filtre) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                return Optional.empty();
            }
        };
    }
}

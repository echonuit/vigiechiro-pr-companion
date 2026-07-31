package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// `reset-guide` (#1151) : le bilan de récupérabilité en CLI, et surtout ses **codes de sortie**.
///
/// La procédure de reset doit être **scriptable**, et un script doit pouvoir refuser d'enchaîner sur une
/// perte d'audio. D'où le code **2** (distinct du succès (0) et de l'échec (1)) dès qu'une seule nuit
/// est en « perdu ».
class CliResetGuideTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

    @TempDir
    Path workspaceDir;

    private Injector injecteur;
    private Cli cli;
    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        // La topologie naît du premier passage semé, par trouver-ou-créer.
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texteSortie() {
        return capture.texte();
    }

    @Test
    @DisplayName("#1419 : --executer sans --confirmer ne détruit rien, une base ne s'efface pas par une"
            + " option qu'on aurait laissée traîner dans un script")
    void executer_exige_confirmer() throws IOException {
        creerNuit(1, true);

        int code = cli.executer(new String[] {"reset-guide", "--executer"}, sortie, erreur);

        assertThat(code).isEqualTo(2);
        assertThat(injecteur.getInstance(PassageDao.class).findAll())
                .as("la base est intacte : rien n'a été touché")
                .hasSize(1);
    }

    @Test
    @DisplayName("#1419 : hors connexion, --executer --confirmer REFUSE, la base neuve se repeuple depuis"
            + " la plateforme, la détruire sans elle laisserait un workspace vide")
    void executer_refuse_hors_connexion() throws IOException {
        creerNuit(1, true);

        int code = cli.executer(
                new String[] {"reset-guide", "--executer", "--confirmer", "--accepter-perte"}, sortie, erreur);

        assertThat(code).isEqualTo(2);
        assertThat(texteSortie())
                .contains("Reset refusé")
                .contains("Vigie-Chiro ne répond pas")
                .as("et le refus le dit noir sur blanc : rien n'a bougé")
                .contains("Rien n'a été modifié");
        assertThat(injecteur.getInstance(PassageDao.class).findAll())
                .as("le garde-fou tient jusque dans le CLI, sur le vrai câblage")
                .hasSize(1);
    }

    @Test
    @DisplayName("Base vide : rien à perdre, code 0")
    void base_vide() {
        int code = cli.executer(new String[] {"reset-guide"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(texteSortie()).contains("rien à perdre");
    }

    @Test
    @DisplayName("Audio sur le disque : aucune perte annoncée, code 0")
    void audio_sur_disque_code_0() throws IOException {
        creerNuit(1, true);

        int code = cli.executer(new String[] {"reset-guide"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(texteSortie()).contains("[disque ]").contains("Aucune perte");
    }

    @Test
    @DisplayName("Nuit déposée en ZIP, disque vide : code 2, un script peut refuser d'enchaîner")
    void audio_perdu_code_2() throws IOException {
        Long idPassage = creerNuit(1, false);
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new DepotUniteDao(source).insert(DepotUnite.aDeposer(idPassage, "lot.zip", TypeDepotUnite.ZIP, "2026-07-01"));
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), "6a53f5fa"));

        int code = cli.executer(new String[] {"reset-guide"}, sortie, erreur);

        assertThat(code)
                .as("la perte d'audio a son propre code de sortie : elle n'est ni un succès, ni une panne")
                .isEqualTo(2);
        assertThat(texteSortie())
                .contains("[PERDU  ]")
                .contains("ne reviendra PAS")
                .contains("passages archivés")
                .as("et la commande dit le geste qui protège")
                .contains("sauvegarder --complet");
    }

    @Test
    @DisplayName("--json : le bilan est exploitable en script (source et motif par nuit)")
    void bilan_json() throws IOException {
        creerNuit(1, true);

        int code = cli.executer(new String[] {"reset-guide", "--json"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(texteSortie()).contains("\"source\": \"DISQUE\"").contains("\"motif\"");
    }

    /// Crée une nuit avec 2 séquences déclarées ; `surDisque` décide si leurs fichiers existent vraiment.
    private Long creerNuit(int numeroPassage, boolean surDisque) throws IOException {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        Workspace workspace = injecteur.getInstance(Workspace.class);
        Long idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur(ID_USER)
                .carre("130711")
                .nomSite("Carré")
                .point("Z41")
                .enregistreur(SERIE)
                .nuit(numeroPassage, 2026, "2026-07-03")
                .statut(StatutWorkflow.DEPOSE)
                .deposeLe("2026-07-03")
                .semerPassage()
                .idPassage();
        String nomSession = "Car130711-2026-Pass" + numeroPassage + "-Z41";
        Path racine = workspace.dossierSession(nomSession);
        Path transformes = workspace.dossierTransformes(nomSession);
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, racine.toString(), 0L, 0L, idPassage))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null, nomSession + "-brut.wav", racine.toString(), null, null, null, idSession))
                .id();
        if (surDisque) {
            Files.createDirectories(transformes);
        }
        SequenceDao sequenceDao = new SequenceDao(source);
        for (int index = 0; index < 2; index++) {
            String nom = nomSession + "-seq" + index + ".wav";
            Path fichier = transformes.resolve(nom);
            if (surDisque) {
                Files.writeString(fichier, "audio");
            }
            sequenceDao.insert(new SequenceDEcoute(
                    null, nom, idOriginal, index, null, null, fichier.toString(), false, idSession, null));
        }
        return idPassage;
    }
}

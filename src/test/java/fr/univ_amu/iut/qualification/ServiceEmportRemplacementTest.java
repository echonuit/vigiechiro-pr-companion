package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Ouvrir un paquet sur une nuit qui porte déjà une sélection : le remplacement se confirme.
///
/// Un seul poste suffit à poser le cas. La nuit y est emportée, puis le paquet y est rouvert : la
/// sélection tirée ici et son verdict sont ce qu'une ouverture ferait perdre.
class ServiceEmportRemplacementTest {

    private static final ProfilVigieChiro RELECTEUR =
            new ProfilVigieChiro("507f1f77bcf86cd799439011", "chiro-pierre", "Observateur");

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private SelectionDao selectionDao;
    private ServiceEmport emport;
    private long idPassage;
    private Long idSelectionLocale;
    private Long idSequenceJugee;
    private Path paquet;

    @BeforeEach
    void emporterUneNuitJugeeIci() throws IOException {
        source = new SourceDeDonnees(new Workspace(dossier.resolve("poste")));
        new MigrationSchema(source).migrer();
        selectionDao = new SelectionDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        emport = new ServiceEmport(
                selectionDao,
                sequenceDao,
                new SessionDao(source),
                new PassageDao(source),
                new PointDao(source),
                new SiteDao(source),
                new UniteDeTravail(source));

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
        long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "/ws/sess", null, null, idPassage))
                .id();
        long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "orig.wav", "/ws/orig.wav", 5.0, 384000, null, idSession))
                .id();
        String nom = "Car040962-2026-Pass1-A1-000.wav";
        Path fichier = Files.writeString(dossier.resolve(nom), "contenu");
        idSequenceJugee = sequenceDao
                .insert(new SequenceDEcoute(
                        null, nom, idOriginal, 0, 0.0, 5.0, fichier.toString(), false, idSession, null, null))
                .id();

        SelectionDEcoute locale =
                selectionDao.insert(new SelectionDEcoute(null, MethodeSelection.MANUEL, 1, idPassage));
        idSelectionLocale = locale.id();
        selectionDao.attacherSequence(new SequenceSelectionnee(idSelectionLocale, idSequenceJugee, 0, false));
        paquet = dossier.resolve("nuit.zip");
        emport.composer(idPassage, paquet);
        // Jugée après l'emport : le paquet voyage « non jugé », et le verdict Bon n'existe qu'ici.
        selectionDao.marquerVerdict(idSelectionLocale, idSequenceJugee, VerdictFichier.BON);
    }

    @Test
    @DisplayName("Sans confirmation, ouvrir un paquet refuse de remplacer la sélection déjà présente")
    void sans_confirmation_ouvrir_refuse_de_remplacer_la_selection_presente() {
        assertThatThrownBy(() -> emport.reprendre(paquet, Optional.of(RELECTEUR), false))
                .as("le refus nomme ce qui serait perdu : une sélection, et le verdict qu'elle porte")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 séquence(s), dont 1 jugée(s)")
                .hasMessageContaining("ouverture non confirmée");

        SelectionDEcoute restee = selectionDao.findByPassage(idPassage).orElseThrow();
        assertThat(restee.methode())
                .as("un refus qui aurait déjà remplacé ne serait pas un refus")
                .isEqualTo(MethodeSelection.MANUEL);
        assertThat(selectionDao.listerSequences(restee.id()))
                .as("et le verdict posé ici est encore là")
                .extracting(SequenceSelectionnee::idSequence, SequenceSelectionnee::verdict)
                .containsExactly(tuple(idSequenceJugee, VerdictFichier.BON));
    }

    @Test
    @DisplayName("Confirmé, ouvrir un paquet remplace la sélection déjà présente par celle de l'expéditeur")
    void confirme_ouvrir_remplace_la_selection_presente() throws IOException {
        ServiceEmport.BilanReprise bilan = emport.reprendre(paquet, Optional.of(RELECTEUR), true);

        assertThat(bilan.sequences())
                .as("la confirmation lève le refus : la séquence du paquet")
                .isEqualTo(1);
        SelectionDEcoute recue = selectionDao.findByPassage(idPassage).orElseThrow();
        assertThat(recue.methode())
                .as("la sélection du poste est maintenant celle de l'expéditeur")
                .isEqualTo(MethodeSelection.RECUE_D_UN_PAQUET);
        assertThat(selectionDao.listerSequences(recue.id()))
                .as("et le verdict posé ici a bien été perdu : c'est ce que la confirmation acceptait")
                .extracting(SequenceSelectionnee::verdict)
                .containsExactly(VerdictFichier.NON_JUGE);
    }

    @Test
    @DisplayName("Sans sélection à perdre, ouvrir un paquet ne demande aucune confirmation")
    void sans_selection_a_perdre_ouvrir_ne_demande_rien() throws IOException {
        new UniteDeTravail(source)
                .executer(connexion -> selectionDao.supprimerDansTransaction(connexion, idSelectionLocale));

        ServiceEmport.BilanReprise bilan = emport.reprendre(paquet, Optional.of(RELECTEUR), false);

        assertThat(bilan.sequences())
                .as("rien ne serait perdu : exiger une confirmation serait refuser sans motif")
                .isEqualTo(1);
    }
}

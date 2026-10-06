package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.DiscussionValidateur;
import fr.univ_amu.iut.audio.viewmodel.ExporteurAudio;
import fr.univ_amu.iut.audio.viewmodel.FormatAvisValidateur;
import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.PastillesEntieres;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.ExportObservationsEtSons;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les deux colonnes à pastille de la table des observations audio montrent leurs libellés en
/// entier (#6101).
///
/// La table est chargée depuis son FXML, donc avec les largeurs que l'écran déclare.
///
/// L'avis du validateur écrit un nom de taxon suivi d'une certitude. Les noms ne sont pas une
/// énumération : ils viennent du référentiel que les migrations sèment. Le test les y lit et retient
/// les plus longs, croisés avec les trois certitudes, pour que la largeur suive le référentiel.
@ExtendWith(ApplicationExtension.class)
class PastillesDesObservationsAudioTest {

    private static final String FXML = "TableObservations.fxml";

    /// Combien de noms retenir, par longueur décroissante. Le plus long en caractères n'est pas
    /// forcément le plus large une fois dessiné : en retenir plusieurs couvre cet écart.
    private static final int NOMS_RETENUS = 6;

    /// JUnit crée ce répertoire et le supprime en fin de test (#4901).
    @TempDir
    private Path dossierTemporaire;

    private TableView<LigneObservationAudio> table;
    private List<LigneObservationAudio> lignes;

    @Start
    void demarrer(Stage fenetre) throws Exception {
        ServiceValidation service = mock(ServiceValidation.class);
        AudioViewModel viewModel = new AudioViewModel(
                service,
                mock(ProjectionsAudioDao.class),
                mock(PlageNuitPassage.class),
                mock(ValidationManuelle.class),
                mock(MarquageDouteux.class),
                mock(SaisieCertitude.class),
                mock(RevueEnLot.class),
                new ExporteurAudio(
                        service,
                        mock(ServiceBibliotheque.class),
                        new ExportObservationsEtSons(mock(SequenceDao.class), mock(SessionDao.class))),
                mock(ServiceDisponibiliteAudio.class),
                chemin -> true,
                mock(DiscussionValidateur.class));
        FXMLLoader chargeur = new FXMLLoader(TableObservationsController.class.getResource(FXML));
        Parent panneau = chargeur.load();
        TableObservationsController controleur = chargeur.getController();
        controleur.installer(
                viewModel,
                new AppuisAudio(
                        mock(DepotVues.class),
                        mock(DepotDispositionColonnes.class),
                        new ExecuteurTacheSynchrone(),
                        () -> Set.of()));
        table = controleur.table();
        lignes = lignes(plusLongsNomsDuReferentiel());
        table.setItems(FXCollections.observableArrayList(lignes));
        FenetreAjustable.poserHabillee(fenetre, panneau, 1400, 900);
        FenetreAjustable.afficher(fenetre);
    }

    private List<String> plusLongsNomsDuReferentiel() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossierTemporaire));
        new MigrationSchema(source).migrer();
        return new TaxonDao(source)
                .findAll().stream()
                        .map(Taxon::nomVernaculaireFr)
                        .filter(nom -> nom != null && !nom.isBlank())
                        .distinct()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .limit(NOMS_RETENUS)
                        .toList();
    }

    /// Chaque nom avec chaque certitude, puis une ligne que personne n'a tranchée.
    private static List<LigneObservationAudio> lignes(List<String> noms) {
        List<LigneObservationAudio> lignes = new ArrayList<>();
        StatutObservation[] statuts = StatutObservation.values();
        for (String nom : noms) {
            for (Certitude certitude : Certitude.values()) {
                lignes.add(ligne(lignes.size(), statuts[lignes.size() % statuts.length], nom, certitude));
            }
        }
        lignes.add(ligne(lignes.size(), statuts[0], null, null));
        return lignes;
    }

    private static LigneObservationAudio ligne(
            int rang, StatutObservation statut, String nomValidateur, Certitude certitudeValidateur) {
        return new LigneObservationAudio(
                (long) rang,
                rang,
                1L,
                1,
                "2026-06-21",
                "640380",
                "A1",
                "Étang",
                "Pippip",
                0.9,
                "Pippip",
                null,
                statut,
                false,
                null,
                45,
                "Pipistrelle commune",
                "Pipistrelle commune",
                "Pipistrellus pipistrellus",
                "Chiroptères",
                "seq-" + rang + ".wav",
                1.0,
                2.0,
                LocalDateTime.of(2026, 6, 21, 22, 30),
                false,
                null,
                nomValidateur == null ? null : "Pipnat",
                certitudeValidateur,
                nomValidateur,
                0,
                "Ahetze");
    }

    private TableColumn<LigneObservationAudio, ?> colonne(String titre) {
        return table.getColumns().stream()
                .filter(colonne -> titre.equals(colonne.getText()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("#6101 : la colonne « Statut » montre les trois statuts de revue en entier")
    void le_statut_est_entier() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Statut"), FXML))
                .containsAll(Arrays.stream(StatutObservation.values())
                        .map(FormatLigneAudio::libelleStatut)
                        .toList());
    }

    @Test
    @DisplayName("#6101 : la colonne « Avis du validateur » montre un nom de taxon et sa certitude en entier")
    void l_avis_du_validateur_est_entier() {
        assertThat(PastillesEntieres.libellesEntiers(table, colonne("Avis du validateur"), FXML))
                .hasSize(NOMS_RETENUS * Certitude.values().length + 1)
                .containsExactlyInAnyOrderElementsOf(
                        lignes.stream().map(FormatAvisValidateur::avis).toList());
    }
}

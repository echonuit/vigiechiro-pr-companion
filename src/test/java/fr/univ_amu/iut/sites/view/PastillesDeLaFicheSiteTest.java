package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.di.DiagnosticGuice;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.PastillesEntieres;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import fr.univ_amu.iut.sites.viewmodel.LignePassage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les deux colonnes à pastille de la fiche d'un site montrent chacun de leurs libellés en
/// entier (#6101).
///
/// La fiche est ouverte par le vrai injecteur, donc avec le FXML et les largeurs de l'écran. Les
/// lignes de la table sont ensuite remplacées par une ligne par statut, les quatre verdicts et
/// l'absence de verdict : la base n'a pas à porter sept nuits pour que sept libellés se dessinent.
@ExtendWith(ApplicationExtension.class)
class PastillesDeLaFicheSiteTest {

    private static final String FXML = "SiteDetail.fxml";
    private static final String ID_USER = "u-1";

    /// JUnit crée ce répertoire et le supprime en fin de test (#4901).
    @TempDir
    private Path dossierTemporaire;

    @Start
    void demarrer(Stage fenetre) throws Exception {
        System.setProperty("vigiechiro.workspace", dossierTemporaire.toString());
        Injector injecteur = RacineInjecteur.creer();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-01-01", ID_USER));
        FXMLLoader chargeur = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        chargeur.setControllerFactory(DiagnosticGuice.pour(injecteur));
        Parent racine = chargeur.load();
        FenetreAjustable.poser(fenetre, racine, 1100, 760);
        injecteur.getInstance(NavigationSites.class).ouvrirDetail(site);
        FenetreAjustable.afficher(fenetre);
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    /// Une ligne par statut. Les verdicts tournent sur leurs quatre valeurs puis sur l'absence, que
    /// la fiche écrit « à vérifier » précédé du signe de la valeur absente.
    private static List<LignePassage> lignes() {
        StatutWorkflow[] statuts = StatutWorkflow.values();
        Verdict[] verdicts = Verdict.values();
        List<LignePassage> lignes = new ArrayList<>();
        for (int i = 0; i < statuts.length; i++) {
            Verdict verdict = i < verdicts.length ? verdicts[i] : null;
            lignes.add(
                    new LignePassage((long) i, "2026-06-22", "A1", String.valueOf(i + 1), statuts[i], verdict, "", ""));
        }
        return lignes;
    }

    private static TableColumn<LignePassage, ?> colonne(TableView<LignePassage> table, String titre) {
        return table.getColumns().stream()
                .filter(colonne -> titre.equals(colonne.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static TableView<LignePassage> tableSemee(FxRobot robot) {
        TableView<LignePassage> table = robot.lookup("#tablePassages").queryTableView();
        robot.interact(() -> table.setItems(FXCollections.observableArrayList(lignes())));
        return table;
    }

    @Test
    @DisplayName("#6101 : la colonne « Statut » de la fiche montre les sept statuts en entier")
    void le_statut_est_entier(FxRobot robot) {
        TableView<LignePassage> table = tableSemee(robot);

        assertThat(PastillesEntieres.libellesEntiers(table, colonne(table, "Statut"), FXML))
                .containsExactlyInAnyOrderElementsOf(
                        lignes().stream().map(LignePassage::statutLibelle).toList());
    }

    @Test
    @DisplayName("#6101 : la colonne « Verdict » de la fiche montre chaque verdict en entier, absence comprise")
    void le_verdict_est_entier(FxRobot robot) {
        TableView<LignePassage> table = tableSemee(robot);

        assertThat(PastillesEntieres.libellesEntiers(table, colonne(table, "Verdict"), FXML))
                .containsExactlyInAnyOrderElementsOf(
                        lignes().stream().map(LignePassage::verdictLibelle).toList())
                .containsAll(List.of(
                        Verdict.A_VERIFIER.libelle(),
                        Verdict.OK.libelle(),
                        Verdict.DOUTEUX.libelle(),
                        Verdict.A_JETER.libelle()))
                .anyMatch(libelle -> libelle.endsWith("à vérifier"));
    }
}

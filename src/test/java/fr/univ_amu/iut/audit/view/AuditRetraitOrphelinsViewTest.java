package fr.univ_amu.iut.audit.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.NettoyageDossiersOrphelins;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// L'écran d'audit **retire** les dossiers de session orphelins (#3482, ADR-3482).
///
/// Le geste est le seul de cet écran qui **écrit** sur le disque : ce test l'exerce de bout en bout, avec
/// de vrais dossiers dans un temporaire, parce que c'est le seul moyen de vérifier qu'ils disparaissent.
///
/// La confirmation passe par le [fr.univ_amu.iut.commun.view.ConfirmateurModifiable] du contrôleur : un
/// `Alert.showAndWait()` figerait TestFX headless, et le retrait ne serait jamais exercé.
@ExtendWith(ApplicationExtension.class)
class AuditRetraitOrphelinsViewTest {

    private ServiceAuditCoherence service;
    private AuditController controleur;
    private Path racine;
    private Path orphelinA;
    private Path orphelinB;

    /// Les constats que le mock rend : mutable pour que la relance d'audit après retrait puisse en
    /// rendre d'autres, comme le ferait un vrai balayage.
    private final List<ConstatAudit> constats = new ArrayList<>();

    private Path dossier(String nom) throws IOException {
        Path cree = Files.createDirectories(racine.resolve(nom));
        Files.write(cree.resolve("sequence.wav"), new byte[2048]);
        return cree;
    }

    @Start
    void start(Stage stage) throws Exception {
        racine = Files.createTempDirectory("audit-orphelins");
        orphelinA = dossier("Car130711-2026-Pass2-Z1");
        orphelinB = dossier("Car130711-2026-Pass3-Z1");
        constats.add(new ConstatAudit(
                Severite.AVERTISSEMENT,
                CategorieConstat.DOSSIER_ORPHELIN,
                null,
                orphelinA.toString(),
                "Dossier de session sur disque sans passage correspondant en base."));
        constats.add(new ConstatAudit(
                Severite.AVERTISSEMENT,
                CategorieConstat.DOSSIER_ORPHELIN,
                null,
                orphelinB.toString(),
                "Dossier de session sur disque sans passage correspondant en base."));
        // Un constat d'une AUTRE nature, dont la cible est un fichier : il ne doit jamais être ramassé.
        constats.add(new ConstatAudit(
                Severite.ERREUR,
                CategorieConstat.DISQUE_MANQUANT,
                12L,
                orphelinA.resolve("sequence.wav").toString(),
                "Fichier absent du disque."));

        service = mock(ServiceAuditCoherence.class);
        // Le mock relit le DISQUE, comme le ferait un vrai balayage : un dossier retiré cesse d'être
        // signalé. Sans cela, la relance d'audit qui suit le retrait rendrait les mêmes constats et le
        // test entérinerait un écran qui propose de retirer ce qui n'existe plus.
        when(service.auditerTout())
                .thenAnswer(appel -> new RapportAudit(constats.stream()
                        .filter(constat -> constat.categorie() != CategorieConstat.DOSSIER_ORPHELIN
                                || Files.exists(Path.of(constat.cible())))
                        .toList()));

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Provides
            AuditViewModel viewModel() {
                return new AuditViewModel(service, new NettoyageDossiersOrphelins());
            }

            @Provides
            OuvrirPassage ouvrirPassage() {
                return (idPassage, contexte) -> {};
            }

            @Provides
            DepotVues depotVues() {
                DepotVues depot = mock(DepotVues.class);
                when(depot.findByFeature("audit")).thenReturn(List.of());
                return depot;
            }
        });
        FXMLLoader loader = new FXMLLoader(AuditController.class.getResource("Audit.fxml"));
        loader.setControllerFactory(injector::getInstance);
        Parent vue = loader.load();
        controleur = loader.getController();
        FenetreAjustable.poser(stage, vue, 1000, 640);
        FenetreAjustable.afficher(stage);
    }

    @Test
    @DisplayName("#3482 : le libellé du bouton porte le nombre de dossiers concernés")
    void libelle_porte_le_nombre_de_dossiers(FxRobot robot) {
        Button bouton = robot.lookup("#boutonRetirerOrphelins").queryAs(Button.class);

        // Deux orphelins, pas trois : le constat « fichier absent » n'est pas un dossier à retirer.
        assertThat(bouton.getText()).contains("2");
        assertThat(bouton.isDisabled()).isFalse();
    }

    @Test
    @DisplayName("#3482 : la confirmation chiffre la perte AVANT d'agir, et renoncer ne touche à rien")
    void renoncer_ne_touche_a_rien(FxRobot robot) {
        List<CompteRendu> demandes = new ArrayList<>();
        controleur.confirmateur().definir(new RefusEnregistrant(demandes));

        robot.clickOn("#boutonRetirerOrphelins");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandes).hasSize(1);
        assertThat(demandes.getFirst().conclusion()).contains("4"); // 2 x 2048 octets
        // Ce qui compte : le disque est intact quand on répond non.
        assertThat(orphelinA).exists();
        assertThat(orphelinB).exists();
    }

    @Test
    @DisplayName("#3482 : accepter retire les dossiers du disque et l'annonce avec la place regagnée")
    void accepter_retire_et_rend_compte(FxRobot robot) throws Exception {
        AtomicBoolean demandee = new AtomicBoolean(false);
        controleur.confirmateur().definir(new AccordEnregistrant(demandee));

        robot.clickOn("#boutonRetirerOrphelins");
        WaitForAsyncUtils.waitForFxEvents();
        // Deux passages hors fil s'enchaînent (mesure puis retrait) : on attend que le disque ait bougé.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !Files.exists(orphelinB));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(demandee).isTrue();
        assertThat(orphelinA).doesNotExist();
        assertThat(orphelinB).doesNotExist();

        HBox bandeau = robot.lookup("#bandeauRetour").queryAs(HBox.class);
        Label message = robot.lookup("#lblRetour").queryAs(Label.class);
        assertThat(bandeau.isVisible()).isTrue();
        assertThat(message.getText()).contains("2 dossier(s) retiré(s)");

        // L'audit relancé ne propose plus de retirer ce qui n'existe plus.
        Button bouton = robot.lookup("#boutonRetirerOrphelins").queryAs(Button.class);
        assertThat(bouton.isDisabled()).isTrue();
    }

    /// Confirmateur qui **refuse** en gardant la demande, pour l'inspecter.
    private record RefusEnregistrant(List<CompteRendu> recues) implements fr.univ_amu.iut.commun.view.Confirmateur {
        @Override
        public boolean confirmer(String message) {
            return false;
        }

        @Override
        public boolean confirmer(CompteRendu compteRendu) {
            recues.add(compteRendu);
            return false;
        }
    }

    /// Confirmateur qui **accepte** en notant qu'on lui a bien demandé.
    private record AccordEnregistrant(AtomicBoolean demandee) implements fr.univ_amu.iut.commun.view.Confirmateur {
        @Override
        public boolean confirmer(String message) {
            demandee.set(true);
            return true;
        }

        @Override
        public boolean confirmer(CompteRendu compteRendu) {
            demandee.set(true);
            return true;
        }
    }
}

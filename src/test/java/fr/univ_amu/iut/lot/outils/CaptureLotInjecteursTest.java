package fr.univ_amu.iut.lot.outils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les deux injecteurs de [CaptureLot] diffèrent par **le dépôt**, et par lui seul (#5838).
///
/// L'outil en tient deux pour rendre l'écran de lot hors connexion et connecté. Le premier chargeait
/// pourtant la liaison du dépôt avec le reste de l'application : ses six aperçus dits hors connexion
/// montraient « Téléverser sur Vigie-Chiro », et aucun aperçu ne montrait l'écran réellement hors
/// connexion. Rien ne le signalait, les deux injecteurs se construisant sans erreur.
class CaptureLotInjecteursTest {

    private static final Key<Optional<DepotVigieChiro>> DEPOT = Key.get(new TypeLiteral<>() {});

    private static String workspacePrecedent;

    @TempDir
    private static Path dossierTemporaire;

    @BeforeAll
    static void espaceDeTravailJetable() throws IOException {
        // Construire un injecteur peut toucher la persistance : même précaution que le garde de câblage.
        workspacePrecedent = System.getProperty("vigiechiro.workspace");
        System.setProperty(
                "vigiechiro.workspace",
                Files.createTempDirectory(dossierTemporaire, "vc-capture-lot").toString());
    }

    @AfterAll
    static void restaurerEspaceDeTravail() {
        if (workspacePrecedent == null) {
            System.clearProperty("vigiechiro.workspace");
        } else {
            System.setProperty("vigiechiro.workspace", workspacePrecedent);
        }
    }

    @Test
    @DisplayName("#5838 : l'injecteur hors connexion ne résout aucun dépôt, et son écran ne peut pas téléverser")
    void hors_connexion_aucun_depot() {
        var injecteur = CaptureLot.creerInjecteur();

        assertThat(injecteur.getInstance(DEPOT)).isEmpty();
        assertThat(injecteur.getInstance(DepotViewModel.class).disponible())
                .as("c'est ce que l'écran lit pour offrir « Téléverser sur Vigie-Chiro »")
                .isFalse();
    }

    @Test
    @DisplayName("#5838 : l'injecteur connecté résout un dépôt, et son écran peut téléverser")
    void connecte_un_depot() {
        var injecteur = CaptureLot.creerInjecteurConnecte();

        assertThat(injecteur.getInstance(DEPOT)).isPresent();
        assertThat(injecteur.getInstance(DepotViewModel.class).disponible()).isTrue();
    }
}

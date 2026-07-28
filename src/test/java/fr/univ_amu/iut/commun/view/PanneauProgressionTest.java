package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests de [PanneauProgression] (#2642) : le suivi d'une opération longue **dans l'écran qui l'a
/// lancée**, plutôt que dans une seconde fenêtre par-dessus.
///
/// Aucune fenêtre n'est ouverte ici, et c'est tout l'intérêt : ce suivi est fait pour ne pas en ouvrir.
/// L'extension TestFX ne sert qu'à **démarrer le toolkit** - les contrôles JavaFX ne s'instancient pas
/// sans lui - et l'exécuteur synchrone déroule le travail sur le fil courant, ce qui rend l'enchaînement
/// observable sans robot ni fenêtre.
@ExtendWith(ApplicationExtension.class)
class PanneauProgressionTest {

    private StackPane zone;
    private List<Boolean> inerties;
    private PanneauProgression panneau;

    @BeforeEach
    void preparer() {
        zone = new StackPane();
        zone.setVisible(false);
        zone.setManaged(false);
        inerties = new ArrayList<>();
        panneau = new PanneauProgression(new ExecuteurTacheSynchrone(), zone, inerties::add);
    }

    @Test
    @DisplayName("Pendant l'opération, la barre est DANS la zone fournie ; après, la zone disparaît")
    void greffe_puis_retire() {
        List<String> vues = new ArrayList<>();

        panneau.lancer(
                null,
                "Connexion à Vigie-Chiro",
                (suivi, jeton) -> {
                    // Instantané pris PENDANT le travail : après coup, tout est déjà retiré.
                    vues.add(zone.getChildren().size() + "/" + zone.isVisible() + "/" + zone.isManaged());
                    return "fini";
                },
                resultat -> {},
                erreur -> {});

        assertThat(vues).containsExactly("1/true/true");
        assertThat(zone.getChildren()).isEmpty();
        assertThat(zone.isVisible()).isFalse();
        // `managed` autant que `visible` : une zone seulement invisible garderait sa place et laisserait
        // un trou à hauteur de la barre entre deux opérations.
        assertThat(zone.isManaged()).isFalse();
        assertThat(inerties).containsExactly(true, false);
    }

    @Test
    @DisplayName("Le contenu greffé porte un « Annuler » relié au jeton de l'opération")
    void annuler_est_relie_au_jeton() {
        List<String> issues = new ArrayList<>();

        panneau.lancer(
                null,
                "Connexion à Vigie-Chiro",
                (suivi, jeton) -> {
                    // Ce que fait l'utilisateur : il clique le bouton du panneau, et le travail le voit.
                    boutonAnnuler().fire();
                    jeton.leverSiAnnule();
                    return "jamais atteint";
                },
                resultat -> issues.add("succes"),
                () -> issues.add("annule"),
                erreur -> issues.add("echec:" + erreur.getClass().getSimpleName()));

        assertThat(issues).as("renoncer n'est pas échouer").containsExactly("annule");
        assertThat(zone.getChildren()).isEmpty();
        assertThat(inerties).containsExactly(true, false);
    }

    @Test
    @DisplayName("Un échec retire la zone avant de le restituer : l'écran ne reste pas occupé")
    void echec_retire_la_zone() {
        List<Boolean> zoneAuMomentDeLIssue = new ArrayList<>();

        panneau.lancer(
                null,
                "Connexion à Vigie-Chiro",
                (suivi, jeton) -> {
                    throw new IllegalStateException("réseau coupé");
                },
                resultat -> {},
                () -> {},
                erreur -> zoneAuMomentDeLIssue.add(zone.isVisible()));

        assertThat(zoneAuMomentDeLIssue)
                .as("l'issue est restituée sur un écran déjà libéré")
                .containsExactly(false);
    }

    @Test
    @DisplayName("Deux opérations successives ne laissent pas la première dans la zone")
    void deux_operations_ne_s_empilent_pas() {
        panneau.lancer(null, "Première", (suivi, jeton) -> "a", resultat -> {}, erreur -> {});
        List<Integer> enfantsPendantLaSeconde = new ArrayList<>();

        panneau.lancer(
                null,
                "Seconde",
                (suivi, jeton) -> {
                    enfantsPendantLaSeconde.add(zone.getChildren().size());
                    return "b";
                },
                resultat -> {},
                erreur -> {});

        assertThat(enfantsPendantLaSeconde).containsExactly(1);
    }

    private Button boutonAnnuler() {
        return (Button) zone.lookup(".button");
    }

    /// Garde-fou de lisibilité : l'annulation passe bien par le type dédié du socle.
    @Test
    @DisplayName("L'annulation traverse sous la forme d'OperationAnnuleeException")
    void annulation_est_typee() {
        List<Throwable> erreurs = new ArrayList<>();

        panneau.lancer(
                null,
                "Connexion à Vigie-Chiro",
                (suivi, jeton) -> {
                    throw new OperationAnnuleeException();
                },
                resultat -> {},
                () -> {},
                erreurs::add);

        assertThat(erreurs)
                .as("l'exécuteur route l'annulation vers `annule`, pas vers `echec`")
                .isEmpty();
    }
}

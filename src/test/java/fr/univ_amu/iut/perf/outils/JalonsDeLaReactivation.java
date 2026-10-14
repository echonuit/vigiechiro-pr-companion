package fr.univ_amu.iut.perf.outils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

/// Les jalons d'une réactivation menée par sa modale (#6256) : le clic, la première régénération, la
/// régénération finie, l'ancrage fini, le compte rendu. Chaque écart que la sonde relève se situe parmi
/// eux, comme ceux de l'import parmi les jalons de [JalonsDeLImport].
///
/// Les écouteurs se posent sur les barres de la modale, sur le fil JavaFX, dans le même geste que le
/// clic : la régénération tourne hors du fil, et ne peut rien publier avant qu'il soit rendu.
final class JalonsDeLaReactivation {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final DatationDesEcarts datation = new DatationDesEcarts();
    private LocalTime heureDuClic;
    private boolean regenerationCommencee;
    private boolean regenerationFinie;
    private boolean ancrageFini;
    private boolean conclue;

    /// Le clic sur « Réactiver ce passage » : l'origine de tous les instants. À appeler juste avant `fire()`.
    void marquerLeClic() {
        heureDuClic = LocalTime.now();
        marquer("clic");
    }

    /// Pose les écouteurs sur la modale que le clic vient d'ouvrir. À appeler sur le fil JavaFX.
    void poser(Stage modale) {
        ProgressBar regeneration = noeud(modale, "#barreRegeneration", ProgressBar.class);
        ProgressBar ancrage = noeud(modale, "#barreAncrage", ProgressBar.class);
        Node compteRendu = noeud(modale, "#compteRenduChiffre", Node.class);
        regeneration.progressProperty().addListener((source, avant, valeur) -> {
            if (!regenerationCommencee && valeur.doubleValue() > 0) {
                regenerationCommencee = true;
                marquer("première régénération");
            }
            if (!regenerationFinie && valeur.doubleValue() >= 1) {
                regenerationFinie = true;
                marquer("régénération finie");
            }
        });
        ancrage.progressProperty().addListener((source, avant, valeur) -> {
            if (!ancrageFini && valeur.doubleValue() >= 1) {
                ancrageFini = true;
                marquer("ancrage fini");
            }
        });
        compteRendu.visibleProperty().addListener((source, avant, visible) -> {
            if (visible && !conclue) {
                conclue = true;
                marquer("compte rendu");
            }
        });
    }

    /// Vrai une fois le compte rendu affiché. Lu sur le fil JavaFX.
    boolean conclue() {
        return conclue;
    }

    DatationDesEcarts datation() {
        return datation;
    }

    /// L'heure du clic, qui recoupe le relevé avec un enregistrement de la JVM.
    String contexte() {
        return String.format(Locale.ROOT, "clic à %s", heureDuClic.format(HEURE));
    }

    private void marquer(String nom) {
        datation.marquer(nom, System.nanoTime());
    }

    private static <T> T noeud(Stage modale, String id, Class<T> type) {
        Node trouve = modale.getScene().lookup(id);
        if (trouve == null) {
            throw new IllegalStateException("la modale de réactivation n'a pas de " + id);
        }
        return type.cast(trouve);
    }
}

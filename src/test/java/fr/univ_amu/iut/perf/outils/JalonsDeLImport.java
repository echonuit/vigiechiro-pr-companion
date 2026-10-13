package fr.univ_amu.iut.perf.outils;

import fr.univ_amu.iut.commun.viewmodel.EtatUnite;
import fr.univ_amu.iut.commun.viewmodel.LigneSuivi;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.TableView;

/// Les jalons que l'assistant d'import traverse, lus sur ses nœuds sans toucher au produit (#6175) :
/// le clic, le plan établi (la table des fichiers reçoit ses lignes), le premier et le dernier fichier
/// terminé à l'écran, le passage à `TERMINE` (la phrase de statut s'efface) et le compte rendu.
///
/// Chaque jalon se marque sur le fil JavaFX, au moment où l'écran change : un fichier « terminé » est
/// daté quand sa ligne le montre, pas quand le fil de transformation l'a fini.
///
/// Les écouteurs posés sur les lignes coûtent au fil JavaFX, au plan établi : ce coût est mesuré et
/// écrit dans le relevé, pour qu'il ne passe pas pour celui de l'application.
final class JalonsDeLImport {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final DatationDesEcarts datation = new DatationDesEcarts();
    private long terminees;
    private LocalTime heureDuClic;
    private double poseDesEcouteursMs;

    private JalonsDeLImport() {}

    /// Pose les écouteurs sur l'assistant affiché. À appeler sur le fil JavaFX, avant le clic.
    static JalonsDeLImport poser(EcranDuBanc ecran) {
        JalonsDeLImport jalons = new JalonsDeLImport();
        List<?> lignes = ecran.noeud("#tableFichiers", TableView.class).getItems();
        ((Observable) lignes).addListener(change -> jalons.suivre(lignes));
        ecran.noeud("#labelStatut", Node.class).visibleProperty().addListener((source, avant, visible) -> {
            if (!visible) {
                jalons.marquer("TERMINE");
            }
        });
        ecran.noeud("#compteRenduChiffre", Node.class).visibleProperty().addListener((source, avant, visible) -> {
            if (visible) {
                jalons.marquer("compte rendu");
            }
        });
        return jalons;
    }

    /// Le clic sur « Importer » : l'origine de tous les instants. À appeler juste avant `fire()`.
    void marquerLeClic() {
        heureDuClic = LocalTime.now();
        marquer("clic");
    }

    DatationDesEcarts datation() {
        return datation;
    }

    /// Ce qui recoupe le relevé avec le journal de la JVM (`-Xlog` horodaté à l'heure du poste), et ce
    /// que les écouteurs du banc ont coûté au fil JavaFX.
    String contexte() {
        return String.format(
                Locale.ROOT,
                "clic à %s · écouteurs du banc posés en %.1f ms au plan établi",
                heureDuClic.format(HEURE),
                poseDesEcouteursMs);
    }

    private void marquer(String nom) {
        datation.marquer(nom, System.nanoTime());
    }

    /// La table vient de recevoir ses lignes : le plan est établi, et chaque ligne se suit jusqu'à sa fin.
    private void suivre(List<?> lignes) {
        if (lignes.isEmpty()) {
            return;
        }
        marquer("plan établi");
        long debut = System.nanoTime();
        terminees = 0;
        int total = lignes.size();
        for (Object ligne : lignes) {
            ((LigneSuivi) ligne).etatProperty().addListener((source, avant, etat) -> {
                if (etat == EtatUnite.TERMINEE || etat == EtatUnite.ECHEC) {
                    terminees++;
                    if (terminees == 1) {
                        marquer("premier fichier terminé");
                    }
                    if (terminees == total) {
                        marquer("dernier fichier terminé");
                    }
                }
            });
        }
        poseDesEcouteursMs += (System.nanoTime() - debut) / 1e6;
    }
}

package fr.univ_amu.iut.lot.outils;

import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
import fr.univ_amu.iut.lot.model.EchecUnite;
import fr.univ_amu.iut.lot.viewmodel.CompteRenduChiffreDepot;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **fin d'un dépôt de nuit** (#2653), dans ses **deux états qui ont quelque chose à dire** :
///
/// - `apercu-lot-depot-compte-rendu.png` : dépôt **complet**. La barre est pleine, le volume téléversé
///   est dit (aucune surface ne le disait avant ce lot), et le pied propose **« Lancer la
///   participation »** - l'étape ④, que rien ne désignait à la fin du geste qui la rend possible ;
/// - `apercu-lot-depot-interrompu.png` : dépôt **interrompu à la demande**. C'est l'état qui piège :
///   la tentative n'a **aucun échec**, et sans la part « Restantes » la barre serait pleine et verte
///   alors qu'il manque cinq archives sur la plateforme.
///
/// **Aucune base, aucun injecteur, aucun réseau.** La bande ne dépend que du bilan et du plan qu'on lui
/// donne. Le rendu passe par le composant de production et ses feuilles réelles (ADR 0025).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureCompteRenduDepot {

    /// Largeur de rendu : celle de la zone de restitution sous la table de suivi de l'étape 3.
    private static final int LARGEUR = 900;

    private static final String APERCU_ECRIT = "Apercu ecrit dans ";

    private CaptureCompteRenduDepot() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /// L'identifiant de participation que les trois aperçus partagent : c'est le même dépôt,
    /// montré à trois moments.
    private static final String PARTICIPATION = PARTICIPATION;

    /// L'identifiant de participation des trois aperçus : c'est la même nuit qu'on montre
    /// dans trois états.
    private static final String PARTICIPATION = "part-1";

    /// La raison des deux refus de droits de l'aperçu : deux archives, une seule cause.
    private static final String DROITS_INSUFFISANTS = "HTTP 403 : droits insuffisants";

    private static void capturer() {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        rendre(
                new BilanDepot(PARTICIPATION, 14, List.of(), 4_500_000_000L),
                new CompteRenduChiffreDepot.Plan(14, 14, false),
                List.of(new CompteRenduChiffre.Action("Lancer la participation", true, () -> {})),
                sortie.resolve("apercu-lot-depot-compte-rendu.png"));
        rendre(
                new BilanDepot(PARTICIPATION, 9, List.of(), 2_900_000_000L),
                new CompteRenduChiffreDepot.Plan(14, 9, true),
                List.of(),
                sortie.resolve("apercu-lot-depot-interrompu.png"));
        // L'état que la passe 4 de la clôture #3900 a trouvé SANS APERÇU, alors que c'est celui où
        // l'utilisateur a le plus besoin d'être renseigné : des archives refusées définitivement, dont
        // deux réparables par une reconnexion et une qui ne le sera jamais. C'est aussi le seul état où
        // le bouton cesse de s'appeler « Reprendre le dépôt ».
        rendre(
                new BilanDepot(
                        PARTICIPATION,
                        11,
                        List.of(
                                new EchecUnite("Car-12.zip", DROITS_INSUFFISANTS, true, CauseRefus.AUTHENTIFICATION),
                                new EchecUnite("Car-13.zip", DROITS_INSUFFISANTS, true, CauseRefus.AUTHENTIFICATION),
                                new EchecUnite("Car-14.zip", "HTTP 422 : archive refusée", true, CauseRefus.CONTENU)),
                        3_400_000_000L),
                new CompteRenduChiffreDepot.Plan(14, 11, false),
                List.of(),
                sortie.resolve("apercu-lot-depot-refus-definitif.png"));
    }

    private static void rendre(
            BilanDepot bilan,
            CompteRenduChiffreDepot.Plan plan,
            List<CompteRenduChiffre.Action> actions,
            Path fichier) {
        PanneauCompteRendu bande = new PanneauCompteRendu();
        bande.afficher(CompteRenduChiffreDepot.de(bilan, plan, actions));
        // Marge autour de la bande : la capture montre le composant tel qu'il s'insère sous la table.
        VBox cadre = new VBox(bande);
        cadre.setStyle("-fx-padding: 16; -fx-background-color: #f5f6f8;");
        Scene scene = new Scene(cadre, LARGEUR, -1);
        // Habillage commun (#3374) : la paire palette+base, posée au niveau où la palette vit.
        Habillage.poser(scene);
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println(APERCU_ECRIT + fichier.toAbsolutePath());
    }
}

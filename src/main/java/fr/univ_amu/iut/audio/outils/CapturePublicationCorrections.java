package fr.univ_amu.iut.audio.outils;

import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.ConfirmationNavigation;
import fr.univ_amu.iut.commun.view.DialogueProgression;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.TriPublication;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture les **deux modales de la publication des corrections** (#723, #1838), que rien n'illustrait :
///
/// - `apercu-publication-confirmation.png` : le récapitulatif montré **avant** d'écrire quoi que ce soit
///   sur la plateforme (ce qui part, ce qui sera d'abord ancré, ce qui reste à quai, l'irréversibilité) ;
/// - `apercu-publication-progression.png` : la fenêtre de progression du rapatriement d'ancrage, avec sa
///   barre déterminée et son bouton « Annuler » - le seul signe visible que le geste peut durer.
///
/// **Aucune base, aucun injecteur, aucun réseau.** Ces deux modales ne dépendent que de ce qu'on leur
/// donne à afficher : la première d'un [TriPublication] (dont elle ne lit que des **comptes**), la seconde
/// d'une [Progression]. Les seules données de démonstration sont donc ces comptes et cette étape.
///
/// Ce qui **n'est pas** de la démonstration, c'est le texte : le récapitulatif vient de
/// [PublicationCorrectionsViewModel#recapitulatif], l'`Alert` de [ConfirmationNavigation#dialogue] et le contenu
/// de la progression de [DialogueProgression#apercu] - tous trois du code de production. Un fac-similé
/// assemblé ici n'engagerait personne, et c'est ainsi que des dialogues documentés ont dérivé du produit
/// (#1468). Ce que la doc montre est ce que l'utilisateur verra.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CapturePublicationCorrections {

    /// Une observation **sans contenu** : le récapitulatif ne lit que `publiables().size()`. Lui composer
    /// de vraies détections donnerait l'illusion d'une donnée qui n'est jamais affichée.
    private static final Observation PLACEHOLDER = new Observation(
            1L,
            1L,
            0.0,
            5.0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            false,
            null,
            null,
            Certitude.SUR,
            null,
            null);

    private CapturePublicationCorrections() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
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

    private static void capturer() throws IOException {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        rendreConfirmation(sortie.resolve("apercu-publication-confirmation.png"));
        rendreProgression(sortie.resolve("apercu-publication-progression.png"));
    }

    /// Le récapitulatif dans son cas le **plus riche** : des corrections prêtes, d'autres à ancrer d'abord
    /// (la branche ouverte par #1838), et des écartées de deux causes. C'est celui qui porte le plus
    /// d'information ; les cas plus simples en sont des sous-ensembles.
    private static void rendreConfirmation(Path fichier) {
        TriPublication tri = new TriPublication(Collections.nCopies(12, PLACEHOLDER), 3, 5, 1);
        Alert alerte = new ConfirmationNavigation("Publier les corrections vers Vigie-Chiro ?")
                .dialogue(ApercuFx.enrouler(PublicationCorrectionsViewModel.recapitulatif(tri, true)));
        alerte.getDialogPane().setPrefWidth(560);
        ApercuFx.enregistrerDialogPane(alerte.getDialogPane(), styles(), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// La progression **en cours de rapatriement**, page 3 sur 12 : l'étape est celle qu'émet réellement
    /// `AcquisitionAncrage` (libellé et fraction compris), pour que l'image dise ce que l'utilisateur lit.
    private static void rendreProgression(Path fichier) throws IOException {
        VBox contenu = DialogueProgression.apercu(
                "Publication des corrections",
                new Progression("Récupération des identifiants et des échanges avec le validateur… (page 3/12)", 0.25));
        Scene scene = new Scene(contenu);
        scene.getStylesheets().addAll(styles());
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Feuilles de style partagées (palette indigo + base), comme les autres captures de dialogue : sans
    /// elles, l'image montrerait le thème par défaut de JavaFX et non celui de l'application.
    private static List<String> styles() {
        List<String> feuilles = new ArrayList<>();
        for (String nom : List.of("palette.css", "base.css")) {
            var url = ConfirmationNavigation.class.getResource(nom);
            if (url != null) {
                feuilles.add(url.toExternalForm());
            }
        }
        return feuilles;
    }
}

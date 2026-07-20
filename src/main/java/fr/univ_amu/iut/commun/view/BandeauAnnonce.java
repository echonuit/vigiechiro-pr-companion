package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/// Câble le **bandeau d'annonce du chrome** aux [AnnonceChrome] contribuées (#2109).
///
/// Isolé de [MainController] pour le garder compact, comme [BarreStatut] et [ConstructeurMenuOutils].
///
/// **La recherche se fait hors du fil JavaFX** ([ExecuteurTache]) : une annonce peut interroger le
/// réseau, et le faire au démarrage sur le fil d'affichage figerait la fenêtre avant même qu'elle
/// n'apparaisse - exactement ce qu'un utilisateur en zone mal couverte subirait le plus.
///
/// **Une seule annonce à la fois**, la première trouvée. Empiler des bandeaux au lancement
/// transformerait l'accueil en boîte de réception ; s'il en existe un jour plusieurs, il vaudra mieux
/// les prioriser explicitement que de tout montrer.
public final class BandeauAnnonce {

    private final Set<AnnonceChrome> annonces;
    private final ExecuteurTache executeur;
    private final OuvreurDeLien ouvreur;

    @com.google.inject.Inject
    public BandeauAnnonce(Set<AnnonceChrome> annonces, ExecuteurTache executeur, OuvreurDeLien ouvreur) {
        this.annonces = Objects.requireNonNull(annonces, "annonces");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.ouvreur = Objects.requireNonNull(ouvreur, "ouvreur");
    }

    /// Installe le bandeau : cherche les annonces en tâche de fond, puis affiche la première.
    ///
    /// Tant qu'il n'y a rien à dire - le cas le plus fréquent - le conteneur reste invisible **et non
    /// managé** : il ne prend aucune place, plutôt que de laisser une bande vide sous la barre de
    /// navigation.
    ///
    /// @param conteneur le bandeau lui-même (visibilité)
    /// @param texte le libellé du message
    /// @param lien le lien d'action, masqué si l'annonce n'en porte pas
    /// @param fermer la croix de fermeture
    public void installer(HBox conteneur, Label texte, Hyperlink lien, Button fermer) {
        Objects.requireNonNull(conteneur, "conteneur");

        masquer(conteneur);
        fermer.setOnAction(evenement -> masquer(conteneur));

        if (annonces.isEmpty()) {
            // Aucune feature contributrice active : rien à chercher, donc aucune tâche de fond.
            return;
        }

        executeur.executer(
                () -> premiere(annonces),
                trouvee -> trouvee.ifPresent(annonce -> afficher(conteneur, texte, lien, annonce, ouvreur)),
                // Une annonce qui échoue ne doit rien casser : elle est un confort, pas une fonction.
                // Le contrat d'AnnonceChrome demande déjà de rendre vide plutôt que de lever ; ce filet
                // couvre l'implémentation qui l'oublierait.
                echec -> masquer(conteneur));
    }

    /// La première annonce à afficher, dans l'ordre stable des contributions.
    private static Optional<AnnonceChrome.Annonce> premiere(Set<AnnonceChrome> annonces) {
        return List.copyOf(annonces).stream()
                .map(AnnonceChrome::chercher)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static void afficher(
            HBox conteneur, Label texte, Hyperlink lien, AnnonceChrome.Annonce annonce, OuvreurDeLien ouvreur) {
        texte.setText(annonce.message());

        boolean actionnable =
                annonce.adresseAction() != null && !annonce.adresseAction().isBlank();
        lien.setText(actionnable ? annonce.libelleAction() : "");
        lien.setVisible(actionnable);
        lien.setManaged(actionnable);
        if (actionnable) {
            lien.setOnAction(evenement -> ouvreur.ouvrir(annonce.adresseAction()));
        }

        // La sévérité s'ajoute ici, comme le fait BandeauRetour : le FXML ne porte que
        // `bandeau-retour`, la couleur vient du code. Une annonce est une information neutre.
        if (!conteneur.getStyleClass().contains("retour-info")) {
            conteneur.getStyleClass().add("retour-info");
        }
        conteneur.setVisible(true);
        conteneur.setManaged(true);
    }

    private static void masquer(HBox conteneur) {
        conteneur.setVisible(false);
        conteneur.setManaged(false);
    }
}

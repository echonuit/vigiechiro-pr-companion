package fr.univ_amu.iut.passage.view;

import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.passage.model.ModeRebranchement;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Action IHM « Réactiver ce passage » (#1302), extraite de [PassageController] (pur câblage, PMD
/// GodClass) : demande le dossier des fichiers réimportés, puis ouvre la **modale de réactivation** (#1780).
/// C'est elle qui lance l'opération **hors du fil JavaFX** et suit ses deux phases - régénération des
/// séquences, puis acquisition de l'ancrage - sur deux barres distinctes, plutôt qu'une barre unique qui
/// restait figée à 100 % pendant l'ancrage réseau.
///
/// Aucune confirmation destructive : l'opération **ajoute** de l'audio (les fichiers sont copiés, la
/// sauvegarde de l'utilisateur reste intacte). Le compte rendu - ce qui est revenu et sur quelle preuve, ce
/// qui a été refusé et pourquoi, ce qui manque - s'affiche **dans** la modale
/// ([fr.univ_amu.iut.passage.viewmodel.ReactivationModaleViewModel]).
final class ActionReactivation {

    private final PassageViewModel viewModel;
    private final NavigationPassage navigation;
    private final Supplier<Window> proprietaire;
    private final SelecteurFichier selecteur;
    private final ConfirmateurModifiable confirmateur;
    private final Runnable recharger;

    /// @param viewModel ViewModel de M-Passage (porte la réactivation et connaît l'idPassage courant)
    /// @param navigation façade de navigation de la feature : ouvre la modale de réactivation (FXML injecté)
    /// @param proprietaire fenêtre propriétaire de la modale, lue **au moment du geste** (la scène n'existe
    ///     pas forcément à la construction du contrôleur)
    /// @param selecteur porteur de désignation partagé de l'écran (#1431) : c'est lui qui demande le dossier
    ///     des fichiers d'origine. Un `DirectoryChooser` en dur **figeait** tout test du geste
    /// @param recharger rejeu de l'ouverture de l'écran après réactivation (volumes, boutons)
    ActionReactivation(
            PassageViewModel viewModel,
            NavigationPassage navigation,
            Supplier<Window> proprietaire,
            SelecteurFichier selecteur,
            ConfirmateurModifiable confirmateur,
            Runnable recharger) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.proprietaire = Objects.requireNonNull(proprietaire, "proprietaire");
        this.selecteur = Objects.requireNonNull(selecteur, "selecteur");
        this.confirmateur = Objects.requireNonNull(confirmateur, "confirmateur");
        this.recharger = Objects.requireNonNull(recharger, "recharger");
    }

    /// Demande le dossier des fichiers d'origine, puis ouvre la modale qui vérifie et rebranche ce qui
    /// correspond. Rien ne se passe si l'utilisateur renonce au choix du dossier.
    void reactiver() {
        Optional<Path> dossier =
                selecteur.choisirDossier("Dossier des fichiers d'origine à réimporter", Optional.empty());
        if (dossier.isEmpty()) {
            return;
        }
        Path source = dossier.orElseThrow();
        ModeRebranchement mode = demanderLeMode(source);
        navigation.ouvrirModaleReactivation(
                proprietaire.get(),
                (progresRegeneration, progresAncrage, jeton) ->
                        viewModel.reactiver(source, mode, progresRegeneration, progresAncrage, jeton),
                recharger);
    }

    /// Copier, ou laisser l'audio où il est ? La question est **posée dans tous les cas**, mais sa
    /// recommandation se déduit du dossier désigné : hors de l'espace de travail, ces fichiers sont
    /// ceux de l'utilisateur, et les recopier ferait un doublon qu'il n'a pas demandé.
    ///
    /// La conséquence est dite, pas tue : référencer rend la nuit muette si le support s'absente.
    private ModeRebranchement demanderLeMode(Path source) {
        boolean aLutilisateur = viewModel.horsEspaceDeTravail(source);
        String question = aLutilisateur
                ? "Ce dossier est en dehors de votre dossier de travail : ces fichiers sont les vôtres.\n\n"
                        + "Les laisser où ils sont (recommandé) ? L'application s'y référera sans rien copier.\n"
                        + "Cette nuit ne sera plus écoutable si ce support n'est pas accessible (disque débranché,"
                        + " dossier réseau hors ligne), et le redeviendra dès qu'il le sera.\n\n"
                        + "Répondre « non » copiera les fichiers dans votre dossier de travail."
                : "Laisser ces fichiers où ils sont, sans en faire de copie ?\n\n"
                        + "Répondre « non » les copiera à l'emplacement que l'application attend.";
        return confirmateur.confirmer(question) ? ModeRebranchement.REFERENCE : ModeRebranchement.COPIE;
    }
}

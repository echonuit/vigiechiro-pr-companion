package fr.univ_amu.iut.importation.view;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.view.ChoixDansListe;
import fr.univ_amu.iut.commun.view.ChoixParBoutons;
import fr.univ_amu.iut.commun.view.DialogueProgression;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.NotificateurModifiable;
import fr.univ_amu.iut.commun.view.NotificationDialogue;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.importation.model.ServiceImportReference;
import fr.univ_amu.iut.importation.view.ActionImportTransformes.PointRattachable;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Assemble [ActionImportTransformes] pour [ImportationController]. Porte les collaborateurs **durables**
/// (service métier, source des sites, utilisateur courant, espace de travail, exécuteur), injectés une
/// seule fois ; le contrôleur n'y ajoute, au clic, que les collaborateurs **de scène** (la fenêtre et ses
/// dialogues déjà en place). Cette fabrique existe pour garder [ImportationController] **mince** : sans
/// elle, quatre champs et un second constructeur y feraient franchir le plafond `NcssCount` (GodClass).
public final class FabriqueActionImportTransformes {

    private final ServiceImportReference service;
    private final ServiceSites serviceSites;
    private final String idUtilisateur;
    private final Workspace workspace;
    private final ExecuteurTache executeur;

    @Inject
    public FabriqueActionImportTransformes(
            ServiceImportReference service,
            ServiceSites serviceSites,
            @Named("idUtilisateurCourant") String idUtilisateur,
            Workspace workspace,
            ExecuteurTache executeur) {
        this.service = Objects.requireNonNull(service, "service");
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    /// Assemble l'action avec les collaborateurs **de scène** du contrôleur : la fenêtre propriétaire (lue au
    /// clic), le sélecteur et le confirmateur déjà en place (substituables en test), et le rafraîchissement
    /// d'écran. Les dialogues restants (liste de choix, notification, progression) sont bâtis ici sur cette
    /// même fenêtre.
    ///
    /// @param proprietaire fenêtre propriétaire des dialogues, lue au moment du geste
    /// @param selecteur porteur de désignation du dossier des transformés
    /// @param recharger rafraîchissement de l'écran après un import réussi
    ActionImportTransformes creer(Supplier<Window> proprietaire, SelecteurFichier selecteur, Runnable recharger) {
        Supplier<List<PointRattachable>> points = () -> ActionImportTransformes.pointsDe(serviceSites, idUtilisateur);
        return new ActionImportTransformes(
                service,
                workspace,
                proprietaire,
                selecteur,
                points,
                new ChoixDansListe<>(proprietaire),
                new ChoixParBoutons<>("Importer des transformés", proprietaire),
                new NotificateurModifiable(new NotificationDialogue(proprietaire)),
                new DialogueProgression(executeur),
                recharger);
    }
}

package fr.univ_amu.iut.connexion.viewmodel;

import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.viewmodel.EtatConnexion;
import java.util.Objects;
import java.util.concurrent.Executor;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

/// Reflet observable du jeton disponible (#4205) : implémentation du port [EtatConnexion].
///
/// Même figure que `RevisionDonnees` et `ReglagesReactifs` : un service qui n'est pas observable le
/// devient par une couche mince au-dessus de lui. La source de vérité reste [FournisseurToken] -
/// jeton ponctuel de la CLI, sinon connexion enregistrée - et cette classe n'en garde qu'un reflet.
///
/// ## Pourquoi un reflet, et non une lecture à la demande
///
/// Une propriété JavaFX n'invalide ses liaisons que lorsqu'elle **change**. Un `estConnecte()` relu à
/// chaque affichage ne réveille personne : il faut que quelqu'un pose la nouvelle valeur. C'est le
/// rôle de [#relire()], appelé par la modale de connexion - le seul endroit de l'IHM où le jeton
/// stocké change.
///
/// ## Pourquoi un exécuteur injecté
///
/// Comme `RevisionDonnees` : la connexion se termine sur un fil d'arrière-plan, une `Property` JavaFX
/// se mute sur le fil JavaFX. `Platform.runLater` en dur rendrait cette classe intestable hors TestFX.
public final class RefletDuJeton implements EtatConnexion {

    private final FournisseurToken jetons;
    private final Executor filAffichage;
    private final ReadOnlyBooleanWrapper connecte;

    public RefletDuJeton(FournisseurToken jetons, Executor filAffichage) {
        this.jetons = Objects.requireNonNull(jetons, "jetons");
        this.filAffichage = Objects.requireNonNull(filAffichage, "filAffichage");
        // La valeur initiale se lit au berceau, et non à la première connexion : un jeton déjà
        // enregistré au démarrage doit ouvrir les gestes SANS qu'on ait à se reconnecter. Partir de
        // `false` grisserait tout jusqu'à la première ouverture de la modale.
        this.connecte =
                new ReadOnlyBooleanWrapper(this, "connecte", jetons.token().isPresent());
    }

    @Override
    public ReadOnlyBooleanProperty connecteProperty() {
        return connecte.getReadOnlyProperty();
    }

    /// Relit la source de vérité et publie le résultat, **sur le fil d'affichage**. Appelable depuis
    /// n'importe quel fil. Idempotent : reposer la même valeur ne réveille aucun observateur.
    public void relire() {
        filAffichage.execute(() -> connecte.set(jetons.token().isPresent()));
    }
}

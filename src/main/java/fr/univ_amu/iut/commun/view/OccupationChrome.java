package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.layout.StackPane;

/// Voile d'occupation du **chrome entier** (#1215) : les traitements longs déclenchés depuis le menu
/// « ☰ » (sauvegarde / restauration de la base, purge des originaux) tournent hors du fil JavaFX sous
/// un voile posé sur la **racine de la fenêtre** - ils ne concernent aucun écran en particulier.
///
/// Singleton du socle, en deux temps : le [MainController] l'**installe** au démarrage (hôte = racine
/// `StackPane` du chrome), les actions du menu ☰ le consomment par injection. Chaque [#occuper] pose
/// aussi l'**opération critique** (#906) le temps du travail : fermer l'application ou naviguer
/// pendant une sauvegarde déclenche l'avertissement du socle au lieu d'un arrêt silencieux.
///
/// Hors chrome (outils, contexte partiel), [#occuper] reste utilisable sans voile : le travail passe
/// par l'[ExecuteurTache] injecté (synchrone en test et en capture, #1278).
@Singleton
public class OccupationChrome {

    private final ExecuteurTache executeur;
    private final NavigationViewModel navigation;
    private IndicateurOccupation indicateur;

    @Inject
    public OccupationChrome(ExecuteurTache executeur, NavigationViewModel navigation) {
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /// Prépare le voile au-dessus de `hote` (la racine `StackPane` du chrome). Appelée une fois par le
    /// [MainController] à l'initialisation.
    void installer(StackPane hote) {
        indicateur = new IndicateurOccupation(hote, executeur);
    }

    /// Exécute `travail` hors du fil JavaFX sous le voile du chrome, en posant l'opération critique
    /// (#906) le temps du travail. Sur le fil JavaFX, l'opération critique est effacée puis le
    /// résultat remis à `succes` (ou l'erreur à `echec`) - exactement l'un des deux est appelé.
    ///
    /// @param libelle libellé du voile (« Sauvegarde de la base… »)
    /// @param operationCritique libellé de l'opération pour l'avertissement de sortie (#906),
    ///     à l'infinitif complété (« la sauvegarde de la base »)
    public <T> void occuper(
            String libelle,
            String operationCritique,
            Supplier<T> travail,
            Consumer<T> succes,
            Consumer<Throwable> echec) {
        navigation.setOperationCritique(operationCritique);
        if (indicateur == null) {
            executeur.executer(travail, conclure(succes), conclure(echec));
            return;
        }
        indicateur.occuper(libelle, travail, conclure(succes), conclure(echec));
    }

    /// Efface l'opération critique avant de rendre la main à la suite (succès comme échec).
    private <T> Consumer<T> conclure(Consumer<T> suite) {
        return valeur -> {
            navigation.setOperationCritique("");
            suite.accept(valeur);
        };
    }
}

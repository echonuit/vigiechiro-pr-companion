package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirAudio;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Implémentation du contrat socle [OuvrirAudio] : charge la vue audio unifiée (`SonsValidation.fxml`)
/// et la publie dans la zone centrale du chrome via le [Navigateur].
///
/// Même patron que les autres façades de navigation du projet (`NavigationSites`…) : seul point de la feature
/// qui sait charger un FXML, avec la `controllerFactory` branchée sur Guice (`injector::getInstance`)
/// pour que [SonsValidationController] reçoive son ViewModel par injection. La [SourceObservations] (et
/// la cible de focus éventuelle) est transmise au controller **après** le chargement, avant publication.
///
/// La source `References` (atteinte depuis l'accueil, elle remplace l'écran bibliothèque) ouvre **en
/// racine** ; les sources atteintes depuis un écran parent (un passage via M-Passage, et à terme analyse
/// et multisite) sont **empilées** sur l'historique pour offrir le « ← Retour ». Le fil d'Ariane
/// hiérarchique, lui, est reconstruit par le controller ([SonsValidationController#emplacement]).
@Singleton
public class NavigationAudio implements OuvrirAudio {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationAudio(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    @Override
    public void ouvrir(SourceObservations source) {
        ouvrir(source, null);
    }

    @Override
    public void ouvrir(SourceObservations source, Long idObservationCible) {
        FXMLLoader loader = new FXMLLoader(NavigationAudio.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            SonsValidationController controleur = loader.getController();
            controleur.ouvrirSur(source, idObservationCible);
            if (source instanceof SourceObservations.References) {
                navigateur.ouvrirRacine(vue, "audio", "Sons & validation", controleur);
            } else {
                navigateur.empiler(vue, "audio", "Sons & validation", controleur);
            }
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }
}

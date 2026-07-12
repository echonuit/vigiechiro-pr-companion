package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Ouverture de l'écran « Réglages » (#927) depuis le menu ☰. Écran **du socle** (pas d'une feature) :
/// cette façade vit donc dans `commun.view`, calquée sur les `Navigation*` des features (chargement du
/// FXML via la `controllerFactory` Guice, puis empilement dans le [Navigateur]).
///
/// Expose aussi [#aDesReglages()] pour que le socle **grise** l'entrée de menu tant qu'aucune feature
/// ne contribue de réglage affichable (affordance : pas d'entrée menant à un écran vide).
@Singleton
public class NavigationReglages {

    private final Injector injector;
    private final Navigateur navigateur;
    private final Set<OngletReglages> onglets;

    @Inject
    public NavigationReglages(Injector injector, Navigateur navigateur, Set<OngletReglages> onglets) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
        this.onglets = Objects.requireNonNull(onglets, "onglets");
    }

    /// Vrai si au moins une contribution a quelque chose à afficher (cf.
    /// [EcranReglagesController#estAffichable]). Faux -> l'écran serait vide, on désactive l'entrée ☰.
    public boolean aDesReglages() {
        return onglets.stream().anyMatch(EcranReglagesController::estAffichable);
    }

    /// Charge `EcranReglages.fxml` (controllers injectés par Guice) et l'empile dans la zone centrale.
    public void ouvrir() {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationReglages.class, "EcranReglages.fxml");
        loader.setControllerFactory(injector::getInstance);
        Parent vue;
        try {
            vue = loader.load();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
        navigateur.empiler(vue, "reglages", "Réglages", loader.getController());
    }
}

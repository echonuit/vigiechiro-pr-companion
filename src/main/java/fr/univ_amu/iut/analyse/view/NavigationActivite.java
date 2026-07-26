package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.OuvrirActivite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/// Façade de navigation de l'écran **Activité de la nuit** (#2352) : charge M-Activite pour un passage et
/// l'affiche dans la zone centrale du chrome.
///
/// Même patron que [NavigationDiagnostic] : le [FXMLLoader] reçoit la `controllerFactory` Guice, si bien
/// que [ActiviteController] obtient son ViewModel par injection. Fournit le contrat socle
/// [OuvrirActivite] (bindé par `ActiviteModule`), que le `view` de `passage` (M-Passage) injecte sans
/// dépendre de cette feature.
@Singleton
public class NavigationActivite implements OuvrirActivite {

    private final Injector injector;
    private final Navigateur navigateur;

    @Inject
    public NavigationActivite(Injector injector, Navigateur navigateur) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.navigateur = Objects.requireNonNull(navigateur, "navigateur");
    }

    /// Affiche l'activité **d'un passage** dans la zone centrale du chrome, **empilée** sur le passage (le
    /// contexte alimente le fil d'Ariane). C'est l'entrée « depuis un passage » (carte de M-Passage).
    @Override
    public void ouvrir(ContextePassage passage) {
        Chargement chargement = charger();
        chargement.controleur().ouvrirSur(passage);
        navigateur.empiler(chargement.vue(), "activite", "Activité de la nuit", chargement.controleur());
    }

    /// Affiche l'activité de **tous les passages** de l'utilisateur comme écran **racine** (entrée
    /// transverse depuis l'accueil, prisme « Espèces & biodiversité »). Sans contexte de passage : le fil
    /// d'Ariane est `Accueil › Activité de la nuit`, et la fenêtre filtrable couvre toutes les nuits.
    public void ouvrirTout() {
        String idUtilisateur = injector.getInstance(Key.get(String.class, Names.named("idUtilisateurCourant")));
        Chargement chargement = charger();
        chargement.controleur().ouvrirTout(idUtilisateur);
        navigateur.ouvrirRacine(chargement.vue(), "activite", "Activité de la nuit", chargement.controleur());
    }

    private Chargement charger() {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationActivite.class, "Activite.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            return new Chargement(loader.load(), loader.getController());
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }

    private record Chargement(Parent vue, ActiviteController controleur) {}
}

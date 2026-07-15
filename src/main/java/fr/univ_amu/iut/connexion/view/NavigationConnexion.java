package fr.univ_amu.iut.connexion.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

/// Façade d'ouverture de la **modale « Connexion VigieChiro »** (#727/#741). Utilisée par l'entrée de
/// menu [ActionConnexion] de la feature (#931) : le menu ☰ du chrome ouvre la modale et affiche l'état
/// de connexion, sans que `commun` dépende de la feature.
///
/// Charge le FXML avec la `controllerFactory` Guice (comme les autres modales du projet) et l'affiche
/// en fenêtre modale applicative, **sans propriétaire** : la modale est déclenchée depuis le menu, hors
/// de tout contexte de fenêtre.
@Singleton
public final class NavigationConnexion {

    private final Injector injector;
    private final StockageConnexion stockage;

    @Inject
    public NavigationConnexion(Injector injector, StockageConnexion stockage) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.stockage = Objects.requireNonNull(stockage, "stockage");
    }

    /// Ouvre la modale de connexion (non bloquante).
    public void ouvrir() {
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationConnexion.class, "ConnexionModale.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            Stage modale = new Stage();
            modale.initModality(Modality.APPLICATION_MODAL);
            modale.setTitle("Connexion VigieChiro");
            modale.setScene(new Scene(vue));
            Modales.fermerParEchap(modale);
            modale.show();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }

    /// Libellé de l'entrée de menu selon l'état stocké (sans réseau) : identité si connecté, invite
    /// sinon. Emoji cohérent avec les autres entrées du menu ☰ (💾 / ↩ / 🧹).
    public String libelleMenu() {
        return stockage.profil()
                .map(profil -> "✅ VigieChiro : "
                        + (profil.pseudo() == null ? "?" : profil.pseudo())
                        + (profil.role() == null ? "" : " (" + profil.role() + ")"))
                .orElse("🔌 Se connecter à VigieChiro…");
    }
}

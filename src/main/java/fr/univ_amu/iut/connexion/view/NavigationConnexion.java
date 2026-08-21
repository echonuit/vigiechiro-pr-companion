package fr.univ_amu.iut.connexion.view;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.view.ChargeurFxml;
import fr.univ_amu.iut.commun.view.Habillage;
import fr.univ_amu.iut.commun.view.Modales;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

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
    /// Ouvre la modale de connexion **au-dessus de** `proprietaire`.
    ///
    /// ⚠️ Le propriétaire n'est pas décoratif, et son absence a coûté un clip illisible. Sans lui,
    /// le gestionnaire de fenêtres pose la modale où il veut : elle ne se centre pas sur
    /// l'application, peut passer derrière elle, et ne la suit pas quand on la déplace. Le film de
    /// `S1-26` la montrait collée en haut à gauche, rognée, puis ailleurs à l'image suivante - soit
    /// exactement le « saut » que ce cas affirme absent.
    ///
    /// ⚠️ Sept écrans du produit ouvrent une modale ; six posaient déjà leur propriétaire. Celui-ci
    /// était le seul à ne pas le faire, alors que [ActionConnexion] le RECEVAIT et le jetait. La
    /// plomberie existait, elle n'était pas branchée.
    public void ouvrir(Window proprietaire) {
        Objects.requireNonNull(proprietaire, "proprietaire");
        FXMLLoader loader = ChargeurFxml.chargeur(NavigationConnexion.class, "ConnexionModale.fxml");
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent vue = loader.load();
            Stage modale = new Stage();
            modale.initOwner(proprietaire);
            Modales.centrerSur(modale, proprietaire);
            // ⚠️ `WINDOW_MODAL`, comme les huit autres modales du produit. Celle-ci était la seule en
            // `APPLICATION_MODAL`, et la seule sans propriétaire : deux écarts au même endroit, tous
            // deux invisibles tant que personne ne regardait la fenêtre. Avec un propriétaire, la
            // modale se centre sur l'application au lieu d'aller se coller dans un coin.
            modale.initModality(Modality.WINDOW_MODAL);
            modale.setTitle("Connexion Vigie-Chiro");
            modale.setScene(Habillage.scene(vue));
            Modales.fermerParEchap(modale);
            modale.show();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement FXML impossible : " + loader.getLocation(), echec);
        }
    }

    /// Libellé de l'entrée de menu selon l'état stocké (sans réseau) : identité si connecté, invite
    /// sinon. Son **icône** suit le même état ([#iconeMenu()]) : elle est portée par le contrat
    /// `ActionMenu`, plus par le libellé (#1933).
    public String libelleMenu() {
        return stockage.profil()
                .map(profil -> "Vigie-Chiro : "
                        + (profil.pseudo() == null ? "?" : profil.pseudo())
                        + (profil.role() == null ? "" : " (" + profil.role() + ")"))
                .orElse("Se connecter à Vigie-Chiro…");
    }

    /// Icône de l'entrée de menu : une prise quand il reste à se brancher, une coche une fois l'identité
    /// connue. Une icône figée dirait le contraire du libellé une fois sur deux.
    public String iconeMenu() {
        return stockage.profil().isPresent() ? "fas-check-circle" : "fas-plug";
    }
}

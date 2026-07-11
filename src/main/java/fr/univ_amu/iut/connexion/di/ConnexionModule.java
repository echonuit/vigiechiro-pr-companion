package fr.univ_amu.iut.connexion.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.view.ActionConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import java.util.Set;

/// Module Guice de la feature `connexion` (#727/#741). Câble :
/// - le stockage local du token comme [FournisseurToken] du socle (consommé par [ClientVigieChiro]) ;
/// - le [ClientVigieChiro] (paquet `commun.api`) construit sur ce fournisseur de token ;
/// - l'entrée « Connexion » du menu ☰ via une contribution [ActionMenu] (#931), sans que `commun`
///   connaisse la feature.
public class ConnexionModule extends ModuleDeFeature {

    /// Identité de la feature. `COEUR` : socle non désactivable (dépendue par d'autres features).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("connexion", "Connexion VigieChiro", Categorie.COEUR);
    }

    @Override
    protected void configure() {
        // Le stockage local EST la source du token pour tout le socle réseau (commun.api).
        bind(FournisseurToken.class).to(StockageConnexion.class);
        // Entrée « Se connecter à VigieChiro… » du menu ☰ (#741/#931) : contribuée au point d'extension
        // du socle (`Multibinder<ActionMenu>`), sans que le socle connaisse la connexion. Remplace
        // l'ancien contrat `OuvrirConnexion` + défaut inerte, qui n'existaient que pour cette entrée.
        actionMenu(ActionConnexion.class);
        // Déclare le point d'extension de rapprochement (#728). Vide ici : les features taxons/sites y
        // contribuent leurs rapprocheurs. Déclaré même sans contributeur pour que `ConnexionViewModel`
        // reçoive un Set (éventuellement vide) quand seule `connexion` est chargée (outil de capture).
        Multibinder.newSetBinder(binder(), RapprochementVigieChiro.class);
    }

    @Provides
    @Singleton
    StockageConnexion fournirStockage(Workspace workspace, Horloge horloge) {
        return new StockageConnexion(workspace, horloge);
    }

    /// Client de l'API VigieChiro, alimenté par le token stocké. Singleton : partagé par les futures
    /// features consommatrices (référentiel taxons, sites, dépôt…).
    @Provides
    @Singleton
    ClientVigieChiro fournirClient(FournisseurToken fournisseurToken) {
        return new ClientVigieChiro(fournisseurToken);
    }

    // ViewModel non-singleton : le FXMLLoader recrée le controller à chaque ouverture de la modale.
    // Reçoit l'ensemble des rapprocheurs (#728) qu'il déclenche après une connexion réussie.
    @Provides
    ConnexionViewModel fournirViewModel(
            StockageConnexion stockage, ClientVigieChiro client, Set<RapprochementVigieChiro> rapprocheurs) {
        return new ConnexionViewModel(stockage, client, rapprocheurs);
    }
}

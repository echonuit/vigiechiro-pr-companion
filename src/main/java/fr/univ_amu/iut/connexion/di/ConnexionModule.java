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
import java.util.Optional;
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
        // (FournisseurToken est fourni plus bas : jeton ponctuel prioritaire, sinon stockage local.)
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

    /// Source du token pour tout le socle réseau (`commun.api`) : un **jeton ponctuel** (propriété
    /// système `vigiechiro.token`, sinon variable d'environnement `VIGIECHIRO_TOKEN`) l'emporte sur la
    /// **connexion enregistrée** ([StockageConnexion]). Le jeton ponctuel sert à la CLI (#1043,
    /// `deposer-vigiechiro --token …`) et aux outils, sans persister quoi que ce soit ; la propriété
    /// porte le même nom que celle de la suite de contrat (`-Dvigiechiro.token`).
    @Provides
    @Singleton
    FournisseurToken fournirFournisseurToken(StockageConnexion stockage) {
        return () -> jetonPonctuel().or(stockage::token);
    }

    /// Jeton fourni **hors connexion enregistrée**, consulté à chaque requête (surchargeable en cours
    /// d'exécution) : propriété système d'abord, variable d'environnement ensuite ; vide sinon.
    static Optional<String> jetonPonctuel() {
        return Optional.ofNullable(System.getProperty("vigiechiro.token"))
                .filter(jeton -> !jeton.isBlank())
                .or(() -> Optional.ofNullable(System.getenv("VIGIECHIRO_TOKEN")).filter(jeton -> !jeton.isBlank()));
    }

    /// Client de l'API VigieChiro, alimenté par le token stocké (ou ponctuel). Singleton : partagé par
    /// les features consommatrices (référentiel taxons, sites, dépôt…).
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

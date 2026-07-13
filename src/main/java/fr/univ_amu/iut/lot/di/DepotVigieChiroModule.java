package fr.univ_amu.iut.lot.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.PassageDao;

/// Liaison **réelle** du dépôt VigieChiro (#142) : pose la valeur de l'`OptionalBinder<DepotVigieChiro>`
/// déclaré (à vide) par [LotModule]. Ce module n'est chargé que dans l'**injecteur applicatif complet**
/// (`RacineInjecteur`), là où `ClientVigieChiro` est lié (par `ConnexionModule`).
///
/// Il vit **à part** de [LotModule] à dessein : les injecteurs partiels de capture assemblent la feature
/// `lot` sans `connexion` (donc sans client HTTP) et ne chargent pas ce module ; `LotViewModel` y reçoit
/// un `Optional.empty()` et le téléversement y est simplement indisponible, sans binding manquant.
///
/// La liaison de l'optional vise une **clé qualifiée** (`@Named`) plutôt que `DepotVigieChiro` directement,
/// pour éviter que la cible ne se référence elle-même (`RecursiveBinding` / double binding avec le
/// `@Provides`).
public class DepotVigieChiroModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro";

    /// Identité de la feature. `COEUR` : socle non désactivable (dépendue par d'autres features).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("depot-vigiechiro", "Dépôt VigieChiro", Categorie.COEUR);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), DepotVigieChiro.class)
                .setBinding()
                .to(Key.get(DepotVigieChiro.class, Names.named(QUALIFIANT)));
        // Vérification a posteriori d’un dépôt (#1132) : même montage, consommée par la CLI.
        OptionalBinder.newOptionalBinder(binder(), VerificationDepot.class)
                .setBinding()
                .to(Key.get(VerificationDepot.class, Names.named(QUALIFIANT)));
    }

    /// DAO du suivi de dépôt par unité (#981) : plan et avancement persistés du dépôt reprenable (#982).
    /// Fourni ici (et non dans `LotModule`) car il exige `SourceDeDonnees`, absente des injecteurs
    /// partiels de capture qui assemblent la feature `lot` sans persistance réelle.
    @Provides
    @Singleton
    DepotUniteDao fournirDepotUniteDao(SourceDeDonnees source) {
        return new DepotUniteDao(source);
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    VerificationDepot fournirVerificationDepot(
            @Named(QUALIFIANT) SynchronisationParticipation participations,
            ClientVigieChiro client,
            DepotUniteDao depotUnites) {
        return new VerificationDepot(participations, client, depotUnites);
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    DepotVigieChiro fournirDepotVigieChiro(
            @Named(QUALIFIANT) SynchronisationParticipation participations,
            ClientVigieChiro client,
            TraitementVigieChiro traitement,
            DepotUniteDao depotUnites,
            PassageDao passageDao,
            MoteurWorkflowPassage moteurWorkflow,
            Horloge horloge) {
        return new DepotVigieChiro(
                participations, client, traitement, depotUnites, passageDao, moteurWorkflow, horloge);
    }
}

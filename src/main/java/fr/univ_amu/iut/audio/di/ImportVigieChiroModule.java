package fr.univ_amu.iut.audio.di;

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
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.ImportObservationsVigieChiro;
import fr.univ_amu.iut.validation.model.ImportResultatsGroupe;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Optional;

/// Liaison **réelle** de l'import VigieChiro (axe 4.2) : pose la valeur de l'`OptionalBinder<ImportVigieChiro>`
/// déclaré (à vide) par [AudioModule]. Chargé **uniquement** dans l'injecteur applicatif complet
/// (`RacineInjecteur`), là où `ClientVigieChiro` est lié (par `ConnexionModule`).
///
/// À part de [AudioModule] à dessein : les injecteurs partiels de capture (`CaptureSonsValidation`,
/// `CaptureValidationTadarida`) assemblent la vue audio **sans `connexion`** et ne chargent pas ce module ;
/// `AudioViewModel` y reçoit un `Optional.empty()` et l'import VigieChiro y est simplement indisponible.
///
/// L'optional vise une **clé qualifiée** (`@Named`) pour éviter l'auto-référence (`RecursiveBinding` /
/// double binding avec le `@Provides`), comme `lot/di/DepotVigieChiroModule`.
public class ImportVigieChiroModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro";

    /// Identité de la feature. `OPTIONNELLE` : déjà pleinement optionnelle (`OptionalBinder` vide,
    /// ne binde aucun contrat `Ouvrir…`) donc désactivable en sécurité : feature de référence.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("import-vigiechiro", "Import depuis Vigie-Chiro", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        // Action(s) de lot (#2357) : la valeur du port optionnel déclaré par `MultisiteModule`.
        // Coupée, cette feature retire simplement son entrée du menu.
        OptionalBinder.newOptionalBinder(
                        binder(), Key.get(ActionGroupee.class, Names.named("action.importerResultats")))
                .setBinding()
                .to(Key.get(ActionGroupee.class, Names.named("action.importerResultats.impl")));
        OptionalBinder.newOptionalBinder(binder(), ImportVigieChiro.class)
                .setBinding()
                .to(Key.get(ImportVigieChiro.class, Names.named(QUALIFIANT)));
        // Port ImportObservations (#1264) : ce que M-Passage consomme, sans dépendre de la feature
        // `validation` (un `passage` qui en dépendrait fermerait un cycle qu'ArchUnit refuse). Le port est
        // déclaré à vide par CommunModule ; sa valeur est posée ici, là où l'import existe vraiment.
        OptionalBinder.newOptionalBinder(binder(), ImportObservations.class)
                .setBinding()
                .to(Key.get(ImportObservations.class, Names.named(QUALIFIANT)));
    }

    /// Action groupée **« Importer les résultats »** (#2357, lot 3, PR 4/5), sous le port
    /// [ActionGroupee].
    ///
    /// Fournie ici parce que c'est cette feature qui possède le geste. Elle prend l'import en
    /// `Optional` : hors connexion, tous les passages seront écartés avec ce motif, ce que l'annonce
    /// du lot dira avant de partir.
    @Provides
    @Singleton
    @Named("action.importerResultats.impl")
    ActionGroupee fournirImportResultatsGroupe(
            Optional<ImportVigieChiro> importateur, ResultatsIdentificationDao resultats) {
        return new ImportResultatsGroupe(importateur, resultats);
    }

    /// Adaptateur du port : l import reel, rendu consommable par les autres ecrans (#1264).
    @Provides
    @Singleton
    @Named(QUALIFIANT)
    ImportObservations fournirImportObservations(@Named(QUALIFIANT) ImportVigieChiro importateur) {
        return new ImportObservationsVigieChiro(importateur);
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    ImportVigieChiro fournirImportVigieChiro(
            ClientVigieChiro client,
            TraitementVigieChiro traitement,
            LienVigieChiroDao liens,
            ServiceValidation service) {
        return new ImportVigieChiro(client, traitement, liens, service);
    }
}

package fr.univ_amu.iut.passage.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.ReferentielPoint;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;

/// Liaison **réelle** de la passerelle [SynchronisationParticipation] (patron de `DepotVigieChiroModule`) :
/// chargée seulement dans `RacineInjecteur` (app complète, `ConnexionModule` présent), elle fournit l'instance
/// qualifiée `@Named("vigiechiro")` et la pose sur l'`OptionalBinder` déclaré vide par `PassageModule`. Le
/// qualificateur évite l'auto-référence (`RecursiveBinding`). Hors connexion, l'`Optional` reste vide.
public class SynchronisationParticipationModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro";

    /// Identité de la feature. `COEUR` : socle non désactivable (dépendue par d'autres features).
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite(
                "synchronisation-participation", "Synchronisation des participations", Categorie.COEUR);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), SynchronisationParticipation.class)
                .setBinding()
                .to(Key.get(SynchronisationParticipation.class, Names.named(QUALIFIANT)));
        // Le rattrapage en lot (#1861) ne fait que rejouer la passerelle nuit après nuit : il vit donc au
        // même endroit qu'elle, et n'existe pas non plus hors connexion.
        OptionalBinder.newOptionalBinder(binder(), RattrapageMetadonnees.class)
                .setBinding()
                .to(Key.get(RattrapageMetadonnees.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    RattrapageMetadonnees fournirRattrapageMetadonnees(
            @Named(QUALIFIANT) SynchronisationParticipation synchronisation, LienVigieChiroDao liens) {
        return new RattrapageMetadonnees(synchronisation, liens);
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    SynchronisationParticipation fournirSynchronisationParticipation(
            ClientVigieChiro client,
            LienVigieChiroDao liens,
            PassageDao passageDao,
            MaterielMicroDao materielDao,
            EnregistreurDao enregistreurDao,
            ReferentielPoint referentielPoint,
            FenetreObserveeNuit fenetreObservee) {
        return new SynchronisationParticipation(
                client, liens, passageDao, materielDao, enregistreurDao, referentielPoint, fenetreObservee);
    }
}

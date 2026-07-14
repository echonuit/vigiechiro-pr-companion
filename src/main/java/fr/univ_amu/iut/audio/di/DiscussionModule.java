package fr.univ_amu.iut.audio.di;

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
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.validation.model.PublicationMessage;
import fr.univ_amu.iut.validation.model.dao.MessageObservationDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;

/// Liaison **réelle** de l'envoi d'un message au validateur (#1418) : pose la valeur de
/// l'`OptionalBinder<PublicationMessage>`. Calqué sur [PublicationCorrectionsModule] (même raison d'être,
/// même clé qualifiée `@Named` contre l'auto-référence), et chargé **uniquement** dans l'injecteur
/// applicatif complet, là où `ClientVigieChiro` est lié.
///
/// **Feature à part, et désactivable — délibérément.** Lire le fil (#1417) est sans conséquence : c'est un
/// reflet du serveur. **Écrire** dedans est *définitif* : le serveur ajoute par `$push` et n'offre aucune
/// route de suppression. Une organisation qui ne veut pas que ses observateurs puissent adresser un
/// message irréversible à un validateur du MNHN peut donc simplement couper cette fonctionnalité — la
/// lecture du fil, elle, reste.
public class DiscussionModule extends ModuleDeFeature {

    private static final String QUALIFIANT = "vigiechiro-discussion";

    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("discuter-validateur", "Répondre au validateur VigieChiro", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), PublicationMessage.class)
                .setBinding()
                .to(Key.get(PublicationMessage.class, Names.named(QUALIFIANT)));
    }

    @Provides
    @Singleton
    @Named(QUALIFIANT)
    PublicationMessage fournirPublicationMessage(
            ClientVigieChiro client,
            ObservationDao observations,
            MessageObservationDao messages,
            UniteDeTravail uniteDeTravail) {
        return new PublicationMessage(client, observations, messages, uniteDeTravail);
    }
}

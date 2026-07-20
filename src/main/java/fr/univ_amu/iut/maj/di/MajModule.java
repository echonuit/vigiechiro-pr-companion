package fr.univ_amu.iut.maj.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.view.AnnonceChrome;
import fr.univ_amu.iut.maj.api.DerniereVersionGitHub;
import fr.univ_amu.iut.maj.model.DerniereVersionPubliee;
import fr.univ_amu.iut.maj.model.VerificateurMiseAJour;
import fr.univ_amu.iut.maj.view.AnnonceMiseAJour;

/// Module Guice de la feature `maj` : savoir qu'une version plus récente existe (#2109).
///
/// **`OPTIONNELLE`, et c'est le point.** Cette feature est la seule à contacter un service **tiers**
/// (l'API GitHub) sans que l'utilisateur l'ait demandé, et au démarrage. Quelqu'un peut légitimement
/// ne pas vouloir de cet appel : réseau mesuré en déplacement, poste sur un réseau fermé, ou refus de
/// principe. La désactiver doit donc suffire à ce qu'**aucune requête ne parte** - c'est pourquoi
/// l'adaptateur HTTP est fourni *par ce module*, et disparaît avec lui.
///
/// Rien d'autre dans l'application ne dépend de cette feature : la désactiver n'enlève qu'un message.
public class MajModule extends ModuleDeFeature {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), AnnonceChrome.class).addBinding().to(AnnonceMiseAJour.class);
    }

    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("maj", "Annonce des mises à jour", Categorie.OPTIONNELLE);
    }

    /// L'adaptateur réseau. Fourni ici et nulle part ailleurs : feature désactivée, aucun appel n'est
    /// possible faute de liaison.
    @Provides
    @Singleton
    DerniereVersionPubliee fournirAmont() {
        return new DerniereVersionGitHub();
    }

    @Provides
    @Singleton
    VerificateurMiseAJour fournirVerificateur(VersionApplication version, DerniereVersionPubliee amont) {
        return new VerificateurMiseAJour(version, amont);
    }
}

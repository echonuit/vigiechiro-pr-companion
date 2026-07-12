package fr.univ_amu.iut.audit.di;

import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import fr.univ_amu.iut.audit.model.AuditPointsServeur;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.view.ActiviteAudit;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.util.Optional;

/// Feature `audit` : audit de cohérence disque / base, exposé par la commande CLI `audit-coherence` et par
/// l'écran d'accueil « Audit de cohérence » ([ActiviteAudit], prisme « Collecte & passages »). Feature
/// `OPTIONNELLE` (désactivable) : sa carte d'accueil disparaît simplement quand elle est absente (le socle
/// bâtit les cartes depuis le `Multibinder<ActiviteAccueil>`, sans dépendre de la feature).
public class AuditModule extends ModuleDeFeature {

    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("audit", "Audit de cohérence", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        activite(ActiviteAudit.class);
        // Audit en ligne (#1132 dépôts, #1178 points) : injecté en Optional pour dégrader dans les
        // injecteurs partiels (outils de capture) qui ne chargent pas ces bindings.
        // - VerificationDepot : fourni par DepotVigieChiroModule (app complète) ; défaut-vide ici.
        // - AuditPointsServeur : lié au provider ci-dessous (ClientVigieChiro présent dans l'app complète) ;
        //   il dégrade lui-même en INFO hors connexion (client.mesSites() vide).
        OptionalBinder.newOptionalBinder(binder(), VerificationDepot.class);
        OptionalBinder.newOptionalBinder(binder(), AuditPointsServeur.class)
                .setBinding()
                .to(Key.get(AuditPointsServeur.class, Names.named("vigiechiro")));
    }

    @Provides
    @Singleton
    @Named("vigiechiro")
    AuditPointsServeur fournirAuditPointsServeur(
            ClientVigieChiro client,
            SiteDao siteDao,
            PointDao pointDao,
            LienVigieChiroDao liens,
            @Named("idUtilisateurCourant") String idUtilisateur) {
        return new AuditPointsServeur(client, siteDao, pointDao, liens, idUtilisateur);
    }

    @Provides
    @Singleton
    ServiceAuditCoherence fournirServiceAuditCoherence(
            SourceDeDonnees source,
            Workspace workspace,
            Optional<VerificationDepot> verificationDepot,
            Optional<AuditPointsServeur> auditPointsServeur) {
        return new ServiceAuditCoherence(source, workspace, verificationDepot, auditPointsServeur);
    }

    /// ViewModel de l'écran d'audit. **Non-singleton** (un VM frais par chargement FXML).
    @Provides
    AuditViewModel fournirAuditViewModel(ServiceAuditCoherence service) {
        return new AuditViewModel(service);
    }
}

package fr.univ_amu.iut.audit.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.audit.view.ActiviteAudit;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;

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
    }

    @Provides
    @Singleton
    ServiceAuditCoherence fournirServiceAuditCoherence(SourceDeDonnees source, Workspace workspace) {
        return new ServiceAuditCoherence(source, workspace);
    }

    /// ViewModel de l'écran d'audit. **Non-singleton** (un VM frais par chargement FXML).
    @Provides
    AuditViewModel fournirAuditViewModel(ServiceAuditCoherence service) {
        return new AuditViewModel(service);
    }
}

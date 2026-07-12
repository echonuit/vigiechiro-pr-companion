package fr.univ_amu.iut.audit.di;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;

/// Feature `audit` : audit de cohérence disque / base, exposé pour l'instant par la commande CLI
/// `audit-coherence`. Feature `OPTIONNELLE` (désactivable) : elle n'ajoute ni activité d'accueil ni
/// contrat de navigation socle, donc rien à neutraliser chez un consommateur quand elle est absente.
public class AuditModule extends ModuleDeFeature {

    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("audit", "Audit de cohérence", Categorie.OPTIONNELLE);
    }

    @Provides
    @Singleton
    ServiceAuditCoherence fournirServiceAuditCoherence(SourceDeDonnees source, Workspace workspace) {
        return new ServiceAuditCoherence(source, workspace);
    }
}

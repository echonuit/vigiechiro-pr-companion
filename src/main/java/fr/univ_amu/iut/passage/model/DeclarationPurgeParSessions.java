package fr.univ_amu.iut.passage.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.persistence.DeclarationPurgeOriginaux;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.util.Objects;

/// Implémentation du port [DeclarationPurgeOriginaux] (#1303) : après la purge **globale** des
/// `bruts/`, déclare le geste sur **chaque** session (marqueur `originals_purged_at` + volume à
/// zéro). Sans cette déclaration, l'audit prendrait les bruts manquants pour une corruption ;
/// idempotent, re-déclarer une session déjà purgée ne change rien d'observable (nouveau simple
/// horodatage).
public final class DeclarationPurgeParSessions implements DeclarationPurgeOriginaux {

    private final SessionDao sessionDao;
    private final Horloge horloge;

    @Inject
    public DeclarationPurgeParSessions(SessionDao sessionDao, Horloge horloge) {
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    @Override
    public void declarerPurgeGlobale() {
        for (SessionDEnregistrement session : sessionDao.findAll()) {
            sessionDao.marquerOriginauxPurges(session.id(), horloge.maintenant());
        }
    }
}

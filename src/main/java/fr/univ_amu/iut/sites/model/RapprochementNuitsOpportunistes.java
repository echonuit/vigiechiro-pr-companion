package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteTiersDao;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Marque **opportunistes** les nuits réalisées sur un carré appartenant à un tiers (#2525).
///
/// [Phase#DEPENDANTE] : il s'appuie sur le référentiel que `RapprochementSites` vient d'établir au même
/// tour : c'est lui qui dit quels carrés sont ceux d'un tiers (`site_tiers`, dérivé de
/// `site.observateur`). Ce rapprocheur ne fait **aucun appel réseau** : il tire les conséquences locales
/// de la synchronisation, d'où le `client` inutilisé.
///
/// **Sens unique, délibéré.** On marque, on ne démarque **jamais** :
///  - un carré qui vous est transféré ne réécrit pas l'histoire : les nuits y ont bien été réalisées
///    alors qu'il appartenait à quelqu'un d'autre ;
///  - surtout, démarquer écraserait les **saisies manuelles** faites pour les participations non
///    connectées (import #2543, modale #2545), dont le site local n'est lié à aucun site distant et
///    n'apparaît donc pas dans `site_tiers`.
///
/// Idempotent : rejouer une synchronisation ne remarque rien et ne rend aucun compte.
public class RapprochementNuitsOpportunistes implements RapprochementVigieChiro {

    private static final Logger LOG = Logger.getLogger(RapprochementNuitsOpportunistes.class.getName());

    /// Libellé du compte rendu (pluriel, cf. RapportSynchro#libelle).
    private static final String LIBELLE = "nuits opportunistes";

    private final SiteTiersDao siteTiers;
    private final PointDao pointDao;
    private final PassageDao passageDao;
    private final PassageOpportunisteDao opportunistes;

    public RapprochementNuitsOpportunistes(
            SiteTiersDao siteTiers, PointDao pointDao, PassageDao passageDao, PassageOpportunisteDao opportunistes) {
        this.siteTiers = Objects.requireNonNull(siteTiers, "siteTiers");
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.opportunistes = Objects.requireNonNull(opportunistes, "opportunistes");
    }

    /// Après le rapprochement des sites : marque les nuits des carrés de tiers **pas encore** marquées.
    ///
    /// @return le nombre de nuits **nouvellement** marquées, ou [Optional#empty()] s'il n'y a rien à dire
    ///     (aucun carré de tiers, ou tout est déjà à jour : le cas d'une synchro de routine)
    @Override
    public Optional<RapportSynchro> synchroniser(ClientVigieChiro client) {
        try {
            Set<Long> carresDeTiers = siteTiers.tousLesIds();
            if (carresDeTiers.isEmpty()) {
                return Optional.empty();
            }
            Set<Long> dejaMarquees = opportunistes.tousLesIds();
            int marquees = 0;
            for (Long idSite : carresDeTiers) {
                marquees += marquerLesNuitsDuCarre(idSite, dejaMarquees);
            }
            return marquees == 0 ? Optional.empty() : Optional.of(new RapportSynchro(LIBELLE, marquees));
        } catch (RuntimeException echec) {
            LOG.log(Level.FINE, echec, () -> "Marquage des nuits opportunistes ignoré (best-effort)");
            return Optional.empty();
        }
    }

    /// Nuits du carré `idSite` (via ses points) pas encore marquées, désormais marquées. Renvoie combien.
    private int marquerLesNuitsDuCarre(Long idSite, Set<Long> dejaMarquees) {
        int marquees = 0;
        for (PointDEcoute point : pointDao.findBySite(idSite)) {
            for (Passage passage : passageDao.findByPoint(point.id())) {
                if (passage.id() != null && !dejaMarquees.contains(passage.id())) {
                    opportunistes.marquer(passage.id());
                    marquees++;
                }
            }
        }
        return marquees;
    }

    /// Dépend du référentiel des carrés rapproché au même tour (`site_tiers`).
    @Override
    public Phase phase() {
        return Phase.DEPENDANTE;
    }
}

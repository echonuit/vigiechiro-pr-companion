package fr.univ_amu.iut.lot.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RearmementDepot;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import java.util.Objects;

/// Implémentation du port [RearmementDepot] par la feature `lot` (#3689).
///
/// Une reconnexion réussie **ne réarme que les refus d'authentification** : c'est la seule famille dont
/// cet événement pouvait lever la cause. Un contenu refusé (400 / 422) reste refusé, parce que rien
/// dans une reconnexion ne le change.
///
/// C'est ce filtre qui distingue ce réarmement d'un « forcer la reprise ». Réarmer tout ramènerait
/// exactement le bouton que #3687 vient de faire taire : celui qui promet une reprise vouée à échouer.
public final class RearmementDepotUnites implements RearmementDepot {

    private final DepotUniteDao depotUnites;
    private final Horloge horloge;

    @Inject
    public RearmementDepotUnites(DepotUniteDao depotUnites, Horloge horloge) {
        this.depotUnites = Objects.requireNonNull(depotUnites, "depotUnites");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    @Override
    public void reconnexionReussie() {
        depotUnites.rearmer(CauseRefus.AUTHENTIFICATION, horloge.maintenant().toString());
    }
}

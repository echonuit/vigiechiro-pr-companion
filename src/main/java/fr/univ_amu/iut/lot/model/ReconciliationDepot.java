package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.lot.model.dao.DepotUniteDao;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Réconciliation serveur (#1046), **extraite de [DepotVigieChiro]** pour la cohésion (seuil GodClass) :
/// marque `depose` les unités **WAV** dont le contenu est **déjà traité** côté plateforme (titre de
/// `donnees` = nom de fichier **sans extension**), pour ne jamais les re-téléverser.
///
/// Limites (documentées) : `donnees` n'existe qu'après traitement (un fichier téléversé mais pas encore
/// traité reste invisible et sera re-téléversé, idempotent côté plateforme) ; une archive **ZIP** n'est
/// pas appariable par titre (contenu inconnu localement). Pour un dépôt **purement ZIP** (#984), on
/// **saute** donc le `GET /donnees` paginé — lourd, et en croissance continue quand le serveur traite un
/// dépôt web parallèle : c'était la source du blocage « Dépôt 0/N figé ».
final class ReconciliationDepot {

    private final ClientVigieChiro client;
    private final DepotUniteDao depotUnites;
    private final Horloge horloge;

    ReconciliationDepot(ClientVigieChiro client, DepotUniteDao depotUnites, Horloge horloge) {
        this.client = Objects.requireNonNull(client, "client");
        this.depotUnites = Objects.requireNonNull(depotUnites, "depotUnites");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    void reconcilier(Long idPassage, String participationId, SuiviDepot suivi) {
        List<DepotUnite> restantes = depotUnites.restantes(idPassage);
        if (restantes.isEmpty() || restantes.stream().noneMatch(unite -> unite.type() == TypeDepotUnite.WAV)) {
            return;
        }
        Set<String> titresTraites = new HashSet<>();
        for (var donnee : client.donnees(participationId)) {
            titresTraites.add(donnee.titre());
        }
        if (titresTraites.isEmpty()) {
            return;
        }
        for (DepotUnite unite : restantes) {
            if (unite.type() == TypeDepotUnite.WAV && titresTraites.contains(sansExtension(unite.identifiantUnite()))) {
                depotUnites.mettreAJour(
                        unite.id(),
                        StatutDepotUnite.DEPOSE,
                        unite.fichierIdDistant(),
                        null,
                        horloge.maintenant().toString());
                suivi.uniteDeposee(depotUnites.findById(unite.id()).orElse(unite));
            }
        }
    }

    private static String sansExtension(String nom) {
        int point = nom.lastIndexOf('.');
        return point <= 0 ? nom : nom.substring(0, point);
    }
}

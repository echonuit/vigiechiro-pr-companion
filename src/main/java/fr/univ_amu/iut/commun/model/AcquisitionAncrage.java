package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.api.SuiviPagination;
import java.util.Objects;
import java.util.function.Consumer;

/// **Acquérir l'ancrage plateforme qui manque à une nuit**, quand il manque (ADR 0019).
///
/// L'ancrage (`idDonneeVigieChiro` + indice) est la cible du `PATCH /donnees/{id}/observations/{indice}`
/// : sans lui, une correction ne peut pas être poussée. Une nuit importée par **CSV** (#1565) n'en porte
/// pas - le CSV ne le transporte pas - et deux gestes en ont besoin, chacun au moment où il sert :
///
/// - la **réactivation** (#1571), qui rapatrie de toute façon les `donnees` pour rebrancher l'audio ;
/// - la **publication des corrections** (#1838), qui en a besoin juste avant d'envoyer.
///
/// Les deux écrivaient le même geste, à l'identique. Ce n'était pas seulement dix lignes en double : le
/// renommage d'un libellé pendant la clôture de #1838 a dû être fait **deux fois**, et rien n'aurait
/// signalé l'oubli du second. Le concept que l'ADR 0019 nomme méritait d'exister dans le code.
///
/// Passe par le **port** [ImportObservations] : la publication vit dans la feature `validation`, la
/// réactivation dans `passage`, et un pont direct entre elles serait un cycle (ADR 0004).
public final class AcquisitionAncrage {

    /// Ce que la phase annonce pendant qu'elle tourne. **Public** parce qu'il est partagé : l'outil de
    /// capture le rend, la fiche de recette le fait chercher à l'écran, et un libellé recopié se renomme
    /// une fois sur deux (ADR 0022). Ici, il n'existe qu'une fois.
    ///
    /// Il nomme **les deux** choses que le rapatriement ramène : l'ancrage, et les échanges avec le
    /// validateur (#1417). Ils voyagent dans les mêmes `donnees` et s'écrivent dans le même geste ; ne
    /// citer que l'ancrage était exact mais incomplet, et l'observateur découvrait un message du
    /// validateur par hasard (#1867).
    public static final String LIBELLE = "Récupération des identifiants et des échanges avec le validateur…";

    private AcquisitionAncrage() {}

    /// Rapatrie l'ancrage manquant du passage, **si** il y a lieu : import disponible, nuit **rattachée**
    /// à une participation (sinon il n'y a rien à quoi s'ancrer) et ancrage **effectivement absent**
    /// (sinon on ne paie rien). Sans quoi, ne fait rien - pas d'appel réseau, pas de progression émise.
    ///
    /// Le ré-import se fait avec `remplacer = true`, qui rapatrie l'ancrage **en préservant les
    /// validations** de l'observateur : aucun de ces deux gestes ne doit lui coûter son travail de revue.
    ///
    /// @param progres avancement du rapatriement (muet si l'ancrage est déjà là)
    /// @param jeton annulation honorée **à chaque page**, et non après coup (#1597)
    /// @return ce que la phase a rapporté ([RapportAncrage]), **muet** quand il n'y avait rien à
    ///     acquérir : dire « rien n'a été fait » après un geste qui n'a rien coûté serait du bruit.
    public static RapportAncrage acquerirSiNecessaire(
            ImportObservations importateur, Long idPassage, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(importateur, "importateur");
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(progres, "progres");
        Objects.requireNonNull(jeton, "jeton");
        if (!importateur.estRattache(idPassage) || !importateur.ancrageManquant(idPassage)) {
            return RapportAncrage.aucun();
        }
        progres.accept(new Progression(LIBELLE, 0.0));
        return new RapportAncrage(importateur.importer(idPassage, true, suivi(progres, jeton)));
    }

    /// Suivi **page par page** : le rapatriement des `donnees` compte des dizaines de pages ; sans relais
    /// la barre resterait figée plusieurs minutes, et « Annuler » ne se lèverait qu'à la fin.
    private static SuiviPagination suivi(Consumer<Progression> progres, JetonAnnulation jeton) {
        return (page, totalPages) -> {
            jeton.leverSiAnnule();
            double fraction = Math.min(page, totalPages) / (double) Math.max(totalPages, 1);
            progres.accept(new Progression(LIBELLE + " (page " + page + "/" + totalPages + ")", fraction));
        };
    }
}

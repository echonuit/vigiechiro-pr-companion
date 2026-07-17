package fr.univ_amu.iut.commun.api;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// Point d'extension de **rapprochement** d'un référentiel local avec VigieChiro (#728, axe 1).
///
/// Chaque feature qui possède des entités correspondant à des objets de la plateforme (les taxons
/// pour `validation`, les sites pour `sites`) contribue un rapprocheur au `Multibinder`. À la
/// connexion, la feature `connexion` les invoque tous pour amorcer / rafraîchir la table
/// `vigiechiro_link` (cf. [fr.univ_amu.iut.commun.model.LienVigieChiro]).
///
/// **Le client est passé en argument** (et non injecté dans l'implémentation) : un rapprocheur ne
/// dépend ainsi que du DAO de sa feature et de la persistance du socle, jamais de la feature
/// `connexion`. Les injecteurs autonomes des autres features (outils de capture, tests) restent donc
/// valides sans charger `ConnexionModule`.
///
/// Contrat **best-effort** : `synchroniser` ne doit jamais lever d'exception vers l'appelant. Le
/// client ([ClientVigieChiro]) dégrade déjà proprement (réseau/token indisponible → listes vides), et
/// l'implémentation avale ses propres erreurs de rapprochement : un échec ne compromet ni la connexion
/// ni les autres rapprocheurs.
///
/// **Ordre des rapprocheurs** (#1776) : le `Multibinder` est un ensemble **non ordonné**, or certains
/// rapprocheurs **dépendent** du résultat d'un autre. Les passages, par exemple, ne se rapatrient que sur
/// des **points d'écoute déjà locaux** ([Phase#DEPENDANTE]) - points que le rapprocheur des sites vient
/// justement de créer ([Phase#STRUCTURE]). Sans ordre, un site tout juste créé ne verrait ses passages
/// qu'à la synchro **suivante**. Les appelants (connexion, CLI) rejouent donc les rapprocheurs via
/// [#ordonnes(Collection)], qui place la structure avant ce qui en dépend. Un rapprocheur qui ne dépend de
/// rien n'a rien à déclarer : la phase par défaut est [Phase#STRUCTURE].
@FunctionalInterface
public interface RapprochementVigieChiro {

    /// Récupère les objets VigieChiro via `client` et met à jour les correspondances locales. À
    /// appeler **hors du fil JavaFX** (réseau + écritures base).
    ///
    /// @return un [RapportSynchro] si une synchronisation a eu lieu **ou a été empêchée** (#1284 :
    ///     plateforme injoignable, refus serveur — cause portée par [RapportSynchro#souci()]), ou
    ///     [Optional#empty()] s'il n'y a rien à dire (non connecté : silence légitime ; échec interne
    ///     best-effort). L'appelant affiche [RapportSynchro#enClair()] sans deviner.
    Optional<RapportSynchro> synchroniser(ClientVigieChiro client);

    /// Phase d'exécution de ce rapprocheur (#1776). Par défaut [Phase#STRUCTURE] : un rapprocheur qui
    /// n'attend rien d'un autre n'a rien à surcharger. Ceux qui dépendent d'un référentiel déjà rapproché
    /// (les passages, sur les points locaux) renvoient [Phase#DEPENDANTE].
    default Phase phase() {
        return Phase.STRUCTURE;
    }

    /// Ordonne des rapprocheurs par phase (structure d'abord, dépendants ensuite), en **préservant l'ordre
    /// d'origine à l'intérieur d'une même phase** (tri stable). À utiliser partout où l'on rejoue le
    /// `Multibinder` (connexion, CLI), pour qu'un site tout juste rapproché voie ses passages dès ce tour.
    static List<RapprochementVigieChiro> ordonnes(Collection<RapprochementVigieChiro> rapprocheurs) {
        return rapprocheurs.stream()
                .sorted(Comparator.comparingInt(
                        rapprocheur -> rapprocheur.phase().ordinal()))
                .toList();
    }

    /// Les phases de synchro, dans leur ordre d'exécution.
    enum Phase {
        /// Établit le référentiel local (les sites et leurs points, les taxons) : rien ne le précède.
        STRUCTURE,

        /// Dépend d'un référentiel déjà rapproché à ce tour - les passages, rapatriés uniquement sur des
        /// points d'écoute **déjà locaux** (#1776).
        DEPENDANTE
    }
}

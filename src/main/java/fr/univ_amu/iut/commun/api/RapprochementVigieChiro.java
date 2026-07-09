package fr.univ_amu.iut.commun.api;

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
@FunctionalInterface
public interface RapprochementVigieChiro {

    /// Récupère les objets VigieChiro via `client` et met à jour les correspondances locales. À
    /// appeler **hors du fil JavaFX** (réseau + écritures base).
    ///
    /// @return un [RapportSynchro] si une synchronisation a effectivement eu lieu, ou
    ///     [Optional#empty()] si rien n'a été fait (hors-ligne, échec best-effort, ou aucune donnée) :
    ///     l'appelant peut ainsi afficher un résumé « référentiel à jour : N … » sans deviner.
    Optional<RapportSynchro> synchroniser(ClientVigieChiro client);
}

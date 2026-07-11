package fr.univ_amu.iut.commun.model;

import com.google.inject.ImplementedBy;
import java.util.Optional;

/// Dépôt de la **disposition des colonnes par écran** (#994, couche « défaut par écran ») : l'abstraction
/// de persistance dont dépend le socle [fr.univ_amu.iut.commun.view.GestionnaireColonnes] pour retenir et
/// restaurer l'ordre + la visibilité des colonnes d'une table entre deux ouvertures.
///
/// Interface **du domaine** (paquet `model`), pour que la couche `view` passe par elle plutôt que par le
/// DAO concret (règle ArchUnit « la vue ne touche jamais `model.dao`/JDBC »). Le `layoutJson` est **opaque**
/// côté dépôt : sa (dé)sérialisation appartient à la vue (`DescripteurColonnesJson`).
///
/// [#ImplementedBy] fournit une implémentation **en mémoire** par défaut : les contextes sans base (tests
/// de vue isolés) obtiennent un dépôt fonctionnel sans binding ; la **racine de composition** relie
/// l'interface au DAO SQLite (`DispositionColonnesDao`) pour l'application et les captures.
@ImplementedBy(fr.univ_amu.iut.commun.model.DispositionColonnesEnMemoire.class)
public interface DepotDispositionColonnes {

    /// La disposition (JSON) mémorisée pour `(feature, cle)`, ou vide si aucune n'a encore été enregistrée.
    Optional<String> charger(String feature, String cle);

    /// Enregistre (ou remplace) la disposition (JSON) de `(feature, cle)`.
    void enregistrer(String feature, String cle, String layoutJson);
}

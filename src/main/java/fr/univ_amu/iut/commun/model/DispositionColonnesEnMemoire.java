package fr.univ_amu.iut.commun.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Implémentation **en mémoire** de [DepotDispositionColonnes] (défaut `@ImplementedBy`) : une simple table
/// `(feature|cle) → JSON`, sans persistance sur disque. Sert les contextes **sans base de données** (tests
/// de vue isolés qui construisent un injecteur partiel) : la disposition vit le temps de l'instance. La
/// **racine de composition** relie l'interface au DAO SQLite ([DispositionColonnesDao]) pour l'application.
public final class DispositionColonnesEnMemoire implements DepotDispositionColonnes {

    private final Map<String, String> dispositions = new HashMap<>();

    @Override
    public Optional<String> charger(String feature, String cle) {
        return Optional.ofNullable(dispositions.get(feature + '|' + cle));
    }

    @Override
    public void enregistrer(String feature, String cle, String layoutJson) {
        dispositions.put(feature + '|' + cle, layoutJson);
    }
}

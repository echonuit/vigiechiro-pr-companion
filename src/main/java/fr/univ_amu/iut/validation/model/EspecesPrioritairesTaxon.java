package fr.univ_amu.iut.validation.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.util.Objects;
import java.util.Set;

/// Implémentation « réelle » du port [EspecesPrioritaires] (#2353) : lit le marquage posé par la
/// migration V36 dans la table latérale du référentiel taxonomique.
///
/// Classe nommée plutôt que lambda `@Provides`, parce que la liaison passe par un `OptionalBinder` dont
/// les features consommatrices posent le **défaut vide** : même montage d'inversion que
/// `CoordonneesPointSites` (#547). Un `@Provides` sur la même clé entrerait en conflit avec ce défaut.
public final class EspecesPrioritairesTaxon implements EspecesPrioritaires {

    private final TaxonDao taxonDao;

    @Inject
    EspecesPrioritairesTaxon(TaxonDao taxonDao) {
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
    }

    @Override
    public Set<String> codes() {
        return taxonDao.codesPrioritaires();
    }
}

package fr.univ_amu.iut.commun.view;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/// Résolveur **GBIF** : remplace l'URL de **recherche** produite par le socle
/// (`…/species/search?q=<nom latin>`) par la **fiche** de l'espèce (`…/species/{clé}`), en résolvant la
/// clé d'usage GBIF à partir du nom latin (#922).
///
/// La résolution réseau est déléguée à `cleUsage` (nom latin → clé d'usage), injecté : l'HTTP réel en
/// production ([fr.univ_amu.iut.commun.api.ClientGbif]), un faux en test. La détection de l'URL de
/// recherche et la construction de l'URL de fiche restent, elles, **pures** (testables sans réseau).
///
/// **Best-effort** : toute URL non GBIF passe inchangée ; un nom non résolu (ou une erreur réseau)
/// **retombe** sur l'URL de recherche d'origine plutôt que sur rien.
public final class ResolveurFicheGbif implements ResolveurFiche {

    static final String PREFIXE_RECHERCHE = "https://www.gbif.org/species/search?q=";

    private static final String BASE_ESPECE = "https://www.gbif.org/species/";

    private final Function<String, Optional<Long>> cleUsage;

    public ResolveurFicheGbif(Function<String, Optional<Long>> cleUsage) {
        this.cleUsage = Objects.requireNonNull(cleUsage, "cleUsage");
    }

    @Override
    public String resoudre(String url) {
        if (url == null || !url.startsWith(PREFIXE_RECHERCHE)) {
            return url;
        }
        try {
            String nomLatin = URLDecoder.decode(url.substring(PREFIXE_RECHERCHE.length()), StandardCharsets.UTF_8);
            return cleUsage.apply(nomLatin).map(cle -> BASE_ESPECE + cle).orElse(url);
        } catch (RuntimeException echecResolution) {
            return url; // repli : la recherche vaut mieux que rien
        }
    }
}

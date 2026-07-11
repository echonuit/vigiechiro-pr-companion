package fr.univ_amu.iut.commun.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/// Source universelle alternative : l'article **Wikipédia FR** du taxon, adressé par son nom
/// scientifique (les articles d'espèces y sont titrés par le binôme latin, `Genre_espèce`).
///
/// Variante francophone et plus descriptive de [LienGbif] ; interchangeable via [SourceUniverselle]
/// selon la préférence utilisateur (cf. [SourceUniversellePreferee]).
public final class LienWikipediaFr implements SourceUniverselle {

    private static final String ARTICLE = "https://fr.wikipedia.org/wiki/";

    @Override
    public Optional<String> lienPourNomLatin(String nomLatin) {
        if (nomLatin == null || nomLatin.isBlank()) {
            return Optional.empty();
        }
        // Titre Wikipédia : espaces → underscores, puis encodage (les underscores et lettres restent
        // intacts ; les caractères spéciaux éventuels sont percent-encodés).
        String titre = URLEncoder.encode(nomLatin.strip().replace(' ', '_'), StandardCharsets.UTF_8);
        return Optional.of(ARTICLE + titre);
    }
}

package fr.univ_amu.iut.commun.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/// Source universelle par défaut : la **recherche d'espèce GBIF** (`gbif.org`), indexée sur le nom
/// scientifique. On vise l'URL de **recherche** (`/species/search?q=...`) plutôt qu'une page d'espèce
/// (`/species/{key}`) : elle est **déterministe** (aucune clé à résoudre, aucun appel réseau) et se
/// rabat proprement sur le genre quand le binôme est incertain (`Myotis sp.`, `Myotis cf. myotis`).
///
/// Couvre tout ce que Tadarida peut proposer hors chiroptères de métropole (oiseaux, orthoptères,
/// autres mammifères), pourvu qu'un nom latin soit connu.
public final class LienGbif implements SourceUniverselle {

    private static final String RECHERCHE = "https://www.gbif.org/species/search?q=";

    @Override
    public Optional<String> lienPourNomLatin(String nomLatin) {
        if (nomLatin == null || nomLatin.isBlank()) {
            return Optional.empty();
        }
        String requete = URLEncoder.encode(nomLatin.strip(), StandardCharsets.UTF_8);
        return Optional.of(RECHERCHE + requete);
    }
}

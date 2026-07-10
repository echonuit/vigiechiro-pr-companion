package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/// Construit l'URL de la **fiche d'information** d'une espèce identifiée, sans IHM ni réseau : pur et
/// déterministe (cf. #845).
///
/// Deux stratégies, par priorité :
///
/// 1. **PNA Chiroptères** : si le code Tadarida figure dans la table de correspondance embarquée
///    (`especes-pna.properties`, ~32 chiroptères de métropole), on renvoie sa fiche descriptive
///    spécialisée, en français.
/// 2. **Source universelle** (par nom latin) : sinon, si un nom latin est connu, on délègue à une
///    [SourceUniverselle] (GBIF par défaut, cf. [LienGbif]) — ce qui couvre oiseaux, orthoptères,
///    niveaux genre et couples que le PNA ne référence pas.
///
/// Sinon (pseudo-taxon `noise`/`piaf`, ou taxon sans code connu ni nom latin) : [Optional#empty()],
/// et l'action IHM correspondante reste désactivée.
public final class ConstructeurLienEspece {

    private static final String RESSOURCE_PNA = "especes-pna.properties";

    private static final String BASE_PNA = "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/";

    /// code Tadarida -> slug de fiche PNA (immuable).
    private final Map<String, String> slugsPna;

    private final SourceUniverselle sourceUniverselle;

    /// Constructeur par défaut : table PNA embarquée + repli GBIF.
    public ConstructeurLienEspece() {
        this(new LienGbif());
    }

    /// Variante avec source universelle explicite (repli GBIF ou Wikipédia FR, #849 ; tests).
    public ConstructeurLienEspece(SourceUniverselle sourceUniverselle) {
        this.sourceUniverselle = Objects.requireNonNull(sourceUniverselle, "sourceUniverselle");
        this.slugsPna = chargerSlugsPna();
    }

    /// URL de la fiche pour `espece` : PNA si le code est connu, sinon source universelle par nom latin,
    /// sinon vide. Tolérant : `espece` null renvoie vide.
    public Optional<String> lienFiche(EspeceIdentifiee espece) {
        if (espece == null) {
            return Optional.empty();
        }
        String slug = espece.codeTadarida() == null ? null : slugsPna.get(espece.codeTadarida());
        if (slug != null) {
            return Optional.of(BASE_PNA + slug + "/");
        }
        return sourceUniverselle.lienPourNomLatin(espece.nomLatin());
    }

    /// Codes chiroptères disposant d'une fiche PNA (utile au diagnostic et au test de complétude).
    public Set<String> codesAvecFichePna() {
        return slugsPna.keySet();
    }

    private static Map<String, String> chargerSlugsPna() {
        Properties proprietes = new Properties();
        try (InputStream flux = ConstructeurLienEspece.class.getResourceAsStream(RESSOURCE_PNA)) {
            if (flux == null) {
                throw new IllegalStateException("Table de correspondance PNA introuvable : " + RESSOURCE_PNA);
            }
            proprietes.load(flux);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la table PNA impossible", e);
        }
        return proprietes.stringPropertyNames().stream()
                .collect(Collectors.toUnmodifiableMap(code -> code, proprietes::getProperty));
    }
}

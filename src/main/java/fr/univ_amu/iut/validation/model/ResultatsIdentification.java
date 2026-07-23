package fr.univ_amu.iut.validation.model;

/// Résultats d'identification : jeu d'observations importé pour la validation taxonomique (C12, table
/// `identification_results`). Sa source est **soit** un CSV produit par Tadarida (le cas d'origine),
/// **soit** la plateforme VigieChiro (#719, axe 4.2), qui rend les mêmes observations sans qu'aucun
/// fichier ne transite par le disque.
///
/// Rattaché à **un seul passage** (`passage_id` unique, cardinalité 0:1 côté passage) : un
/// passage est annoté par au plus un jeu de résultats. Agrège `1..*` [Observation] via
/// `observation.results_id`.
///
/// L'`id` (clé technique auto-incrémentée) vaut `null` tant que les résultats n'ont pas été
/// insérés. Le `formatDetecte` (« Brut » / « Vu ») est un énum stocké en `TEXT` libre (aucun énum
/// `commun.model` fourni pour ce point de variation, cf. note d'intégration).
///
/// @param id clé technique, `null` avant insertion
/// @param cheminFichier chemin du CSV **à son emplacement d'origine** (choisi par l'utilisateur ;
///     le CSV n'est pas copié dans la session, R23), ou la **provenance** [#SOURCE_VIGIECHIRO] quand
///     les observations viennent de la plateforme : ce n'est alors **pas** un chemin, et rien ne doit
///     le chercher sur le disque (cf. [#issuDunFichier])
/// @param formatDetecte format détecté (ex. `"Brut"` avec guillemets, `"Vu"` réinjectable)
/// @param dateImport date/heure d'import (ISO-8601)
/// @param idPassage identifiant du passage annoté (FK → `passage.id`, unique)
public record ResultatsIdentification(
        Long id, String cheminFichier, String formatDetecte, String dateImport, Long idPassage) {

    /// Provenance des observations **rapatriées de la plateforme** : la valeur que porte `cheminFichier`
    /// quand aucun CSV n'a jamais existé. C'est un **marqueur de provenance**, pas un chemin : le
    /// confondre avec un fichier fait dire à l'audit qu'un fichier « externe » est introuvable, et
    /// suggérer une carte SD non montée là où il n'y en a jamais eu (#1050).
    public static final String SOURCE_VIGIECHIRO = "vigiechiro";

    /// `true` si ces résultats viennent d'un **fichier** (CSV Tadarida) qu'on peut donc chercher sur le
    /// disque ; `false` s'ils ont été rapatriés de la plateforme.
    public boolean issuDunFichier() {
        return !SOURCE_VIGIECHIRO.equals(cheminFichier);
    }
}

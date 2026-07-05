package fr.univ_amu.iut.validation.model;

/// Projection « **une espèce observée dans un passage** » (#323) : une espèce ([Taxon]) effectivement
/// présente dans les observations de l'utilisateur, **rattachée au passage** où elle a été relevée. Une
/// même espèce vue dans plusieurs passages donne plusieurs lignes (une par passage) ; une espèce vue
/// plusieurs fois dans le même passage n'en donne qu'une (DISTINCT).
///
/// L'espèce retenue est le taxon **validé** par l'observateur s'il existe, sinon la proposition Tadarida
/// (`COALESCE(taxon_observer, taxon_tadarida)`). Sert à la **recherche globale** : correspondance par code
/// ou nom (latin / vernaculaire), navigation vers le passage via les clés `idPassage` / `numeroCarre` /
/// `codePoint`.
///
/// @param code code 6 lettres du taxon (clé naturelle)
/// @param nomLatin nom latin (optionnel)
/// @param nomVernaculaireFr nom vernaculaire français (optionnel)
/// @param groupe taxon parent : nom du groupe taxonomique du taxon (ex. « Chiroptères »), tel qu'affiché
///     par le portail VigieChiro ; `null` si le taxon n'est rattaché à aucun groupe
/// @param idPassage passage où l'espèce a été observée (cible de navigation)
/// @param numeroCarre n° de carré du site du passage
/// @param codePoint code du point d'écoute du passage
/// @param annee année du passage
/// @param numeroPassage n° de passage dans l'année
/// @param dateEnregistrement date d'enregistrement du passage (texte)
public record EspeceObservee(
        String code,
        String nomLatin,
        String nomVernaculaireFr,
        String groupe,
        long idPassage,
        String numeroCarre,
        String codePoint,
        int annee,
        int numeroPassage,
        String dateEnregistrement) {}

package fr.univ_amu.iut.commun.model;

import java.util.Objects;

/// Un résultat de la **recherche globale** (#144) : de quoi l'**afficher** dans la liste déroulante
/// (type, libellé principal, détail secondaire) et de quoi **naviguer** vers l'élément (clés
/// d'identité). Donnée de présentation **agnostique de l'IHM** (aucune dépendance JavaFX), construite
/// par [RechercheGlobale] ; la couche `view` décide quel écran ouvrir selon le [#type].
///
/// @param type nature du résultat (site, point, passage)
/// @param libelle ligne principale affichée (jamais nulle)
/// @param details ligne secondaire (contexte), éventuellement vide
/// @param numeroCarre n° de carré du site concerné (toujours présent : tout résultat appartient à un site)
/// @param codePoint code du point d'écoute (`null` pour un résultat de type site)
/// @param nomSite nom convivial du site (`null` si absent ; sert de contexte de navigation)
/// @param idPassage identifiant du passage (présent uniquement pour [TypeResultat#PASSAGE])
public record ResultatRecherche(
        TypeResultat type,
        String libelle,
        String details,
        String numeroCarre,
        String codePoint,
        String nomSite,
        Long idPassage) {

    public ResultatRecherche {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(libelle, "libelle");
        Objects.requireNonNull(numeroCarre, "numeroCarre");
    }
}

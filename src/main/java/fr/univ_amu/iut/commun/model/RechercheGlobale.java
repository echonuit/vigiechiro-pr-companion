package fr.univ_amu.iut.commun.model;

import java.util.List;

/// Contrat de **recherche globale** (#144) : à partir d'une saisie libre, retourne les éléments
/// correspondants (sites, points, passages) de l'utilisateur courant, insensible casse/accents.
///
/// Vit dans le **socle** (`commun.model`) pour que le chrome (`MainController`) le consomme sans
/// dépendre d'une feature ; l'implémentation (`recherche.model.ServiceRechercheGlobale`) agrège les
/// services des features `sites` et `multisite`. Espèces/observations **différées** (aucune requête
/// d'observations par espèce dans le modèle à ce stade).
public interface RechercheGlobale {

    /// Résultats correspondant à `requete` (sites, puis points, puis passages). Une requête vide ou
    /// blanche retourne une liste **vide**. Le nombre de résultats est borné (liste déroulante).
    List<ResultatRecherche> rechercher(String requete);
}

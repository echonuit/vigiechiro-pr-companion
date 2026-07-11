package fr.univ_amu.iut.commun.view;

import com.google.inject.ImplementedBy;

/// Dernière transformation d'une URL de fiche espèce **avant ouverture**, pour les sources qui ne
/// livrent pas directement l'adresse finale.
///
/// Cas d'usage : GBIF. Le socle produit une URL de **recherche** (`…/species/search?q=…`, sans réseau,
/// cf. [fr.univ_amu.iut.commun.model.LienGbif]) qui tombe sur une liste de résultats, pas sur la fiche ;
/// le résolveur la remplace par la **fiche** de l'espèce (`…/species/{clé}`) en résolvant la clé via
/// l'API GBIF (#922). Les liens PNA et Wikipédia sont déjà des adresses directes : ils passent
/// **inchangés**.
///
/// [ImplementedBy] fixe l'**identité** comme défaut (aucun réseau, utile aux tests) ; l'application
/// complète surcharge par [ResolveurFicheGbif] (`CommunModule`).
@ImplementedBy(ResolveurFicheIdentite.class)
public interface ResolveurFiche {

    /// Renvoie l'URL finale à ouvrir pour `url`. Ne lève jamais : en cas d'échec (réseau, résolution
    /// impossible), renvoie `url` tel quel (repli).
    String resoudre(String url);
}

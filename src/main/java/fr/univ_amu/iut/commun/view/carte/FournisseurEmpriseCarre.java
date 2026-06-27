package fr.univ_amu.iut.commun.view.carte;

import java.util.List;
import java.util.Optional;

/// Fournit l'[EmpriseCarre] d'un carré. **Pluggable** : le numéro de carré n'encodant pas de coordonnées
/// (cf. [CarreGeo#departement()]), l'emprise vient d'une source au choix :
///
/// - [EmpriseAutourDesPoints] : repli **livré maintenant**, déduit du barycentre des points géolocalisés ;
/// - *(à venir)* carroyage **officiel** Vigie-Chiro (service/référentiel externe), branché derrière cette
///   même interface sans toucher au composant.
///
/// Renvoie `Optional.empty()` quand l'emprise ne peut pas être déterminée (p. ex. aucun point géolocalisé
/// et pas de référentiel) : le carré n'est alors simplement pas tracé.
@FunctionalInterface
public interface FournisseurEmpriseCarre {

    /// Emprise du carré `numeroCarre`, en s'aidant au besoin de ses points (`pointsDuCarre`), ou vide.
    Optional<EmpriseCarre> emprise(String numeroCarre, List<PointGeo> pointsDuCarre);
}

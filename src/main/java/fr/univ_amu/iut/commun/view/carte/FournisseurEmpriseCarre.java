package fr.univ_amu.iut.commun.view.carte;

import java.util.List;
import java.util.Optional;

/// Fournit l'[EmpriseCarre] d'un carré. **Pluggable** : le numéro de carré n'encode **pas** de
/// coordonnées - la seule chose qu'on en tire est son département
/// ([fr.univ_amu.iut.commun.model.RegionDuCarre#departement], ADR 2351) - et l'emprise vient donc d'une
/// source au choix :
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

    /// Fournisseur **par défaut** partagé : carroyage officiel d'abord, repli autour des points ensuite.
    /// Instance unique (la grille nationale n'est chargée qu'une fois) à réutiliser par tous les écrans.
    static FournisseurEmpriseCarre parDefaut() {
        return Defaut.CHAINE;
    }

    /// Porteur d'initialisation paresseuse de la chaîne par défaut.
    final class Defaut {
        private static final FournisseurEmpriseCarre CHAINE = new FournisseurEmpriseCarreEnChaine(
                new FournisseurEmpriseCarreOfficiel(), new EmpriseAutourDesPoints());

        private Defaut() {}
    }
}

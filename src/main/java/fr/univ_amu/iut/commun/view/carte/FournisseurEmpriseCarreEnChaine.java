package fr.univ_amu.iut.commun.view.carte;

import java.util.List;
import java.util.Optional;

/// Compose plusieurs [FournisseurEmpriseCarre] et renvoie la **première** emprise disponible : le
/// carroyage **officiel** d'abord ([FournisseurEmpriseCarreOfficiel]), le **repli** ensuite
/// ([EmpriseAutourDesPoints]). Ainsi un carré du référentiel national est calé sur la grille, et les
/// autres restent ancrés sur leurs points (#152/#325), sans que le composant carte change.
public final class FournisseurEmpriseCarreEnChaine implements FournisseurEmpriseCarre {

    private final List<FournisseurEmpriseCarre> fournisseurs;

    public FournisseurEmpriseCarreEnChaine(FournisseurEmpriseCarre... fournisseurs) {
        this.fournisseurs = List.of(fournisseurs);
    }

    @Override
    public Optional<EmpriseCarre> emprise(String numeroCarre, List<PointGeo> pointsDuCarre) {
        for (FournisseurEmpriseCarre fournisseur : fournisseurs) {
            Optional<EmpriseCarre> emprise = fournisseur.emprise(numeroCarre, pointsDuCarre);
            if (emprise.isPresent()) {
                return emprise;
            }
        }
        return Optional.empty();
    }
}

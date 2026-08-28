package fr.univ_amu.iut.commun.view.carte;

import fr.univ_amu.iut.commun.model.CarroyageNational;
import fr.univ_amu.iut.commun.model.PositionGeo;
import java.util.List;
import java.util.Optional;

/// Fournisseur d'emprise **officiel** (#325) : cale le carré sur le carroyage national Vigie-Chiro.
///
/// Le numéro n'encode pas de coordonnées : on lit le centroïde WGS84 de la maille dans
/// [CarroyageNational], puis on en déduit l'emprise du carré **2 km** centrée dessus.
///
/// Le référentiel lui-même a été descendu dans le modèle par #4621 : le découpage du territoire est une
/// donnée du domaine, et la carte n'est qu'un de ses lecteurs. Cette classe ne garde que ce qui la
/// concerne, passer d'un centroïde à une emprise.
///
/// Un numéro absent (hors métropole, ou numéro inconnu) renvoie `Optional.empty()` : la chaîne
/// ([FournisseurEmpriseCarreEnChaine]) bascule alors sur le repli [EmpriseAutourDesPoints].
public final class FournisseurEmpriseCarreOfficiel implements FournisseurEmpriseCarre {

    /// Demi-côté du carré Vigie-Chiro (2 km de côté) et conversion km → degrés (cf. [EmpriseAutourDesPoints]).
    private static final double DEMI_COTE_KM = 1.0;

    private static final double KM_PAR_DEGRE_LAT = 111.0;

    private final CarroyageNational carroyage;

    public FournisseurEmpriseCarreOfficiel() {
        this(CarroyageNational.embarque());
    }

    /// Sur un carroyage donné : sert aux tests, qui n'ont pas à charger la grille nationale.
    public FournisseurEmpriseCarreOfficiel(CarroyageNational carroyage) {
        this.carroyage = carroyage;
    }

    @Override
    public Optional<EmpriseCarre> emprise(String numeroCarre, List<PointGeo> pointsDuCarre) {
        return carroyage.centroide(numeroCarre).map(FournisseurEmpriseCarreOfficiel::autourDe);
    }

    /// Nombre de carrés connus du référentiel (utile aux tests / au diagnostic).
    public int taille() {
        return carroyage.taille();
    }

    private static EmpriseCarre autourDe(PositionGeo centre) {
        double demiLat = DEMI_COTE_KM / KM_PAR_DEGRE_LAT;
        double demiLon = DEMI_COTE_KM / (KM_PAR_DEGRE_LAT * Math.cos(Math.toRadians(centre.latitude())));
        return new EmpriseCarre(
                centre.latitude() - demiLat,
                centre.longitude() - demiLon,
                centre.latitude() + demiLat,
                centre.longitude() + demiLon);
    }
}

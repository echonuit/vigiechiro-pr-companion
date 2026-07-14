package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.Certitude;
import java.util.Locale;

/// Les **filtres de revue** d'un passage (#1311) : le pendant, en ligne de commande, de la barre à puces
/// de l'écran « Sons & validation ».
///
/// Chaque critère `null` signifie **« ne filtre pas là-dessus »** - et non « vaut faux ». La nuance
/// compte : `--douteux` retient les seules observations douteuses, tandis que **l'absence** de l'option les
/// laisse toutes passer. Un filtre booléen à trois états, donc, et c'est délibéré : sans le troisième, on
/// ne saurait pas exprimer « je me fiche de ce drapeau ».
///
/// Ces critères sont partagés par `lister-observations` et par **tous les gestes de revue**. C'est la
/// garantie qui les rend sûrs : le geste touche **exactement** ce que la liste a montré, parce que c'est le
/// **même** code qui choisit.
///
/// @param statut `NON_TOUCHEE` / `VALIDEE` / `CORRIGEE`, ou `null` pour ne pas filtrer sur le statut
/// @param taxon code du taxon **proposé par Tadarida** (ex. `Pippip`), ou `null` ; comparé sans tenir
///     compte de la casse
/// @param douteux `true` = seulement les douteuses, `false` = seulement les non douteuses, `null` = les
///     deux
/// @param reference `true` = seulement les références, `false` = seulement les non-références, `null` =
///     les deux
/// @param certitude certitude **observateur** déclarée, ou `null` pour ne pas filtrer
public record CriteresRevue(
        StatutObservation statut, String taxon, Boolean douteux, Boolean reference, Certitude certitude) {

    /// Aucun filtre : toutes les observations du passage.
    public static CriteresRevue aucun() {
        return new CriteresRevue(null, null, null, null, null);
    }

    /// L'observation passe-t-elle **tous** les critères posés ? Les critères absents (`null`) ne retiennent
    /// rien : ils laissent passer.
    public boolean retient(LigneObservationAudio ligne) {
        return statutRetenu(ligne) && taxonRetenu(ligne) && drapeauxRetenus(ligne) && certitudeRetenue(ligne);
    }

    /// Y a-t-il au moins un filtre ? Sert aux commandes à **dire** qu'elles ont agi sur tout un passage
    /// plutôt que sur une sélection : un geste en masse sans filtre mérite d'être annoncé comme tel.
    public boolean vide() {
        return statut == null && taxon == null && douteux == null && reference == null && certitude == null;
    }

    private boolean statutRetenu(LigneObservationAudio ligne) {
        return statut == null || statut == ligne.statut();
    }

    private boolean taxonRetenu(LigneObservationAudio ligne) {
        return taxon == null
                || (ligne.taxonTadarida() != null
                        && ligne.taxonTadarida().toLowerCase(Locale.ROOT).equals(taxon.toLowerCase(Locale.ROOT)));
    }

    private boolean drapeauxRetenus(LigneObservationAudio ligne) {
        return (douteux == null || douteux == ligne.douteux()) && (reference == null || reference == ligne.reference());
    }

    private boolean certitudeRetenue(LigneObservationAudio ligne) {
        return certitude == null || certitude == ligne.certitude();
    }
}

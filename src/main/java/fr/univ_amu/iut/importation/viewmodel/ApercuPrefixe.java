package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;

/// Calcul **pur** de l'aperçu du nom préfixé (R6) appliqué à un exemple de nom d'origine.
///
/// Extrait de [ImportationViewModel] pour garder ce dernier cohésif : composer le préfixe Vigie-Chiro
/// est une fonction des seules entrées du rattachement (site, point, année, n° de passage) et du
/// rapport d'inspection, sans état mutable ni JavaFX. La sortir ici allège la responsabilité du
/// ViewModel (assistant en 4 étapes) et rend le calcul testable isolément.
final class ApercuPrefixe {

    private static final String EXEMPLE_PAR_DEFAUT = "PaRec…_AAAAMMJJ_HHMMSS.wav";

    private ApercuPrefixe() {}

    /// Aperçu du préfixe appliqué à un exemple de nom d'origine ; chaîne vide tant que le site ou le
    /// point n'est pas choisi (rattachement incomplet).
    static String calculer(Site site, PointDEcoute point, int annee, int numeroPassage, RapportInspection rapport) {
        if (site == null || point == null) {
            return "";
        }
        Prefixe prefixe = new Prefixe(site.numeroCarre(), annee, numeroPassage, point.code());
        return prefixe.nommerOriginal(exempleNomOriginal(rapport));
    }

    /// Exemple de nom d'origine : le premier enregistrement réellement inspecté si disponible, sinon un
    /// gabarit générique (avant toute inspection).
    private static String exempleNomOriginal(RapportInspection rapport) {
        if (rapport != null && !rapport.originaux().isEmpty()) {
            return rapport.originaux().get(0).getFileName().toString();
        }
        return EXEMPLE_PAR_DEFAUT;
    }
}

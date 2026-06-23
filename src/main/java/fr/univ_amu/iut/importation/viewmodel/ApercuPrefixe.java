package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;

/// Calcul **pur** de l'aperçu du nom préfixé (R6) appliqué à un exemple de nom d'origine.
///
/// Extrait de [ImportationViewModel] pour garder ce dernier cohésif : composer le préfixe Vigie-Chiro
/// est une fonction des seules entrées du rattachement (site, point, année, n° de passage) et d'un
/// **exemple de nom d'origine** (issu de l'inspection), sans état mutable ni JavaFX. Reçoit un simple
/// `String` plutôt que le rapport d'inspection, pour ne pas coupler le rattachement à l'inspection.
final class ApercuPrefixe {

    private static final String EXEMPLE_PAR_DEFAUT = "PaRec…_AAAAMMJJ_HHMMSS.wav";

    private ApercuPrefixe() {}

    /// Aperçu du préfixe appliqué à un `exempleNomOriginal` (un gabarit générique est utilisé s'il est
    /// nul ou vide, c.-à-d. avant toute inspection) ; chaîne vide tant que le site ou le point n'est pas
    /// choisi (rattachement incomplet).
    ///
    /// Cas **fichiers déjà préfixés** (#111) : le nom est rendu **tel quel** (R7, jamais de double
    /// préfixe). La discordance éventuelle du préfixe présent avec le rattachement choisi est signalée
    /// séparément (avertissement observable du rattachement, #33), pas dans cet aperçu.
    static String calculer(Site site, PointDEcoute point, int annee, int numeroPassage, String exempleNomOriginal) {
        if (site == null || point == null) {
            return "";
        }
        Prefixe prefixe = new Prefixe(site.numeroCarre(), annee, numeroPassage, point.code());
        String exemple =
                exempleNomOriginal == null || exempleNomOriginal.isBlank() ? EXEMPLE_PAR_DEFAUT : exempleNomOriginal;
        if (Prefixe.estNomPrefixe(exemple)) {
            return exemple; // déjà préfixé : conservé tel quel, jamais re-préfixé
        }
        return prefixe.nommerOriginal(exemple);
    }
}

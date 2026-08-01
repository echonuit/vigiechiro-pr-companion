package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.List;
import java.util.Objects;

/// Les trois critères de l'écran **Sons & validation** qui manquaient en ligne de commande (#3082).
///
/// ## Trois, et non huit
///
/// L'issue d'origine annonçait huit critères absents. La confrontation réelle des deux inventaires en a
/// donné **trois** : les sept autres existaient déjà (`--statut`, `--taxon`, `--lieu`, `--reference`,
/// `--douteux`, `--a-enjeu`, `--proba-min`). L'écart avait été écrit **de mémoire**, dans une issue dont
/// la consigne était précisément de confronter les listes plutôt que des exemples.
///
/// ## Chacun se comporte selon sa nature (ADR 3082)
///
/// Le **taxon parent** désigne quelque chose : mal tapé, il rend un ensemble vide qui est une faute de
/// frappe, donc il **refuse** en nommant les taxons parents présents. Les **non identifiés** et la
/// **plage horaire** qualifient : « aucune séquence sans proposition » est une réponse, et souvent celle
/// qu'on cherchait.
///
/// ## Ce que l'écran dit, la commande le dit pareil
///
/// Une observation **sans heure de capture** est conservée par la plage, et la plage **traverse minuit**
/// quand son début est plus tard que sa fin (21 h → 6 h retient la nuit). Ces deux règles sont celles de
/// la puce ; les changer ici ferait diverger deux surfaces sur la même donnée, et un recoupement entre
/// elles deviendrait faux sans que rien ne le signale.
public final class FiltresRevue {

    private FiltresRevue() {}

    /// Les lignes du **taxon parent** demandé (correspondance partielle, insensible casse/accents, comme
    /// `--lieu`). Nul ou vide n'écarte rien.
    ///
    /// @throws RegleMetierException si aucune ligne ne relève de ce taxon parent
    public static List<LigneObservationAudio> parTaxonParent(List<LigneObservationAudio> lignes, String groupe) {
        if (groupe == null || groupe.isBlank()) {
            return lignes;
        }
        String demande = NormalisationTexte.normaliser(groupe);
        List<LigneObservationAudio> retenues = lignes.stream()
                .filter(ligne -> ligne.groupe() != null
                        && NormalisationTexte.normaliser(ligne.groupe()).contains(demande))
                .toList();
        if (retenues.isEmpty()) {
            throw new RegleMetierException("Aucune observation pour le taxon parent « " + groupe
                    + " » parmi celles retenues. Taxons parents présents : " + presents(lignes) + ".");
        }
        return retenues;
    }

    /// Les séquences **non identifiées** : présentes sur le disque, absentes du CSV Tadarida, donc sans
    /// proposition. Même règle que la puce de l'écran (`taxonTadarida == null`).
    ///
    /// Ne refuse pas sur un ensemble vide : c'est une qualification, et « aucune séquence en attente » est
    /// une réponse, souvent la bonne nouvelle qu'on venait chercher.
    public static List<LigneObservationAudio> nonIdentifiees(List<LigneObservationAudio> lignes, boolean actif) {
        return actif
                ? lignes.stream().filter(ligne -> ligne.taxonTadarida() == null).toList()
                : lignes;
    }

    /// Les lignes dont l'**heure de capture** tombe dans la plage `[de, a]`, bornes comprises.
    ///
    /// La plage **traverse minuit** quand `de > a` : `21 → 6` retient la nuit, et non ses quinze heures
    /// complémentaires. Une ligne **sans heure** est conservée, comme à l'écran : l'absence d'horodatage
    /// n'est pas une heure hors plage, et l'écarter perdrait précisément les séquences à examiner.
    ///
    /// Les deux bornes vont ensemble : n'en donner qu'une est un refus, parce qu'une plage à demi
    /// spécifiée se lirait de deux façons opposées (« depuis 21 h » ou « jusqu'à 21 h »).
    ///
    /// @throws RegleMetierException si une seule borne est donnée, ou si une borne sort de 0..23
    public static List<LigneObservationAudio> parPlageHoraire(
            List<LigneObservationAudio> lignes, Integer de, Integer a) {
        if (de == null && a == null) {
            return lignes;
        }
        if (de == null || a == null) {
            throw new RegleMetierException("Plage horaire incomplète : --heure-debut et --heure-fin vont "
                    + "ensemble. Une seule borne se lirait de deux façons opposées.");
        }
        borner(de, "--heure-debut");
        borner(a, "--heure-fin");
        return lignes.stream().filter(dansPlage(de, a)).toList();
    }

    private static java.util.function.Predicate<LigneObservationAudio> dansPlage(int de, int a) {
        return ligne -> {
            if (ligne.heureCapture() == null) {
                return true;
            }
            int heure = ligne.heureCapture().getHour();
            return de <= a ? (heure >= de && heure <= a) : (heure >= de || heure <= a);
        };
    }

    private static void borner(int heure, String option) {
        if (heure < 0 || heure > 23) {
            throw new RegleMetierException(
                    "Heure hors bornes : " + option + " " + heure + ". Les heures vont de 0 à 23.");
        }
    }

    /// Les taxons parents réellement présents, triés : ce qu'un refus doit nommer pour être corrigeable.
    private static String presents(List<LigneObservationAudio> lignes) {
        List<String> groupes = lignes.stream()
                .map(LigneObservationAudio::groupe)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return groupes.isEmpty() ? "aucun" : String.join(", ", groupes);
    }
}

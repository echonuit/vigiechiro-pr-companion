package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.FiltresTaxonParent;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// Restreindre l'inventaire des espèces **en ligne de commande** (#3269), aux mêmes conditions que la
/// barre de filtres de l'écran « Espèces & observations ».
///
/// ## Jumelle de [FiltresActivite], et pourquoi elle ne délègue pas à l'écran
///
/// Le catalogue de l'écran vit dans `view`, que la ligne de commande ne peut pas lire. Mais la
/// duplication n'est qu'apparente : les deux surfaces ne posent pas la même question. L'écran fait
/// **choisir dans une liste** des valeurs présentes ; la commande reçoit un **fragment tapé**, et
/// compare donc partiellement, insensible à la casse et aux accents - comme `--lieu` partout ailleurs.
/// Déléguer alignerait la commande sur une ergonomie qui n'est pas la sienne.
///
/// ## Un écart assumé, le même que chez ses jumelles
///
/// Le **point** n'est pas une dimension de lieu en ligne de commande. Le schéma pose
/// `UNIQUE(site_id, code)` : un code seul désigne autant de lieux qu'il y a de carrés. L'écran s'en tire
/// en le qualifiant (« 640380 · A1 ») ; la commande s'aligne sur `FiltresActivite` et ne l'offre pas.
///
/// ## Ce qui refuse, et ce qui rend vide
///
/// Un critère qui **désigne** quelque chose refuse quand ce quelque chose n'existe pas, en nommant ce
/// qui existe : rendre une liste vide laisserait croire que la valeur existe et ne ramène rien.
///
/// Un critère qui **qualifie** (la nature d'une nuit, l'enjeu d'une espèce) rend légitimement un
/// ensemble vide : « aucune espèce à enjeu dans cet inventaire » est une réponse, souvent celle qu'on
/// cherchait. C'est la distinction de l'[ADR
/// 3082](https://companion-dev.echonuit.fr/decisions/3082-designer-refuse-qualifier-rend-vide/).
public final class FiltresAnalyse {

    private FiltresAnalyse() {}

    /// Les dimensions de lieu **comparables en ligne de commande** : commune, carré, et **point qualifié
    /// par son carré** (#3350) - la sortie porte les deux, elle désambiguïse donc un code seul.
    ///
    /// Le carré est écrit comme l'écran l'affiche, « 640380 · Vallon » (#3157), pour que le refus de
    /// `--lieu` nomme les lieux tels qu'on les y voit.
    public static List<String> dimensionsLieu(ObservationAnalyse observation) {
        return Stream.of(
                        observation.commune(),
                        LieuQualifie.qualifier(observation.numeroCarre(), observation.nomSite()),
                        LieuQualifie.qualifier(observation.numeroCarre(), observation.codePoint()))
                .filter(valeur -> valeur != null && !valeur.isBlank())
                .toList();
    }

    /// Les observations de l'**état de revue** demandé. `statut` nul n'écarte rien.
    ///
    /// Ne refuse pas sur un résultat vide : l'état de revue **qualifie** une observation, et « aucune
    /// observation corrigée » est une réponse - c'est même ce qu'on espère lire en fin de saison.
    public static List<ObservationAnalyse> parStatut(List<ObservationAnalyse> observations, StatutObservation statut) {
        if (statut == null) {
            return observations;
        }
        return observations.stream()
                .filter(observation -> observation.statut() == statut)
                .toList();
    }

    /// Les observations du **taxon parent** demandé (correspondance partielle, insensible casse/accents,
    /// comme `--lieu`). `groupe` nul n'écarte rien.
    ///
    /// @throws RegleMetierException si aucune observation ne relève de ce taxon parent
    public static List<ObservationAnalyse> parTaxonParent(List<ObservationAnalyse> observations, String groupe) {
        return FiltresTaxonParent.parTaxonParent(
                observations, groupe, ObservationAnalyse::groupe, "Aucune observation");
    }

    /// Les observations des nuits de la **nature** demandée. `nature` nul n'écarte rien.
    ///
    /// Ne refuse pas sur un résultat vide : une nuit sans marquage relève du protocole, qui est le cas
    /// courant, et « aucune participation opportuniste » est une réponse.
    ///
    /// @throws RegleMetierException si la nature n'est ni `protocole` ni `opportuniste`
    public static List<ObservationAnalyse> parNature(
            List<ObservationAnalyse> observations, String nature, Set<Long> nuitsOpportunistes) {
        if (nature == null || nature.isBlank()) {
            return observations;
        }
        boolean opportuniste =
                switch (NormalisationTexte.normaliser(nature)) {
                    case "opportuniste" -> true;
                    case "protocole" -> false;
                    default ->
                        throw new RegleMetierException("Nature inconnue : --nature " + nature
                                + ". Valeurs acceptées : protocole, opportuniste.");
                };
        return observations.stream()
                .filter(observation -> opportuniste == nuitsOpportunistes.contains(observation.idPassage()))
                .toList();
    }

    /// Les observations d'espèces **prioritaires** au sens du Plan National d'Actions Chiroptères.
    ///
    /// Ne refuse pas sur un résultat vide, pour la même raison que [#parNature] : c'est une
    /// qualification, et « aucune espèce à enjeu » est une information.
    public static List<ObservationAnalyse> aEnjeu(
            List<ObservationAnalyse> observations, Predicate<ObservationAnalyse> estPrioritaire) {
        return observations.stream().filter(estPrioritaire).toList();
    }

    /// Ce que le **refus** enumere : commune et carre, sans le point (#3350). Le point evincait la
    /// moitie des carres sous la borne du message, mesure faite.
    public static List<String> dimensionsNommees(ObservationAnalyse observation) {
        return Stream.of(
                        observation.commune(), LieuQualifie.qualifier(observation.numeroCarre(), observation.nomSite()))
                .filter(valeur -> valeur != null && !valeur.isBlank())
                .toList();
    }
}

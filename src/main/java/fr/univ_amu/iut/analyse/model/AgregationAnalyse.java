package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// Agrégation **en mémoire** de l'inventaire « Espèces & observations » (#analyse, #537 étape 4) : à
/// partir des [ObservationAnalyse] déjà filtrées (statut, texte, groupe…), reproduit fidèlement les
/// `GROUP BY` autrefois faits en SQL. Classe **pure** (aucun accès base, aucun JavaFX), directement
/// testable.
///
/// **Fidélité au SQL remplacé** : mêmes compteurs (`COUNT` / `COUNT(DISTINCT …)`), mêmes min/max
/// d'année, et **même tri par défaut** — par espèce : nombre d'observations décroissant puis nom
/// vernaculaire ; par carré : richesse décroissante puis numéro de carré. Le tri secondaire place les
/// valeurs nulles en premier, comme `ORDER BY … ASC` sous SQLite.
public final class AgregationAnalyse {

    private AgregationAnalyse() {}

    /// Regroupe par **espèce** (taxon retenu) : une [EspeceAgregee] par espèce présente, avec le nombre
    /// d'observations, de passages/carrés/points **distincts** et la plage d'années. Triée par nombre
    /// d'observations décroissant, puis nom vernaculaire.
    public static List<EspeceAgregee> parEspece(List<ObservationAnalyse> observations) {
        Map<String, List<ObservationAnalyse>> parTaxon = grouperStable(observations, ObservationAnalyse::taxonRetenu);
        return parTaxon.values().stream()
                .map(AgregationAnalyse::agregerEspece)
                .sorted(Comparator.comparingInt(EspeceAgregee::nbObservations)
                        .reversed()
                        .thenComparing(
                                EspeceAgregee::nomVernaculaireFr, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /// Regroupe par **carré** : une [CarreEspeces] par carré présent, avec la **richesse** (espèces
    /// distinctes), le nombre d'observations et la plage d'années. Triée par richesse décroissante, puis
    /// numéro de carré.
    public static List<CarreEspeces> parCarre(List<ObservationAnalyse> observations) {
        Map<String, List<ObservationAnalyse>> parCarre = grouperStable(observations, ObservationAnalyse::numeroCarre);
        return parCarre.values().stream()
                .map(AgregationAnalyse::agregerCarre)
                .sorted(Comparator.comparingInt(CarreEspeces::richesse)
                        .reversed()
                        .thenComparing(CarreEspeces::numeroCarre, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private static EspeceAgregee agregerEspece(List<ObservationAnalyse> groupe) {
        ObservationAnalyse premiere = groupe.get(0);
        return new EspeceAgregee(
                premiere.taxonRetenu(),
                premiere.nomLatin(),
                premiere.nomVernaculaireFr(),
                premiere.groupe(),
                groupe.size(),
                compterDistinct(groupe, ObservationAnalyse::idPassage),
                compterDistinct(groupe, ObservationAnalyse::numeroCarre),
                compterDistinct(groupe, ObservationAnalyse::idPoint),
                minAnnee(groupe),
                maxAnnee(groupe));
    }

    private static CarreEspeces agregerCarre(List<ObservationAnalyse> groupe) {
        ObservationAnalyse premiere = groupe.get(0);
        return new CarreEspeces(
                premiere.numeroCarre(),
                premiere.nomSite(),
                compterDistinct(groupe, ObservationAnalyse::taxonRetenu),
                groupe.size(),
                minAnnee(groupe),
                maxAnnee(groupe));
    }

    /// Regroupe en préservant l'**ordre de première apparition** des clés (agrégation déterministe avant
    /// le tri final).
    private static Map<String, List<ObservationAnalyse>> grouperStable(
            List<ObservationAnalyse> observations, Function<ObservationAnalyse, String> cle) {
        Map<String, List<ObservationAnalyse>> groupes = new LinkedHashMap<>();
        for (ObservationAnalyse observation : observations) {
            groupes.computeIfAbsent(cle.apply(observation), c -> new ArrayList<>())
                    .add(observation);
        }
        return groupes;
    }

    private static int compterDistinct(List<ObservationAnalyse> groupe, Function<ObservationAnalyse, ?> champ) {
        return (int) groupe.stream().map(champ).distinct().count();
    }

    private static int minAnnee(List<ObservationAnalyse> groupe) {
        return groupe.stream().mapToInt(ObservationAnalyse::annee).min().orElse(0);
    }

    private static int maxAnnee(List<ObservationAnalyse> groupe) {
        return groupe.stream().mapToInt(ObservationAnalyse::annee).max().orElse(0);
    }
}

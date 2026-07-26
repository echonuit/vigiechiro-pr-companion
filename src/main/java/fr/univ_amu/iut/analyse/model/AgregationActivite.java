package fr.univ_amu.iut.analyse.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Agrégation **en mémoire** de l'activité d'une nuit par espèce et par tranche horaire (#2352, lot 2 du
/// chantier #2348). Jumelle temporelle de [AgregationAnalyse] : même esprit (classe **pure**, aucun accès
/// base ni JavaFX, directement testable ; regroupement stable puis tri), mais l'axe est le **temps de la
/// nuit** au lieu de l'espèce ou du carré.
///
/// **Entrée** : les [ContactHoraire] d'**une** nuit, déjà filtrés par l'appelant (statut, pseudo-taxons
/// `noise`/`piaf`, sélection de nuit) — exactement comme [AgregationAnalyse] reçoit des observations déjà
/// filtrées. L'agrégation **écarte** les contacts sans heure (impossibles à situer sur l'axe) et sans
/// taxon (une séquence non identifiée n'est pas une espèce) : ce ne sont pas des erreurs, juste des
/// contacts sans place sur une courbe d'espèce.
///
/// **Sortie** : une [CourbeEspece] par espèce présente, **triée par total décroissant** — l'ordre dans
/// lequel la vue proposera les espèces et sélectionnera les cinq premières par défaut — puis, à total
/// égal, par nom vernaculaire (nuls en premier, comme le tri secondaire de [AgregationAnalyse]).
public final class AgregationActivite {

    private AgregationActivite() {}

    /// Regroupe les contacts d'une nuit par espèce, puis chaque espèce par tranche de largeur `tranche`.
    /// Voir la documentation de classe pour les contacts écartés et l'ordre de sortie.
    public static List<CourbeEspece> parEspece(List<ContactHoraire> contacts, LargeurTranche tranche) {
        Map<String, List<ContactHoraire>> parTaxon = grouperParEspece(contacts);
        return parTaxon.values().stream()
                .map(groupe -> agregerEspece(groupe, tranche))
                .sorted(Comparator.comparingInt(CourbeEspece::total)
                        .reversed()
                        .thenComparing(CourbeEspece::nomEspece, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /// Regroupe par taxon retenu en préservant l'**ordre de première apparition** (agrégation déterministe
    /// avant le tri final), en écartant au passage les contacts inexploitables (sans taxon ou sans heure).
    private static Map<String, List<ContactHoraire>> grouperParEspece(List<ContactHoraire> contacts) {
        Map<String, List<ContactHoraire>> groupes = new LinkedHashMap<>();
        for (ContactHoraire contact : contacts) {
            if (contact.taxon() == null || contact.heure() == null) {
                continue;
            }
            groupes.computeIfAbsent(contact.taxon(), t -> new ArrayList<>()).add(contact);
        }
        return groupes;
    }

    private static CourbeEspece agregerEspece(List<ContactHoraire> groupe, LargeurTranche tranche) {
        ContactHoraire premier = groupe.get(0);
        Map<LocalDateTime, Integer> parTranche = new LinkedHashMap<>();
        for (ContactHoraire contact : groupe) {
            parTranche.merge(debutTranche(contact.heure(), tranche), 1, Integer::sum);
        }
        List<PointActivite> points = parTranche.entrySet().stream()
                .map(entree -> new PointActivite(entree.getKey(), entree.getValue()))
                .sorted(Comparator.comparing(PointActivite::debutTranche))
                .toList();
        return new CourbeEspece(premier.taxon(), premier.nomEspece(), premier.groupe(), groupe.size(), points);
    }

    /// Début de la tranche contenant `heure`, **aligné sur l'horloge** : les minutes écoulées depuis le
    /// début du jour sont tronquées au multiple inférieur de la largeur de tranche (top d'heure pour 60
    /// min, :00/:30 pour 30, :00/:15/:30/:45 pour 15), secondes et nanosecondes remises à zéro.
    private static LocalDateTime debutTranche(LocalDateTime heure, LargeurTranche tranche) {
        int minutesDuJour = heure.getHour() * 60 + heure.getMinute();
        int debutMinutes = (minutesDuJour / tranche.minutes()) * tranche.minutes();
        return heure.toLocalDate().atStartOfDay().plusMinutes(debutMinutes);
    }
}

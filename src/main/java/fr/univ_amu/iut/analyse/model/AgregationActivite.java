package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.Nuit;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
/// **Entrée** : des [ContactHoraire] déjà filtrés par l'appelant, d'une nuit ou de plusieurs (statut, pseudo-taxons
/// `noise`/`piaf`, sélection de nuit) — exactement comme [AgregationAnalyse] reçoit des observations déjà
/// filtrées. L'agrégation **écarte** les contacts sans heure (impossibles à situer sur l'axe) et sans
/// taxon (une séquence non identifiée n'est pas une espèce) : ce ne sont pas des erreurs, juste des
/// contacts sans place sur une courbe d'espèce.
///
/// **Sortie** : une [CourbeEspece] par espèce présente, **triée par total décroissant** — l'ordre dans
/// lequel la vue proposera les espèces et sélectionnera les cinq premières par défaut — puis, à total
/// égal, par nom vernaculaire (nuls en premier, comme le tri secondaire de [AgregationAnalyse]).
///
/// Sur **plusieurs nuits**, [#parEspece] rend un point par (nuit, tranche) : c'est la matière de
/// l'export, qui date ses lignes. Une **courbe** ne se lit pas ainsi — l'axe est celui d'une nuit —
/// et demande le repliement de [#replierSurLaNuit].
public final class AgregationActivite {

    /// Origine de l'axe nocturne : 18 h, le repère depuis lequel se comptent les heures de la nuit.
    private static final LocalTime DEBUT_NUIT = LocalTime.of(18, 0);

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

    /// **Replie** des courbes couvrant plusieurs nuits sur **une** nuit : les tranches de même heure de nuit
    /// sont **sommées**, et la courbe redevient une fonction du temps de la nuit.
    ///
    /// Sans ce repliement, une vue transverse trace, pour une même espèce, un point par (nuit, tranche) ;
    /// l'axe nocturne les ramenant tous à leur heure de nuit, plusieurs points se superposent à la même
    /// abscisse et la ligne, qui suit l'ordre chronologique, **repart en arrière** à chaque nuit suivante —
    /// la courbe prend un aspect de dents de scie qui ne décrit rien.
    ///
    /// L'instant porté par chaque point retombe sur la **nuit la plus ancienne** du jeu : après repliement,
    /// seule l'**heure de la nuit** a du sens, la date n'est qu'un support. Sur une nuit unique, le
    /// repliement est l'identité.
    public static List<CourbeEspece> replierSurLaNuit(List<CourbeEspece> courbes) {
        LocalDate reference = nuitLaPlusAncienne(courbes);
        if (reference == null) {
            return courbes;
        }
        return courbes.stream().map(courbe -> replier(courbe, reference)).toList();
    }

    private static CourbeEspece replier(CourbeEspece courbe, LocalDate reference) {
        Map<Long, Integer> parHeureDeNuit = new LinkedHashMap<>();
        for (PointActivite point : courbe.points()) {
            parHeureDeNuit.merge(minutesDepuisDebutDeNuit(point.debutTranche()), point.nombre(), Integer::sum);
        }
        List<PointActivite> points = parHeureDeNuit.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entree ->
                        new PointActivite(reference.atTime(DEBUT_NUIT).plusMinutes(entree.getKey()), entree.getValue()))
                .toList();
        return new CourbeEspece(courbe.taxon(), courbe.nomEspece(), courbe.groupe(), courbe.total(), points);
    }

    /// Minutes écoulées depuis 18 h le soir de la nuit à laquelle `instant` appartient (bascule à midi,
    /// [Nuit]) : la clé de repliement, et l'abscisse même de l'axe nocturne.
    private static long minutesDepuisDebutDeNuit(LocalDateTime instant) {
        return Duration.between(Nuit.de(instant).atTime(DEBUT_NUIT), instant).toMinutes();
    }

    /// **Comble les creux** : sur la plage `[debutMinutes, finMinutes]` de l'axe nocturne, toute tranche
    /// sans contact reçoit un point à **zéro**.
    ///
    /// Sans ce comblement, une espèce détectée à 21 h puis à 01 h voit ses deux points **reliés par une
    /// droite** : la courbe donne à voir une activité continue là où il n'y en avait aucune. Une absence
    /// doit se lire comme un zéro, pas comme une interpolation.
    ///
    /// La plage est **fournie par l'appelant**, et c'est tout l'enjeu : un zéro affirme « on écoutait, et
    /// rien n'est venu ». Hors de la fenêtre où l'on écoutait vraiment, l'absence de contact ne dit rien —
    /// le capteur pouvait être éteint — et la courbe **s'abstient** plutôt que d'affirmer un silence
    /// observé. Les points hors plage sont conservés tels quels : ce sont des faits.
    public static List<CourbeEspece> comblerLesCreux(
            List<CourbeEspece> courbes, LargeurTranche tranche, long debutMinutes, long finMinutes) {
        if (courbes.isEmpty() || finMinutes < debutMinutes) {
            return courbes;
        }
        LocalDate reference = nuitLaPlusAncienne(courbes);
        if (reference == null) {
            return courbes;
        }
        long premiere = alignerSurLaTranche(debutMinutes, tranche);
        return courbes.stream()
                .map(courbe -> combler(courbe, tranche, premiere, finMinutes, reference))
                .toList();
    }

    private static CourbeEspece combler(
            CourbeEspece courbe, LargeurTranche tranche, long premiere, long finMinutes, LocalDate reference) {
        Map<Long, Integer> parHeureDeNuit = new LinkedHashMap<>();
        for (long minute = premiere; minute <= finMinutes; minute += tranche.minutes()) {
            parHeureDeNuit.put(minute, 0);
        }
        for (PointActivite point : courbe.points()) {
            parHeureDeNuit.put(minutesDepuisDebutDeNuit(point.debutTranche()), point.nombre());
        }
        List<PointActivite> points = parHeureDeNuit.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entree ->
                        new PointActivite(reference.atTime(DEBUT_NUIT).plusMinutes(entree.getKey()), entree.getValue()))
                .toList();
        return new CourbeEspece(courbe.taxon(), courbe.nomEspece(), courbe.groupe(), courbe.total(), points);
    }

    /// Aligne une minute de l'axe sur le **début de sa tranche**, pour que les zéros tombent sur la même
    /// grille que les contacts (une fenêtre ouverte à 20:25 commence la tranche de 20:00 en pas horaire).
    private static long alignerSurLaTranche(long minutes, LargeurTranche tranche) {
        return Math.floorDiv(minutes, tranche.minutes()) * (long) tranche.minutes();
    }

    /// Minutes depuis 18 h (l'origine de l'axe nocturne) pour un instant donné : sert à l'appelant à
    /// convertir une fenêtre d'enregistrement en bornes de comblement.
    public static long minutesSurLAxe(LocalDateTime instant) {
        return minutesDepuisDebutDeNuit(instant);
    }

    private static LocalDate nuitLaPlusAncienne(List<CourbeEspece> courbes) {
        return courbes.stream()
                .flatMap(courbe -> courbe.points().stream())
                .map(point -> Nuit.de(point.debutTranche()))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}

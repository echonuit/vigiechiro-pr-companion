package fr.univ_amu.iut.commun.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Optional;

/// Comment le produit écrit une **date et une heure** à l'utilisateur (#3821). Le produit en écrivait
/// trois formes sans que personne l'ait décidé, mesurées à la clôture du lot 4 de #3802 sur 35
/// `DateTimeFormatter.ofPattern`, dont 21 sans locale.
///
/// **Deux formes, pas une** : le « à » se lit bien dans une phrase et mal dans une colonne. C'est le
/// **nom** de la constante qui empêche de se tromper, pas la discipline - [#dansUnTableau] dit où
/// elle va. **Trois familles restent dehors** et les rapatrier serait un défaut : les noms de
/// fichiers, qui doivent trier lexicalement ; la lecture de fichiers tiers, fidèle au producteur ;
/// les sorties `--json` (#3990), contrats de script où l'ISO est attendue.
public final class Horodatage {

    /// ⚠️ Locale **explicite**, alors que le motif est purement numérique et n'en aurait presque jamais
    /// besoin. « Presque jamais » est exactement la formule qui a produit la police système de #3773 et
    /// la couleur ANSI de #3738 : le dépôt épingle déjà locale et fuseau pour ses captures, pour que
    /// l'aperçu montre le produit et non la machine.
    private static final DateTimeFormatter DANS_UNE_PHRASE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE);

    private static final DateTimeFormatter DANS_UN_TABLEAU =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    /// La **date seule**, pour un titre ou une phrase qui parle d'une nuit et non d'un instant
    /// (#3950). « nuit du 22/04/2026 ».
    ///
    /// ⚠️ Elle ne remplace pas [#dansUnTableau] dans une **colonne** de date. Ces colonnes rendent une
    /// chaîne ISO venue de la base, et une colonne de chaînes trie **lexicalement** : l'ISO est le seul
    /// format où ce tri reste chronologique. Les convertir demande un comparateur, et un filtre qui
    /// cherche dans le texte affiché suivrait. C'est un autre travail, délibérément hors de #3950.
    private static final DateTimeFormatter DATE_SEULE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private Horodatage() {}

    /// « 22/04/2026 » - la date d'une nuit, dans un titre ou une phrase.
    ///
    /// @param dateIso la date au format ISO telle que la base la porte (`Passage.dateEnregistrement`),
    ///     éventuellement `null` ou illisible - le titre d'un compte rendu ne doit pas casser sur une
    ///     donnée abîmée, il rend alors la chaîne telle quelle
    public static String dateSeule(String dateIso) {
        if (dateIso == null || dateIso.isBlank()) {
            return "";
        }
        try {
            return DATE_SEULE.format(java.time.LocalDate.parse(dateIso));
        } catch (java.time.format.DateTimeParseException illisible) {
            return dateIso;
        }
    }

    /// L'instant que la plateforme renvoie (`2026-07-03T19:00:00+00:00`), ramené à **l'heure murale du
    /// site** ; vide s'il est absent ou illisible.
    ///
    /// Couper la chaîne au `T` paraît suffire et **change la date** dès que le décalage traverse
    /// minuit : une nuit commencée à 21:00 dans un fuseau à `-03:00` arrive en `2026-07-04T00:00:00Z`
    /// (#4017). L'écriture convertit déjà dans ce sens ([ADR 3406], [ADR 3442]) et les deux moitiés de
    /// la boucle doivent parler le même fuseau, sans quoi chaque cycle déplace la nuit - le cliquet de
    /// #1860.
    ///
    /// @param borne l'instant tel que l'API le donne, ou `null`
    /// @param fuseau le fuseau du site - `FuseauDuPoint.pour(idPoint)` quand le point est connu,
    ///     [FuseauDuSite#ZONE] sinon
    public static Optional<LocalDateTime> heureMurale(String borne, ZoneId fuseau) {
        if (borne == null || borne.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    OffsetDateTime.parse(borne).atZoneSameInstant(fuseau).toLocalDateTime());
        } catch (DateTimeParseException premiere) {
            try {
                // Sans décalage, rien à convertir : la borne est déjà une heure murale.
                return Optional.of(LocalDateTime.parse(borne));
            } catch (DateTimeParseException seconde) {
                return Optional.empty();
            }
        }
    }

    /// « 03/07/2026 21:00 » - l'instant de la plateforme, lisible, dans le fuseau du site.
    ///
    /// ⚠️ Rend la chaîne **telle quelle** quand elle est illisible, plutôt qu'un vide : une donnée
    /// abîmée doit se voir, et un affichage qui l'escamote se présenterait en succès.
    public static String heureMuraleLisible(String borne, ZoneId fuseau) {
        if (borne == null || borne.isBlank()) {
            return "";
        }
        return heureMurale(borne, fuseau).map(Horodatage::dansUnTableau).orElse(borne);
    }

    /// « 03/07/2026 » - la **date** de l'instant que la plateforme renvoie, lue dans le fuseau du site.
    ///
    /// ⚠️ Convertir **puis** couper, et non l'inverse. Couper d'abord donne un jour faux dès que le
    /// décalage traverse minuit, et c'est le défaut de #4017 : `2026-07-03T23:30:00Z` est une nuit du
    /// **4** à Paris, la troncature annonçait le 3.
    public static String dateMuraleLisible(String borne, ZoneId fuseau) {
        if (borne == null || borne.isBlank()) {
            return "";
        }
        return heureMurale(borne, fuseau)
                .map(instant -> DATE_SEULE.format(instant))
                .orElse(borne);
    }

    /// « 03/07/2026 à 21:00 » - pour un instant **inséré dans une phrase**.
    public static String dansUnePhrase(TemporalAccessor instant) {
        return DANS_UNE_PHRASE.format(instant);
    }

    /// « 03/07/2026 21:00 » - pour une **colonne**, où le « à » alourdit sans rien apprendre.
    ///
    /// ⚠️ Et pour tout ce qui doit **s'aligner** sur une colonne vue ailleurs. Le refus du verrou de
    /// workspace l'emploie bien qu'il vive dans une phrase : #3640 l'avait délibérément aligné sur la
    /// table de choix d'une sauvegarde, « deux écrans plus loin ». Le nom seul ne suffisait pas à le
    /// deviner - c'est son test qui l'a rappelé, en rougissant.
    public static String dansUnTableau(TemporalAccessor instant) {
        return DANS_UN_TABLEAU.format(instant);
    }
}

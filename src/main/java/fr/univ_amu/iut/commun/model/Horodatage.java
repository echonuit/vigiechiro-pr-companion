package fr.univ_amu.iut.commun.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Optional;

/// Comment le produit écrit une **date et une heure** à l'utilisateur (#3821).
///
/// ## Le défaut
///
/// Le produit en écrivait **trois** formes, dans le même logiciel, sans que personne l'ait décidé :
/// `03/07/2026 21:00` (4 sites), `03/07/2026 à 21:00` (2), `03/07/2026 à 21h00` (1). Mesuré à la
/// clôture du lot 4 de #3802, sur 35 `DateTimeFormatter.ofPattern` répartis dans `src/main`, dont
/// **21 sans aucune locale**.
///
/// ## Pourquoi DEUX formes, et pas une
///
/// Regardées en contexte plutôt qu'en liste, les divergences n'étaient pas une négligence : le « à »
/// lit bien **dans une phrase** et mal **dans une colonne**, où la date est déjà comprise comme un
/// instant. Et les quatre sites sans « à » sont précisément des **tableaux**.
///
/// Forcer une forme unique aurait donc été mauvais quelque part. Le besoin était réel ; ce qui manquait
/// était de le **nommer**.
///
/// ⚠️ C'est le **nom** qui empêche de se tromper, pas la discipline. `LISIBLE`, `QUAND` ou `FORMAT_NUIT`
/// - les noms qu'on trouvait sur ces constantes - n'apprennent rien à celui qui choisit.
/// [#dansUnTableau] dit où il va.
///
/// ## Ce que cette classe ne couvre PAS, et il faut le lire avant d'y toucher
///
/// Deux autres familles de formateurs vivent dans le produit, avec des exigences **opposées** :
///
/// - **noms de fichiers** (`yyyyMMdd_HHmmss`, `yyyyMMdd-HHmmss`) : ils doivent rester **stables** et
///   trier lexicalement. Leur donner une locale française casserait le tri ;
/// - **lecture de fichiers tiers** (ThLog en `Locale.ROOT`) : ils doivent rester fidèles au
///   **producteur**, pas à nous. Les changer casserait l'import ;
/// - **sorties `--json` de la ligne de commande** (#3990) : ce sont des **contrats de script**, et
///   l'ISO y est la forme attendue. La même commande écrit donc sa date **deux fois différemment** -
///   en français dans son texte, en ISO dans son JSON - parce que les deux sorties n'ont pas le même
///   lecteur. Ce n'est pas une incohérence, c'est la distinction qu'il faut tenir.
///
/// Les rapatrier ici serait un défaut, pas un remède.
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
    /// ## Pourquoi une conversion, et pas une troncature
    ///
    /// ⚠️ Couper la chaîne au `T` paraît suffire et **change la date** dès que le décalage traverse
    /// minuit : une nuit commencée à 21:00 dans un fuseau à `-03:00` arrive en `2026-07-04T00:00:00Z`,
    /// et la troncature annonce le **4** pour une nuit du **3**. C'est ce que faisait
    /// `lister-participations-vigiechiro` (#4017).
    ///
    /// ## Pourquoi ici
    ///
    /// L'écriture convertit déjà dans ce sens ([ADR 3406], [ADR 3442]) : les deux moitiés de la boucle
    /// doivent parler le même fuseau, sans quoi chaque cycle « reconstruire puis envoyer » déplace la
    /// nuit - le cliquet de #1860. La conversion vivait dans `passage.model`, hors de portée de la CLI ;
    /// une seconde copie aurait dérivé de la première.
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

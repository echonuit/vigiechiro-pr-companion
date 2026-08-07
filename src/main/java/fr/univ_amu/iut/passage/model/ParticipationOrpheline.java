package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.model.FuseauDuSite;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Une participation de la plateforme **sans équivalent local** (#1305) : elle existe sur VigieChiro
/// (déposée depuis un autre poste, avant l'application, ou après une réinstallation), mais **rien** de
/// cette nuit n'est en base ici, ni passage, ni observation, ni séquence. C'est la population que les
/// issues A à G ne traitaient pas : elles savent archiver et réactiver un passage **qu'on a eu**.
///
/// @param idParticipation `_id` de la participation sur la plateforme
/// @param numeroCarre carré extrait du titre du site VigieChiro, ou `null` s'il n'a pas pu l'être
/// @param codePoint code de la localité (ex. `Z41`), ou `null`
/// @param dateDebut début de la nuit (ISO 8601), ou `null`
/// @param pointLocalConnu `true` si le carré **et** le point existent déjà localement : la
///     reconstruction est alors possible telle quelle ; sinon, il faut d'abord créer le site et le point
public record ParticipationOrpheline(
        String idParticipation, String numeroCarre, String codePoint, String dateDebut, boolean pointLocalConnu) {

    /// Carré à six chiffres, extrait du **titre du site** VigieChiro (ex. `Vigiechiro - Point Fixe-130711`).
    private static final Pattern CARRE = Pattern.compile("(\\d{6})");

    /// L'orpheline correspondant à une participation distante. Le carré se lit dans le **titre du site** :
    /// l'API ne l'expose pas autrement dans la liste des participations.
    ///
    /// @param pointLocalConnu calculé par l'appelant (lui seul connaît le référentiel local)
    static ParticipationOrpheline depuis(ParticipationVigieChiro participation, boolean pointLocalConnu) {
        return new ParticipationOrpheline(
                participation.id(),
                carreDe(participation),
                participation.point(),
                participation.dateDebut(),
                pointLocalConnu);
    }

    /// Numéro de carré d'une participation, lu dans le titre de son site (`null` s'il n'y figure pas).
    static String carreDe(ParticipationVigieChiro participation) {
        if (participation.siteTitre() == null) {
            return null;
        }
        Matcher trouve = CARRE.matcher(participation.siteTitre());
        return trouve.find() ? trouve.group(1) : null;
    }

    /// Date/heure **locale** d'une borne de nuit, tolérante au format ; vide si la borne est absente ou
    /// illisible.
    ///
    /// L'API rend un **instant** daté d'un décalage (`2026-07-04T19:00:00+00:00`) ; l'application, elle,
    /// raisonne en **heure murale locale** : c'est ce que l'observateur a lu sur sa montre en posant
    /// l'enregistreur, et c'est ce que [CorrespondanceParticipation] reconvertit en UTC à l'envoi. La
    /// conversion doit donc **préserver l'instant** et changer de repère.
    ///
    /// ⚠️ **#1860** : ce code appelait `toLocalDateTime()`, qui **jette le décalage** au lieu de convertir -
    /// `19:00+00:00` devenait `19:00` local au lieu de `21:00` à Paris. L'erreur ne s'arrêtait pas là :
    /// l'envoi retraduit ensuite l'heure locale en UTC, si bien que **chaque cycle
    /// reconstruire → envoyer retranchait un décalage horaire**. Une nuit de 21 h était descendue à
    /// 15 h en quatre allers-retours, entraînant la météo avec elle (Open-Meteo est interrogé sur la
    /// fenêtre du passage). Un cliquet, pas un décalage ponctuel.
    /// Variante **historique**, qui lit dans le fuseau de la métropole.
    ///
    /// ⚠️ À n'employer que là où le point n'est pas connu. Partout où il l'est, passer par
    /// [#horodatage(String, ZoneId)] : depuis #3442, l'écriture emploie le fuseau du **territoire** du
    /// site, et relire dans un autre casserait le point fixe décrit ci-dessus.
    static Optional<LocalDateTime> horodatage(String borne) {
        return horodatage(borne, FuseauDuSite.ZONE);
    }

    /// La borne renvoyée par la plateforme, ramenée à l'heure murale du site (#3442).
    ///
    /// `fuseau` vient de `FuseauDuPoint.pour(idPoint)` : c'est **le même** que celui dont l'écriture se
    /// sert pour cette nuit. Les deux moitiés de la boucle doivent parler le même fuseau, sans quoi
    /// chaque cycle « reconstruire puis envoyer » déplace la nuit - le cliquet de #1860.
    static Optional<LocalDateTime> horodatage(String borne, ZoneId fuseau) {
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
}

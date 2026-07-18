package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Une participation de la plateforme **sans équivalent local** (#1305) : elle existe sur VigieChiro
/// (déposée depuis un autre poste, avant l'application, ou après une réinstallation), mais **rien** de
/// cette nuit n'est en base ici — ni passage, ni observation, ni séquence. C'est la population que les
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

    /// Date/heure d'une borne de nuit, tolérante au format (l'API rend de l'ISO 8601 avec décalage) ; vide
    /// si la borne est absente ou illisible.
    static Optional<LocalDateTime> horodatage(String borne) {
        if (borne == null || borne.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(borne).toLocalDateTime());
        } catch (DateTimeParseException premiere) {
            try {
                return Optional.of(LocalDateTime.parse(borne));
            } catch (DateTimeParseException seconde) {
                return Optional.empty();
            }
        }
    }
}

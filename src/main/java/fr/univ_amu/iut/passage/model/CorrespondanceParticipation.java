package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/// Correspondance **pure** entre un passage local (+ sa configuration micro) et l'objet `participation` de
/// l'API VigieChiro, dans les **deux sens** (axe 4) :
///  - *push* : [#versParticipation] assemble le corps envoyé à l'API (dates RFC 1123 UTC, météo, configuration) ;
///  - *pull* : [#fusionnerMeteo] / [#microDepuis] retraduisent une météo/config distante vers les types locaux.
///
/// Fonctions statiques, sans état ni réseau : c'est le point unique où vivent les conventions de format de
/// l'API (formats de date, codes météo, clés `micro0_*` / `detecteur_enregistreur_*`), vérifiées par la suite
/// de contrat. (Le dépôt [fr.univ_amu.iut.lot.model.DepotVigieChiro] portera aussi ces règles jusqu'à sa
/// bascule sur ce mapper, Phase 1d.)
final class CorrespondanceParticipation {

    /// Type de détecteur/enregistreur (l'app cible les Passive Recorders, log `PaRecPR…`).
    private static final String TYPE_DETECTEUR = "PassiveRecorder";

    private CorrespondanceParticipation() {}

    /// Corps de participation (push) : `point` = code de la localité, fenêtre de nuit en **RFC 1123 UTC**,
    /// bloc météo (#702) et configuration matérielle (#697). Le commentaire n'est pas synchronisé.
    static ParticipationADeposer versParticipation(String codePoint, Passage passage, MaterielMicro micro) {
        return new ParticipationADeposer(
                codePoint, debutVc(passage), finVc(passage), meteo(passage), configuration(passage, micro), null);
    }

    // --- push : local -> API -----------------------------------------------------------------------

    private static MeteoDepot meteo(Passage passage) {
        if (passage.donneesMeteo() == null || passage.donneesMeteo().isBlank()) {
            return null;
        }
        MeteoReleve releve = MeteoPassage.lire(passage.donneesMeteo());
        String vent = releve.vent() == null ? null : releve.vent().name();
        String couverture = codeCouverture(releve.couvertureNuageuse());
        return vent == null && couverture == null ? null : new MeteoDepot(vent, couverture);
    }

    /// Tranche de couverture au format API (`0-25|25-50|50-75|75-100`), ou `null`.
    private static String codeCouverture(CouvertureNuageuse couverture) {
        if (couverture == null) {
            return null;
        }
        return switch (couverture) {
            case DE_0_A_25 -> "0-25";
            case DE_25_A_50 -> "25-50";
            case DE_50_A_75 -> "50-75";
            case DE_75_A_100 -> "75-100";
        };
    }

    /// Configuration matérielle (`detecteur_enregistreur_*` + `micro0_*`, #697), ou `null` si rien n'est
    /// renseigné. Dictionnaire libre, sérialisé tel quel.
    private static Map<String, String> configuration(Passage passage, MaterielMicro micro) {
        Map<String, String> config = new LinkedHashMap<>();
        if (passage.idEnregistreur() != null) {
            config.put("detecteur_enregistreur_type", TYPE_DETECTEUR);
            config.put("detecteur_enregistreur_numserie", passage.idEnregistreur());
        }
        if (micro.typeMicro() != null) {
            config.put("micro0_type", micro.typeMicro());
        }
        if (micro.positionMicro() != null) {
            config.put("micro0_position", micro.positionMicro().name());
        }
        if (micro.hauteurMetres() != null) {
            config.put("micro0_hauteur", hauteur(micro.hauteurMetres()));
        }
        return config.isEmpty() ? null : config;
    }

    /// Début de nuit en RFC 1123 UTC, ou `null` si date/heure de début manquante.
    private static String debutVc(Passage passage) {
        if (passage.dateEnregistrement() == null || passage.heureDebut() == null) {
            return null;
        }
        return rfc1123Utc(LocalDate.parse(passage.dateEnregistrement()), LocalTime.parse(passage.heureDebut()));
    }

    /// Fin de nuit en RFC 1123 UTC ; la nuit **franchit minuit** quand l'heure de fin ne suit pas l'heure de
    /// début (date de fin = lendemain). `null` si date/heure de fin manquante.
    private static String finVc(Passage passage) {
        if (passage.dateEnregistrement() == null || passage.heureFin() == null) {
            return null;
        }
        LocalDate jour = LocalDate.parse(passage.dateEnregistrement());
        LocalTime fin = LocalTime.parse(passage.heureFin());
        if (passage.heureDebut() != null && !fin.isAfter(LocalTime.parse(passage.heureDebut()))) {
            jour = jour.plusDays(1);
        }
        return rfc1123Utc(jour, fin);
    }

    /// Formate un instant **local** (fuseau système de l'observateur) au format datetime attendu par Eve :
    /// **RFC 1123 en UTC** (ex. `Fri, 04 Jul 2026 19:00:00 GMT`). Eve refuse l'ISO 8601 en entrée (vérifié
    /// en réel) et stocke en UTC.
    private static String rfc1123Utc(LocalDate jour, LocalTime heure) {
        return LocalDateTime.of(jour, heure)
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    /// Hauteur du micro en texte, sans décimale superflue (`4.0` → `4`).
    private static String hauteur(Double metres) {
        return metres == Math.rint(metres) ? String.valueOf(metres.intValue()) : String.valueOf(metres);
    }

    // --- pull : API -> local -----------------------------------------------------------------------

    /// Fusionne une météo **distante** ([MeteoDepot]) dans le relevé local `existant` : ne met à jour que le
    /// vent et la couverture (les seuls champs portés par l'API), en **préservant les températures** locales.
    /// `distant` `null` → relevé inchangé.
    static MeteoReleve fusionnerMeteo(MeteoReleve existant, MeteoDepot distant) {
        if (distant == null) {
            return existant;
        }
        MeteoReleve base = existant == null ? MeteoReleve.VIDE : existant;
        return new MeteoReleve(
                base.temperatureDebutNuit(),
                base.temperatureFinNuit(),
                Vent.depuisTexte(distant.vent()),
                couvertureDepuisCode(distant.couverture()));
    }

    /// Couverture locale depuis le code API (`0-25|…`), ou `null` si absent / inconnu.
    private static CouvertureNuageuse couvertureDepuisCode(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "0-25" -> CouvertureNuageuse.DE_0_A_25;
            case "25-50" -> CouvertureNuageuse.DE_25_A_50;
            case "50-75" -> CouvertureNuageuse.DE_50_A_75;
            case "75-100" -> CouvertureNuageuse.DE_75_A_100;
            default -> null;
        };
    }

    /// Matériel micro local depuis la `configuration` distante (`micro0_*`). Valeurs absentes / illisibles →
    /// champs `null` (le [MaterielMicro] reste tolérant).
    static MaterielMicro microDepuis(long idPassage, Map<String, String> configuration) {
        return new MaterielMicro(
                idPassage,
                positionMicro(configuration.get("micro0_position")),
                hauteurMetres(configuration.get("micro0_hauteur")),
                configuration.get("micro0_type"));
    }

    private static PositionMicro positionMicro(String nom) {
        if (nom == null) {
            return null;
        }
        try {
            return PositionMicro.valueOf(nom);
        } catch (IllegalArgumentException inconnu) {
            return null;
        }
    }

    private static Double hauteurMetres(String texte) {
        if (texte == null) {
            return null;
        }
        try {
            return Double.valueOf(texte);
        } catch (NumberFormatException illisible) {
            return null;
        }
    }
}

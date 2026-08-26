package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.MeteoDepot;
import fr.univ_amu.iut.commun.api.ParticipationADeposer;
import fr.univ_amu.iut.commun.model.RegleMetierException;
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

    /// Clé **canonique** VigieChiro du numéro de série de l'enregistreur (celle du formulaire web).
    private static final String CLE_SERIE_CANONIQUE = "detecteur_enregistreur_numero_serie";

    /// Clé **historique** poussée par l'application (#1689) : une participation déposée par l'app porte le
    /// numéro de série sous cette forme, le web sous [#CLE_SERIE_CANONIQUE]. La lecture accepte les deux.
    private static final String CLE_SERIE_APP = "detecteur_enregistreur_numserie";

    private CorrespondanceParticipation() {}

    /// Corps de participation (push) : `point` = code de la localité, fenêtre de nuit en **RFC 1123 UTC**,
    /// bloc météo (#702) et configuration matérielle (#697). Le commentaire n'est pas synchronisé.
    static ParticipationADeposer versParticipation(
            String codePoint,
            Passage passage,
            MaterielMicro micro,
            Map<String, String> configurationDistante,
            ZoneId fuseauDuSite) {
        exigerBornesDeNuit(passage);
        return new ParticipationADeposer(
                codePoint,
                debutVc(passage, fuseauDuSite),
                finVc(passage, fuseauDuSite),
                meteo(passage),
                configuration(passage, micro, configurationDistante),
                null);
    }

    /// Variante **création** (`POST`) : la participation n'existe pas encore, il n'y a aucune configuration
    /// distante à préserver.
    static ParticipationADeposer versParticipation(
            String codePoint, Passage passage, MaterielMicro micro, ZoneId fuseauDuSite) {
        return versParticipation(codePoint, passage, micro, Map.of(), fuseauDuSite);
    }

    // --- push : local -> API -----------------------------------------------------------------------

    private static MeteoDepot meteo(Passage passage) {
        if (passage.donneesMeteo() == null || passage.donneesMeteo().isBlank()) {
            return null;
        }
        MeteoReleve releve = MeteoPassage.lire(passage.donneesMeteo());
        String vent = releve.vent() == null ? null : releve.vent().name();
        String couverture = codeCouverture(releve.couvertureNuageuse());
        // #1844 : les températures partent enfin. Le schéma serveur les porte depuis toujours ; l'app ne
        // les transportait pas, si bien qu'une saisie locale n'atteignait jamais la fiche web.
        Integer debut = enDegresEntiers(releve.temperatureDebutNuit());
        Integer fin = enDegresEntiers(releve.temperatureFinNuit());
        return vent == null && couverture == null && debut == null && fin == null
                ? null
                : new MeteoDepot(vent, couverture, debut, fin);
    }

    /// Température en degrés **entiers** : `meteo.temperature_*` est typé `integer` côté serveur (#1844),
    /// un décimal serait refusé. On **arrondit** au plus proche plutôt que de tronquer - 8,6 °C vaut mieux
    /// 9 que 8.
    private static Integer enDegresEntiers(Double celsius) {
        return celsius == null ? null : (int) Math.round(celsius);
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
    private static Map<String, String> configuration(
            Passage passage, MaterielMicro micro, Map<String, String> distante) {
        // #1844 : on PART de la configuration distante. Le PATCH remplace le dictionnaire entier ; n'envoyer
        // que le nôtre effaçait silencieusement les champs que l'app ne modélise pas mais que le formulaire
        // web gère (micro0_numero_serie, micro1_*, canal_expansion_temps, canal_enregistrement_direct). Nos
        // clés écrasent les leurs ; les autres survivent.
        Map<String, String> config = new LinkedHashMap<>(distante == null ? Map.of() : distante);
        if (passage.idEnregistreur() != null) {
            config.put("detecteur_enregistreur_type", TYPE_DETECTEUR);
            // #1828 : le TYPE est publié (l'app ne pilote que des Passive Recorders : c'est vrai), mais
            // JAMAIS un numéro de série sentinelle. « INCONNU » n'est pas un numéro, c'est un aveu
            // d'ignorance ; le publier fabriquerait une donnée que l'app - et le prochain poste - se
            // reliraient ensuite comme si elle était réelle. Ne rien dire vaut mieux que mentir. Comme
            // `recorder_id` est NOT NULL en base, sans cette garde la sentinelle partait à chaque dépôt.
            if (!Enregistreur.estInconnu(passage.idEnregistreur())) {
                // #1844 : clé **canonique**, celle que lie le formulaire web. L'app poussait la clé
                // historique `..._numserie`, que le front ne lit pas : le numéro arrivait bien sur la
                // plateforme, mais **invisible**. On retire au passage l'ancienne clé, pour que les
                // participations déjà déposées par l'app se réparent au premier envoi.
                config.remove(CLE_SERIE_APP);
                config.put(CLE_SERIE_CANONIQUE, passage.idEnregistreur());
            }
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

    /// Refuse d'assembler une participation dont les **bornes de nuit** manquent (#3451).
    ///
    /// ## Pourquoi un refus, alors que le cas ne se produit pas
    ///
    /// Il ne se produit pas **aujourd'hui** : `V01__schema.sql` déclare `recording_date`, `start_time` et
    /// `end_time` en `TEXT NOT NULL`, et les deux chemins de dépôt chargent leur passage par le DAO. La
    /// seule substitution d'heures, `realignerSurLesPreuves`, écrit d'ailleurs en base **avant** l'envoi :
    /// une valeur nulle buterait sur la contrainte bien avant d'atteindre la plateforme.
    ///
    /// L'invariant n'est donc tenu que par **SQLite**. Ce qui vivait ici avant était un `return null`
    /// silencieux par borne : le champ disparaissait simplement du corps envoyé. Le jour où un chemin de
    /// construction contournerait la base - un passage bâti depuis la plateforme, une entité en mémoire -
    /// une nuit partirait **sans ses bornes** sur la plateforme nationale, et rien ne le dirait.
    ///
    /// Un silence ne se remarque pas ; un refus, si. La nuit est l'unité de traitement du produit
    /// ([ADR 0009]) et ses heures décident de la partition : une nuit sans bornes n'est pas une nuit.
    ///
    /// ## Pourquoi ici et non chez les appelants
    ///
    /// [#versParticipation] est l'entonnoir **unique** des deux chemins d'écriture. Le garde y porte donc
    /// sur la forme du défaut, et non sur la liste des appelants connus au moment de l'écrire.
    private static void exigerBornesDeNuit(Passage passage) {
        if (passage.dateEnregistrement() == null || passage.heureDebut() == null || passage.heureFin() == null) {
            throw new RegleMetierException("Cette nuit n'a pas de bornes complètes (date, heure de début et heure"
                    + " de fin) : elle ne peut pas être déposée sur Vigie-Chiro. Réimportez-la ou réalignez-la sur"
                    + " ses enregistrements pour rétablir ses horaires.");
        }
    }

    /// Début de nuit en RFC 1123 UTC. Les bornes sont garanties présentes par [#exigerBornesDeNuit].
    private static String debutVc(Passage passage, ZoneId fuseauDuSite) {
        return rfc1123Utc(
                LocalDate.parse(passage.dateEnregistrement()), LocalTime.parse(passage.heureDebut()), fuseauDuSite);
    }

    /// Fin de nuit en RFC 1123 UTC ; la nuit **franchit minuit** quand l'heure de fin ne suit pas l'heure de
    /// début (date de fin = lendemain). Les bornes sont garanties présentes par [#exigerBornesDeNuit].
    private static String finVc(Passage passage, ZoneId fuseauDuSite) {
        LocalDate jour = LocalDate.parse(passage.dateEnregistrement());
        LocalTime fin = LocalTime.parse(passage.heureFin());
        if (!fin.isAfter(LocalTime.parse(passage.heureDebut()))) {
            jour = jour.plusDays(1);
        }
        return rfc1123Utc(jour, fin, fuseauDuSite);
    }

    /// Formate une heure **du site** au format datetime attendu par Eve : **RFC 1123 en UTC**
    /// (ex. `Fri, 04 Jul 2026 19:00:00 GMT`). Eve refuse l'ISO 8601 en entrée (vérifié en réel) et
    /// stocke en UTC.
    ///
    /// Le fuseau vient de [FuseauDuSite], pas de `ZoneId.systemDefault()`. Ces heures sont produites
    /// par l'enregistreur **posé sur le site**, pas par la personne qui dépouille : les convertir avec
    /// le fuseau du poste faisait partir un instant différent selon la machine - `19:00 GMT` depuis
    /// Paris, `21:00 GMT` depuis un poste en UTC, et depuis Cayenne un **changement de date** (#3406).
    /// C'est une donnée déposée sur la plateforme nationale, pas un affichage.
    private static String rfc1123Utc(LocalDate jour, LocalTime heure, ZoneId fuseauDuSite) {
        return LocalDateTime.of(jour, heure)
                .atZone(fuseauDuSite)
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    /// Hauteur du micro en texte, sans décimale superflue (`4.0` → `4`).
    private static String hauteur(Double metres) {
        return metres == Math.rint(metres) ? String.valueOf(metres.intValue()) : String.valueOf(metres);
    }

    // --- pull : API -> local -----------------------------------------------------------------------

    /// Fusionne une météo **distante** ([MeteoDepot]) dans le relevé local `existant` : le bloc météo de la
    /// plateforme **remplace** le bloc local, températures comprises. `distant` `null` → relevé inchangé
    /// (il n'y a rien à fusionner ; c'est le seul cas où le local survit).
    ///
    /// Cette méthode a longtemps **préservé les températures locales**, au motif que l'API ne portait que le
    /// vent et la couverture. Le motif était faux : le schéma serveur porte `meteo.temperature_*` depuis
    /// toujours, c'est l'application qui ne les transportait pas (#1844). Une fois l'envoi corrigé, la
    /// récupération restait asymétrique - on lisait les températures pour les jeter aussitôt.
    ///
    /// La règle retenue est celle qui vaut **déjà** pour le vent et la couverture : quand la plateforme a un
    /// bloc météo, c'est lui qui fait foi. Conséquence assumée : une température locale est **écrasée** par
    /// une participation dont le bloc météo existe mais ne porte pas de température (fiche saisie sur le web
    /// avant #1844). C'est le prix de la cohérence du bloc - le traiter champ par champ ferait cohabiter deux
    /// règles de fusion dans le même objet.
    static MeteoReleve fusionnerMeteo(MeteoReleve existant, MeteoDepot distant) {
        if (distant == null) {
            return existant;
        }
        return new MeteoReleve(
                enCelsius(distant.temperatureDebut()),
                enCelsius(distant.temperatureFin()),
                Vent.depuisTexte(distant.vent()),
                couvertureDepuisCode(distant.couverture()));
    }

    /// Température locale (décimale) depuis l'entier de l'API, ou `null`.
    private static Double enCelsius(Integer degres) {
        return degres == null ? null : degres.doubleValue();
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

    /// Numéro de série de l'enregistreur lu dans une `configuration` distante, quelle que soit la clé
    /// employée à la création de la participation : forme canonique VigieChiro ([#CLE_SERIE_CANONIQUE],
    /// web) **ou** forme historique de l'application ([#CLE_SERIE_APP]). `null` si aucune n'est présente
    /// (ou vide) : c'est alors qu'il n'y a vraiment rien à rapatrier.
    static String serieDepuis(Map<String, String> configuration) {
        if (configuration == null) {
            return null;
        }
        String serie = configuration.getOrDefault(CLE_SERIE_CANONIQUE, configuration.get(CLE_SERIE_APP));
        return serie == null || serie.isBlank() ? null : serie;
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

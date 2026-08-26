package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.JsonSimple;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// Les paramètres d'acquisition **posés à un instant**, tels qu'une ligne « Paramètres : … » du
/// journal du capteur les déclare (#3460).
///
/// ## Pourquoi ce type existe
///
/// Le journal en porte **autant que de sessions**. `AnalyseurLogPR` n'en retenait que la première :
///
/// > *« J'ai l'impression que la lecture du log par companion se fait sur les premières lignes pour
/// > trouver la config du PR. »*
///
/// Sur une carte laissée plusieurs nuits au même point - le cas **courant** d'un protocole Point Fixe,
/// pas une manipulation exotique - une nuit repartait donc avec la fréquence d'échantillonnage et la
/// bande passante d'une **autre** session. Ce ne sont pas des métadonnées d'agrément : elles
/// conditionnent la transformation des séquences, si bien que le défaut produisait des données fausses
/// **en silence**, jusqu'à la plateforme.
///
/// ## Ce que l'horodatage permet, et le patron dont il vient
///
/// Le remède n'est pas neuf dans ce dépôt : #1696 a rencontré exactement ce problème pour les
/// évènements et les anomalies, et l'a résolu en les **horodatant**, ce qui permet de les ranger dans
/// la bonne nuit. La configuration est le champ qu'on avait oublié d'horodater ; ce type le répare, et
/// [JournalParse#configurationPourNuit] fait l'appariement.
///
/// @param horodatage l'instant de la ligne qui déclare ces paramètres
/// @param heureDebut début de la fenêtre d'acquisition (ISO `HH:MM:SS`), ou `null`
/// @param heureFin fin de la fenêtre d'acquisition (ISO `HH:MM:SS`), ou `null`
/// @param frequenceEchantillonnageHz fréquence d'acquisition en Hz (ex. 384000), ou `null`
/// @param bandePassante bande passante du micro (ex. `8-120kHz`), ou `null`
/// @param sensibilite réglage de sensibilité (ex. `16dB 1dt. GN0`), ou `null`
/// @param brut la ligne « Paramètres : … » telle quelle, conservée pour ce que l'analyse n'en tire pas
public record ConfigurationAcquisition(
        LocalDateTime horodatage,
        String heureDebut,
        String heureFin,
        Integer frequenceEchantillonnageHz,
        String bandePassante,
        String sensibilite,
        String brut) {

    public ConfigurationAcquisition {
        Objects.requireNonNull(horodatage, "horodatage");
    }

    /// La **nuit** à laquelle cette configuration se rattache, par la bascule de midi de
    /// [PartitionNuits#nuitDe].
    ///
    /// C'est bien la nuit et non la date calendaire, et l'écart n'est pas théorique : le capteur est
    /// posé et configuré l'**après-midi** pour la nuit qui suit. Une configuration du 22 à 16 h régit
    /// donc la nuit du 22 au 23, ce que la même règle dit déjà des réveils et des mises en veille.
    public java.time.LocalDate nuit() {
        return PartitionNuits.nuitDe(horodatage);
    }

    /// Ces paramètres sérialisés en JSON, pour la colonne `passage.acquisition_params`.
    ///
    /// Le format est **identique** à celui de [JournalParse#parametresAcquisitionJson] : les mêmes
    /// clés, dans le même ordre. Ce qui change est la **source** - la configuration de cette nuit-là
    /// plutôt que la première du journal - et rien d'autre. Une nuit importée avant #3460 et une nuit
    /// importée après restent donc comparables.
    public String enJson() {
        Map<String, String> champs = new LinkedHashMap<>();
        champs.put("feHz", frequenceEchantillonnageHz == null ? null : frequenceEchantillonnageHz.toString());
        champs.put("fenetre", heureDebut == null || heureFin == null ? null : heureDebut + "-" + heureFin);
        champs.put("bandePassante", bandePassante);
        champs.put("sensibilite", sensibilite);
        champs.put("brut", brut);
        return JsonSimple.objet(champs);
    }
}

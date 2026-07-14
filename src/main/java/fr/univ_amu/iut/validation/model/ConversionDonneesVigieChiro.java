package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.model.ModeValidation;
import java.util.List;

/// Convertit les résultats Tadarida d'une participation VigieChiro ([DonneeVigieChiro], renvoyés par
/// `GET /participations/#id/donnees`) en [LigneObservation] locales — la **même** projection que le
/// parseur CSV Tadarida ([ParserCsvTadarida]), de sorte que les deux sources rejoignent le cœur d'import
/// commun de [ServiceValidation] (#719, axe 4.2).
///
/// Le rattachement à une séquence d'écoute se fait par **nom de fichier** : le `titre` de la donnée
/// (ex. `Car130711-2026-Pass1-Z41-PaRec_20260703_220529_000`) devient le [LigneObservation#nomSequence].
/// Les unités sont déjà alignées sur le CSV (fréquence en kHz, temps en secondes) ; seule la fréquence
/// médiane est arrondie à l'entier (la colonne `median_freq_khz` est `INTEGER`).
///
/// Faute de colonne de mode de validation dans l'API, le mode est **déduit** : la présence d'un taxon
/// observateur (`observateur_taxon`) traduit une décision humaine sur la plateforme, donc
/// [ModeValidation#MANUEL] ; sinon [ModeValidation#NON_VALIDE] (résultat Tadarida brut).
///
/// L'avis du **validateur** (#1417) traverse ici sans transformation : il ne change **pas** le mode de
/// validation. Un expert du MNHN peut trancher une observation que l'observateur n'a jamais touchée —
/// la faire passer pour « validée manuellement » attribuerait à l'observateur une décision qui n'est
/// pas la sienne. Les deux avis restent distincts, et affichés comme tels.
///
/// Le **fil de discussion** ne passe pas par [LigneObservation] : c'est un 1-N, et le CSV Tadarida n'en
/// a pas. `ServiceValidation` l'écrit à part, une fois les observations insérées et leurs clés connues.
final class ConversionDonneesVigieChiro {

    private ConversionDonneesVigieChiro() {}

    /// Aplati les données en lignes d'observation : une [LigneObservation] par observation, portant le
    /// titre de sa donnée comme nom de séquence et son **ancrage plateforme** (#1139 : `_id` de la
    /// donnée + indice brut serveur, la cible du `PATCH` du contrat #1203). Fonction pure (aucun accès
    /// base ni réseau).
    static List<LigneObservation> enLignes(List<DonneeVigieChiro> donnees) {
        return donnees.stream()
                .flatMap(donnee -> donnee.observations().stream().map(obs -> enLigne(donnee, obs)))
                .toList();
    }

    private static LigneObservation enLigne(DonneeVigieChiro donnee, ObservationVigieChiro obs) {
        return new LigneObservation(
                donnee.titre(),
                obs.tempsDebut(),
                obs.tempsFin(),
                frequenceEntiere(obs.frequenceMediane()),
                obs.taxonTadarida(),
                obs.probabilite(),
                obs.taxonAutre(),
                obs.taxonObservateur(),
                null, // pas de probabilité numérique observateur côté serveur (c'est la certitude)
                obs.taxonObservateur() != null ? ModeValidation.MANUEL : ModeValidation.NON_VALIDE,
                donnee.id(),
                obs.indiceServeur(),
                obs.certitudeObservateur(),
                obs.taxonValidateur(),
                obs.certitudeValidateur());
    }

    private static Integer frequenceEntiere(Double frequenceKHz) {
        return frequenceKHz == null ? null : (int) Math.round(frequenceKHz);
    }
}

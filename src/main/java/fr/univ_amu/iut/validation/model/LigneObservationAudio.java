package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import java.time.LocalDateTime;

/// Projection **unifiée** d'une observation pour la **vue audio** (#audio) : tout ce qu'il faut pour
/// l'écouter, la situer, la valider/corriger et l'archiver, **quelle que soit la source** (un passage, un
/// lot de passages, une espèce à travers les passages, ou le corpus de référence).
///
/// Superset des projections existantes (`ObservationStatut` de la validation n'a pas le contexte passage ;
/// `ObservationEspece` de l'analyse n'a pas `reference`/commentaire/fréquence). Introduite **standalone** :
/// les autres records restent inchangés. L'espèce retenue suit la convention habituelle
/// (`COALESCE(taxon_observer, taxon_tadarida)`) ; le `statut` est dérivé en SQL comme ailleurs.
///
/// @param idObservation clé de l'observation (cible de valider/corriger/marquer-référence), ou `null`
///     pour une **séquence non identifiée** : un enregistrement présent sur disque (donc écoutable) mais
///     sans ligne dans `observation` (aucune identification Tadarida). L'`idSequence` reste, lui, toujours
///     présent : l'écoute ne dépend pas d'une observation.
/// @param idSequence séquence d'écoute associée (cible de l'écoute audio)
/// @param idPassage passage d'où vient l'observation (situer la ligne, « ouvrir le passage »)
/// @param numeroPassage n° de passage dans l'année
/// @param dateEnregistrement date d'enregistrement du passage (texte)
/// @param numeroCarre n° de carré du site du passage
/// @param codePoint code du point d'écoute du passage
/// @param nomSite nom convivial du site, ou `null`
/// @param taxonTadarida proposition automatique Tadarida (code), ou `null` pour une séquence non
///     identifiée (aucune proposition Tadarida)
/// @param probTadarida probabilité de la proposition Tadarida, ou `null`
/// @param taxonObservateur taxon saisi par l'observateur, ou `null` (non touchée)
/// @param probObservateur probabilité saisie par l'observateur, ou `null`
/// @param statut statut de revue dérivé (validée / corrigée / non touchée)
/// @param reference `true` si l'observation est dans le corpus de référence (`is_reference`)
/// @param commentaire commentaire libre de l'observateur, ou `null`
/// @param frequenceKHz fréquence médiane en kHz, ou `null`
/// @param nomEspece nom vernaculaire FR de l'espèce **retenue** (`COALESCE(observateur, tadarida)`), ou
///     `null` si le taxon n'a pas de nom vernaculaire (souche hors référentiel) — la vue affiche alors le code
/// @param nomTadarida nom vernaculaire FR de la **proposition Tadarida** (`taxon_tadarida`), ou `null`
///     (souche hors référentiel) — la vue affiche alors le code
/// @param latinTadarida nom **latin** de la proposition Tadarida (`taxon_tadarida.latin_name`), ou `null` —
///     sert de clé à la source universelle (GBIF/Wikipédia) pour la fiche des taxons hors PNA (oiseaux…)
/// @param groupe nom du **groupe taxonomique parent** de l'espèce retenue (`taxonomic_group.name`, ex.
///     « Chiroptères », « Oiseaux », « Orthoptères et cigales »), ou `null` si le taxon n'a pas de groupe —
///     permet de filtrer la liste par grand groupe (chauves-souris, oiseaux…) sans lister chaque espèce
/// @param nomFichier nom de fichier de la séquence d'écoute (`listening_sequence.file_name`), pour relier
///     la ligne à l'enregistrement écouté
/// @param debutS début du cri dans la séquence, en secondes **réelles** (issu du CSV Tadarida, dont les
///     temps sont en secondes réelles dans la tranche de 5 s), ou `null`
/// @param finS fin du cri dans la séquence, en secondes **réelles**, ou `null` — la durée du cri vaut
///     directement `(finS − debutS)`, sans division (cf. `FormatLigneAudioTest`)
/// @param heureCapture **instant réel** de capture (date + heure) de la séquence, issu de son horodatage
///     persisté (`listening_sequence.recorded_at`, #530), ou `null` si la séquence n'est pas horodatée. On
///     porte l'instant complet (et non l'heure seule) pour un **tri chronologique correct à cheval sur
///     minuit** (00:15 est *après* 22:00 dans une même nuit) ; le filtre par plage horaire raisonne, lui,
///     sur l'heure du jour (`heureCapture.toLocalTime()`)
/// @param douteux `true` si l'observation est marquée « douteuse / à repasser » (`is_doubtful`, #160)
/// @param certitude certitude déclarée manuellement par l'observateur (`observer_certainty`, #1139), ou
///     `null` = non renseignée (vide par défaut, jamais préremplie) ; ajoutée en **dernier** composant
///     pour préserver l'ordre historique du record
public record LigneObservationAudio(
        Long idObservation,
        long idSequence,
        long idPassage,
        int numeroPassage,
        String dateEnregistrement,
        String numeroCarre,
        String codePoint,
        String nomSite,
        String taxonTadarida,
        Double probTadarida,
        String taxonObservateur,
        Double probObservateur,
        StatutObservation statut,
        boolean reference,
        String commentaire,
        Integer frequenceKHz,
        String nomEspece,
        String nomTadarida,
        String latinTadarida,
        String groupe,
        String nomFichier,
        Double debutS,
        Double finS,
        LocalDateTime heureCapture,
        boolean douteux,
        CertitudeObservateur certitude) {

    /// Ces deux projections désignent-elles la **même ligne** ? Une observation se réidentifie par son
    /// `idObservation` (unique, même si une séquence porte plusieurs cris) ; une **séquence non identifiée**
    /// (id nul, puis id non nul après validation manuelle) se réidentifie par son `idSequence`. Sert à
    /// **préserver la sélection** au rechargement de la vue audio, y compris au moment où on valide une
    /// séquence à la main.
    public boolean estLaMemeLigneQue(LigneObservationAudio autre) {
        return idObservation != null ? idObservation.equals(autre.idObservation()) : idSequence == autre.idSequence();
    }
}

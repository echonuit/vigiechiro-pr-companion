package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.Certitude;
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
///     `null` = non renseignée (vide par défaut, jamais préremplie)
/// @param taxonValidateur code du taxon **tranché par le validateur** du MNHN (`taxon_validator`, #1417),
///     ou `null` tant qu'aucun expert ne s'est prononcé. C'est le **troisième avis**, celui qui fait
///     autorité : la vue le présentait jusqu'ici comme inexistant, et l'observateur pouvait croire que sa
///     propre correction était le dernier mot
/// @param certitudeValidateur certitude déclarée par le validateur (`validator_certainty`, #1417), ou
///     `null`
/// @param nomValidateur nom vernaculaire FR du taxon du validateur, ou `null` (souche hors référentiel —
///     la vue affiche alors le code), pendant de `nomTadarida`
/// @param nbMessages nombre de messages du **fil de discussion** de l'observation (#1417) : `0` = personne
///     n'a écrit. Un compteur plutôt que le fil lui-même — la table dit qu'une discussion existe, la modale
///     la donne à lire ; les quatre derniers composants sont ajoutés en **queue** pour préserver l'ordre
///     historique du record
/// @param commune nom de la commune du point d'écoute (table latérale `point_commune`, #2791), ou
///     `null` tant qu'elle n'est pas résolue (point sans GPS, hors ligne à la création) - en queue,
///     comme les ajouts précédents
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
        Certitude certitude,
        String taxonValidateur,
        Certitude certitudeValidateur,
        String nomValidateur,
        int nbMessages,
        String commune) {

    /// Un expert du MNHN s'est-il prononcé sur cette détection ? Vrai dès qu'un taxon de validateur est
    /// posé — c'est ce qui distingue une observation *revue par un expert* d'une observation qu'on est
    /// seul à avoir regardée.
    public boolean trancheeParUnValidateur() {
        return taxonValidateur != null;
    }

    /// Le validateur **contredit-il** l'observateur ? Vrai seulement si les deux se sont prononcés et
    /// qu'ils divergent. Un désaccord est ce qu'on veut voir en premier : c'est là que se joue la qualité
    /// de la donnée déposée.
    public boolean validateurEnDesaccord() {
        return taxonValidateur != null && taxonObservateur != null && !taxonValidateur.equals(taxonObservateur);
    }

    /// Le taxon **retenu** de la ligne : la correction de l'observateur si elle existe, sinon la
    /// proposition de Tadarida (`COALESCE(taxon_observer, taxon_tadarida)`, comme partout ailleurs).
    /// C'est l'espèce que la ligne **affirme**, par opposition à celle qui a été proposée. `null` pour une
    /// séquence non identifiée.
    public String taxonRetenu() {
        return taxonObservateur != null ? taxonObservateur : taxonTadarida;
    }

    /// Une discussion est-elle ouverte sur cette détection ?
    public boolean aUnFil() {
        return nbMessages > 0;
    }

    /// Ces deux projections désignent-elles la **même ligne** ? Une observation se réidentifie par son
    /// `idObservation` (unique, même si une séquence porte plusieurs cris) ; une **séquence non identifiée**
    /// (id nul, puis id non nul après validation manuelle) se réidentifie par son `idSequence`. Sert à
    /// **préserver la sélection** au rechargement de la vue audio, y compris au moment où on valide une
    /// séquence à la main.
    public boolean estLaMemeLigneQue(LigneObservationAudio autre) {
        return idObservation != null ? idObservation.equals(autre.idObservation()) : idSequence == autre.idSequence();
    }
}

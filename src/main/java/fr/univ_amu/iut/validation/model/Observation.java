package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.commun.model.ModeValidation;

/// Observation : une ligne du fichier de résultats Tadarida, soumise à validation (C13, table
/// `observation`). Une séquence d'écoute peut générer plusieurs observations (1 ligne par espèce
/// détectée, avec timing début/fin).
///
/// Point remarquable du MCD : l'observation porte **quatre** clés étrangères distinctes vers
/// [Taxon] :
///
/// - `taxonTadarida` (FK `taxon_tadarida`) : **obligatoire**, proposition de Tadarida ;
/// - `taxonAutreTadarida` (FK `taxon_other_tadarida`) : optionnel, 2e proposition ;
/// - `taxonObservateur` (FK `taxon_observer`) : optionnel, saisi en validation ;
/// - `taxonValidateur` (FK `taxon_validator`) : optionnel, **tranché par un expert du MNHN** sur la
///   plateforme (#1417, V26).
///
/// Ce sont les **trois avis** que VigieChiro distingue sur une même détection : Tadarida *propose*,
/// l'observateur *corrige*, le validateur *tranche*. Le troisième arrivait déjà dans la réponse de
/// `GET /participations/{id}/donnees` : l'application le jetait, et présentait donc la correction de
/// l'observateur comme le dernier mot alors qu'un expert avait pu la réviser.
///
/// L'avis du validateur est en **lecture seule** : le serveur refuse (403) qu'un jeton de rôle
/// `Observateur` le pose (spike de #724). Il est donc toujours un **reflet** du serveur, rafraîchi à
/// chaque import — jamais une saisie locale, jamais préservé d'un import à l'autre.
///
/// Le `modeValidation` est mappé via [ModeValidation] (colonne `validation_mode` ; `null` →
/// [ModeValidation#NON_VALIDE]). Les colonnes numériques optionnelles (`REAL` / `INTEGER`
/// nullable) sont des types wrapper, `null` si absentes.
///
/// Depuis #1139, l'observation porte aussi son **ancrage plateforme** (couple `idDonneeVigieChiro`
/// + `indiceVigieChiro`, cible du `PATCH /donnees/{id}/observations/{index}` du contrat #1203) et la
/// **certitude observateur** ([CertitudeObservateur], déclaration manuelle à la revue, distincte de
/// `probObservateur` qui reste la confiance numérique Tadarida recopiée à la validation).
///
/// @param id clé technique, `null` avant insertion
/// @param idSequence séquence d'écoute source (FK → `listening_sequence.id`, obligatoire)
/// @param debutS temps de début dans la séquence en secondes (optionnel)
/// @param finS temps de fin dans la séquence en secondes (optionnel)
/// @param frequenceMedianeKHz fréquence médiane en kHz (métrique Tadarida, optionnelle au schéma)
/// @param taxonTadarida code du taxon proposé par Tadarida (FK → `taxon.code`, obligatoire)
/// @param probTadarida probabilité Tadarida dans `[0,1]` (optionnelle)
/// @param taxonAutreTadarida code de la 2e proposition Tadarida (FK → `taxon.code`, optionnel)
/// @param taxonObservateur code saisi par l'observateur (FK → `taxon.code`, optionnel)
/// @param probObservateur probabilité numérique `[0,1]` héritée du format `_Vu` : confiance Tadarida
///     recopiée à la validation, **pas** une certitude d'observateur (optionnelle)
/// @param commentaire commentaire libre de l'observateur (optionnel)
/// @param reference marquée comme référence dans la bibliothèque de sons (défaut `false`)
/// @param modeValidation mode de validation (R24 : manuel / auto / non validé)
/// @param idResultats résultats d'identification agrégateurs (FK → `identification_results.id`)
/// @param douteux marquée « douteuse / à repasser » par l'observateur (#160, défaut `false`)
/// @param idDonneeVigieChiro `_id` Eve de la donnée serveur dont cette observation est un
///     sous-document (#1139) ; `null` hors import VigieChiro. Jamais préservé au ré-import : il
///     vient frais du serveur (un re-compute régénère les `_id`)
/// @param indiceVigieChiro indice **brut** de l'observation dans le tableau `observations` de sa
///     donnée côté serveur (#1139) : l'identifiant positionnel du `PATCH` ; `null` hors import
/// @param certitudeObservateur certitude déclarée manuellement à la revue (#1139), `null` = non
///     renseignée (vide par défaut, jamais dérivée d'une probabilité) ; les composants suivants
///     sont ajoutés en **queue** pour préserver l'ordre historique du record
/// @param taxonValidateur code du taxon **tranché par le validateur** du MNHN (#1417) ; `null` tant
///     qu'aucun expert ne s'est prononcé, ou hors import VigieChiro. En lecture seule
/// @param certitudeValidateur certitude déclarée par le validateur (#1417), même domaine fermé que
///     celle de l'observateur (`SUR | PROBABLE | POSSIBLE`) ; `null` = non renseignée
public record Observation(
        Long id,
        Long idSequence,
        Double debutS,
        Double finS,
        Integer frequenceMedianeKHz,
        String taxonTadarida,
        Double probTadarida,
        String taxonAutreTadarida,
        String taxonObservateur,
        Double probObservateur,
        String commentaire,
        boolean reference,
        ModeValidation modeValidation,
        Long idResultats,
        boolean douteux,
        String idDonneeVigieChiro,
        Integer indiceVigieChiro,
        CertitudeObservateur certitudeObservateur,
        String taxonValidateur,
        CertitudeObservateur certitudeValidateur) {

    /// Copie de cette observation avec un **commentaire** différent (tous les autres champs inchangés) :
    /// évite de réénumérer les composants du record à chaque mise à jour mono-champ côté service. Le
    /// texte est **normalisé** : un commentaire vide ou uniquement composé d'espaces est ramené à `null`
    /// (« pas de commentaire »), sinon il est enregistré sans espaces de bordure.
    public Observation avecCommentaire(String texte) {
        String commentaire = (texte == null || texte.isBlank()) ? null : texte.strip();
        return new Observation(
                id,
                idSequence,
                debutS,
                finS,
                frequenceMedianeKHz,
                taxonTadarida,
                probTadarida,
                taxonAutreTadarida,
                taxonObservateur,
                probObservateur,
                commentaire,
                reference,
                modeValidation,
                idResultats,
                douteux,
                idDonneeVigieChiro,
                indiceVigieChiro,
                certitudeObservateur,
                taxonValidateur,
                certitudeValidateur);
    }

    /// Copie de cette observation avec l'archivage en **référence** modifié (tous les autres champs
    /// inchangés).
    public Observation avecReference(boolean reference) {
        return new Observation(
                id,
                idSequence,
                debutS,
                finS,
                frequenceMedianeKHz,
                taxonTadarida,
                probTadarida,
                taxonAutreTadarida,
                taxonObservateur,
                probObservateur,
                commentaire,
                reference,
                modeValidation,
                idResultats,
                douteux,
                idDonneeVigieChiro,
                indiceVigieChiro,
                certitudeObservateur,
                taxonValidateur,
                certitudeValidateur);
    }

    /// Copie de cette observation avec le drapeau **douteux** (#160) modifié (tous les autres champs
    /// inchangés). Pendant de [#avecReference(boolean)] : un seul champ modifié sur le record immuable.
    public Observation avecDouteux(boolean douteux) {
        return new Observation(
                id,
                idSequence,
                debutS,
                finS,
                frequenceMedianeKHz,
                taxonTadarida,
                probTadarida,
                taxonAutreTadarida,
                taxonObservateur,
                probObservateur,
                commentaire,
                reference,
                modeValidation,
                idResultats,
                douteux,
                idDonneeVigieChiro,
                indiceVigieChiro,
                certitudeObservateur,
                taxonValidateur,
                certitudeValidateur);
    }

    /// Copie de cette observation avec le **triplet observateur** (taxon retenu, probabilité, mode de
    /// validation) modifié, les autres champs inchangés : c'est ce que produisent valider (R15) et
    /// corriger (R16). Sert aux actions **en lot** pour reconstruire l'observation sans I/O (#479).
    public Observation avecObservateur(String taxonObservateur, Double probObservateur, ModeValidation mode) {
        return new Observation(
                id,
                idSequence,
                debutS,
                finS,
                frequenceMedianeKHz,
                taxonTadarida,
                probTadarida,
                taxonAutreTadarida,
                taxonObservateur,
                probObservateur,
                commentaire,
                reference,
                mode,
                idResultats,
                douteux,
                idDonneeVigieChiro,
                indiceVigieChiro,
                certitudeObservateur,
                taxonValidateur,
                certitudeValidateur);
    }

    /// Copie de cette observation avec la **certitude observateur** (#1139) modifiée (tous les autres
    /// champs inchangés). `null` = « non renseignée » : la certitude est une déclaration manuelle,
    /// jamais posée par défaut ni dérivée d'une probabilité.
    public Observation avecCertitude(CertitudeObservateur certitude) {
        return new Observation(
                id,
                idSequence,
                debutS,
                finS,
                frequenceMedianeKHz,
                taxonTadarida,
                probTadarida,
                taxonAutreTadarida,
                taxonObservateur,
                probObservateur,
                commentaire,
                reference,
                modeValidation,
                idResultats,
                douteux,
                idDonneeVigieChiro,
                indiceVigieChiro,
                certitude,
                taxonValidateur,
                certitudeValidateur);
    }
}

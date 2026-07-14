package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Préservation des **validations observateur** au fil des (ré)imports Tadarida (Extract Class de
/// [ServiceValidation]). Un import remplace le jeu Tadarida d'un passage ; sans précaution, il effacerait
/// le travail humain déjà saisi (taxon corrigé, marquage référence, commentaire). Ce collaborateur porte
/// ce sous-concept isolément :
///
/// - définit ce qu'est une observation **validée** ([#estValidee]) : décision humaine à conserver ;
/// - lit les validations de l'ancien jeu **avant** le remplacement ([#existantes]) et les **réapplique**
///   aux nouvelles observations de même clé d'appariement ([#reappliquer]) ;
/// - compte les validations **menacées** par une suppression / un écrasement ([#compterValidees]).
///
/// Les validations d'un ancien jeu sont manipulées via un jeton opaque [ValidationsAnciennes] : l'appelant
/// n'a pas à connaître la clé d'appariement interne.
final class PreservationValidations {

    private final ResultatsIdentificationDao resultatsDao;
    private final ObservationDao observationDao;

    PreservationValidations(ResultatsIdentificationDao resultatsDao, ObservationDao observationDao) {
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
    }

    /// Validations observateur de l'ancien jeu du passage (taxon corrigé, marquage référence ou
    /// commentaire), indexées par [CleObservation]. Vide si le passage n'a pas encore de jeu. Lu **avant**
    /// la transaction de remplacement (aucun écrivain concurrent sur ce poste).
    ValidationsAnciennes existantes(Long idPassage) {
        Map<CleObservation, Observation> parCle = new LinkedHashMap<>();
        resultatsDao.findByPassage(idPassage).map(r -> observationDao.findByResults(r.id())).orElse(List.of()).stream()
                .filter(PreservationValidations::estValidee)
                .forEach(obs -> parCle.putIfAbsent(cleDe(obs), obs));
        return new ValidationsAnciennes(parCle);
    }

    /// Nombre d'observations **validées** du passage : le travail de validation qui serait perdu si le
    /// passage était supprimé ou écrasé (sert aux confirmations destructives des features `passage` et
    /// `importation` via le port socle `CompteurValidations`).
    long compterValidees(Long idPassage) {
        return resultatsDao
                .findByPassage(idPassage)
                .map(resultats -> observationDao.findByResults(resultats.id()))
                .orElseGet(List::of)
                .stream()
                .filter(PreservationValidations::estValidee)
                .count();
    }

    /// Réattache **en place** les validations mémorisées aux nouvelles observations de même
    /// [CleObservation] (les champs Tadarida et l'`idResultats` du nouveau jeu restent ceux de la nouvelle
    /// observation). Renvoie le nombre de validations distinctes effectivement réappliquées ; celles dont la
    /// clé a disparu du nouveau jeu sont perdues (comptées par différence côté appelant).
    int reappliquer(List<Observation> neuves, ValidationsAnciennes anciennes) {
        if (anciennes.estVide()) {
            return 0;
        }
        Set<CleObservation> reattachees = new HashSet<>();
        for (int i = 0; i < neuves.size(); i++) {
            CleObservation cle = cleDe(neuves.get(i));
            Observation ancienne = anciennes.pour(cle);
            if (ancienne != null) {
                neuves.set(i, avecValidation(neuves.get(i), ancienne));
                reattachees.add(cle);
            }
        }
        return reattachees.size();
    }

    /// Une observation « validée » par l'observateur : elle porte un taxon corrigé, un marquage référence,
    /// un commentaire ou une certitude saisie (#1139). Ce sont les décisions humaines à préserver au fil
    /// des réimports (les champs purement Tadarida, eux, sont recalculés à chaque import).
    private static boolean estValidee(Observation observation) {
        return observation.reference()
                || observation.commentaire() != null
                || observation.taxonObservateur() != null
                || observation.certitudeObservateur() != null;
    }

    private static CleObservation cleDe(Observation observation) {
        return new CleObservation(
                observation.idSequence(), observation.taxonTadarida(), observation.debutS(), observation.finS());
    }

    /// Copie `neuve` en y réinjectant les champs de **décision humaine** de `ancienne` : taxon
    /// observateur, probabilité observateur, commentaire, référence, mode de validation, douteux et
    /// certitude (#1139). L'**ancrage plateforme** (`idDonneeVigieChiro`, `indiceVigieChiro`), lui,
    /// reste celui de `neuve` : il vient frais du serveur à chaque import (un re-compute régénère les
    /// `_id`), l'ancien serait périmé.
    ///
    /// L'**avis du validateur** (#1417) suit la même règle que l'ancrage : il reste celui de `neuve`. Ce
    /// n'est pas une décision *de l'utilisateur* qu'un réimport risquerait d'effacer, c'est un reflet du
    /// serveur — et c'est bien le point : si l'expert du MNHN a changé d'avis entre deux imports, c'est
    /// **son** nouvel avis qui doit s'afficher, pas la copie qu'on en gardait.
    private static Observation avecValidation(Observation neuve, Observation ancienne) {
        return new Observation(
                neuve.id(),
                neuve.idSequence(),
                neuve.debutS(),
                neuve.finS(),
                neuve.frequenceMedianeKHz(),
                neuve.taxonTadarida(),
                neuve.probTadarida(),
                neuve.taxonAutreTadarida(),
                ancienne.taxonObservateur(),
                ancienne.probObservateur(),
                ancienne.commentaire(),
                ancienne.reference(),
                ancienne.modeValidation(),
                neuve.idResultats(),
                ancienne.douteux(),
                neuve.idDonneeVigieChiro(),
                neuve.indiceVigieChiro(),
                ancienne.certitudeObservateur(),
                neuve.taxonValidateur(),
                neuve.certitudeValidateur());
    }

    /// Clé d'appariement d'une observation entre deux imports : même séquence, même taxon Tadarida et même
    /// fenêtre temporelle. **Exacte** (aucune tolérance) : Tadarida est déterministe pour un segment donné,
    /// donc réimporter le même cri reproduit ces quatre valeurs à l'identique.
    private record CleObservation(Long idSequence, String taxonTadarida, Double debutS, Double finS) {}

    /// Jeton **opaque** des validations d'un ancien import : l'appelant l'obtient via [#existantes], le
    /// repasse à [#reappliquer] et lit seulement sa [#taille]. La clé d'appariement reste interne.
    static final class ValidationsAnciennes {
        private final Map<CleObservation, Observation> parCle;

        private ValidationsAnciennes(Map<CleObservation, Observation> parCle) {
            this.parCle = parCle;
        }

        /// Aucune validation à préserver (import simple, hors réimport).
        static ValidationsAnciennes vide() {
            return new ValidationsAnciennes(Map.of());
        }

        private boolean estVide() {
            return parCle.isEmpty();
        }

        private Observation pour(CleObservation cle) {
            return parCle.get(cle);
        }

        /// Nombre de validations mémorisées (pour compter celles perdues après réapplication).
        int taille() {
            return parCle.size();
        }
    }
}

package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// **Noyau d'import des observations** d'un passage, extrait de [ServiceValidation] (cohésion, plafond
/// GodClass) : le cœur commun à toutes les sources d'observations (CSV Tadarida local ou résultats
/// VigieChiro). Rattache chaque ligne à la séquence d'écoute de **même nom** (les lignes sans séquence en
/// base ou sans taxon Tadarida sont ignorées), auto-crée les taxons hors référentiel, et écrit le jeu de
/// résultats + les observations dans **une seule transaction** - en préservant les validations observateur
/// d'un jeu précédent lors d'un remplacement. Calque les collaborateurs [PreservationValidations],
/// [FilsDiscussionVigieChiro] et [EtatAncragePassage] : pure Java testable, DAO reçus par constructeur.
///
/// ## Invariant « un seul jeu par passage »
///
/// `identification_results.passage_id` est **UNIQUE**. Hors remplacement, importer sur un passage qui a
/// **déjà un jeu** (déjà importé, ou reconstruit par CSV #1565) est donc **refusé avant l'INSERT**
/// ([RegleMetierException]), et non laissé fuir en `DataAccessException` (« échec inattendu ») quand la
/// contrainte SQL saute. Le **remplacement** (`remplacer=true`), lui, supprime l'ancien jeu dans la même
/// transaction que l'insertion du nouveau (atomicité). Un passage **neuf** (le cas de la reconstruction)
/// n'a aucun jeu : l'import y est toujours autorisé.
public final class NoyauImportObservations {

    private final ResultatsIdentificationDao resultatsDao;
    private final ObservationDao observationDao;
    private final TaxonDao taxonDao;
    private final SessionDao sessionDao;
    private final SequenceDao sequenceDao;
    private final UniteDeTravail uniteDeTravail;
    private final Horloge horloge;
    private final PreservationValidations preservation;

    public NoyauImportObservations(
            ResultatsIdentificationDao resultatsDao,
            ObservationDao observationDao,
            TaxonDao taxonDao,
            SessionDao sessionDao,
            SequenceDao sequenceDao,
            UniteDeTravail uniteDeTravail,
            Horloge horloge,
            PreservationValidations preservation) {
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.taxonDao = Objects.requireNonNull(taxonDao, "taxonDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
        this.uniteDeTravail = Objects.requireNonNull(uniteDeTravail, "uniteDeTravail");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
        this.preservation = Objects.requireNonNull(preservation, "preservation");
    }

    /// Importe les `lignes` d'observations sur le passage, `source` et `format` traçant la provenance dans
    /// le [ResultatsIdentification]. Hors remplacement, **refuse** si un jeu existe déjà (invariant ci-dessus).
    /// En remplacement, supprime l'ancien jeu et réapplique les validations observateur préservées, le tout
    /// dans une seule transaction. La suppression n'a lieu qu'**après** les validations d'entrée (aucune
    /// séquence importable → lève sans rien supprimer) : en cas d'échec d'écriture, le rollback préserve
    /// l'ancien jeu.
    ///
    /// @param idPassage passage cible (doit posséder une session d'enregistrement)
    /// @param lignes lignes d'observation déjà parsées (CSV Tadarida ou résultats VigieChiro convertis)
    /// @param source provenance tracée dans le jeu de résultats
    /// @param format libellé du format détecté
    /// @param remplacer remplace le jeu existant (en préservant les validations observateur) si `true`
    /// @return le bilan de l'import (jeu créé + compteurs)
    /// @throws RegleMetierException si un jeu existe déjà hors remplacement, si le passage n'a pas de
    ///     session, ou si aucune séquence des `lignes` n'existe en base
    public BilanImport importer(
            Long idPassage, List<LigneObservation> lignes, String source, String format, boolean remplacer) {
        Objects.requireNonNull(idPassage, "idPassage");

        // Invariant « un seul jeu par passage » : hors remplacement, refuser AVANT l'INSERT si un jeu existe
        // déjà. Sans ce garde, la contrainte UNIQUE identification_results.passage_id fuit en
        // DataAccessException (rollback, « échec inattendu ») au lieu d'un refus métier lisible. Le
        // remplacement, lui, supprime l'ancien jeu dans la transaction ci-dessous.
        if (!remplacer && resultatsDao.findByPassage(idPassage).isPresent()) {
            throw new RegleMetierException("Ce passage a déjà un jeu de résultats Tadarida. Pour le remplacer,"
                    + " ouvrez « Sons & validation » (menu ☰ > Réimporter, ou glissez-y un nouveau CSV) : le"
                    + " remplacement préserve vos validations en cours.");
        }

        SessionDEnregistrement session = sessionDao
                .trouverParPassage(idPassage)
                .orElseThrow(() -> new RegleMetierException("Aucune session d'enregistrement pour le passage "
                        + idPassage
                        + " : importez d'abord la nuit (P2) avant les résultats Tadarida."));

        Map<String, Long> sequenceParNom = indexerSequences(session.id());
        Set<String> taxonsConnus = chargerCodesTaxons();

        // Lignes **importables** : séquence audio en base ET taxon Tadarida renseigné (`taxon_tadarida`
        // est NOT NULL en base ; Tadarida en assigne toujours un, une ligne sans taxon est invalide).
        // Tout le reste est ignoré (audio non fourni - cas courant d'un échantillon - ou ligne sans taxon)
        // plutôt que de faire échouer l'import en bloc ou de laisser planter l'insertion.
        List<LigneObservation> retenues = lignes.stream()
                .filter(ligne -> sequenceParNom.containsKey(cleSequence(ligne.nomSequence())))
                .filter(ligne ->
                        ligne.taxonTadarida() != null && !ligne.taxonTadarida().isBlank())
                .toList();
        int ignorees = lignes.size() - retenues.size();
        if (retenues.isEmpty()) {
            throw new RegleMetierException("Séquence d'écoute introuvable : aucune des "
                    + lignes.size()
                    + " observations n'est importable (séquence audio absente, ou ligne sans taxon)."
                    + " Importez d'abord la nuit de ce passage (carré, année, n° de passage et point doivent"
                    + " correspondre au nom du fichier).");
        }

        // Tolérance taxons : auto-souches pour les codes Tadarida hors référentiel des lignes retenues.
        Set<String> taxonsAutoCrees = taxonsHorsReferentiel(retenues, taxonsConnus);
        Set<String> taxonsApresImport = new HashSet<>(taxonsConnus);
        taxonsApresImport.addAll(taxonsAutoCrees);

        // Réimport : on mémorise les **validations observateur** de l'ancien jeu (taxon corrigé, marquage
        // référence, commentaire) AVANT sa suppression, pour les réattacher aux nouvelles observations de
        // même (séquence, taxon Tadarida, début, fin). Sans cela, réimporter effacerait tout le travail de
        // validation déjà saisi. Vide hors réimport.
        PreservationValidations.ValidationsAnciennes validationsAnciennes =
                remplacer ? preservation.existantes(idPassage) : PreservationValidations.ValidationsAnciennes.vide();

        ResultatsIdentification aCreer = new ResultatsIdentification(
                null, source, format, horloge.maintenant().toString(), idPassage);

        // Remplacement (réimport) + souches + jeu de résultats + observations dans une **seule
        // transaction** (atomicité, FK). La suppression de l'ancien jeu n'a lieu qu'ici, après que le
        // parse et la validation ont réussi : une source invalide a déjà levé plus haut, sans rien
        // supprimer. En cas d'échec d'écriture, le rollback préserve l'ancien jeu.
        ResultatsIdentification[] insere = {null};
        int[] preservees = {0};
        uniteDeTravail.executer(connexion -> {
            if (remplacer) {
                resultatsDao.deleteParPassage(connexion, idPassage);
            }
            taxonDao.enregistrerHorsReferentiel(connexion, taxonsAutoCrees);
            insere[0] = resultatsDao.insert(connexion, aCreer);
            List<Observation> neuves =
                    construireObservations(retenues, sequenceParNom, taxonsApresImport, insere[0].id());
            preservees[0] = preservation.reappliquer(neuves, validationsAnciennes);
            observationDao.insererTout(connexion, neuves);
        });
        int perdues = validationsAnciennes.taille() - preservees[0];
        return new BilanImport(insere[0], retenues.size(), ignorees, taxonsAutoCrees.size(), preservees[0], perdues);
    }

    /// Codes hors référentiel parmi `lignes`, à auto-enregistrer en souches : taxon Tadarida (stocké tel
    /// quel → FK obligatoire), taxon **observateur** (sa décision, à préserver) et taxon **validateur**
    /// (#1417 : le verdict de l'expert du MNHN). Le validateur est ici pour la même raison que les deux
    /// autres - sans souche, la FK forcerait [#codeOuNull] à ramener son code à `null`, et l'application
    /// **jetterait en silence** l'avis qui fait autorité.
    private static Set<String> taxonsHorsReferentiel(List<LigneObservation> lignes, Set<String> taxonsConnus) {
        Set<String> manquants = new LinkedHashSet<>();
        for (LigneObservation ligne : lignes) {
            ajouterSiInconnu(manquants, ligne.taxonTadarida(), taxonsConnus);
            ajouterSiInconnu(manquants, ligne.taxonObservateur(), taxonsConnus);
            ajouterSiInconnu(manquants, ligne.taxonValidateur(), taxonsConnus);
        }
        return manquants;
    }

    private static void ajouterSiInconnu(Set<String> cible, String code, Set<String> taxonsConnus) {
        if (code != null && !taxonsConnus.contains(code)) {
            cible.add(code);
        }
    }

    private List<Observation> construireObservations(
            List<LigneObservation> lignes,
            Map<String, Long> sequenceParNom,
            Set<String> taxonsConnus,
            Long idResultats) {
        List<Observation> aInserer = new ArrayList<>();
        for (LigneObservation ligne : lignes) {
            Long idSequence = sequenceParNom.get(cleSequence(ligne.nomSequence()));
            aInserer.add(new Observation(
                    null,
                    idSequence,
                    ligne.debutS(),
                    ligne.finS(),
                    ligne.frequenceMedianeKHz(),
                    ligne.taxonTadarida(),
                    ligne.probTadarida(),
                    codeOuNull(ligne.taxonAutreTadarida(), taxonsConnus),
                    codeOuNull(ligne.taxonObservateur(), taxonsConnus),
                    ligne.probObservateur(),
                    null,
                    false,
                    ligne.modeValidation(),
                    idResultats,
                    false,
                    ligne.idDonneeVigieChiro(),
                    ligne.indiceVigieChiro(),
                    ligne.certitudeObservateur(),
                    codeOuNull(ligne.taxonValidateur(), taxonsConnus),
                    ligne.certitudeValidateur()));
        }
        return aInserer;
    }

    /// Index nom de séquence (sans extension) → id, pour les séquences d'une session.
    private Map<String, Long> indexerSequences(Long idSession) {
        Map<String, Long> index = new HashMap<>();
        for (SequenceDEcoute sequence : sequenceDao.findBySession(idSession)) {
            index.put(cleSequence(sequence.nomFichier()), sequence.id());
        }
        return index;
    }

    private Set<String> chargerCodesTaxons() {
        Set<String> codes = new HashSet<>();
        taxonDao.findAll().forEach(t -> codes.add(t.code()));
        return codes;
    }

    /// Renvoie le code s'il est connu (FK valide), sinon `null` (ex. liste multi-valuée).
    private static String codeOuNull(String code, Set<String> codesConnus) {
        return code != null && codesConnus.contains(code) ? code : null;
    }

    /// Clé de raccrochage d'une séquence : nom de fichier sans extension. Le CSV Tadarida nomme les
    /// séquences sans extension (`…_000`), alors que la base stocke le nom complet (`…_000.wav`,
    /// R8). On compare donc sur la base du nom. Package-private : réutilisée par l'export de
    /// [ServiceValidation], qui relit les séquences par leur nom.
    static String cleSequence(String nomFichier) {
        if (nomFichier == null) {
            return null;
        }
        String trim = nomFichier.trim();
        int point = trim.lastIndexOf('.');
        return point < 0 ? trim : trim.substring(0, point);
    }
}

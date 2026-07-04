package fr.univ_amu.iut.bibliotheque.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// Service métier de la feature `bibliotheque` (parcours P9/P10, story E8, statut **COULD**) :
/// exporte une **bibliothèque de sons de référence** à partir des observations validées que
/// l'utilisateur a marquées comme « séquence de référence » pendant la validation taxonomique
/// (P7).
///
/// Suit le patron du service de référence `ServiceSites` : pure Java testable, dépendances reçues
/// par constructeur (avec `requireNonNull`), **aucun import JavaFX**, le SQL reste dans les DAO.
/// La feature est en **lecture seule** : pas d'écriture, donc pas de transaction multi-tables
/// (`UniteDeTravail`) ni de moteur de workflow, et — point important pour le déterminisme exigé
/// sur le CSV exporté (SERVICE-CONVENTIONS §5) — **pas d'`Horloge`** : la sortie ne porte ni
/// horodatage ni hash.
///
/// **Dépendances inter-features** assumées (sens autorisé, graphe acyclique vérifié par
/// `ArchitectureTest`) :
///
/// - `bibliotheque → validation.model` : [Observation] + [ObservationDao] pour sélectionner les
///   observations `is_reference` ;
/// - `bibliotheque → passage.model` : [SequenceDEcoute] + [SequenceDao] pour résoudre le fichier
///   et le chemin de la séquence source de chaque observation ;
/// - `bibliotheque → commun` : utilitaires partagés (CSV) côté [ExportBiblioSons].
///
/// Rien ne dépend de `bibliotheque` (feuille du graphe). **Aucun accès réseau.**
public class ServiceBibliotheque {

    private final ObservationDao observationDao;
    private final SequenceDao sequenceDao;

    public ServiceBibliotheque(ObservationDao observationDao, SequenceDao sequenceDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.sequenceDao = Objects.requireNonNull(sequenceDao, "sequenceDao");
    }

    /// Constitue la bibliothèque de sons de référence (P10) : sélectionne **toutes** les
    /// observations marquées `is_reference`, résout la séquence d'écoute source de chacune et
    /// assemble un [ExportBiblioSons] (CSV récapitulatif + liste des fichiers à copier).
    ///
    /// Les entrées sont triées de façon **déterministe** (taxon retenu, puis nom de séquence,
    /// puis chemin) pour garantir une sortie reproductible au bit près. Le taxon retenu est le taxon
    /// observateur s'il a été saisi en validation, sinon le taxon proposé par Tadarida.
    ///
    /// @return la bibliothèque exportable (vide si aucune observation n'est marquée référence)
    /// @throws RegleMetierException si une observation de référence pointe vers une séquence
    ///     introuvable (incohérence de données)
    public ExportBiblioSons exporterBibliotheque() {
        List<EntreeBiblio> entrees = observationDao.findAll().stream()
                .filter(Observation::reference)
                .map(this::construireEntree)
                .sorted(Comparator.comparing(EntreeBiblio::taxon)
                        .thenComparing(EntreeBiblio::nomSequence)
                        .thenComparing(EntreeBiblio::cheminFichier))
                .toList();
        return new ExportBiblioSons(entrees);
    }

    /// Rapproche une observation de référence et sa séquence source pour produire une entrée.
    private EntreeBiblio construireEntree(Observation observation) {
        SequenceDEcoute sequence = sequenceDao
                .findById(observation.idSequence())
                .orElseThrow(() -> new RegleMetierException("Séquence d'écoute introuvable (id "
                        + observation.idSequence()
                        + ") pour l'observation de référence "
                        + observation.id()
                        + "."));
        String taxonRetenu =
                observation.taxonObservateur() != null ? observation.taxonObservateur() : observation.taxonTadarida();
        return new EntreeBiblio(
                taxonRetenu,
                sequence.nomFichier(),
                sequence.cheminFichier(),
                observation.frequenceMedianeKHz(),
                observation.commentaire());
    }
}

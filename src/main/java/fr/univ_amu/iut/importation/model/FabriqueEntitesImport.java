package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.Micro;
import fr.univ_amu.iut.passage.model.Passage;
import java.time.LocalDate;
import java.util.Objects;

/// Construit les entités d'agrégat d'un import (passage, micro) à partir du journal parsé. Extrait de
/// [ServiceImport] pour ne pas y concentrer trop de responsabilités : les valeurs de repli (heure
/// inconnue, modèle de micro non journalisé) et l'[Horloge] (date par défaut) vivent ici.
final class FabriqueEntitesImport {

    /// Heure de repli si le journal ne renseigne pas la fenêtre d'acquisition (`NOT NULL`).
    private static final String HEURE_INCONNUE = "00:00:00";

    /// Référence de micro inscrite quand le journal ne nomme aucun modèle (colonne `model_ref`
    /// obligatoire) : le journal LogPR donne bande passante et sensibilité, pas la référence commerciale.
    private static final String MODELE_MICRO_NON_JOURNALISE = "Micro PR (modèle non journalisé)";

    private final Horloge horloge;

    FabriqueEntitesImport(Horloge horloge) {
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Passage au statut [StatutWorkflow#TRANSFORME] (état final d'un import complet), daté du journal.
    Passage passage(JournalParse journal, Long idPoint, Prefixe prefixe) {
        return passage(journal, idPoint, prefixe, journal.dateDebut());
    }

    /// Variante **datée par nuit** : lors d'un import découpé en plusieurs nuits, le journal unique ne
    /// décrit que la première nuit ; chaque passage porte donc la **date propre de sa nuit** (issue des
    /// noms de fichiers). Repli sur la date du jour si `dateNuit` est `null`. Heures d'acquisition et
    /// paramètres restent ceux du journal (fenêtre commune à toutes les nuits).
    Passage passage(JournalParse journal, Long idPoint, Prefixe prefixe, LocalDate dateNuit) {
        String date =
                dateNuit != null ? dateNuit.toString() : horloge.aujourdhui().toString();
        String heureDebut = journal.heureDebut() != null ? journal.heureDebut() : HEURE_INCONNUE;
        String heureFin = journal.heureFin() != null ? journal.heureFin() : HEURE_INCONNUE;
        return new Passage(
                null,
                prefixe.numeroPassage(),
                prefixe.annee(),
                date,
                heureDebut,
                heureFin,
                journal.parametresAcquisitionJson(),
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                idPoint,
                journal.numeroSerie());
    }

    /// Micro déduit du journal, ou `null` si ni bande passante ni sensibilité ne sont journalisées.
    Micro micro(JournalParse journal) {
        if (journal.bandePassante() == null && journal.sensibilite() == null) {
            return null;
        }
        return new Micro(
                null,
                MODELE_MICRO_NON_JOURNALISE,
                journal.bandePassante(),
                journal.sensibilite(),
                null,
                null,
                true,
                "Micro déduit du journal LogPR (modèle non journalisé).",
                journal.numeroSerie());
    }
}

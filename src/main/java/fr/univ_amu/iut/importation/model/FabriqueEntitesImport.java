package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.Micro;
import fr.univ_amu.iut.passage.model.Passage;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

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

    /// Variante **datée par nuit** : lors d'un import découpé en plusieurs nuits, chaque passage porte la
    /// date propre de sa nuit, issue des noms de fichiers. Repli sur la date du jour si `dateNuit` est
    /// `null`.
    ///
    /// **Les paramètres suivent la nuit** (#3460). Un capteur laissé plusieurs nuits au même point repose
    /// ses paramètres à chaque session, et la fréquence d'échantillonnage peut changer d'une nuit à
    /// l'autre : une nuit repartait avec la configuration d'une autre session, et comme la fréquence
    /// conditionne la transformation des séquences, le défaut produisait des données fausses **en silence**.
    /// La configuration est donc demandée pour cette nuit-là ([JournalParse#configurationPourNuit]).
    Passage passage(JournalParse journal, Long idPoint, Prefixe prefixe, LocalDate dateNuit) {
        String date =
                dateNuit != null ? dateNuit.toString() : horloge.aujourdhui().toString();
        LocalDate nuit = dateNuit != null ? dateNuit : journal.dateDebut();
        Optional<ConfigurationAcquisition> configuration =
                nuit == null ? Optional.empty() : journal.configurationPourNuit(nuit);

        String heureDebut =
                ouRepli(configuration.map(ConfigurationAcquisition::heureDebut).orElse(journal.heureDebut()));
        String heureFin =
                ouRepli(configuration.map(ConfigurationAcquisition::heureFin).orElse(journal.heureFin()));
        String parametres =
                configuration.map(ConfigurationAcquisition::enJson).orElseGet(journal::parametresAcquisitionJson);

        return new Passage(
                null,
                prefixe.numeroPassage(),
                prefixe.annee(),
                date,
                heureDebut,
                heureFin,
                parametres,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                idPoint,
                journal.numeroSerie(),
                null);
    }

    /// Une heure lue du journal, ou [#HEURE_INCONNUE] : la colonne est `NOT NULL`.
    private static String ouRepli(String heure) {
        return heure != null ? heure : HEURE_INCONNUE;
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

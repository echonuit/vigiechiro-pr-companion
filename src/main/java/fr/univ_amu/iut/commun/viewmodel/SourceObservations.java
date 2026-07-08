package fr.univ_amu.iut.commun.viewmodel;

import java.util.List;
import java.util.Objects;

/// Descripteur **extensible** d'un ensemble d'observations à ouvrir dans la **vue audio unifiée**
/// (#audio) : écoute + validation/correction + archivage référence sur un même écran, alimenté depuis
/// n'importe quelle vue. La source décrit *quel* ensemble charger (le ViewModel audio la résout en
/// `List<LigneObservationAudio>`), *quelles* actions propres sont pertinentes, et porte le **contexte
/// de provenance** nécessaire au fil d'Ariane (retour vers l'écran d'origine).
///
/// Vit dans le **socle** (`commun.viewmodel`) car plusieurs features la construisent (M-Passage,
/// analyse, multisite, accueil) : elle ne dépend donc d'**aucune** feature. Le filtre de statut de
/// [ParEspece] est porté en **texte** (nom du `StatutObservation`, ou `null`) pour ne pas faire
/// dépendre le socle de la feature `validation` (ce qui introduirait un cycle) ; le ViewModel audio
/// le reconvertit en énumération.
///
/// Chaque variante **porte son propre comportement** (intitulé, capacités, contexte) : le socle expose
/// des **défauts** que seuls les sous-types concernés redéfinissent, sans aucun test de type
/// (`instanceof`/`switch`) côté appelant. Ajouter une source = ajouter un `record` et redéfinir ce qui
/// diffère.
///
/// Variantes :
/// - [ParPassage] un passage (workflow Tadarida) — seule à permettre l'import CSV / l'export `_Vu` ;
/// - [ParPassages] un lot de passages (multisite filtré) ;
/// - [ParEspece] une espèce à travers les passages d'un utilisateur ;
/// - [References] le corpus `is_reference` d'un utilisateur — seule à permettre l'export bibliothèque ;
/// - [NonIdentifies] les séquences d'un passage **sans observation Tadarida** (à écouter/valider à la main).
public sealed interface SourceObservations {

    /// **Intitulé** de la source, tel qu'affiché dans le fil d'Ariane / le titre de l'écran audio. C'est
    /// une propriété **sémantique** de la source (ce qu'elle représente), pas du rendu : chaque variante
    /// porte donc le sien.
    String titre();

    /// `true` si le **workflow Tadarida par passage** (import d'un CSV, export `_Vu`) est pertinent.
    /// Faux par défaut ; seule [ParPassage] le redéfinit (un lot, une espèce ou les références n'ont pas
    /// un unique jeu de résultats à importer/exporter).
    default boolean permetWorkflowTadarida() {
        return false;
    }

    /// `true` si l'**export de la bibliothèque** de référence est pertinent. Faux par défaut ; seule
    /// [References] le redéfinit.
    default boolean permetExportBibliotheque() {
        return false;
    }

    /// Contexte du **passage ciblé** quand la source en vise un seul, `null` sinon (défaut). Redéfini par
    /// [ParPassage] et [NonIdentifies]. Sert au fil d'Ariane (retour au passage) et à la plage nuit par
    /// défaut du filtre heure.
    default ContextePassage contexteDuPassage() {
        return null;
    }

    /// `true` si la source cible **un seul passage** : les colonnes de contexte (passage / carré / point /
    /// date) y sont constantes, donc masquées par la vue. Dérivé de [#contexteDuPassage()].
    default boolean cibleUnPassageUnique() {
        return contexteDuPassage() != null;
    }

    /// Observations d'**un passage** (parcours de validation Tadarida). Porte le [ContextePassage]
    /// (carré, point, numéro) pour revenir au passage. Seule source du workflow Tadarida (import/`_Vu`).
    record ParPassage(ContextePassage contexte) implements SourceObservations {
        public ParPassage {
            Objects.requireNonNull(contexte, "contexte");
        }

        @Override
        public String titre() {
            return "Sons & validation";
        }

        @Override
        public boolean permetWorkflowTadarida() {
            return true;
        }

        @Override
        public ContextePassage contexteDuPassage() {
            return contexte;
        }
    }

    /// Observations d'**un lot de passages** (le lot filtré courant de M-Multisite). `libelle` résume
    /// le lot pour le fil d'Ariane (par exemple « lot (3 passages) »).
    record ParPassages(List<Long> idPassages, String libelle) implements SourceObservations {
        public ParPassages {
            idPassages = List.copyOf(Objects.requireNonNull(idPassages, "idPassages"));
            Objects.requireNonNull(libelle, "libelle");
        }

        @Override
        public String titre() {
            return "Écoute : " + libelle;
        }
    }

    /// Observations d'**une espèce** à travers les passages d'un utilisateur (depuis « Espèces &
    /// observations »). `statut` est un filtre optionnel (nom d'un `StatutObservation`, ou `null` pour
    /// toutes) ; `libelle` est le nom affichable de l'espèce (fil d'Ariane).
    record ParEspece(String idUtilisateur, String codeEspece, String statut, String libelle)
            implements SourceObservations {
        public ParEspece {
            Objects.requireNonNull(idUtilisateur, "idUtilisateur");
            Objects.requireNonNull(codeEspece, "codeEspece");
            Objects.requireNonNull(libelle, "libelle");
        }

        @Override
        public String titre() {
            return "Écoute : " + libelle;
        }
    }

    /// **Corpus de référence** (`is_reference`) d'un utilisateur (ex-bibliothèque). Seule source à
    /// permettre l'export de la bibliothèque de sons.
    record References(String idUtilisateur) implements SourceObservations {
        public References {
            Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        }

        @Override
        public String titre() {
            return "Sons de référence";
        }

        @Override
        public boolean permetExportBibliotheque() {
            return true;
        }
    }

    /// **Séquences non identifiées** d'un passage : les enregistrements présents sur disque (écoutables)
    /// mais **sans observation Tadarida**. Permet de les écouter pour les valider manuellement, alors que
    /// le CSV Tadarida ne les a pas retenues. Cible un **passage unique** (comme [ParPassage]) : porte le
    /// [ContextePassage] pour le fil d'Ariane (retour au passage).
    record NonIdentifies(ContextePassage contexte) implements SourceObservations {
        public NonIdentifies {
            Objects.requireNonNull(contexte, "contexte");
        }

        @Override
        public String titre() {
            return "Sons non identifiés";
        }

        @Override
        public ContextePassage contexteDuPassage() {
            return contexte;
        }
    }
}

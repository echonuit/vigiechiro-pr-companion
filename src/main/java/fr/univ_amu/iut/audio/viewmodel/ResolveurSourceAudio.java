package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Résout une [SourceObservations] (descripteur de provenance) en données concrètes via
/// [ServiceValidation] : la liste des [LigneObservationAudio] à écouter, l'identifiant du jeu de
/// résultats (pour l'export `_Vu`), le passage unique (pour l'import), et le message d'état d'un
/// ensemble vide.
///
/// Sort du [AudioViewModel] le **dépiautage des variantes de source** (accès aux champs des records
/// `ParPassage`/`ParPassages`/`ParEspece`/`References`) pour que le ViewModel garde une seule
/// responsabilité (orchestrer la revue) et reste cohésif (PMD GodClass). Le filtre de statut, porté en
/// texte par la source pour ne pas coupler le socle à `validation`, est reconverti ici en énumération.
final class ResolveurSourceAudio {

    private final ServiceValidation service;

    ResolveurSourceAudio(ServiceValidation service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// Lignes audio de l'ensemble décrit par `source`.
    List<LigneObservationAudio> lignes(SourceObservations source) {
        return switch (source) {
            case SourceObservations.ParPassage s ->
                service.lignesAudioDuPassage(s.contexte().idPassage());
            case SourceObservations.ParPassages s -> service.lignesAudioDesPassages(s.idPassages());
            case SourceObservations.ParEspece s ->
                service.lignesAudioDeLEspece(s.idUtilisateur(), s.codeEspece(), statutDe(s.statut()));
            case SourceObservations.References s -> service.lignesAudioReferences(s.idUtilisateur());
            case SourceObservations.NonIdentifies s ->
                service.lignesAudioNonIdentifiees(s.contexte().idPassage());
        };
    }

    /// Plage **nuit** par défaut du filtre « Heure » (#549) : pour une source ciblant **un passage unique**
    /// ([ParPassage] ou [NonIdentifies]), le coucher/lever de la nuit de la relève ; **vide** sinon (plusieurs
    /// nuits, ou pas de contexte), le filtre retombant alors sur son défaut fixe 21 h → 6 h.
    Optional<PlageNuit> plageNuit(SourceObservations source) {
        ContextePassage contexte = source.contexteDuPassage();
        return contexte == null ? Optional.empty() : service.plageNuitParDefaut(contexte.idPassage());
    }

    /// Identifiant du jeu de résultats Tadarida, connu seulement pour `ParPassage` (`null` sinon).
    Long idResultats(SourceObservations source) {
        Long idPassage = idPassage(source);
        return idPassage == null ? null : service.resultatsDuPassage(idPassage).orElse(null);
    }

    /// Passage unique de la source, ou `null` si la source n'en cible pas un seul (`ParPassage` seule).
    static Long idPassage(SourceObservations source) {
        return source instanceof SourceObservations.ParPassage s ? s.contexte().idPassage() : null;
    }

    /// Message d'état affiché quand l'ensemble est vide (nuance selon la source).
    static String messageVide(SourceObservations source) {
        if (source instanceof SourceObservations.NonIdentifies) {
            return "Aucun enregistrement non identifié : toutes les séquences de ce passage ont une"
                    + " identification Tadarida.";
        }
        return source instanceof SourceObservations.ParPassage
                ? "Aucun résultat Tadarida importé pour ce passage."
                : "Aucune observation à écouter pour cette source.";
    }

    private static StatutObservation statutDe(String nom) {
        return nom == null ? null : StatutObservation.valueOf(nom);
    }
}

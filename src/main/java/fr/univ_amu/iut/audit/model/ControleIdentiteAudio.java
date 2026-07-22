package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.PresenceFichiers.Presence;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.passage.model.IdentiteSequence;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Traduit en **constats d'audit** les divergences d'identité des fichiers présents (#2254,
/// ADR 0048). La comparaison elle-même vit dans [IdentiteSequence], parce que l'écran d'écoute en a
/// besoin aussi et que le graphe de slices refuse les cycles entre features.
///
/// Extrait de [ServiceAuditCoherence] pour qu'il garde la seule responsabilité d'orchestrer les
/// contrôles (PMD GodClass), comme l'ont été [BalayageDisque] et [AuditEnLigne].
///
/// Une divergence est le **seul** écart de disponibilité qui reste une [Severite#ERREUR] : l'absence
/// d'un fichier est un état observé, sa **substitution** est un conflit.
final class ControleIdentiteAudio {

    private ControleIdentiteAudio() {}

    /// Les conflits d'identité des séquences **présentes** du passage, dans l'ordre des séquences.
    /// Les absentes n'ont rien à confronter ; les séquences sans empreinte ne sont pas vérifiables à
    /// ce coût et restent silencieuses ([IdentiteSequence#divergence]).
    static List<ConstatAudit> conflits(
            Long idPassage, List<SequenceDEcoute> sequences, Map<String, Presence> presences) {
        List<ConstatAudit> conflits = new ArrayList<>();
        for (SequenceDEcoute sequence : sequences) {
            if (presences.get(sequence.cheminFichier()) != Presence.PRESENTE) {
                continue;
            }
            IdentiteSequence.divergence(sequence)
                    .ifPresent(motif -> conflits.add(new ConstatAudit(
                            Severite.ERREUR,
                            CategorieConstat.AUDIO_DIVERGENT,
                            idPassage,
                            sequence.cheminFichier(),
                            "Le fichier présent n'est pas celui attendu (" + motif + "). Ce n'est pas le même"
                                    + " enregistrement : écouter ou valider dessus produirait un résultat faux."
                                    + " Réactivez ce passage depuis le dossier qui contient les bons fichiers.")));
        }
        return conflits;
    }
}

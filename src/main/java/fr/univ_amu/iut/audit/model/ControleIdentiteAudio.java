package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.PresenceFichiers.Presence;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Contrôle que les fichiers **présents** sont bien ceux que la base décrit (#2254, ADR 0048).
/// Extrait de [ServiceAuditCoherence] pour qu'il garde la seule responsabilité d'orchestrer les
/// contrôles (PMD GodClass), comme l'ont été [BalayageDisque] et [AuditEnLigne].
///
/// **Pourquoi ce contrôle existe.** La présence est établie sur le **nom** : [PresenceFichiers]
/// liste les dossiers, il ne lit pas les fichiers. Un fichier remplacé par un autre enregistrement
/// au même chemin - redécoupe, autre nuit du même carré, sauvegarde restaurée d'une autre version -
/// passerait donc pour l'audio attendu. L'utilisateur **validerait une espèce en écoutant autre
/// chose**, sans que rien ne le signale.
///
/// Une divergence n'est **pas** une absence : l'absence est un état observé (l'utilisateur possède
/// ses fichiers), la divergence est un **conflit**. C'est le seul écart de disponibilité qui reste
/// une [Severite#ERREUR].
///
/// **Coût borné.** Seules les séquences **porteuses d'une empreinte** (#1299) sont contrôlées :
/// taille, puis SHA-256 des 64 premiers Kio ([Empreintes#empreinteCourte]), soit ~113 µs par
/// fichier - de l'ordre d'une demi-seconde pour une nuit de 4806 séquences. Les séquences **sans**
/// empreinte (importées avant V23 et jamais rétro-remplies) sont **sautées** : les vérifier
/// demanderait la cascade structurelle et acoustique (`VerificationIdentiteAudio`), dont le coût n'a
/// pas sa place dans un balayage. La commande `retro-empreintes` les rend contrôlables.
final class ControleIdentiteAudio {

    private ControleIdentiteAudio() {}

    /// Les conflits d'identité des séquences **présentes** du passage, dans l'ordre des séquences.
    static List<ConstatAudit> conflits(
            Long idPassage, List<SequenceDEcoute> sequences, Map<String, Presence> presences) {
        List<ConstatAudit> conflits = new ArrayList<>();
        for (SequenceDEcoute sequence : sequences) {
            if (presences.get(sequence.cheminFichier()) != Presence.PRESENTE || sequence.empreinte() == null) {
                continue;
            }
            divergence(sequence)
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

    /// Le motif de divergence d'une séquence présente, ou **vide** si son identité concorde. La
    /// taille est confrontée d'abord (comparaison sans lecture), l'empreinte ensuite. Un fichier
    /// devenu **illisible** est rapporté comme divergent : on ne peut plus affirmer que c'est le bon.
    private static Optional<String> divergence(SequenceDEcoute sequence) {
        Path fichier = Path.of(sequence.cheminFichier());
        try {
            long taille = Files.size(fichier);
            if (sequence.tailleOctets() != null && taille != sequence.tailleOctets()) {
                return Optional.of("taille " + taille + " au lieu de " + sequence.tailleOctets());
            }
            return Empreintes.empreinteCourte(fichier).equals(sequence.empreinte())
                    ? Optional.empty()
                    : Optional.of("empreinte de contenu différente");
        } catch (IOException illisible) {
            return Optional.of("fichier illisible : " + illisible.getMessage());
        }
    }
}

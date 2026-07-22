package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Empreintes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/// Le fichier d'une séquence est-il **encore celui que la base décrit** (#2254, ADR 0048) ?
///
/// La présence d'un fichier s'établit sur son **nom** (`PresenceFichiers` liste les dossiers, il ne
/// lit pas les fichiers). Un fichier remplacé par un autre enregistrement au même chemin -
/// redécoupe, autre nuit du même carré, sauvegarde restaurée d'une autre version - passerait donc
/// pour l'audio attendu, et l'utilisateur **validerait une espèce en écoutant autre chose**.
///
/// Une divergence n'est **pas** une absence : l'absence est un état observé (l'utilisateur possède
/// ses fichiers), la divergence est un **conflit** - ces octets ne sont pas ceux qui ont produit les
/// observations.
///
/// **Vit ici, et pas dans la feature qui l'affiche.** L'audit en fait un constat, l'écran d'écoute
/// en grise le lecteur : deux features qui dépendent déjà de `passage.model`. Le placer dans l'une
/// des deux forcerait l'autre à en dépendre, et le graphe de slices refuse les cycles.
///
/// **Coût borné.** Taille, puis SHA-256 des 64 premiers Kio ([Empreintes#empreinteCourte]) : ~113 µs
/// par fichier, de l'ordre d'une demi-seconde pour une nuit de 4806 séquences. Une séquence **sans**
/// empreinte (importée avant V23, jamais rétro-remplie) n'est **pas vérifiable ainsi** et rend
/// [Optional#empty()] : la trancher demanderait la cascade structurelle et acoustique
/// ([VerificationIdentiteAudio]), dont le coût n'a pas sa place dans un balayage. La commande
/// `retro-empreintes` la rend contrôlable.
public final class IdentiteSequence {

    private IdentiteSequence() {}

    /// Le **motif** de divergence du fichier de cette séquence, ou **vide** quand rien ne permet de
    /// crier : identité concordante, ou séquence sans empreinte (donc non vérifiable à ce coût).
    ///
    /// La taille est confrontée d'abord (comparaison sans lecture), l'empreinte ensuite. Un fichier
    /// devenu **illisible** est rapporté comme divergent : on ne peut plus affirmer que c'est le bon.
    public static Optional<String> divergence(SequenceDEcoute sequence) {
        if (sequence.empreinte() == null) {
            return Optional.empty();
        }
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

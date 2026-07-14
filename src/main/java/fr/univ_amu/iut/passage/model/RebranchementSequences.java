package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Acceptee;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Refusee;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// **La garde** de la réactivation (#1302, #1309), extraite de [ServiceReactivationPassage] : confronter
/// une séquence attendue aux fichiers candidats, et ne rebrancher que ce qui est **vérifié**.
///
/// Les deux voies (#1406) passent par ici - les séquences retrouvées telles quelles **comme** celles
/// régénérées depuis les bruts. C'est exactement ce qu'on veut mettre en évidence : un fichier qu'on a
/// fabriqué soi-même n'a **aucun privilège**, il est vérifié comme les autres.
final class RebranchementSequences {

    private final VerificationIdentiteAudio verification;

    /// Cris attendus des observations (#1309), **optionnel** : absent des injecteurs partiels, la cascade
    /// retombe sur la vérification structurelle seule.
    private final Optional<CrisAttendus> crisAttendus;

    RebranchementSequences(VerificationIdentiteAudio verification, Optional<CrisAttendus> crisAttendus) {
        this.verification = Objects.requireNonNull(verification, "verification");
        this.crisAttendus = Objects.requireNonNull(crisAttendus, "crisAttendus");
    }

    /// Confronte chaque séquence **absente du disque** aux fichiers candidats de même nom, et rebranche
    /// ce qui est vérifié.
    BilanReactivation rebrancher(
            List<SequenceDEcoute> sequences, CandidatsReactivation candidats, Consumer<Progression> progres) {
        BilanReactivation bilan = new BilanReactivation();
        int traitees = 0;
        for (SequenceDEcoute sequence : sequences) {
            traitees++;
            progres.accept(new Progression(
                    "Vérification " + traitees + "/" + sequences.size(), traitees / (double) sequences.size()));
            Path destination = Path.of(sequence.cheminFichier());
            if (Files.exists(destination)) {
                bilan.dejaPresentes++;
                continue;
            }
            List<Path> homonymes = candidats.pour(sequence.nomFichier());
            if (homonymes.isEmpty()) {
                bilan.manquantes++;
                continue;
            }
            appliquer(bilan, sequence, homonymes, destination);
        }
        return bilan;
    }

    /// Confronte **chacun** des fichiers portant ce nom, et rebranche le **premier accepté**.
    ///
    /// Essayer tous les homonymes n'est pas un luxe : une sauvegarde réelle en contient (une copie
    /// interrompue à côté de la bonne, deux sauvegardes empilées, une carte SD copiée deux fois).
    /// S'arrêter au premier fichier rencontré - dans l'ordre où le système de fichiers veut bien les
    /// rendre - reviendrait à **refuser une séquence qui était pourtant là**, sur un tirage au sort.
    ///
    /// Aucun risque ajouté : chaque candidat passe la cascade complète. Si **tous** échouent, l'écart
    /// rapporté est celui du premier, et il dit combien de fichiers portaient ce nom.
    private void appliquer(BilanReactivation bilan, SequenceDEcoute sequence, List<Path> homonymes, Path destination) {
        List<CriAttendu> cris =
                crisAttendus.map(port -> port.pour(sequence.id())).orElseGet(List::of);
        String premierMotif = null;
        for (Path candidat : homonymes) {
            VerdictIdentite verdict = verification.verifierSequence(sequence, candidat, cris);
            if (verdict instanceof Acceptee acceptee) {
                copier(candidat, destination);
                bilan.accepter(acceptee.niveau());
                return;
            }
            if (premierMotif == null && verdict instanceof Refusee refusee) {
                premierMotif = refusee.motif();
            }
        }
        bilan.refuser(sequence.nomFichier(), premierMotif, homonymes.size());
    }

    /// Copie (jamais déplace : la sauvegarde de l'utilisateur reste intacte) le fichier vérifié à
    /// l'emplacement que la base attend.
    private static void copier(Path candidat, Path destination) {
        try {
            Path dossier = destination.getParent();
            if (dossier != null) {
                Files.createDirectories(dossier);
            }
            Files.copy(candidat, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Copie impossible vers " + destination, e);
        }
    }
}

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
import java.util.OptionalDouble;
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

    /// Mode **régénération** (#1682) : quand les candidats sont des tranches **régénérées** depuis le brut
    /// désigné (voie hydratation d'un passage reconstruit), l'identité tient à la régénération déterministe,
    /// pas à une empreinte. La vérification accepte alors sur le structurel et l'acoustique n'est qu'un
    /// **indice** (pas de veto). En mode normal (`false`), la cascade complète s'applique, acoustique comprise.
    private final boolean regeneration;

    RebranchementSequences(VerificationIdentiteAudio verification, Optional<CrisAttendus> crisAttendus) {
        this(verification, crisAttendus, false);
    }

    RebranchementSequences(
            VerificationIdentiteAudio verification, Optional<CrisAttendus> crisAttendus, boolean regeneration) {
        this.verification = Objects.requireNonNull(verification, "verification");
        this.crisAttendus = Objects.requireNonNull(crisAttendus, "crisAttendus");
        this.regeneration = regeneration;
    }

    /// D'où viennent les candidats, et donc **ce que veut dire leur absence** (#1943).
    ///
    /// À ne pas confondre avec le mode `regeneration`, qui pilote l'**acceptation** : la voie « bruts »
    /// régénère ses candidats tout en exigeant la cascade complète. L'un dit comment juger, l'autre
    /// comment expliquer.
    enum OrigineCandidats {
        /// Les fichiers que l'utilisateur a désignés : leur absence est un fait sur son disque.
        DOSSIER("aucun fichier de ce nom dans le dossier"),

        /// Les tranches que nous venons de fabriquer : leur absence est un défaut de notre côté.
        REGENERATION("tranche non régénérée depuis son enregistrement");

        private final String motif;

        OrigineCandidats(String motif) {
            this.motif = motif;
        }

        String motifAbsence() {
            return motif;
        }
    }

    /// Ce qu'on fait d'un candidat **vérifié**. Deux gestes possibles, et le choix appartient à
    /// l'appelant :
    ///
    /// - **copier** ([#COPIE]) : le fichier rejoint l'emplacement que la base attend. L'audio devient
    ///   *possédé* par l'espace de travail ;
    /// - **référencer** (#2255) : rien n'est déplacé, c'est la **base** qui pointe désormais le fichier
    ///   là où il vit. L'audio reste *à l'utilisateur* - sur son NAS, son disque externe, son dossier.
    ///
    /// Cette classe ignore la persistance : elle sait vérifier et déléguer, pas écrire. Le geste qui
    /// touche la base est fourni par [ServiceReactivationPassage], qui, lui, tient les DAO.
    @FunctionalInterface
    interface PoseurCandidat {
        void poser(SequenceDEcoute sequence, Path candidat, Path destination);
    }

    /// Le geste historique : copier (**jamais** déplacer, la sauvegarde de l'utilisateur reste intacte)
    /// le fichier vérifié à l'emplacement que la base attend.
    static final PoseurCandidat COPIE = (sequence, candidat, destination) -> copier(candidat, destination);

    /// Confronte chaque séquence **absente du disque** aux fichiers candidats de même nom, et rebranche
    /// ce qui est vérifié, par le geste que `poseur` décide (copie ou référence).
    BilanReactivation rebrancher(
            List<SequenceDEcoute> sequences,
            CandidatsReactivation candidats,
            OrigineCandidats origine,
            PoseurCandidat poseur,
            Consumer<Progression> progres) {
        BilanReactivation bilan = new BilanReactivation();
        int traitees = 0;
        for (SequenceDEcoute sequence : sequences) {
            traitees++;
            progres.accept(new Progression(
                    "Vérification " + traitees + "/" + sequences.size(), traitees / (double) sequences.size()));
            if (sequence.estSurLeDisque()) {
                bilan.dejaPresentes++;
                continue;
            }
            Path destination = Path.of(sequence.cheminFichier());
            List<Path> homonymes = candidats.pour(sequence.nomFichier());
            if (homonymes.isEmpty()) {
                // Aucun fichier de ce nom parmi les candidats. Ce que cela signifie dépend d'où ils
                // viennent : dossier de l'utilisateur, ou tranches que nous venons de régénérer (#1943).
                bilan.absenter(sequence.nomFichier(), origine.motifAbsence(), 1);
                continue;
            }
            appliquer(bilan, sequence, homonymes, destination, poseur);
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
    private void appliquer(
            BilanReactivation bilan,
            SequenceDEcoute sequence,
            List<Path> homonymes,
            Path destination,
            PoseurCandidat poseur) {
        List<CriAttendu> cris =
                crisAttendus.map(port -> port.pour(sequence.id())).orElseGet(List::of);
        String premierMotif = null;
        for (Path candidat : homonymes) {
            VerdictRegenere resultat = verifier(sequence, candidat, cris);
            if (resultat.verdict() instanceof Acceptee acceptee) {
                poseur.poser(sequence, candidat, destination);
                bilan.accepter(acceptee.niveau());
                bilan.noterAcoustique(resultat.concordanceCris());
                return;
            }
            if (premierMotif == null && resultat.verdict() instanceof Refusee refusee) {
                premierMotif = refusee.motif();
            }
        }
        bilan.refuser(sequence.nomFichier(), premierMotif, homonymes.size());
    }

    /// Vérifie un candidat selon le mode : en **régénération** (#1682), acceptation structurelle et
    /// concordance acoustique en indice ; en mode normal, cascade complète (l'acoustique redevient un veto,
    /// concordance non exposée). Un seul point de branchement, pour que le rebranchement reste identique.
    private VerdictRegenere verifier(SequenceDEcoute sequence, Path candidat, List<CriAttendu> cris) {
        if (regeneration) {
            return verification.verifierSequenceRegeneree(sequence, candidat, cris);
        }
        return new VerdictRegenere(verification.verifierSequence(sequence, candidat, cris), OptionalDouble.empty());
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

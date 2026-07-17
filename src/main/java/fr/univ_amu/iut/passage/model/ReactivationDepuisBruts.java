package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Acceptee;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Refusee;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// La voie **« bruts »** de la réactivation (#1406), extraite de [ServiceReactivationPassage] (qui
/// portait les deux voies et devenait une classe à tout faire).
///
/// Pour chaque original dont le brut a été retrouvé : on **prouve le brut** (SHA-256 intégral, capturé à
/// l'import depuis toujours), on **régénère** ses tranches dans un dossier temporaire, et on les remet à
/// [RebranchementSequences] - c'est-à-dire à **la même garde** que n'importe quel candidat. Rien n'est
/// cru sur parole du seul fait qu'on l'a fabriqué soi-même : la transformation étant déterministe (R11),
/// une tranche régénérée retrouve l'empreinte capturée avant l'archivage, et c'est **cette preuve-là**
/// qui la fait accepter.
///
/// Un **brut refusé ne régénère rien** : recalculer des séquences à partir d'un fichier dont l'identité
/// n'est pas établie, ce serait fabriquer du faux.
final class ReactivationDepuisBruts {

    private final VerificationIdentiteAudio verification;
    private final RebranchementSequences rebranchement;

    /// Le moteur de régénération, **optionnel** : il vit dans la feature « Importation », désactivable.
    private final Optional<RegenerationSequences> regeneration;

    ReactivationDepuisBruts(
            VerificationIdentiteAudio verification,
            RebranchementSequences rebranchement,
            Optional<RegenerationSequences> regeneration) {
        this.verification = Objects.requireNonNull(verification, "verification");
        this.rebranchement = Objects.requireNonNull(rebranchement, "rebranchement");
        this.regeneration = Objects.requireNonNull(regeneration, "regeneration");
    }

    /// Régénère et rebranche, **original par original**.
    ///
    /// Le temporaire est vidé après chaque original : régénérer une nuit entière d'un coup doublerait
    /// transitoirement l'occupation disque, ce que l'utilisateur cherchait précisément à éviter.
    BilanReactivation appliquer(
            List<SequenceDEcoute> sequences,
            List<EnregistrementOriginal> originaux,
            CandidatsReactivation candidats,
            Optional<Prefixe> prefixeSession,
            Consumer<Progression> progres) {
        RegenerationSequences moteur = regeneration.orElseThrow(() -> new RegleMetierException(
                "Ce dossier ne contient que les enregistrements bruts, et les régénérer demande la"
                        + " fonctionnalité « Importation », désactivée : réactivez-la (menu ☰ >"
                        + " Fonctionnalités) puis recommencez."));
        Prefixe prefixe = prefixeSession.orElseThrow(() -> new RegleMetierException(
                "Le dossier de cette session ne porte pas un nom reconnaissable : impossible de régénérer"
                        + " ses séquences."));

        BilanReactivation bilan = new BilanReactivation();
        int traites = 0;
        for (EnregistrementOriginal original : originaux) {
            traites++;
            progres.accept(new Progression(
                    "Régénération " + traites + "/" + originaux.size(), traites / (double) originaux.size()));
            List<SequenceDEcoute> sesSequences = sequencesDe(sequences, original);
            Path brut = brutProuve(bilan, original, candidats.brutsDe(original, prefixeSession));
            if (brut == null) {
                // Aucun brut, ou aucun brut PROUVÉ : dans les deux cas, ces séquences restent absentes.
                bilan.manquantes += absentesDuDisque(sesSequences);
                continue;
            }
            regenererEtRebrancher(moteur, bilan, original, sesSequences, brut, prefixe);
        }
        return bilan;
    }

    /// Le **premier** brut candidat dont l'identité est prouvée, `null` si aucun ne l'est. Comme pour les
    /// séquences, on essaie **tous** les homonymes : une carte SD sauvegardée deux fois, dont une copie
    /// interrompue, ne doit pas faire échouer une réactivation sur un tirage au sort.
    private Path brutProuve(BilanReactivation bilan, EnregistrementOriginal original, List<Path> homonymes) {
        String premierMotif = null;
        for (Path candidat : homonymes) {
            VerdictIdentite verdict = verification.verifierOriginal(original, candidat);
            if (verdict instanceof Acceptee) {
                return candidat;
            }
            if (premierMotif == null && verdict instanceof Refusee refusee) {
                premierMotif = refusee.motif();
            }
        }
        if (premierMotif != null) {
            bilan.refuser(original.nomFichier(), premierMotif, homonymes.size());
        }
        return null;
    }

    /// Régénère les tranches d'**un** brut dans un temporaire, les fait passer par la garde, puis efface
    /// le temporaire (quoi qu'il arrive).
    private void regenererEtRebrancher(
            RegenerationSequences moteur,
            BilanReactivation bilan,
            EnregistrementOriginal original,
            List<SequenceDEcoute> sesSequences,
            Path brut,
            Prefixe prefixe) {
        Path temporaire = DossierTemporaire.creer("vc-regen-" + original.id() + "-");
        try {
            moteur.regenerer(brut, original.nomFichier(), prefixe, frequenceAcquisition(original), temporaire);
            bilan.absorber(
                    rebranchement.rebrancher(sesSequences, CandidatsReactivation.dans(temporaire), progres -> {}));
        } finally {
            DossierTemporaire.supprimer(temporaire);
        }
    }

    /// Fréquence d'**acquisition** persistée à l'import (`Fe` du log, pas celle de l'en-tête) : c'est elle
    /// qui pilote le découpage à 5 s réelles, donc elle qui doit être rejouée à l'identique.
    private static int frequenceAcquisition(EnregistrementOriginal original) {
        Integer frequence = original.frequenceEchantillonnageHz();
        if (frequence == null) {
            throw new RegleMetierException("La fréquence d'acquisition de « " + original.nomFichier()
                    + " » est inconnue en base : ses séquences ne peuvent pas être régénérées à l'identique.");
        }
        return frequence;
    }

    private static List<SequenceDEcoute> sequencesDe(List<SequenceDEcoute> sequences, EnregistrementOriginal original) {
        return sequences.stream()
                .filter(sequence -> original.id().equals(sequence.idEnregistrementOriginal()))
                .sorted(Comparator.comparing(SequenceDEcoute::nomFichier))
                .toList();
    }

    private static int absentesDuDisque(List<SequenceDEcoute> sequences) {
        return (int) sequences.stream()
                .filter(sequence -> !Files.exists(Path.of(sequence.cheminFichier())))
                .count();
    }
}

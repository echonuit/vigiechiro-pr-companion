package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.NommageSequences;
import fr.univ_amu.iut.commun.model.NommageSequences.TranchesAttendues;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.RebranchementSequences.OrigineCandidats;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Acceptee;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Refusee;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

        // L'arbitrage des collisions de noms se rejoue sur la nuit ENTIÈRE, avant toute régénération : il
        // ne manipule que des chaînes, donc il ne coûte rien, et il doit voir tous les originaux que
        // l'import voyait. Le fonder sur les bruts retrouvés rendrait des noms différents dès qu'un
        // manque - et la jointure avec l'observations.csv casserait.
        Map<String, List<String>> nomsArbitres = NommageSequences.arbitrer(prefixe, attendues(originaux));

        BilanReactivation bilan = new BilanReactivation();
        int traites = 0;
        for (EnregistrementOriginal original : originaux) {
            traites++;
            progres.accept(new Progression(
                    "Régénération " + traites + "/" + originaux.size(), traites / (double) originaux.size()));
            List<SequenceDEcoute> sesSequences = sequencesDe(sequences, original, nomsArbitres);
            if (absentesDuDisque(sesSequences) == 0) {
                // Rien à récupérer pour cet original : ses tranches sont déjà toutes en place (#1962). On
                // évite de prouver son brut par SHA-256 puis de le redécouper en entier pour ne rien
                // rebrancher. Le rebranchement les aurait comptées « déjà présentes » : on le fait ici.
                //
                // Ce n'est pas une optimisation de confort : une relance après correctif - le cas normal
                // quand on revérifie - passait 4 min 31 à redécouper 1815 enregistrements pour en rebrancher
                // 117.
                bilan.dejaPresentes += sesSequences.size();
                continue;
            }
            List<Path> homonymes = candidats.brutsDe(original, prefixeSession);
            Path brut = brutProuve(bilan, original, homonymes);
            if (brut == null) {
                // Aucun brut, ou aucun brut PROUVÉ : dans les deux cas, ces séquences restent absentes.
                // Un refus a déjà son écart, avec son motif ; une absence, elle, n'avait rien (#1943).
                int perdues = absentesDuDisque(sesSequences);
                if (homonymes.isEmpty() && perdues > 0) {
                    bilan.absenter(original.nomFichier(), "enregistrement absent du dossier", perdues);
                } else {
                    bilan.manquantes += perdues;
                }
                continue;
            }
            regenererEtRebrancher(
                    moteur,
                    bilan,
                    original,
                    sesSequences,
                    brut,
                    prefixe,
                    nomsArbitres.getOrDefault(original.nomFichier(), List.of()));
        }
        return bilan;
    }

    /// Ce que chaque original attend de l'arbitrage : son nom, et son nombre de tranches (`ceil(D / 5)`).
    ///
    /// Un original **sans durée** en base ne participe pas : c'est le cas du placeholder d'un passage
    /// reconstruit (#1648), qui ne représente aucun fichier et ne produit donc aucune tranche.
    private static List<TranchesAttendues> attendues(List<EnregistrementOriginal> originaux) {
        return originaux.stream()
                .filter(original -> original.dureeSecondes() != null)
                .map(original -> new TranchesAttendues(
                        original.nomFichier(), NommageSequences.nombreTranches(original.dureeSecondes())))
                .toList();
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
            Prefixe prefixe,
            List<String> nomsArbitres) {
        Path temporaire = DossierTemporaire.creer("vc-regen-" + original.id() + "-");
        try {
            moteur.regenerer(
                    brut, original.nomFichier(), prefixe, frequenceAcquisition(original), temporaire, nomsArbitres);
            bilan.absorber(rebranchement.rebrancher(
                    sesSequences,
                    CandidatsReactivation.dans(temporaire),
                    OrigineCandidats.REGENERATION,
                    RebranchementSequences.COPIE,
                    progres -> {}));
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

    /// Les séquences que **cet original** produit, revendiquées par leur **nom** avant de l'être par leur
    /// rattachement en base (#1937).
    ///
    /// Le rattachement seul ne suffit pas : une séquence ayant perdu une collision de noms à l'import peut
    /// se retrouver accrochée au **placeholder** d'un passage reconstruit, et plus à l'enregistrement dont
    /// elle vient. Personne ne la cherchait alors, et son audio - pourtant régénérable - disparaissait avec
    /// le temporaire. L'arbitrage sait quel original produit quel nom : c'est donc lui qui tranche.
    ///
    /// Le rattachement reste le **filet** : une séquence qu'aucun arbitrage ne revendique (original absent
    /// de la base, nommage d'une autre époque) suit encore son `idEnregistrementOriginal`. On ne la perd
    /// pas faute de savoir la nommer.
    private static List<SequenceDEcoute> sequencesDe(
            List<SequenceDEcoute> sequences, EnregistrementOriginal original, Map<String, List<String>> nomsArbitres) {
        Set<String> siens = Set.copyOf(nomsArbitres.getOrDefault(original.nomFichier(), List.of()));
        Set<String> revendiques =
                nomsArbitres.values().stream().flatMap(List::stream).collect(Collectors.toUnmodifiableSet());
        return sequences.stream()
                .filter(sequence -> siens.contains(sequence.nomFichier())
                        || (original.id().equals(sequence.idEnregistrementOriginal())
                                && !revendiques.contains(sequence.nomFichier())))
                .sorted(Comparator.comparing(SequenceDEcoute::nomFichier))
                .toList();
    }

    private static int absentesDuDisque(List<SequenceDEcoute> sequences) {
        return (int) sequences.stream()
                .filter(sequence -> !sequence.estSurLeDisque())
                .count();
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages.BilanReconstructionGroupe;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages.IssueNuit;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `reconstruire-passage` (#1305, EPIC #1297) : rend visibles — et récupérables — les nuits **déposées sur
/// la plateforme mais jamais importées sur cette machine** (déposées depuis un autre poste, avant
/// l'application, ou après une réinstallation).
///
/// Sans argument, la commande **liste** ces participations orphelines. Avec `--participation <objectid>`,
/// elle en **reconstruit** une ; avec `--tout`, elle les reconstruit **toutes** en une passe (parité CLI
/// de l'import groupé #1708 — best-effort : une nuit qui échoue est ignorée, le lot continue). Une nuit
/// reconstruite devient un **passage archivé** : lignes de séquences (sans fichier) et observations
/// rapatriées. Elle se consulte comme tout passage archivé, et se réactive si l'utilisateur retrouve ses
/// fichiers (`reactiver`).
///
/// **Ce qui manque est dit** : un passage reconstruit n'a ni journal du capteur, ni relevé climatique, ni
/// séquences non identifiées, ni empreintes — la plateforme ne les connaît pas.
@Command(
        name = "reconstruire-passage",
        description = "Liste les participations VigieChiro sans équivalent local (nuits déposées depuis un "
                + "autre poste, ou avant l'application) et, avec --participation, en reconstruit une (ou "
                + "--tout pour les reconstruire toutes) en passage archivé : observations rapatriées, "
                + "audio absent, réactivable par réimport.")
public final class ReconstruirePassage implements Callable<Integer> {

    @Option(
            names = "--participation",
            paramLabel = "<objectid>",
            description = "Reconstruit cette participation en passage archivé. Sans cette option, "
                    + "la commande se contente de lister les participations orphelines.")
    private String idParticipation;

    @Option(
            names = "--tout",
            description = "Reconstruit TOUTES les participations orphelines, l'une après l'autre "
                    + "(best-effort : une nuit qui échoue - point inconnu ici, analyse non terminée - est "
                    + "ignorée, le lot continue). Parité CLI de l'import groupé.")
    private boolean tout;

    @Option(names = "--json", description = "Émet la liste ou le rapport au format JSON.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma
    // (cf. Auditer). L'Optional est vide hors connexion : le service a besoin de la plateforme.
    private final Provider<Optional<ServiceReconstructionPassages>> service;

    @Inject
    public ReconstruirePassage(Provider<Optional<ServiceReconstructionPassages>> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        ServiceReconstructionPassages reconstruction = service.get()
                .orElseThrow(() -> new RegleMetierException("La reconstruction a besoin de la connexion"
                        + " VigieChiro : renseignez un jeton (VIGIECHIRO_TOKEN) puis recommencez."));
        if (tout) {
            return reconstruireTout(sortie, reconstruction);
        }
        return idParticipation == null
                ? lister(sortie, reconstruction.orphelines())
                : reconstruire(sortie, reconstruction.reconstruire(idParticipation));
    }

    /// Reconstruit **toutes** les participations orphelines (parité CLI de l'import groupé #1708). Best-effort
    /// par nuit : une nuit qui échoue (point inconnu ici, analyse non terminée) est **comptée « ignorée » et
    /// sautée**, le lot continue - un incident isolé ne prive pas l'utilisateur des autres reconstructions.
    /// Réutilise l'orpheline déjà en main (carré + localité), sans re-télécharger la liste par nuit.
    private int reconstruireTout(PrintWriter sortie, ServiceReconstructionPassages reconstruction) {
        List<ParticipationOrpheline> orphelines = reconstruction.orphelines();
        if (orphelines.isEmpty()) {
            sortie.println("Aucune participation orpheline : rien à reconstruire.");
            return 0;
        }
        int total = orphelines.size();
        // La boucle + le best-effort vivent au service (harmonisation passe 7). La CLI ne garde que son
        // rendu : une ligne par nuit (via l'issue), sans barre de progression (les progressions sont ignorées).
        int[] rang = {0};
        BilanReconstructionGroupe bilan = reconstruction.reconstruireTout(
                orphelines,
                progression -> {},
                progression -> {},
                issue -> {
                    rang[0]++;
                    if (!json) {
                        sortie.println("  " + ligneIssue(issue, rang[0], total));
                    }
                },
                JetonAnnulation.neutre());
        if (json) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("reussies", bilan.reussies());
            objet.put("ignorees", bilan.ignorees());
            objet.put("sequences", bilan.sequences());
            objet.put("observations", bilan.observations());
            sortie.println(FormatJson.objet(objet));
        } else {
            sortie.println(bilan.reussies() + " nuit(s) reconstruite(s), " + bilan.ignorees() + " ignorée(s) : "
                    + bilan.sequences() + " séquence(s), " + bilan.observations() + " observation(s) rapatriée(s).");
        }
        return 0;
    }

    /// Ligne de compte rendu d'une nuit d'un import groupé : le passage créé (reconstruite) ou la cause du
    /// saut (ignorée). La CLI **pattern-matche** l'issue du service pour son rendu ; le service, lui, ne
    /// connaît ni la CLI ni l'IHM.
    private static String ligneIssue(IssueNuit issue, int rang, int total) {
        String position = "[" + rang + "/" + total + "] nuit du " + issue.nuit().dateDebut();
        return switch (issue) {
            case IssueNuit.Reconstruite reconstruite ->
                position + " -> passage " + reconstruite.rapport().idPassage() + " ("
                        + reconstruite.rapport().sequencesRecreees() + " séquence(s), "
                        + reconstruite.rapport().observationsImportees() + " observation(s))";
            case IssueNuit.Ignoree ignoree -> position + " -> ignorée : " + ignoree.cause();
        };
    }

    /// Liste les participations sans équivalent local ; `0` même si la liste est vide (ce n'est pas une
    /// erreur : cela veut dire que tout est déjà là).
    private int lister(PrintWriter sortie, List<ParticipationOrpheline> orphelines) {
        if (json) {
            sortie.println(FormatJson.tableau(
                    orphelines.stream().map(ReconstruirePassage::projeter).toList()));
            return 0;
        }
        if (orphelines.isEmpty()) {
            sortie.println("Aucune participation orpheline : toutes vos nuits VigieChiro ont un passage local.");
            return 0;
        }
        sortie.println(orphelines.size() + " participation(s) sans équivalent local :");
        for (ParticipationOrpheline orpheline : orphelines) {
            sortie.println("  - " + orpheline.idParticipation() + "  carré " + orpheline.numeroCarre() + ", localité "
                    + orpheline.codePoint() + ", nuit du " + orpheline.dateDebut()
                    + (orpheline.pointLocalConnu() ? "" : "  [point inconnu localement : créez-le d'abord]"));
        }
        sortie.println("Reconstruisez-en une avec --participation <objectid>.");
        return 0;
    }

    /// Reconstruit et rend compte, **lacunes comprises** : un passage reconstruit est moins riche qu'un
    /// passage archivé par purge, et le taire laisserait croire à une équivalence.
    private int reconstruire(PrintWriter sortie, RapportReconstruction rapport) {
        if (json) {
            sortie.println(FormatJson.objet(projeter(rapport)));
            return 0;
        }
        sortie.println("Passage " + rapport.idPassage() + " reconstruit (archivé) : " + rapport.sequencesRecreees()
                + " séquence(s) recréée(s), " + rapport.observationsImportees() + " observation(s) importée(s).");
        sortie.println("Ce qui manque, faute d'exister côté plateforme :");
        rapport.lacunes().forEach(lacune -> sortie.println("  - " + lacune));
        return 0;
    }

    private static Map<String, Object> projeter(ParticipationOrpheline orpheline) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("participation", orpheline.idParticipation());
        objet.put("carre", orpheline.numeroCarre());
        objet.put("point", orpheline.codePoint());
        objet.put("dateDebut", orpheline.dateDebut());
        objet.put("pointLocalConnu", orpheline.pointLocalConnu());
        return objet;
    }

    private static Map<String, Object> projeter(RapportReconstruction rapport) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", rapport.idPassage());
        objet.put("sequencesRecreees", rapport.sequencesRecreees());
        objet.put("observationsImportees", rapport.observationsImportees());
        objet.put("lacunes", rapport.lacunes());
        return objet;
    }
}

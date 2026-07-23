package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.importation.model.ServiceImportReference;
import fr.univ_amu.iut.importation.model.ServiceImportReference.ResultatImportReference;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `importer-transformes` (#2433, EPIC #2258) : crée un passage à partir d'un dossier de séquences **déjà
/// transformées** (`×10 + découpées`), sans rejouer la transformation. Pendant en ligne de commande de
/// l'écran « importer des transformés déjà présents ». Contrairement à `importer` (qui part des bruts d'une
/// carte SD), ce geste n'a ni brut ni journal LogPR : la série et la date sont **déduites des noms** de
/// fichiers, le point d'écoute est fourni.
///
/// Par défaut, les WAV sont **copiés** dans l'espace de travail (audio possédé) ; `--referencer` les laisse
/// **en place** (aucun octet audio recopié, la base pointe l'emplacement externe, cf. ADR 2433 / ADR 0048).
@Command(
        name = "importer-transformes",
        description = "Crée un passage à partir d'un dossier de séquences déjà transformées (sans re-transformer).")
public final class ImporterTransformes implements Callable<Integer> {

    /// Code de sortie d'un refus métier (dossier introuvable, aucun WAV, point inconnu, quadruplet déjà
    /// pris) : rien n'a été créé, l'état est intact. Distinct du succès (0) et de l'échec inattendu (1),
    /// convention #2294 (comme `importer`).
    private static final int CODE_REFUS = 2;

    @Option(
            names = "--dossier",
            required = true,
            paramLabel = "<dir>",
            description = "Dossier des séquences déjà transformées (WAV nommés à la convention R6).")
    private Path dossier;

    @Option(
            names = "--point",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du point d'écoute auquel rattacher la nuit (voir « lister-passages »).")
    private long point;

    @Option(names = "--annee", paramLabel = "<N>", description = "Année du passage. Défaut : année courante.")
    private Integer annee;

    @Option(
            names = "--passage",
            paramLabel = "<N>",
            description = "Numéro de passage. Défaut : prochain numéro libre pour ce point.")
    private Integer numeroPassage;

    @Option(
            names = "--referencer",
            description = "Ne copie rien : la base pointe les WAV là où ils sont (NAS, disque externe, dossier"
                    + " de travail). La nuit devient muette si ce support n'est plus joignable, et redevient"
                    + " écoutable quand il revient. Sans cette option, les WAV sont copiés dans l'espace de travail.")
    private boolean referencer;

    @Spec
    private CommandSpec spec;

    private final ServiceImportReference service;

    @Inject
    public ImporterTransformes(ServiceImportReference service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        ResultatImportReference resultat;
        try {
            resultat = service.importer(
                    dossier, point, annee, numeroPassage, referencer, p -> {}, JetonAnnulation.neutre());
        } catch (RegleMetierException refus) {
            // Refus métier : rien n'a été créé, l'état est intact. On dit lequel, sans trace, code 2 (#2294).
            spec.commandLine().getErr().println("Refus : " + refus.getMessage());
            return CODE_REFUS;
        }
        sortie.println("Import par " + (referencer ? "référence" : "copie") + " réussi.");
        sortie.println("  Passage   : #" + resultat.idPassage());
        sortie.println("  Session   : " + resultat.nomSession());
        sortie.println("  Séquences : " + resultat.nombreSequences()
                + (referencer
                        ? " (référencées en place, aucun octet audio recopié)"
                        : " (copiées dans l'espace de travail)"));
        return 0;
    }
}

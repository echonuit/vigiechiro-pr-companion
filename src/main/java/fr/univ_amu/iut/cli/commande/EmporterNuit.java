package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.model.NatureDEntree;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `emporter-nuit` (#4729) : écrit le paquet d'une nuit pour la faire relire ailleurs, parité en
/// ligne de commande du geste « Emporter cette nuit… » de M-Qualification (article A19).
///
/// **Le volume s'annonce avant d'écrire**, comme à l'écran. Sans `--oui`, la commande annonce et
/// s'arrête : on n'emporte pas des gigaoctets sur un script lancé de travers. Avec `--oui`, elle
/// annonce puis écrit.
///
/// Refus métier (nuit sans sélection, séquence introuvable) : code 1. Erreur d'usage : code 2.
@Command(
        name = "emporter-nuit",
        description = "Écrit le paquet d'une nuit pour relecture. Annonce le volume ; --oui pour écrire.")
public final class EmporterNuit implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--passage", required = true, paramLabel = "<id>", description = "Nuit à emporter.")
    private Long idPassage;

    @Option(names = "--vers", required = true, paramLabel = "<fichier>", description = "Archive à écrire.")
    private Path destination;

    @Option(names = "--oui", description = "Écrit réellement le paquet, après l'annonce du volume.")
    private boolean confirme;

    private final ServiceEmport service;

    @Inject
    public EmporterNuit(ServiceEmport service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() throws IOException {
        ServiceEmport.EmportPrepare prepare = service.preparer(idPassage, destination);
        long audio = prepare.plan().octetsParNature().getOrDefault(NatureDEntree.SEQUENCE, 0L);
        spec.commandLine()
                .getOut()
                .println(prepare.fichiers().size() + " séquence(s), "
                        + prepare.plan().octetsEstimes() + " octet(s) dont " + audio + " d'audio.");
        if (!confirme) {
            spec.commandLine().getOut().println("Rien n'est écrit. Relancez avec --oui pour emporter.");
            return 0;
        }
        long octets = service.ecrire(prepare);
        spec.commandLine().getOut().println("Paquet écrit : " + destination + " (" + octets + " octets).");
        return 0;
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `exporter-lot` (P4) : prépare le **lot prêt à déposer** d'un passage vérifié (récapitulatif + archives ZIP
/// de dépôt Tadarida), via [ServiceLot].
@Command(
        name = "exporter-lot",
        description = "Prépare le lot prêt à déposer d'un passage vérifié (récapitulatif + archives ZIP).")
public final class ExporterLot implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du passage dont préparer le lot.")
    private long passage;

    @Spec
    private CommandSpec spec;

    private final ServiceLot serviceLot;

    @Inject
    public ExporterLot(ServiceLot serviceLot) {
        this.serviceLot = Objects.requireNonNull(serviceLot, "serviceLot");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();

        Lot lot = serviceLot.preparerLot(passage);
        sortie.println("Lot prêt à déposer pour le passage #" + lot.idPassage() + ".");
        sortie.println("  Séquences : " + lot.nombreSequences());
        sortie.println("  Volume    : "
                + (lot.volumeSequencesOctets() == null ? "-" : lot.volumeSequencesOctets() + " octets"));
        sortie.println("  Dossier   : " + lot.cheminDossier());

        List<ArchiveDepot> archives = serviceLot.genererArchivesDepot(passage);
        sortie.println("  Archives de dépôt (" + archives.size() + ") :");
        for (ArchiveDepot archive : archives) {
            sortie.println("    - " + archive.chemin().getFileName() + " (" + archive.nombreFichiers() + " fichiers, "
                    + archive.tailleOctets() + " octets)");
        }
        return 0;
    }
}

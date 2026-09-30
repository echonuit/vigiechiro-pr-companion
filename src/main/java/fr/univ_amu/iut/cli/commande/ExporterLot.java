package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.EtatLot;
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

/// `exporter-lot` (P4) : prépare le **dépôt** d'un passage vérifié (récapitulatif + archives ZIP
/// de dépôt Tadarida), via [ServiceLot].
@Command(
        name = "exporter-lot",
        description =
                "Prépare le dépôt d'un passage vérifié, ou régénère les archives ZIP d'un dépôt déjà préparé ou entamé.")
public final class ExporterLot implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du passage dont préparer le dépôt.")
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

        // Préparer seulement ce qui ne l'est pas (#5599) : la préparation n'admet que « Vérifié », et la
        // commande refusait donc tout passage déjà préparé, dont un dépôt entamé, que l'écran régénère.
        EtatLot etat = serviceLot.consulterLot(passage);
        if (ServiceLot.archivesSeGenerent(etat.statut())) {
            sortie.println("Dépôt déjà préparé pour le passage #" + passage + " ("
                    + etat.statut().libelle() + ").");
            decrire(sortie, etat.nombreSequences(), etat.volumeSequencesOctets(), etat.cheminDossier());
        } else {
            Lot lot = serviceLot.preparerLot(passage);
            sortie.println("Dépôt prêt pour le passage #" + lot.idPassage() + ".");
            decrire(sortie, lot.nombreSequences(), lot.volumeSequencesOctets(), lot.cheminDossier());
        }

        List<ArchiveDepot> archives = serviceLot.genererArchivesDepot(passage);
        sortie.println("  Archives de dépôt (" + archives.size() + ") :");
        for (ArchiveDepot archive : archives) {
            sortie.println("    - " + archive.chemin().getFileName() + " (" + archive.nombreFichiers() + " fichiers, "
                    + archive.tailleOctets() + " octets)");
        }
        return 0;
    }

    private static void decrire(PrintWriter sortie, int sequences, Long volumeOctets, String dossier) {
        sortie.println("  Séquences : " + sequences);
        sortie.println("  Volume    : " + (volumeOctets == null ? "-" : volumeOctets + " octets"));
        sortie.println("  Dossier   : " + dossier);
    }
}

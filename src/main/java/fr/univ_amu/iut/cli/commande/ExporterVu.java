package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `exporter-vu` (P7) : exporte le **CSV `*_Vu.csv` réinjectable** des résultats validés d'un passage, via
/// [ServiceValidation]. L'export s'appuie sur le **jeu de résultats** annotant le passage (résolu d'abord).
@Command(name = "exporter-vu", description = "Exporte le CSV réinjectable *_Vu des résultats validés d'un passage.")
public final class ExporterVu implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du passage dont exporter les résultats validés.")
    private long passage;

    @Option(
            names = "--sortie",
            required = true,
            paramLabel = "<fichier>",
            description = "Chemin du fichier CSV *_Vu à écrire.")
    private Path sortie;

    @Spec
    private CommandSpec spec;

    private final ResultatsIdentificationDao resultatsDao;
    private final ServiceValidation validation;

    @Inject
    public ExporterVu(ResultatsIdentificationDao resultatsDao, ServiceValidation validation) {
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    @Override
    public Integer call() {
        ResultatsIdentification resultats = resultatsDao
                .findByPassage(passage)
                .orElseThrow(() -> new RegleMetierException("Aucun résultat Tadarida importé pour le passage "
                        + passage
                        + " : rien à exporter (importez d'abord le CSV d'observations Tadarida)."));

        // inclureMode=true : ajoute la colonne validation_mode (R24) au CSV réinjectable.
        Path ecrit = validation.exporter(resultats.id(), sortie, true);
        spec.commandLine().getOut().println("Export Vu écrit : " + ecrit.toAbsolutePath());
        return 0;
    }
}

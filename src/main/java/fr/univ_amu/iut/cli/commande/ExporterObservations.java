package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.validation.model.ExportObservationsCsv;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `exporter-observations` (#659, facette CLI de #149) : écrit en CSV **toutes** les observations d'un
/// passage, pour l'analyse ou l'interopérabilité hors application (parcours A10). Réutilise
/// [ServiceValidation#lignesAudioDuPassage] et le formateur pur [ExportObservationsCsv] déjà partagé avec
/// l'IHM (UTF-8 + BOM, séparateur `;`, ouvrable directement par un tableur), sans logique nouvelle. Un
/// passage sans observation produit un CSV d'en-têtes seuls (résultat valide). À distinguer d'`exporter-vu`
/// (CSV `_Vu` réinjectable, destiné au dépôt).
@Command(
        name = "exporter-observations",
        description = "Exporte en CSV toutes les observations d'un passage (analyse / interopérabilité).")
public final class ExporterObservations implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du passage dont exporter les observations.")
    private long passage;

    @Option(
            names = "--sortie",
            required = true,
            paramLabel = "<fichier>",
            description = "Chemin du fichier CSV à écrire.")
    private Path sortie;

    @Spec
    private CommandSpec spec;

    private final ServiceValidation validation;

    @Inject
    public ExporterObservations(ServiceValidation validation) {
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    @Override
    public Integer call() throws IOException {
        List<LigneObservationAudio> lignes = validation.lignesAudioDuPassage(passage);
        Path ecrit = ExportObservationsCsv.ecrire(lignes, sortie);
        spec.commandLine()
                .getOut()
                .println("Observations exportées : " + lignes.size() + " ligne(s) → " + ecrit.toAbsolutePath());
        return 0;
    }
}

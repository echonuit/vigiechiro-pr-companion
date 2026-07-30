package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.AgregationActivite;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.ExportActiviteCsv;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.LigneActivite;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `exporter-activite` (#2352, parité CLI de la courbe d'activité) : écrit en CSV la courbe d'activité
/// horaire d'un passage : les contacts par tranche et par espèce, **rattachés à la nuit biologique** (bascule
/// à midi), exactement comme la vue les agrège. Facette **données** de l'export, en pendant de l'export
/// **image** de l'IHM : le même [AgregationActivite#parEspece] alimente les deux.
///
/// Réutilise le [ServiceActivite] (feature `analyse`, toujours active) et le formateur pur
/// [ExportActiviteCsv], sans logique nouvelle. La commande **n'est pas** gouvernée par la fonctionnalité
/// `activite-nuit` : celle-ci conditionne l'*accès à la vue*, alors que l'agrégation exportée ici est une
/// capacité stable de `analyse`. Un passage sans contact produit un CSV d'en-têtes seuls (résultat valide).
@Command(
        name = "exporter-activite",
        description = "Exporte en CSV la courbe d'activité horaire d'un passage (contacts par tranche et par espèce).")
public final class ExporterActivite implements Callable<Integer> {

    /// Un passage, ou **tous** : les deux s'excluent, et l'un des deux est exigé. Sans cette exclusion,
    /// `--passage 3 --tout` aurait un sens ambigu que la commande trancherait en silence.
    @ArgGroup(multiplicity = "1")
    private Portee portee;

    /// Portée de l'export : une nuit précise, ou toutes celles de l'utilisateur (la vue transverse de
    /// l'écran, #2613).
    private static final class Portee {

        @Option(
                names = "--passage",
                paramLabel = "<id>",
                description = "Identifiant du passage dont exporter l'activité.")
        private Long passage;

        @Option(names = "--tout", description = "Exporte l'activité de tous les passages de l'utilisateur.")
        private boolean tout;
    }

    @Option(
            names = "--sortie",
            required = true,
            paramLabel = "<fichier>",
            description = "Chemin du fichier CSV à écrire.")
    private Path sortie;

    @Option(
            names = "--tranche",
            paramLabel = "<minutes>",
            defaultValue = "30",
            description = "Largeur de tranche horaire en minutes : 15, 30 ou 60. Défaut : ${DEFAULT-VALUE}.")
    private int trancheMinutes;

    @Option(
            names = "--format",
            paramLabel = "<format>",
            defaultValue = "csv",
            description = "Format d'export. Seul csv est disponible. Défaut : ${DEFAULT-VALUE}.")
    private String format;

    @Spec
    private CommandSpec spec;

    private final ServiceActivite service;

    /// Identifiant de l'utilisateur courant, pour la portée `--tout`. En `Provider` : la commande est
    /// instanciée par picocli **avant** la migration du schéma, et le résoudre au constructeur ouvrirait
    /// la base trop tôt.
    private final Provider<String> utilisateur;

    @Inject
    public ExporterActivite(ServiceActivite service, @Named("idUtilisateurCourant") Provider<String> utilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
    }

    @Override
    public Integer call() throws IOException {
        if (!"csv".equalsIgnoreCase(format)) {
            spec.commandLine().getErr().println("Format non pris en charge : " + format + ". Seul csv est disponible.");
            return ExitCode.USAGE;
        }
        Optional<LargeurTranche> tranche = LargeurTranche.deMinutes(trancheMinutes);
        if (tranche.isEmpty()) {
            spec.commandLine()
                    .getErr()
                    .println("Tranche invalide : " + trancheMinutes + " min. Valeurs acceptées : 15, 30 ou 60.");
            return ExitCode.USAGE;
        }

        List<ContactHoraire> contacts = portee.tout
                ? service.contactsDeLUtilisateur(utilisateur.get())
                : service.contactsDuPassage(portee.passage);
        List<LigneActivite> lignes = AgregationActivite.pourExport(contacts, tranche.get());
        Path ecrit = ExportActiviteCsv.ecrire(tranche.get(), lignes, sortie);
        spec.commandLine()
                .getOut()
                .println("Activité exportée : " + lignes.size() + " ligne(s) → " + ecrit.toAbsolutePath());
        return 0;
    }
}

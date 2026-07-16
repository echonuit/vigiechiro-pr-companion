package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `pre-check` (#1512) : affiche le **pré-check** consultatif d'une nuit avant sa vérification — trois
/// feux (couverture horaire R3, nombre de fichiers, cohérence du renommage R6) avec leur explication en
/// clair, plus un résumé de la ou des anomalies. **Consultatif (R13)** : jamais bloquant. Parité CLI du
/// bandeau de pré-check de M-Qualification (chantier #1524). Lecture pure ; `--json` pour les scripts.
@Command(
        name = "pre-check",
        description =
                "Affiche le pré-check consultatif d'une nuit (3 feux : couverture, nombre de fichiers, renommage).")
public final class PreCheck implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage dont on affiche le pré-check.")
    private Long idPassage;

    @Option(names = "--json", description = "Émet le pré-check au format JSON plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final ServiceQualification service;

    @Inject
    public PreCheck(ServiceQualification service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PreCheckNuit.Diagnostic diag = service.precheck(idPassage);
        PrintWriter sortie = spec.commandLine().getOut();
        if (json) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("passage", idPassage);
            objet.put("anomalie", diag.presenteUneAnomalie());
            objet.put("couvertureHoraire", feu(diag.couvertureHoraire(), diag.detailCouverture()));
            objet.put("nombreFichiers", feu(diag.nombreFichiers(), diag.detailNombre()));
            objet.put("coherenceRenommage", feu(diag.coherenceRenommage(), diag.detailRenommage()));
            objet.put("resume", diag.presenteUneAnomalie() ? diag.resumeAnomalie() : null);
            sortie.println(FormatJson.objet(objet));
            return 0;
        }
        sortie.println("Pré-check du passage #" + idPassage + " (consultatif) :");
        ligne(sortie, "Couverture horaire", diag.couvertureHoraire(), diag.detailCouverture());
        ligne(sortie, "Nombre de fichiers", diag.nombreFichiers(), diag.detailNombre());
        ligne(sortie, "Cohérence du renommage", diag.coherenceRenommage(), diag.detailRenommage());
        if (diag.presenteUneAnomalie()) {
            sortie.println("  Anomalie : " + diag.resumeAnomalie());
        }
        return 0;
    }

    private static void ligne(PrintWriter sortie, String libelle, PreCheckNuit.Feu feu, String detail) {
        sortie.println("  " + libelle + " : " + feu.name().toLowerCase(Locale.ROOT)
                + (detail == null || detail.isBlank() ? "" : " — " + detail));
    }

    private static Map<String, Object> feu(PreCheckNuit.Feu feu, String detail) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("feu", feu.name().toLowerCase(Locale.ROOT));
        objet.put("detail", detail);
        return objet;
    }
}

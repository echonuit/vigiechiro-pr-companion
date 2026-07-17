package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.Diagnostic;
import fr.univ_amu.iut.diagnostic.model.ExportDiagnostic;
import fr.univ_amu.iut.diagnostic.model.ServiceDiagnostic;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/// `diagnostiquer` (#1672) : **bilan matériel d'une nuit en lecture seule**, équivalent CLI de l'écran
/// M-Diagnostic.
///
/// Synthétise l'enregistreur, la série climatique (R20), la température de début de nuit, la
/// **cohérence horaire** (fenêtre nocturne au point d'écoute, #548), la **disponibilité GPS** du point,
/// et les anomalies / évènements du journal (R19) : de quoi décider si un enregistreur doit être révisé
/// sans ouvrir l'interface graphique. Même source que l'écran ([ServiceDiagnostic#diagnostiquer(Long)]).
///
/// `--json` pour l'enchaînement de scripts ; `--csv serie|anomalies` exporte la série climatique ou les
/// anomalies (P6-CA6) via [ExportDiagnostic].
@Command(
        name = "diagnostiquer",
        description = "Bilan matériel d'une nuit (climat, anomalies, cohérence horaire, GPS) en lecture seule.")
public final class Diagnostiquer implements Callable<Integer> {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant technique du passage à diagnostiquer.")
    private Long idPassage;

    @Option(
            names = "--json",
            description = "Émet le bilan au format JSON (pour l'enchaînement de scripts) plutôt qu'en texte.")
    private boolean json;

    @Option(
            names = "--csv",
            paramLabel = "<serie|anomalies>",
            description = "Exporte en CSV la série climatique (serie) ou les anomalies (anomalies) au lieu du bilan.")
    private String csv;

    @Spec
    private CommandSpec spec;

    private final ServiceDiagnostic serviceDiagnostic;

    @Inject
    public Diagnostiquer(ServiceDiagnostic serviceDiagnostic) {
        this.serviceDiagnostic = Objects.requireNonNull(serviceDiagnostic, "serviceDiagnostic");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        // Lève RegleMetierException si le passage ou sa session est introuvable → échec d'exécution (code 1).
        Diagnostic diagnostic = serviceDiagnostic.diagnostiquer(idPassage);
        if (csv != null) {
            sortie.println(exportCsv(diagnostic));
        } else {
            sortie.println(
                    json ? FormatJson.objet(projeter(idPassage, diagnostic)) : rendreTexte(idPassage, diagnostic));
        }
        return 0;
    }

    /// Exporte la cible `--csv` demandée via [ExportDiagnostic]. Une valeur inconnue est une mauvaise
    /// invocation (code 2), comme picocli le fait pour un argument non reconnu.
    private String exportCsv(Diagnostic diagnostic) {
        return switch (csv.toLowerCase(Locale.ROOT)) {
            case "serie" -> ExportDiagnostic.climatVersCsv(diagnostic.climat());
            case "anomalies" -> ExportDiagnostic.anomaliesVersCsv(diagnostic.anomalies());
            default ->
                throw new ParameterException(
                        spec.commandLine(), "Valeur --csv invalide : " + csv + " (attendu : serie | anomalies).");
        };
    }

    /// Rendu **texte** du bilan : une donnée par ligne (libellés alignés), suivie du détail des anomalies
    /// et des évènements. Fonction pure (sans base ni effet de bord).
    static String rendreTexte(Long idPassage, Diagnostic d) {
        StringBuilder t =
                new StringBuilder("Diagnostic du passage #").append(idPassage).append('\n');
        ligne(t, "Enregistreur", "PR " + d.numeroSerieEnregistreur());
        ligne(
                t,
                "Relevé climatique",
                d.releveClimatiqueAbsent() ? "absent (R20)" : d.climat().nombreMesures() + " mesures T°/hygrométrie");
        ligne(t, "T° début de nuit", Formats.temperatureLisible(d.temperatureDebutNuit()));
        ligne(t, "Cohérence horaire", coherenceLisible(d.coherenceHoraire()));
        ligne(t, "GPS du point", d.coordonneesGpsDisponibles() ? "disponible" : "non renseigné");
        ligne(t, "Anomalies (R19)", String.valueOf(d.anomalies().anomalies().size()));
        for (String anomalie : d.anomalies().anomalies()) {
            t.append("    - ").append(anomalie).append('\n');
        }
        ligne(t, "Évènements", String.valueOf(d.anomalies().evenements().size()));
        for (String evenement : d.anomalies().evenements()) {
            t.append("    - ").append(evenement).append('\n');
        }
        return t.toString().stripTrailing();
    }

    /// Libellé de la cohérence horaire : fenêtre nocturne au point et repère « hors nuit » (#548), ou
    /// « indisponible » quand elle n'a pas pu être calculée.
    private static String coherenceLisible(CoherenceHoraire coherence) {
        if (!coherence.disponible()) {
            return "indisponible (GPS ou horaires manquants, ou latitude polaire)";
        }
        String fenetre =
                "nuit " + HEURE.format(coherence.coucherSoleil()) + " → " + HEURE.format(coherence.leverSoleil());
        return coherence.aUnEcart() ? fenetre + ", hors nuit (une partie diurne)" : fenetre + ", cohérent";
    }

    /// Ajoute `  <libellé aligné> : <valeur>` (libellé cadré à 19 caractères) suivi d'un retour ligne.
    private static void ligne(StringBuilder texte, String libelle, String valeur) {
        texte.append(String.format("  %-19s : %s", libelle, valeur)).append('\n');
    }

    /// Projection JSON (clés stables pour les scripts). Température et coordonnées en nombres bruts ;
    /// coucher/lever à `null` quand la cohérence est indisponible ; anomalies et évènements en tableaux.
    static Map<String, Object> projeter(Long idPassage, Diagnostic d) {
        CoherenceHoraire coherence = d.coherenceHoraire();
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", idPassage);
        objet.put("enregistreur", d.numeroSerieEnregistreur());
        objet.put("releveClimatiqueAbsent", d.releveClimatiqueAbsent());
        objet.put("nombreMesures", d.climat().nombreMesures());
        objet.put("temperatureDebutNuitCelsius", d.temperatureDebutNuit());
        objet.put("coherenceHoraireDisponible", coherence.disponible());
        objet.put("coucherSoleil", coherence.disponible() ? HEURE.format(coherence.coucherSoleil()) : null);
        objet.put("leverSoleil", coherence.disponible() ? HEURE.format(coherence.leverSoleil()) : null);
        objet.put("horsNuit", coherence.disponible() && coherence.aUnEcart());
        objet.put("gpsDisponible", d.coordonneesGpsDisponibles());
        objet.put("anomalies", d.anomalies().anomalies());
        objet.put("evenements", d.anomalies().evenements());
        return objet;
    }
}

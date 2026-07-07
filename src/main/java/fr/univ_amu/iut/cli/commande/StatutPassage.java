package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `statut-passage` (#618) : **inspecte un passage en lecture seule** (aucun effet de bord). Synthétise le
/// statut du workflow, le verdict, les volumes et séquences de la nuit, et la présence de résultats
/// Tadarida — de quoi savoir « où en est ce passage ? » sans ouvrir l'interface graphique.
///
/// La synthèse provient de [ServicePassage#detailPassage(Long)] ; la présence de résultats Tadarida est lue
/// à part via [ResultatsIdentificationDao] (le CLI est le point de composition entre les features `passage`
/// et `validation`, comme `exporter-vu`). Option `--json` pour l'enchaînement de scripts.
@Command(
        name = "statut-passage",
        description = "Inspecte un passage en lecture seule : statut, verdict, volumes, séquences, résultats Tadarida.")
public final class StatutPassage implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant technique du passage à inspecter.")
    private Long idPassage;

    @Option(
            names = "--json",
            description = "Émet la synthèse au format JSON (pour l'enchaînement de scripts) plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final ServicePassage servicePassage;
    private final ResultatsIdentificationDao resultatsDao;

    @Inject
    public StatutPassage(ServicePassage servicePassage, ResultatsIdentificationDao resultatsDao) {
        this.servicePassage = Objects.requireNonNull(servicePassage, "servicePassage");
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        // Lève RegleMetierException si le passage est introuvable → traité en échec d'exécution (code 1).
        DetailPassage detail = servicePassage.detailPassage(idPassage);
        Optional<ResultatsIdentification> tadarida = resultatsDao.findByPassage(idPassage);

        sortie.println(
                json
                        ? FormatJson.objet(projeter(idPassage, detail, tadarida))
                        : rendreTexte(idPassage, detail, tadarida));
        return 0;
    }

    /// Rendu **texte** de la synthèse : une donnée par ligne, libellés alignés. Fonction pure (sans base
    /// ni effet de bord) : construit et renvoie le bloc, sans le retour ligne final (ajouté par l'appelant).
    static String rendreTexte(Long idPassage, DetailPassage detail, Optional<ResultatsIdentification> tadarida) {
        StringBuilder texte = new StringBuilder("Passage #").append(idPassage).append('\n');
        ligne(texte, "Protocole", "année " + detail.annee() + ", passage n°" + detail.numeroPassage());
        ligne(
                texte,
                "Nuit",
                detail.dateEnregistrement() + "  (" + detail.heureDebut() + " → " + detail.heureFin() + ")");
        ligne(texte, "Enregistreur", detail.idEnregistreur());
        ligne(texte, "Statut", detail.statut().libelle());
        ligne(
                texte,
                "Verdict",
                detail.verdict() == null ? "en attente" : detail.verdict().libelle());
        ligne(texte, "Dépôt", detail.deposeLe() == null ? "non déposé" : "déposé le " + detail.deposeLe());
        ligne(
                texte,
                "Volumes",
                "bruts " + Formats.octetsLisibles(detail.volumeOriginauxOctets()) + ", séquences "
                        + Formats.octetsLisibles(detail.volumeSequencesOctets()));
        ligne(
                texte,
                "Séquences",
                detail.nombreSequences() + " (durée audible " + Formats.dureeLisible(detail.dureeAudibleSecondes())
                        + ")");
        ligne(texte, "Météo", meteoLisible(detail.meteo()));
        ligne(
                texte,
                "Résultats Tadarida",
                tadarida.map(r -> "oui (" + r.formatDetecte() + ", importé le " + r.dateImport() + ")")
                        .orElse("non"));
        return texte.toString().stripTrailing();
    }

    /// Ajoute `  <libellé aligné> : <valeur>` (libellé cadré à 19 caractères) suivi d'un retour ligne.
    private static void ligne(StringBuilder texte, String libelle, String valeur) {
        texte.append(String.format("  %-19s : %s", libelle, valeur)).append('\n');
    }

    /// Résumé météo compact (grandeurs présentes uniquement), ou `non renseignée` si le relevé est vide.
    /// N'appelle [Formats#temperatureLisible(Double)] que sur des valeurs non nulles.
    private static String meteoLisible(MeteoReleve meteo) {
        List<String> grandeurs = new ArrayList<>();
        if (meteo.temperatureDebutNuit() != null) {
            grandeurs.add("début " + Formats.temperatureLisible(meteo.temperatureDebutNuit()));
        }
        if (meteo.temperatureFinNuit() != null) {
            grandeurs.add("fin " + Formats.temperatureLisible(meteo.temperatureFinNuit()));
        }
        if (meteo.vent() != null) {
            grandeurs.add(String.format(Locale.FRANCE, "vent %.0f km/h", meteo.vent()));
        }
        if (meteo.couvertureNuageuse() != null) {
            grandeurs.add(String.format(Locale.FRANCE, "nuages %.0f %%", meteo.couvertureNuageuse()));
        }
        return grandeurs.isEmpty() ? "non renseignée" : String.join(", ", grandeurs);
    }

    /// Projection JSON (clés stables pour les scripts). Nombres émis sans guillemets, dates/libellés en
    /// chaînes, `verdict`/`deposeLe`/`cheminResultatsTadarida` valant `null` quand la donnée manque.
    static Map<String, Object> projeter(
            Long idPassage, DetailPassage detail, Optional<ResultatsIdentification> tadarida) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", idPassage);
        objet.put("annee", detail.annee());
        objet.put("numeroPassage", detail.numeroPassage());
        objet.put("date", detail.dateEnregistrement());
        objet.put("heureDebut", detail.heureDebut());
        objet.put("heureFin", detail.heureFin());
        objet.put("enregistreur", detail.idEnregistreur());
        objet.put("statut", detail.statut().libelle());
        objet.put("verdict", detail.verdict() == null ? null : detail.verdict().libelle());
        objet.put("deposeLe", detail.deposeLe());
        objet.put("volumeOriginauxOctets", detail.volumeOriginauxOctets());
        objet.put("volumeSequencesOctets", detail.volumeSequencesOctets());
        objet.put("nombreSequences", detail.nombreSequences());
        objet.put("dureeAudibleSecondes", detail.dureeAudibleSecondes());
        objet.put("resultatsTadarida", tadarida.isPresent());
        objet.put(
                "cheminResultatsTadarida",
                tadarida.map(ResultatsIdentification::cheminFichier).orElse(null));
        return objet;
    }
}

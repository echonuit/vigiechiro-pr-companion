package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Commande `lister-sauvegardes` (#3197) : ce que `sauvegardes/` contient, et ce que ça pèse.
///
/// L'application écrit un **filet complet avant chaque migration** de schéma et n'en supprime jamais
/// aucun. C'est délibéré - ils sont le filet de l'utilisateur, elle n'a pas à décider à sa place quand
/// il n'en a plus besoin (ADR 0048). Mais elle lui demandait un ménage **qu'elle ne rendait visible
/// nulle part** : ni le nombre, ni la place occupée, ni même la date de chacun.
///
/// Cette commande **observe** : elle ne supprime rien et ne conseille rien. Le total est la ligne qui
/// manquait, parce que c'est lui qui rend la question actionnable - un filet pèse ce que pèse la base,
/// multiplié par le nombre de migrations vécues.
///
/// La suppression existe, mais séparément et **à la demande** : `supprimer-sauvegarde`.
@Command(
        name = "lister-sauvegardes",
        description = "Liste les sauvegardes et les filets de migration présents, avec leur date, "
                + "leur taille et le total occupé.")
public final class ListerSauvegardes implements Callable<Integer> {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Option(
            names = "--dossier",
            paramLabel = "<dossier>",
            description = "Dossier à inventorier. Par défaut : <workspace>/sauvegardes.")
    private Path dossier;

    @Option(names = "--json", description = "Sortie JSON (une ligne par sauvegarde) au lieu du tableau.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceSauvegarde> service;

    @Inject
    public ListerSauvegardes(Provider<ServiceSauvegarde> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        Path inventorie = dossier != null ? dossier : service.get().dossierParDefaut();
        List<InventaireSauvegardes.Entree> entrees = InventaireSauvegardes.lire(inventorie);

        if (json) {
            sortie.println(FormatJson.tableau(
                    entrees.stream().map(ListerSauvegardes::ligneJson).toList()));
            return 0;
        }
        if (entrees.isEmpty()) {
            // Une installation qui n'a jamais migré ni sauvegardé : un état, pas une anomalie (ADR 0007).
            sortie.println("Aucune sauvegarde dans " + inventorie + ".");
            return 0;
        }
        sortie.println(entrees.size() + " sauvegarde(s) dans " + inventorie + " :");
        for (InventaireSauvegardes.Entree entree : entrees) {
            sortie.println(String.format(
                    "  %-16s %-10s %s",
                    DATE.format(entree.date().atZone(ZoneId.systemDefault())),
                    Formats.octetsLisibles(entree.octets()),
                    entree.nom()));
        }
        sortie.println("  " + "-".repeat(28));
        sortie.println(
                String.format("  %-16s %-10s total", "", Formats.octetsLisibles(InventaireSauvegardes.total(entrees))));
        return 0;
    }

    private static Map<String, Object> ligneJson(InventaireSauvegardes.Entree entree) {
        Map<String, Object> ligne = new LinkedHashMap<>();
        ligne.put("nom", entree.nom());
        ligne.put("date", entree.date().toString());
        ligne.put("octets", entree.octets());
        ligne.put("nature", entree.nature().name().toLowerCase(Locale.ROOT));
        return ligne;
    }
}

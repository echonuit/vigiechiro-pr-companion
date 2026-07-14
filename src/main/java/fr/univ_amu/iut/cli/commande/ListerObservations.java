package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.validation.model.CriteresRevue;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.SelectionObservations;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `lister-observations` (#1311) : la **surface de découverte** de la revue en ligne de commande.
///
/// Sans elle, aucun geste de revue n'est utilisable : les commandes désignent leurs cibles par
/// **identifiant** (`--observation 12,13`), et rien ne permettait de connaître ces identifiants sans ouvrir
/// la base SQLite à la main. C'était déjà le cas de `discussion --observation <id>` (#1418), livrée
/// aveugle.
///
/// Elle est aussi le **filet des gestes par filtre** : `lister-observations --passage 3 --statut a-revoir`
/// montre **exactement** ce que `valider-observations --passage 3 --statut a-revoir` toucherait, parce que
/// c'est le même [SelectionObservations] qui choisit. On regarde, puis on agit.
@Command(
        name = "lister-observations",
        description = "Liste les observations d'un passage (identifiant, fichier, avis, statut), avec filtres.")
public final class ListerObservations implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du passage dont lister les observations.")
    private long passage;

    @Option(
            names = "--statut",
            paramLabel = "<statut>",
            description = "Ne garde que ce statut : ${COMPLETION-CANDIDATES}.")
    private StatutObservation statut;

    @Option(
            names = "--taxon",
            paramLabel = "<code>",
            description = "Ne garde que les observations dont Tadarida propose ce taxon (ex. Pippip).")
    private String taxon;

    @Option(
            names = "--douteux",
            description = "Ne garde que les observations marquées douteuses. Sans l'option : les deux.")
    private boolean douteux;

    @Option(
            names = "--reference",
            description = "Ne garde que les observations du corpus de référence. Sans l'option : les deux.")
    private boolean reference;

    @Option(
            names = "--certitude",
            paramLabel = "<certitude>",
            description = "Ne garde que cette certitude observateur : ${COMPLETION-CANDIDATES}.")
    private Certitude certitude;

    @Option(
            names = "--json",
            description = "Émet la liste au format JSON (pour l'enchaînement de scripts) plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    /// `Provider` : picocli instancie **toutes** les sous-commandes avant que le schéma ne soit migré.
    /// Tirer le service ici ouvrirait la base trop tôt.
    private final Provider<SelectionObservations> selection;

    @Inject
    public ListerObservations(Provider<SelectionObservations> selection) {
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    /// Les filtres tels que l'utilisateur les a posés. Les drapeaux picocli sont **binaires** (présent /
    /// absent) alors que le critère est **ternaire** : on traduit donc « absent » en `null` (« ne filtre pas
    /// là-dessus ») et non en `false` (« seulement les non-douteuses »), qui serait un contresens.
    CriteresRevue criteres() {
        return new CriteresRevue(
                statut, taxon, douteux ? Boolean.TRUE : null, reference ? Boolean.TRUE : null, certitude);
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<LigneObservationAudio> lignes = selection.get().lignes(passage, criteres());

        if (json) {
            sortie.println(FormatJson.tableau(
                    lignes.stream().map(ListerObservations::champs).toList()));
            return 0;
        }
        if (lignes.isEmpty()) {
            sortie.println("Aucune observation ne correspond aux filtres pour le passage " + passage + ".");
            return 0;
        }
        sortie.printf(
                "%-8s %-26s %-12s %-22s %-12s %-9s %s%n",
                "ID", "FICHIER", "TADARIDA", "VOTRE TAXON", "STATUT", "CERTITUDE", "DRAPEAUX");
        for (LigneObservationAudio ligne : lignes) {
            sortie.printf(
                    "%-8d %-26s %-12s %-22s %-12s %-9s %s%n",
                    ligne.idObservation(),
                    texte(ligne.nomFichier()),
                    texte(ligne.taxonTadarida()),
                    texte(ligne.taxonObservateur()),
                    ligne.statut().name(),
                    ligne.certitude() != null ? ligne.certitude().name() : "-",
                    drapeaux(ligne));
        }
        sortie.println();
        sortie.println(lignes.size() + " observation(s). Ces identifiants alimentent les gestes de revue "
                + "(valider-observations, corriger-observations, discussion…).");
        return 0;
    }

    /// Ce qui se voit d'un coup d'œil sans encombrer une colonne : douteux, référence, avis d'un validateur,
    /// fil ouvert.
    private static String drapeaux(LigneObservationAudio ligne) {
        StringBuilder marques = new StringBuilder();
        if (ligne.douteux()) {
            marques.append("douteux ");
        }
        if (ligne.reference()) {
            marques.append("reference ");
        }
        if (ligne.trancheeParUnValidateur()) {
            marques.append(ligne.validateurEnDesaccord() ? "validateur:desaccord " : "validateur:accord ");
        }
        if (ligne.aUnFil()) {
            marques.append("fil:").append(ligne.nbMessages()).append(' ');
        }
        return marques.isEmpty() ? "-" : marques.toString().trim();
    }

    private static Map<String, Object> champs(LigneObservationAudio ligne) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("id", ligne.idObservation());
        champs.put("fichier", ligne.nomFichier());
        champs.put("taxonTadarida", ligne.taxonTadarida());
        champs.put("probTadarida", ligne.probTadarida());
        champs.put("taxonObservateur", ligne.taxonObservateur());
        champs.put("certitude", ligne.certitude() != null ? ligne.certitude().name() : null);
        champs.put("taxonValidateur", ligne.taxonValidateur());
        champs.put(
                "certitudeValidateur",
                ligne.certitudeValidateur() != null
                        ? ligne.certitudeValidateur().name()
                        : null);
        champs.put("statut", ligne.statut().name());
        champs.put("douteux", ligne.douteux());
        champs.put("reference", ligne.reference());
        champs.put("messages", ligne.nbMessages());
        return champs;
    }

    private static String texte(String valeur) {
        return valeur != null ? valeur : "-";
    }
}

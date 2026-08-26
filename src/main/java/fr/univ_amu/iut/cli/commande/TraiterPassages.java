package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.GesteAttenduCli;
import fr.univ_amu.iut.cli.model.RegistrePassages;
import fr.univ_amu.iut.cli.model.RegistrePassages.LignePassage;
import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.IssueTraitement;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.MoteurTraitementGroupe;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.ResultatTraitementGroupe;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `traiter-passages` (#2357, clôture du lot 3) : applique **une** action à **plusieurs** passages, avec
/// l'écran d'éligibilité et le compte rendu par passage, équivalent en ligne de commande du menu
/// « Traiter la sélection » de « Carte & passages ».
///
/// Les quatre gestes existent déjà un par un, et un terminal sait boucler ; ce qui manquait est de
/// **savoir à l'avance lesquels sont éligibles**. Les règles du lot 3 ([ActionGroupee#motifNonEligible])
/// lisent l'état du dépôt, le rattachement à une participation et la présence de résultats, rien de tout
/// cela n'étant dans `lister-passages`. Un script qui boucle ne peut donc qu'essayer pour voir, et
/// l'échec d'une nuit y ressemble à l'échec du lot. Ici les écartés sont annoncés avec leur motif sans
/// être tentés, un échec n'arrête pas les suivants, et chaque passage a sa ligne.
///
/// Ni `--forcer` ni `--remplacer` : ces options valent sur les commandes unitaires, où un humain décide
/// pour **une** nuit. Relancer un calcul détruit les observations d'une nuit déposée en archives, et
/// remplacer un jeu de résultats touche à ce que l'observateur a validé.
///
/// Codes de sortie : **`0`** dès qu'aucun passage n'a échoué, les écartés en faisant partie, rejouer un
/// lot déjà traité étant le cas idempotent ; **`1`** si au moins un a échoué ; **`2`** pour un refus en
/// amont, action indisponible ou identifiant inconnu.
@Command(
        name = "traiter-passages",
        description = "Applique une action à plusieurs passages : annonce les écartés, puis rend compte de chacun.")
public final class TraiterPassages implements Callable<Integer> {

    /// Les quatre actions groupées, telles qu'elles se nomment en ligne de commande.
    public enum Action {
        PREPARER_DEPOT("preparer-depot"),
        TELEVERSER("televerser"),
        IMPORTER_RESULTATS("importer-resultats"),
        DECLENCHER_CALCUL("declencher-calcul");

        private final String etiquette;

        Action(String etiquette) {
            this.etiquette = etiquette;
        }

        public String etiquette() {
            return etiquette;
        }

        /// @throws IllegalArgumentException nom inconnu ; picocli en fait un refus d'usage (exit 2)
        public static Action depuis(String nom) {
            for (Action action : values()) {
                if (action.etiquette.equalsIgnoreCase(nom)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("action inconnue : " + nom + " (attendu : preparer-depot,"
                    + " televerser, importer-resultats, declencher-calcul)");
        }
    }

    /// Convertit `--action preparer-depot` : les constantes Java ne peuvent pas porter de tiret, et
    /// imposer `PREPARER_DEPOT` en ligne de commande jurerait avec le reste de la surface.
    public static final class ConvertisseurAction implements ITypeConverter<Action> {
        @Override
        public Action convert(String valeur) {
            return Action.depuis(valeur);
        }
    }

    @Option(
            names = "--action",
            required = true,
            paramLabel = "<action>",
            converter = ConvertisseurAction.class,
            description =
                    "Action à appliquer : preparer-depot, televerser, importer-resultats," + " declencher-calcul.")
    private Action action;

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage à traiter. Répéter l'option pour en viser plusieurs.")
    private List<Long> idsPassages;

    @Option(
            names = "--json",
            description =
                    "Émet le compte rendu au format JSON (pour l'enchaînement de scripts) plutôt qu'en" + " texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final RegistrePassages registre;
    private final Optional<ActionGroupee> preparerDepot;
    private final Optional<ActionGroupee> televerser;
    private final Optional<ActionGroupee> importerResultats;
    private final Optional<ActionGroupee> declencherCalcul;

    /// Les quatre actions sont **optionnelles** : `lot` et `import-vigiechiro` sont désactivables
    /// (ADR 0003). Absente, l'action se refuse en disant laquelle réactiver, plutôt que de faire
    /// échouer l'injecteur entier.
    @Inject
    public TraiterPassages(
            RegistrePassages registre,
            @Named("action.preparerDepot") Optional<ActionGroupee> preparerDepot,
            @Named("action.televerser") Optional<ActionGroupee> televerser,
            @Named("action.importerResultats") Optional<ActionGroupee> importerResultats,
            @Named("action.declencherCalcul") Optional<ActionGroupee> declencherCalcul) {
        this.registre = Objects.requireNonNull(registre, "registre");
        this.preparerDepot = Objects.requireNonNull(preparerDepot, "preparerDepot");
        this.televerser = Objects.requireNonNull(televerser, "televerser");
        this.importerResultats = Objects.requireNonNull(importerResultats, "importerResultats");
        this.declencherCalcul = Objects.requireNonNull(declencherCalcul, "declencherCalcul");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        try {
            ActionGroupee groupee = actionChoisie();
            List<CiblePassage> cibles = cibles();
            // La rédaction des échecs est celle du terminal (ADR 2635) : le modèle dit ce qui manque,
            // GesteAttenduCli nomme la commande qui le lève - jamais un chemin de menu.
            ResultatTraitementGroupe resultat = new MoteurTraitementGroupe(GesteAttenduCli::message)
                    .executer(groupee, cibles, JetonAnnulation.neutre(), journal(sortie));
            sortie.println(json ? enJson(resultat) : enTexte(resultat));
            return resultat.echecs() == 0 ? 0 : 1;
        } catch (RegleMetierException refus) {
            spec.commandLine().getErr().println("Traitement impossible : " + GesteAttenduCli.message(refus));
            return 2;
        }
    }

    /// Le journal du moteur, ligne à ligne, **seulement en mode texte** : en `--json`, la sortie standard
    /// doit rester analysable d'un bloc.
    private Consumer<String> journal(PrintWriter sortie) {
        return json ? ligne -> {} : sortie::println;
    }

    private ActionGroupee actionChoisie() {
        Optional<ActionGroupee> choisie =
                switch (action) {
                    case PREPARER_DEPOT -> preparerDepot;
                    case TELEVERSER -> televerser;
                    case IMPORTER_RESULTATS -> importerResultats;
                    case DECLENCHER_CALCUL -> declencherCalcul;
                };
        // Le refus nomme le besoin (ADR 2635) ; GesteAttenduCli dira franchement que cela se règle dans
        // l'application, les fonctionnalités n'ayant pas de commande.
        return choisie.orElseThrow(() -> new RegleMetierException(
                "L'action « " + action.etiquette() + " » n'est pas disponible.",
                new Besoin.Fonctionnalite(fournisseurDe(action))));
    }

    /// La fonctionnalité, telle qu'elle se nomme dans les réglages, dont l'action dépend.
    private static String fournisseurDe(Action action) {
        return action == Action.IMPORTER_RESULTATS ? "Import depuis Vigie-Chiro" : "Préparation du dépôt";
    }

    /// Les cibles, dans **l'ordre demandé**, avec la désignation lisible qui préfixera chaque ligne. Un
    /// identifiant inconnu arrête tout : traiter dix-neuf nuits sur vingt en passant la vingtième sous
    /// silence serait le pire des deux comportements.
    private List<CiblePassage> cibles() {
        Map<Long, LignePassage> connus = new LinkedHashMap<>();
        registre.lister().forEach(ligne -> connus.put(ligne.idPassage(), ligne));
        List<Long> inconnus =
                idsPassages.stream().filter(id -> !connus.containsKey(id)).toList();
        if (!inconnus.isEmpty()) {
            throw new RegleMetierException("Passage(s) introuvable(s) : " + inconnus);
        }
        return idsPassages.stream()
                .map(connus::get)
                .map(ligne -> new CiblePassage(ligne.idPassage(), designation(ligne)))
                .toList();
    }

    /// Même désignation que dans l'application, pour qu'un compte rendu se lise pareil des deux côtés.
    private static String designation(LignePassage ligne) {
        return ligne.carre() + " / " + ligne.codePoint() + " / " + ligne.annee() + " n°" + ligne.numeroPassage();
    }

    private static String enTexte(ResultatTraitementGroupe resultat) {
        StringBuilder texte = new StringBuilder(resultat.resume());
        for (IssueTraitement issue : resultat.issues()) {
            texte.append("\n  ")
                    .append(issue.cible().designation())
                    .append(" : ")
                    .append(sort(issue));
        }
        return texte.toString();
    }

    private static String sort(IssueTraitement issue) {
        return switch (issue.statut()) {
            case REUSSI -> "fait";
            case ECARTE -> "écarté (" + issue.motif() + ")";
            case ECHEC -> "échec : " + issue.motif();
            case NON_TRAITE -> "non traité";
        };
    }

    private static String enJson(ResultatTraitementGroupe resultat) {
        return FormatJson.tableau(
                resultat.issues().stream().map(TraiterPassages::enObjet).toList());
    }

    /// Projection JSON d'une issue (clés stables pour les scripts).
    private static Map<String, Object> enObjet(IssueTraitement issue) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", issue.cible().idPassage());
        objet.put("designation", issue.cible().designation());
        objet.put("statut", issue.statut().name());
        objet.put("motif", issue.motif().isEmpty() ? null : issue.motif());
        return objet;
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.validation.model.CriteresRevue;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.FiltresLieu;
import fr.univ_amu.iut.validation.model.FiltresProbabilite;
import fr.univ_amu.iut.validation.model.FiltresRevue;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import fr.univ_amu.iut.validation.model.SelectionObservations;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
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

    /// Restreint aux observations d'un ou plusieurs lieux (#2971). Répétable : chaque occurrence ajoute
    /// un lieu, comme cocher une case de plus dans la puce « Lieu » de l'écran.
    @Option(
            names = "--lieu",
            paramLabel = "<lieu>",
            description = "Ne garde que les observations de ce lieu (commune, carré, nom de site ou "
                    + "point). Correspondance partielle, casse et accents ignorés. Répétable pour en "
                    + "cumuler plusieurs.")
    private List<String> lieux = new ArrayList<>();

    /// Seuil de probabilité Tadarida (#2971), à l'échelle 0..1 comme la sortie de `lister-observations`.
    @Option(
            names = "--proba-min",
            paramLabel = "<0..1>",
            description = "Ne garde que les détections dont la probabilité Tadarida atteint ce seuil "
                    + "(celles qui n'en ont pas sont conservées). Échelle 0 à 1 : 90 % s'écrit 0.9.")
    private Double probaMin;

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
            names = "--a-enjeu",
            description = "Ne garde que les observations d'espèces prioritaires du Plan National d'Actions "
                    + "Chiroptères. Sans l'option : les deux.")
    private boolean aEnjeu;

    @Option(
            names = "--json",
            description = "Émet la liste au format JSON (pour l'enchaînement de scripts) plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    /// Instantané du référentiel, résolu au moment de l'exécution (jamais à la construction).
    private MarqueurEspecesAEnjeu marqueurEnjeu;

    /// `Provider` : picocli instancie **toutes** les sous-commandes avant que le schéma ne soit migré.
    /// Tirer le service ici ouvrirait la base trop tôt.
    private final Provider<SelectionObservations> selection;

    /// Repère des **espèces à enjeu** (#2353), pour le drapeau de la sortie texte et le champ JSON.
    /// Différé pour la même raison que la sélection : picocli instancie toutes les sous-commandes avant
    /// la migration du schéma, or le référentiel se lit en base.
    ///
    /// ⚠️ Un [java.util.function.Supplier] et non un `Provider` (#3228). Ce champ n'est **pas** un point
    /// d'injection : il dérive du `Provider` reçu au constructeur. Or `com.google.inject.Provider` n'est
    /// pas annoté `@FunctionalInterface` et hérite de `jakarta.inject.Provider` : `javac` l'accepte
    /// comme cible de lambda, **ecj le refuse**. L'IDE écrivant dans le même `target/classes` que Maven,
    /// la classe en erreur y restait et faisait échouer toute construction de la CLI, très loin d'ici.
    private final Supplier<MarqueurEspecesAEnjeu> marqueur;

    @Inject
    public ListerObservations(
            Provider<SelectionObservations> selection, Provider<EspecesPrioritaires> especesPrioritaires) {
        this.selection = Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(especesPrioritaires, "especesPrioritaires");
        this.marqueur = () -> new MarqueurEspecesAEnjeu(especesPrioritaires.get());
    }

    /// Les filtres tels que l'utilisateur les a posés. Les drapeaux picocli sont **binaires** (présent /
    /// absent) alors que le critère est **ternaire** : on traduit donc « absent » en `null` (« ne filtre pas
    /// là-dessus ») et non en `false` (« seulement les non-douteuses »), qui serait un contresens.
    @Option(
            names = "--taxon-parent",
            paramLabel = "<taxon>",
            description = "Restreint à une catégorie taxonomique (Chiroptères, Oiseaux…). Correspondance "
                    + "partielle, insensible à la casse et aux accents.")
    private String taxonParent;

    @Option(
            names = "--non-identifie",
            description = "Ne garde que les séquences sans proposition Tadarida, à identifier à la main.")
    private boolean nonIdentifie;

    @Option(
            names = "--heure-debut",
            paramLabel = "<0-23>",
            description = "Début de la plage horaire de capture. Va avec --heure-fin ; la plage traverse "
                    + "minuit si le début est plus tard que la fin (21 → 6 retient la nuit).")
    private Integer heureDebut;

    @Option(
            names = "--heure-fin",
            paramLabel = "<0-23>",
            description = "Fin de la plage horaire de capture. Va avec --heure-debut.")
    private Integer heureFin;

    CriteresRevue criteres() {
        return new CriteresRevue(
                statut,
                taxon,
                douteux ? Boolean.TRUE : null,
                reference ? Boolean.TRUE : null,
                certitude,
                aEnjeu ? Boolean.TRUE : null);
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        // Pas de garde de passage vide ici, à dessein (ADR 3269, cas écarté) : `--taxon-parent` refuse
        // même sur un ensemble vide, ce que #3082 a tranché sur cette commande précisément. Sur un
        // `--passage` qui n'existe pas, un succès silencieux masquerait que le passage est inconnu.
        List<LigneObservationAudio> retenues =
                FiltresLieu.parLieu(selection.get().lignes(passage, criteres()), lieux);
        retenues = FiltresRevue.parTaxonParent(retenues, taxonParent);
        retenues = FiltresRevue.nonIdentifiees(retenues, nonIdentifie);
        List<LigneObservationAudio> avantSeuil = FiltresRevue.parPlageHoraire(retenues, heureDebut, heureFin);
        List<LigneObservationAudio> lignes = FiltresProbabilite.parSeuilMinimal(avantSeuil, probaMin);
        marqueurEnjeu = marqueur.get();

        if (json) {
            sortie.println(FormatJson.tableau(lignes.stream().map(this::champs).toList()));
            return 0;
        }
        if (lignes.isEmpty()) {
            sortie.println("Aucune observation ne correspond aux filtres pour le passage " + passage + ".");
            // Le seuil est le seul filtre qui peut légitimement tout écarter : on dit alors de combien
            // il faudrait l'abaisser, plutôt que de laisser l'utilisateur deviner (#2971).
            FiltresProbabilite.avertissementSeuilTropHaut(avantSeuil, probaMin).ifPresent(sortie::println);
            return 0;
        }
        // Le lieu du passage, en tête (#3350). La commande offrait `--lieu` sans jamais montrer de
        // lieu : le filtre portait sur ce que la sortie taisait, et l'utilisateur n'avait aucun moyen
        // de vérifier qu'il avait retenu ce qu'il croyait. Une seule ligne suffit ici, `--passage`
        // étant obligatoire : toutes les observations listées partagent le même point.
        lieuDuPassage(lignes).ifPresent(sortie::println);
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

    /// Le lieu du passage listé, tel que la puce « Lieu » l'écrit : « 640380 · A1 · Ahetze » (#3350).
    ///
    /// Rendu **vide** plutôt qu'approximatif si aucune ligne ne porte de carré : il n'y a alors rien à
    /// affirmer, et une ligne « lieu : - » ferait croire à une donnée manquante là où c'est la question
    /// qui n'a pas de sens.
    private Optional<String> lieuDuPassage(List<LigneObservationAudio> lignes) {
        return lignes.stream()
                .findFirst()
                .map(ligne -> LieuQualifie.qualifier(
                        LieuQualifie.qualifier(ligne.numeroCarre(), ligne.codePoint()), ligne.commune()))
                .filter(lieu -> !lieu.isBlank())
                .map(lieu -> "Lieu : " + lieu);
    }

    /// Ce qui se voit d'un coup d'œil sans encombrer une colonne : douteux, référence, avis d'un validateur,
    /// fil ouvert.
    private String drapeaux(LigneObservationAudio ligne) {
        StringBuilder marques = new StringBuilder();
        if (ligne.douteux()) {
            marques.append("douteux ");
        }
        if (ligne.reference()) {
            marques.append("reference ");
        }
        if (marqueurEnjeu.aEnjeu(ligne.taxonRetenu())) {
            marques.append("enjeu ");
        }
        if (ligne.trancheeParUnValidateur()) {
            marques.append(ligne.validateurEnDesaccord() ? "validateur:desaccord " : "validateur:accord ");
        }
        if (ligne.aUnFil()) {
            marques.append("fil:").append(ligne.nbMessages()).append(' ');
        }
        return marques.isEmpty() ? "-" : marques.toString().trim();
    }

    private Map<String, Object> champs(LigneObservationAudio ligne) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("id", ligne.idObservation());
        // Le lieu, comme le CSV d'`exporter-sons` le porte déjà (#3350) : une sortie machine se lit
        // ligne à ligne, souvent détachée de son contexte. Le mode texte s'en tire avec un en-tête,
        // le JSON non.
        champs.put("carre", ligne.numeroCarre());
        champs.put("point", ligne.codePoint());
        champs.put("commune", ligne.commune());
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
        champs.put("aEnjeu", marqueurEnjeu.aEnjeu(ligne.taxonRetenu()));
        champs.put("messages", ligne.nbMessages());
        return champs;
    }

    private static String texte(String valeur) {
        return valeur != null ? valeur : "-";
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.AgregationActivite;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.ExportActiviteCsv;
import fr.univ_amu.iut.analyse.model.FiltresActivite;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.LigneActivite;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.FiltresLieu;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /// Référentiel des espèces à enjeu, pour `--a-enjeu`. En `Provider` pour la même raison que
    /// l'utilisateur : sa liaison lit la base, que la commande ne doit pas ouvrir à sa construction.
    private final Provider<EspecesPrioritaires> especesPrioritaires;

    @Inject
    public ExporterActivite(
            ServiceActivite service,
            @Named("idUtilisateurCourant") Provider<String> utilisateur,
            Provider<EspecesPrioritaires> especesPrioritaires) {
        this.service = Objects.requireNonNull(service, "service");
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
        this.especesPrioritaires = Objects.requireNonNull(especesPrioritaires, "especesPrioritaires");
    }

    @Option(
            names = "--lieu",
            paramLabel = "<lieu>",
            description = "Restreint à une commune ou un carré (répétable). Correspondance partielle, "
                    + "insensible à la casse et aux accents. Le point n'est pas filtrable : un code seul "
                    + "désigne autant de lieux qu'il y a de carrés.")
    private List<String> lieux = List.of();

    @Option(
            names = "--nuit",
            paramLabel = "<AAAA-MM-JJ>",
            description = "Restreint à une nuit, par sa date du soir (un contact de 2 h appartient à la "
                    + "nuit de la veille).")
    private String nuit;

    @Option(
            names = "--taxon-parent",
            paramLabel = "<taxon>",
            description = "Restreint à une catégorie taxonomique (Chiroptères, Oiseaux…). Correspondance "
                    + "partielle, insensible à la casse et aux accents.")
    private String taxonParent;

    @Option(
            names = "--nature",
            paramLabel = "<protocole|opportuniste>",
            description = "Restreint aux nuits du protocole, ou à celles réalisées sur le carré d'un tiers.")
    private String nature;

    @Option(
            names = "--a-enjeu",
            description = "Ne garde que les espèces prioritaires au Plan National d'Actions Chiroptères.")
    private boolean aEnjeu;

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

        List<ContactHoraire> tous = portee.tout
                ? service.contactsDeLUtilisateur(utilisateur.get())
                : service.contactsDuPassage(portee.passage);
        // Constater l'ensemble vide AVANT les filtres qui DÉSIGNENT (ADR 3269). Sans cela, sur une portée
        // sans aucun contact, `--lieu` et `--taxon-parent` refusaient en code 2 (« Lieux présents :
        // aucun »), mettant en cause une valeur qui n'y était pour rien. Un export vide est ici un
        // résultat valide, que la commande sait déjà écrire : le CSV garde ses en-têtes.
        List<ContactHoraire> contacts = tous.isEmpty() ? tous : restreindre(tous);
        List<LigneActivite> lignes = AgregationActivite.pourExport(contacts, tranche.get());
        Path ecrit = ExportActiviteCsv.ecrire(tranche.get(), lignes, sortie);
        spec.commandLine()
                .getOut()
                .println("Activité exportée : " + lignes.size() + " ligne(s) → " + ecrit.toAbsolutePath());
        return 0;
    }

    /// Applique les cinq critères de l'écran, dans l'ordre du plus large au plus étroit.
    ///
    /// L'ordre n'est pas indifférent pour les **messages** : chaque refus nomme ce qui est présent
    /// **dans ce qu'il a reçu**, donc après les filtres précédents. « Taxons parents présents » après un
    /// `--lieu` annonce ceux du lieu retenu, et non ceux de toute la saison - ce qui serait trompeur.
    private List<ContactHoraire> restreindre(List<ContactHoraire> contacts) {
        List<ContactHoraire> retenus = FiltresLieu.parLieu(contacts, lieux, FiltresActivite::dimensionsLieu);
        retenus = FiltresActivite.parNuit(retenus, nuit);
        retenus = FiltresActivite.parTaxonParent(retenus, taxonParent);
        retenus = FiltresActivite.parNature(retenus, nature, service.nuitsOpportunistes());
        return aEnjeu ? restreindreAuxEspecesAEnjeu(retenus) : retenus;
    }

    /// `--a-enjeu`, en **disant** quand le référentiel est vide plutôt qu'en rendant un fichier muet.
    ///
    /// Ce filtre est le seul de la commande dont un résultat vide a **deux causes opposées** : aucune
    /// espèce prioritaire dans ces nuits, ou aucun référentiel du tout. Les deux produisaient le même
    /// fichier vide en code 0, et elles appellent des conduites contraires - lire le résultat, ou réparer
    /// une installation (ADR 3048).
    ///
    /// La sortie ne **retire** rien : le CSV garde ses colonnes et son code 0. C'est une ligne qui nomme
    /// l'état, ce que l'ADR appelle « dire ». Le marquage d'`exporter-sons` n'a pas ce besoin : il pose
    /// une colonne, qui reste là même sans référentiel.
    private List<ContactHoraire> restreindreAuxEspecesAEnjeu(List<ContactHoraire> contacts) {
        Set<String> prioritaires = especesPrioritaires.get().codes();
        FiltresActivite.avertissementReferentielVide(prioritaires)
                .ifPresent(spec.commandLine().getErr()::println);
        return FiltresActivite.aEnjeu(contacts, prioritaires::contains);
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.ExportObservationsEtSons;
import fr.univ_amu.iut.validation.model.FiltresLieu;
import fr.univ_amu.iut.validation.model.FiltresProbabilite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `exporter-sons` (#2795, parité CLI du geste « Exporter les observations et les sons » #2793) : écrit
/// une archive ZIP `observations.csv` + `sons/<session>/<fichier>` - le même service
/// [ExportObservationsEtSons] que l'IHM, sans logique nouvelle. C'est la version scriptable de l'envoi à
/// un expert : « toutes les observations du passage N » ou « toutes celles de l'espèce X », avec leurs
/// sons.
///
/// `--passage` couvre le même sous-ensemble qu'`exporter-observations` (CSV identique) ; `--espece`
/// couvre l'espèce à travers tous les passages de l'utilisateur, comme la source « espèce » de la vue
/// audio, tous statuts confondus. Un passage inconnu est refusé (erreur d'usage, code 2) ; une espèce
/// sans observation produit une archive au CSV d'en-têtes seuls (résultat valide, patron
/// `exporter-activite`). Les sons introuvables sont comptés sans bloquer, comme à l'écran.
@Command(
        name = "exporter-sons",
        description = "Exporte en ZIP les observations d'un passage ou d'une espèce, avec leurs fichiers son.")
public final class ExporterSons implements Callable<Integer> {

    /// Un passage, ou une espèce : les deux s'excluent, et l'un des deux est exigé. Sans cette
    /// exclusion, `--passage 3 --espece Rhifer` aurait un sens ambigu que la commande trancherait en
    /// silence.
    @ArgGroup(multiplicity = "1")
    private Portee portee;

    /// Portée de l'export : les observations d'une nuit précise, ou celles d'une espèce à travers
    /// toutes les nuits de l'utilisateur.
    private static final class Portee {

        @Option(
                names = "--passage",
                paramLabel = "<id>",
                description = "Identifiant du passage dont exporter les observations et les sons.")
        private Long passage;

        @Option(
                names = "--espece",
                paramLabel = "<code>",
                description = "Code taxon dont exporter les observations et les sons (tous les passages).")
        private String espece;
    }

    /// Restreint aux observations d'un ou plusieurs lieux (#2971). Répétable : chaque occurrence ajoute
    /// un lieu, comme cocher une case de plus dans la puce « Lieu » de l'écran.
    @Option(
            names = "--lieu",
            paramLabel = "<lieu>",
            description = "Ne garde que les observations de ce lieu (commune, carré ou nom de site). "
                    + "Correspondance partielle, casse et accents ignorés. Répétable pour en cumuler plusieurs.")
    private List<String> lieux = new ArrayList<>();

    /// Seuil de probabilité Tadarida (#2971), à l'échelle 0..1 comme la sortie de `lister-observations`.
    @Option(
            names = "--proba-min",
            paramLabel = "<0..1>",
            description = "Ne garde que les détections dont la probabilité Tadarida atteint ce seuil "
                    + "(celles qui n'en ont pas sont conservées). Échelle 0 à 1 : 90 % s'écrit 0.9.")
    private Double probaMin;

    @Option(
            names = "--sortie",
            required = true,
            paramLabel = "<zip>",
            description = "Chemin de l'archive ZIP à écrire.")
    private Path sortie;

    @Spec
    private CommandSpec spec;

    private final ProjectionsAudioDao projections;
    private final PassageDao passages;
    private final SequenceDao sequences;
    private final SessionDao sessions;

    /// Référentiel de conservation (#2353), pour la colonne « Espèce à enjeu » du CSV. `Provider` comme
    /// les autres lectures en base : picocli instancie les sous-commandes avant la migration du schéma.
    private final Provider<EspecesPrioritaires> especesPrioritaires;

    /// Identifiant de l'utilisateur courant, pour la portée `--espece`. En `Provider` : le résoudre au
    /// constructeur ouvrirait la base avant la migration du schéma.
    private final Provider<String> utilisateur;

    @Inject
    public ExporterSons(
            ProjectionsAudioDao projections,
            PassageDao passages,
            SequenceDao sequences,
            SessionDao sessions,
            Provider<EspecesPrioritaires> especesPrioritaires,
            @Named("idUtilisateurCourant") Provider<String> utilisateur) {
        this.projections = Objects.requireNonNull(projections, "projections");
        this.passages = Objects.requireNonNull(passages, "passages");
        this.sequences = Objects.requireNonNull(sequences, "sequences");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.especesPrioritaires = Objects.requireNonNull(especesPrioritaires, "especesPrioritaires");
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
    }

    @Override
    public Integer call() throws IOException {
        List<LigneObservationAudio> avantSeuil = FiltresLieu.parLieu(lignesDeLaPortee(), lieux);
        List<LigneObservationAudio> lignes = FiltresProbabilite.parSeuilMinimal(avantSeuil, probaMin);
        MarqueurEspecesAEnjeu marqueur = new MarqueurEspecesAEnjeu(especesPrioritaires.get());
        ExportObservationsEtSons export = new ExportObservationsEtSons(sequences, sessions);
        ExportObservationsEtSons.Bilan bilan =
                export.exporter(lignes, sortie, marqueur::aEnjeu, progression -> {}, JetonAnnulation.neutre());
        spec.commandLine()
                .getOut()
                .println("Archive écrite : " + bilan.observations() + " observation(s), " + bilan.sonsCopies()
                        + " son(s), " + String.format(Locale.FRENCH, "%.1f Mo", bilan.octets() / 1_048_576.0) + " → "
                        + sortie.toAbsolutePath());
        // Une archive vide est un résultat valide, mais muet : sans cela, l'utilisateur ne saurait pas
        // que son seuil est passé juste au-dessus de tout le lot (#2971).
        FiltresProbabilite.avertissementSeuilTropHaut(avantSeuil, probaMin)
                .ifPresent(avertissement -> spec.commandLine().getOut().println(avertissement));
        if (!bilan.sonsIntrouvables().isEmpty()) {
            spec.commandLine()
                    .getOut()
                    .println("Sons introuvables (restés hors de l'archive, le CSV les nomme) : "
                            + bilan.sonsIntrouvables().size() + ".");
        }
        return 0;
    }

    /// Les observations de la portée demandée : celles du passage (refusé s'il est inconnu, pour ne pas
    /// confondre « passage vide » et « faute de frappe »), ou celles de l'espèce sur tous les passages.
    private List<LigneObservationAudio> lignesDeLaPortee() {
        if (portee.passage != null) {
            if (passages.findById(portee.passage).isEmpty()) {
                throw new ErreurUsage("Passage introuvable : --passage " + portee.passage + ".");
            }
            return projections.lignesAudioDuPassage(portee.passage);
        }
        return projections.lignesAudioDeLEspece(utilisateur.get(), portee.espece, null);
    }
}

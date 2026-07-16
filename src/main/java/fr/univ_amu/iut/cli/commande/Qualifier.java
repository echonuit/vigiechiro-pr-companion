package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `qualifier` (#617) : pose le **verdict de vérification** d'un passage (`ok` / `utilisable` / `inexploitable`),
/// après écoute — première moitié de la clôture, avant `deposer`. Réutilise [ServicePassage#poserVerdict]
/// sans logique nouvelle. Un commentaire de vérification optionnel est joint via une copie du passage
/// (`poserVerdict` préserve le commentaire du passage qu'on lui donne). Les refus métier (passage
/// introuvable, passage déjà déposé donc verdict figé) sortent en échec d'exécution (code 1) ; un verdict
/// inconnu est une erreur d'usage (code 2).
@Command(
        name = "qualifier",
        description = "Pose le verdict de vérification d'un passage (ok / utilisable / inexploitable).")
public final class Qualifier implements Callable<Integer> {

    @Option(names = "--passage", required = true, paramLabel = "<id>", description = "Passage à qualifier.")
    private Long idPassage;

    @Option(
            names = "--verdict",
            required = true,
            paramLabel = "<verdict>",
            description = "Verdict de vérification : ok, utilisable ou inexploitable.")
    private String verdict;

    @Option(
            names = "--commentaire",
            paramLabel = "<texte>",
            description = "Commentaire de vérification (optionnel) ; remplace le commentaire du passage.")
    private String commentaire;

    @Spec
    private CommandSpec spec;

    private final ServicePassage service;
    private final PassageDao passageDao;

    @Inject
    public Qualifier(ServicePassage service, PassageDao passageDao) {
        this.service = Objects.requireNonNull(service, "service");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
    }

    @Override
    public Integer call() {
        Verdict cible = verdictDepuis(verdict);
        Passage passage = passageDao
                .findById(idPassage)
                .orElseThrow(() -> new RegleMetierException("Passage introuvable : " + idPassage));
        Passage aJuger = commentaire == null ? passage : avecCommentaire(passage, commentaire);
        service.poserVerdict(aJuger, cible);
        spec.commandLine()
                .getOut()
                .println("Verdict « " + cible.libelle() + " » posé sur le passage #" + idPassage
                        + (commentaire == null ? "." : " (commentaire mis à jour)."));
        return 0;
    }

    /// Traduit le mot-clé CLI (`ok` / `utilisable` / `inexploitable`, insensible à la casse ; `douteux`
    /// et `a-jeter` restent acceptés en alias rétro-compatibles) en [Verdict]. Le verdict initial
    /// `Non vérifié` n'est volontairement pas exposé (il n'a de sens qu'avant la première écoute).
    ///
    /// @throws ErreurUsage si le mot-clé n'est pas reconnu (code de sortie 2)
    static Verdict verdictDepuis(String motCle) {
        return switch (motCle.toLowerCase(Locale.ROOT)) {
            case "ok" -> Verdict.OK;
            case "utilisable", "douteux" -> Verdict.DOUTEUX;
            case "inexploitable", "a-jeter", "ajeter" -> Verdict.A_JETER;
            default ->
                throw new ErreurUsage(
                        "Verdict inconnu : « " + motCle + " ». Valeurs acceptées : ok, utilisable, inexploitable.");
        };
    }

    /// Copie du passage avec un nouveau commentaire (le record `Passage` n'a pas de modificateur dédié) ;
    /// tous les autres champs sont conservés à l'identique.
    private static Passage avecCommentaire(Passage p, String commentaire) {
        return new Passage(
                p.id(),
                p.numeroPassage(),
                p.annee(),
                p.dateEnregistrement(),
                p.heureDebut(),
                p.heureFin(),
                p.parametresAcquisition(),
                p.statutWorkflow(),
                p.verdictVerification(),
                commentaire,
                p.donneesMeteo(),
                p.deposeLe(),
                p.idPoint(),
                p.idEnregistreur());
    }
}

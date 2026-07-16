package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `qualifier-fichier` (#1512) : pose le **verdict par fichier son** d'une séquence de la sélection
/// d'écoute d'un passage (`bon` / `mauvais` / `inexploitable`), puis affiche le **verdict final proposé**
/// du passage, recalculé (dérivé des verdicts par fichier). Parité CLI du panneau « Votre verdict sur ce
/// fichier » de M-Qualification (chantier #1524).
///
/// Refus métier (passage sans sélection d'écoute) : échec d'exécution (code 1). Verdict inconnu : erreur
/// d'usage (code 2). La séquence doit appartenir à la sélection du passage (sinon l'écriture est sans
/// effet, comme dans l'IHM).
@Command(
        name = "qualifier-fichier",
        description =
                "Pose le verdict par fichier d'une séquence (bon / mauvais / inexploitable) et recalcule le final.")
public final class QualifierFichier implements Callable<Integer> {

    @Option(names = "--passage", required = true, paramLabel = "<id>", description = "Passage concerné.")
    private Long idPassage;

    @Option(
            names = "--sequence",
            required = true,
            paramLabel = "<id>",
            description = "Séquence de la sélection d'écoute à juger.")
    private Long idSequence;

    @Option(
            names = "--verdict",
            required = true,
            paramLabel = "<verdict>",
            description = "Verdict par fichier : bon, mauvais ou inexploitable.")
    private String verdict;

    @Spec
    private CommandSpec spec;

    private final ServiceQualification service;

    @Inject
    public QualifierFichier(ServiceQualification service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        VerdictFichier cible = verdictFichierDepuis(verdict);
        service.enregistrerVerdictFichier(idPassage, idSequence, cible);
        Verdict propose = service.verdictDerivePassage(idPassage);
        spec.commandLine()
                .getOut()
                .println("Verdict « " + cible.libelle() + " » posé sur la séquence #" + idSequence + " du passage #"
                        + idPassage + ". Verdict final proposé : " + propose.libelle() + ".");
        return 0;
    }

    /// Traduit le mot-clé CLI (`bon` / `mauvais` / `inexploitable`, insensible à la casse) en
    /// [VerdictFichier]. `Non jugé` n'est pas exposé (c'est l'absence de verdict, obtenue en ne jugeant
    /// pas la séquence ou en régénérant la sélection).
    ///
    /// @throws ErreurUsage si le mot-clé n'est pas reconnu (code de sortie 2)
    static VerdictFichier verdictFichierDepuis(String motCle) {
        return switch (motCle.toLowerCase(Locale.ROOT)) {
            case "bon" -> VerdictFichier.BON;
            case "mauvais" -> VerdictFichier.MAUVAIS;
            case "inexploitable" -> VerdictFichier.INEXPLOITABLE;
            default ->
                throw new ErreurUsage("Verdict de fichier inconnu : « " + motCle
                        + " ». Valeurs acceptées : bon, mauvais, inexploitable.");
        };
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.qualification.model.GenerateurSelection;
import fr.univ_amu.iut.qualification.model.SelectionDEcoute;
import fr.univ_amu.iut.qualification.model.ServiceQualification;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `constituer-selection` (#1512) : (re)constitue la **sélection d'écoute** d'un passage — l'échantillon
/// de séquences à écouter pour le vérifier (R12). Méthode `reparti` (défaut) / `aleatoire` / `manuel`,
/// taille visée (défaut 20 ; le nombre réellement retenu peut être moindre si la nuit compte moins de
/// séquences). Parité CLI des boutons « Personnaliser… » / « Régénérer » de M-Qualification (#1524).
///
/// **Remplace atomiquement** une sélection existante (comme « Régénérer » dans l'IHM) : les verdicts par
/// fichier et la progression d'écoute déjà saisis sont **effacés**. Refus métier (passage introuvable,
/// sans session ou sans séquence) : échec d'exécution (code 1) ; méthode/taille invalide : erreur d'usage
/// (code 2).
@Command(
        name = "constituer-selection",
        description =
                "(Re)constitue la sélection d'écoute d'un passage (méthode + taille). Efface les verdicts existants.")
public final class ConstituerSelection implements Callable<Integer> {

    @Option(names = "--passage", required = true, paramLabel = "<id>", description = "Passage à échantillonner.")
    private Long idPassage;

    @Option(
            names = "--methode",
            paramLabel = "<methode>",
            description = "Méthode d'échantillonnage : reparti (défaut), aleatoire ou manuel.")
    private String methode;

    @Option(names = "--taille", paramLabel = "<n>", description = "Nombre de séquences visé (défaut 20).")
    private Integer taille;

    @Spec
    private CommandSpec spec;

    private final ServiceQualification service;

    @Inject
    public ConstituerSelection(ServiceQualification service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        MethodeSelection methodeChoisie = methodeDepuis(methode);
        int tailleVisee = taille == null ? GenerateurSelection.TAILLE_DEFAUT : taille;
        if (tailleVisee < 1) {
            throw new ErreurUsage("La taille doit être un entier positif (reçu : " + tailleVisee + ").");
        }
        SelectionDEcoute selection = service.creerSelection(idPassage, methodeChoisie, tailleVisee);
        spec.commandLine()
                .getOut()
                .println("Sélection constituée pour le passage #" + idPassage + " : " + selection.taille()
                        + " séquence(s), méthode « " + selection.methode().libelle() + " ».");
        return 0;
    }

    /// Traduit le mot-clé de méthode (`reparti` / `aleatoire` / `manuel`, insensible à la casse ; défaut
    /// `reparti`) en [MethodeSelection].
    ///
    /// @throws ErreurUsage si le mot-clé n'est pas reconnu (code de sortie 2)
    static MethodeSelection methodeDepuis(String motCle) {
        if (motCle == null) {
            return MethodeSelection.REPARTITION_TEMPORELLE;
        }
        return switch (motCle.toLowerCase(Locale.ROOT)) {
            case "reparti", "repartemporel", "temporel" -> MethodeSelection.REPARTITION_TEMPORELLE;
            case "aleatoire" -> MethodeSelection.ALEATOIRE;
            case "manuel" -> MethodeSelection.MANUEL;
            default ->
                throw new ErreurUsage(
                        "Méthode inconnue : « " + motCle + " ». Valeurs acceptées : reparti, aleatoire, manuel.");
        };
    }
}

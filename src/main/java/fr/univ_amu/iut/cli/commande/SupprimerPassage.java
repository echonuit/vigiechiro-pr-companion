package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.model.CompteurValidations;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `supprimer-passage` (#2278) : supprime **définitivement** un passage et toute sa nuit (séquences,
/// relevés), équivalent scriptable du bouton « Supprimer » de la fiche passage.
///
/// ## Pourquoi un drapeau, et pas une confirmation
///
/// En IHM, la sûreté de ce geste tient à une **modale** qui montre ce qui sera perdu avant de laisser
/// cliquer. Une surface scriptable n'a pas de modale : la parade est un **drapeau explicite**. Sans
/// `--confirmer`, la commande **dit la perte et ne touche à rien**, en sortant en `2` — un script qui
/// enchaînerait s'arrête là plutôt que de détruire.
///
/// Le drapeau ne dispense pas d'informer : il **déplace** seulement le moment où l'utilisateur décide.
/// La perte est donc chiffrée dans les deux cas, avec les mots de l'IHM
/// ([fr.univ_amu.iut.passage.view.ActionsFichePassage]),
/// pour que les deux surfaces ne racontent pas deux histoires.
///
/// Refus du **métier** (passage introuvable, ou déjà **déposé**) : `ServicePassage.supprimer` lève, et
/// la CLI sort en `1` comme pour toute erreur d'exécution.
@Command(
        name = "supprimer-passage",
        description = "Supprime DÉFINITIVEMENT un passage et toute sa nuit (séquences, relevés). "
                + "Sans --confirmer, chiffre la perte et ne touche à rien.")
public final class SupprimerPassage implements Callable<Integer> {

    /// Code de sortie d'un refus faute de `--confirmer` : distinct du succès (0) et de l'échec (1).
    /// Un script qui enchaîne s'arrête dessus sans avoir rien détruit.
    private static final int CODE_REFUS = 2;

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage à supprimer définitivement.")
    private Long idPassage;

    @Option(
            names = "--confirmer",
            description = "Obligatoire pour agir : atteste que la perte chiffrée ci-dessus est voulue.")
    private boolean confirmer;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServicePassage> passages;
    private final Provider<CompteurValidations> validations;

    @Inject
    public SupprimerPassage(Provider<ServicePassage> passages, Provider<CompteurValidations> validations) {
        this.passages = Objects.requireNonNull(passages, "passages");
        this.validations = Objects.requireNonNull(validations, "validations");
    }

    @Override
    public Integer call() {
        ServicePassage service = passages.get();
        // La perte se chiffre AVANT toute écriture : sans --confirmer, rien d'autre ne se produira.
        // Un passage introuvable lève ici même, et la CLI sort en 1.
        DetailPassage detail = service.detailPassage(idPassage);
        int menacees = validations.get().menaceesPourPassage(idPassage);

        PrintWriter sortie = spec.commandLine().getOut();
        annoncerLaPerte(sortie, service, idPassage, detail, menacees);

        if (!confirmer) {
            spec.commandLine().getErr().println("Rien n'a été supprimé. Ajoutez --confirmer pour assumer cette perte.");
            return CODE_REFUS;
        }

        service.supprimer(idPassage);
        sortie.println("Passage " + idPassage + " supprimé (" + detail.nombreSequences() + " séquence(s)).");
        return 0;
    }

    /// Chiffre ce qui disparaîtrait, avec les mots de l'IHM. Le dossier de la nuit est mentionné parce
    /// que la suppression ne le touche **pas** : la cascade s'arrête à la base.
    private static void annoncerLaPerte(
            PrintWriter sortie, ServicePassage service, Long idPassage, DetailPassage detail, int menacees) {
        sortie.println("Suppression DÉFINITIVE du passage " + detail.numeroPassage() + " (" + detail.annee()
                + ") et de toute sa nuit : " + detail.nombreSequences() + " séquence(s) d'écoute, relevés compris.");
        if (menacees > 0) {
            sortie.println("  Dont " + menacees
                    + " validation(s) Tadarida (correction, référence, commentaire) définitivement perdue(s).");
        }
        service.cheminSession(idPassage)
                .ifPresent(chemin -> sortie.println("  Les fichiers restent sur le disque : " + chemin));
        sortie.println("  Statut actuel : " + detail.statut() + ".");
    }
}

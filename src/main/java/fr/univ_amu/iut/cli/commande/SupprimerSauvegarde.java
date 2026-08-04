package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Commande `supprimer-sauvegarde` (#3197) : le ménage que la doc conseille, **explicitement demandé**.
///
/// L'application ne purge rien d'elle-même et ne le fera pas : un filet appartient à l'utilisateur, et
/// une politique de rétention automatique déciderait à sa place du moment où il n'en a plus besoin
/// (ADR 0048). Ce que #3197 corrigeait, c'est le **silence** autour : `lister-sauvegardes` le rompt,
/// cette commande donne le geste qui suit.
///
/// ## Pourquoi un drapeau, et pas une confirmation
///
/// Même parade que `supprimer-passage` : une surface scriptable n'a pas de modale. Sans `--confirmer`,
/// la commande **dit ce qui serait perdu et ne touche à rien**, en sortant en `2` - un script qui
/// enchaînerait s'arrête là. Le drapeau ne dispense pas d'informer : il déplace le moment où
/// l'utilisateur décide.
///
/// Une sauvegarde nommée mais absente est une **erreur d'usage** (`1`), pas un succès silencieux :
/// croire avoir supprimé ce qui n'a jamais été touché est précisément ce qu'on veut éviter ici.
@Command(
        name = "supprimer-sauvegarde",
        description = "Supprime DÉFINITIVEMENT une sauvegarde ou un filet de migration. "
                + "Sans --confirmer, dit ce qui serait perdu et ne touche à rien.")
public final class SupprimerSauvegarde implements Callable<Integer> {

    /// Code de sortie d'un refus faute de `--confirmer` : distinct du succès (0) et de l'échec (1).
    private static final int CODE_REFUS = 2;

    @Option(
            names = "--nom",
            required = true,
            paramLabel = "<nom>",
            description = "Nom exact de la sauvegarde, tel que « lister-sauvegardes » l'affiche.")
    private String nom;

    @Option(
            names = "--dossier",
            paramLabel = "<dossier>",
            description = "Dossier qui la contient. Par défaut : <workspace>/sauvegardes.")
    private Path dossier;

    @Option(
            names = "--confirmer",
            description = "Obligatoire pour agir : atteste que la perte annoncée ci-dessus est voulue.")
    private boolean confirmer;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceSauvegarde> service;

    @Inject
    public SupprimerSauvegarde(Provider<ServiceSauvegarde> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        Path base = dossier != null ? dossier : service.get().dossierParDefaut();
        InventaireSauvegardes.Entree entree = InventaireSauvegardes.lire(base).stream()
                .filter(e -> e.nom().equals(nom))
                .findFirst()
                .orElseThrow(() -> new ErreurUsage("Aucune sauvegarde nommée « " + nom + " » dans " + base
                        + ". « lister-sauvegardes » donne les noms exacts."));

        // La perte est chiffrée dans les DEUX cas : le drapeau déplace la décision, il ne la remplace pas.
        sortie.println("Suppression de « " + entree.nom() + " » (" + Formats.octetsLisibles(entree.octets())
                + "). Ce filet ne pourra plus servir à revenir en arrière.");
        if (!confirmer) {
            spec.commandLine().getErr().println("Rien n'a été supprimé. Ajoutez --confirmer pour assumer cette perte.");
            return CODE_REFUS;
        }
        supprimerRecursivement(base.resolve(entree.nom()));
        sortie.println("Supprimé.");
        return 0;
    }

    /// Une sauvegarde complète est un **dossier** : la supprimer, c'est supprimer son contenu d'abord.
    private static void supprimerRecursivement(Path cible) {
        try (Stream<Path> arborescence = Files.walk(cible)) {
            for (Path chemin : arborescence.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(chemin);
            }
        } catch (IOException echec) {
            throw new UncheckedIOException("Suppression impossible de " + cible, echec);
        }
    }
}

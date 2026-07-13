package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `lancer-traitement-vigiechiro` (#984) : déclenche le **traitement serveur** (Tadarida) de la
/// participation liée à un passage — équivalent CLI du bouton « Lancer la participation » de M-Lot.
/// À lancer **une fois la nuit déposée** ; le serveur enchaîne l'extraction des archives puis Tadarida.
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la **connexion
/// enregistrée** dans l'application (préférer la variable d'environnement à `--token`, qui laisse le
/// jeton dans l'historique du shell).
///
/// Code retour **`0` dès lors que le traitement est en route** — que le serveur vienne de l'accepter ou
/// qu'il tourne déjà (la commande est donc **idempotente**, scriptable sans crainte) — et `1` sinon
/// (refus, serveur injoignable, relance bloquée). Lève une [RegleMetierException] si aucune participation
/// n'est liée au passage (déposer d'abord).
///
/// ⚠️ **Une nuit déjà analysée n'est pas relancée** : le serveur supprimerait ses observations pour les
/// recalculer, or l'audio d'un dépôt en archives ZIP n'est pas récupérable (#1244, #1261). L'option
/// `--forcer` lève cette garde, en connaissance de cause — typiquement après un **échec**, où il n'y a
/// plus rien à perdre.
@Command(
        name = "lancer-traitement-vigiechiro",
        description = "Déclenche le traitement serveur (compute) de la participation d'un passage déjà déposé.")
public final class LancerTraitementVigieChiro implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage dont la participation liée doit être traitée (nuit déjà déposée).")
    private Long idPassage;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton VigieChiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Option(
            names = "--forcer",
            description = "RELANCER une nuit déjà analysée. DESTRUCTEUR : le serveur supprime les"
                    + " observations avant de recalculer, et il ne peut pas les régénérer si la nuit a été"
                    + " déposée en archives (l'audio n'y est pas conservé). À n'utiliser qu'en connaissance"
                    + " de cause, typiquement après un échec.")
    private boolean forcer;

    @Spec
    private CommandSpec spec;

    private final Optional<DepotVigieChiro> depot;

    @Inject
    public LancerTraitementVigieChiro(Optional<DepotVigieChiro> depot) {
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    @Override
    public Integer call() {
        DepotVigieChiro moteur = depot.orElseThrow(
                () -> new RegleMetierException("Dépôt VigieChiro indisponible dans ce contexte d'exécution."));
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel consulté par le client à chaque requête, sans rien persister.
            System.setProperty("vigiechiro.token", token);
        }
        PrintWriter sortie = spec.commandLine().getOut();
        ResultatLancement resultat = moteur.lancerTraitement(idPassage, forcer);
        sortie.println(compteRendu(resultat));
        // 0 = le traitement est en route, qu'il vienne d'être accepté ou qu'il tournait déjà : rejouer la
        // commande ne doit pas faire échouer un script (idempotence).
        return resultat.traitementEnRoute() ? 0 : 1;
    }

    /// Compte rendu du lancement, une ligne par issue. Le message d'avant s'achevait sur « (déjà en
    /// cours ?) » : la question est désormais tranchée par la relecture de l'état (#1261).
    private String compteRendu(ResultatLancement resultat) {
        return switch (resultat.issue()) {
            case ACCEPTE ->
                "Traitement lancé sur VigieChiro pour le passage " + idPassage
                        + " : les résultats arriveront après le calcul serveur.";
            case DEJA_LANCE -> "Le traitement de ce passage est déjà en cours sur VigieChiro : rien à faire.";
            case RELANCE_BLOQUEE ->
                "Cette nuit a déjà été analysée. La relancer effacerait les observations du serveur sans"
                        + " pouvoir les recalculer (audio non conservé pour un dépôt en archives) :"
                        + " importez-les plutôt (importer-vigiechiro).";
            case REFUSE -> "VigieChiro a refusé le lancement du traitement : " + resultat.détail();
            case INJOIGNABLE -> "VigieChiro est injoignable : le traitement n'a pas pu être lancé.";
        };
    }
}

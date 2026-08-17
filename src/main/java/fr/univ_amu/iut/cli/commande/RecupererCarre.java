package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.SouhaitDeclaration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `recuperer-carre` (#3856) : **rapatrie un carré depuis Vigie-Chiro**, rattaché, avec ses points
/// d'écoute positionnés.
///
/// ## Le geste que la ligne de commande n'avait pas
///
/// Préparer une nuit **opportuniste** exige que le site local porte un lien vers son homologue
/// plateforme, faute de quoi le dépôt est refusé. La synchronisation qui pose ce lien part de
/// `/moi/participations` : elle n'atteint que les carrés où une nuit est **déjà** déposée. L'écran a
/// cassé ce cercle en #3806 ; la ligne de commande ne l'avait pas.
///
/// Elle appelle **le même** [RapatriementCarre] que la fenêtre de déclaration : mêmes verdicts, même
/// filtre Point Fixe, même état local posé. Une seconde implémentation aurait divergé.
///
/// ## Ce qu'elle écrit, et où
///
/// L'**identifiant** du site créé part sur la sortie standard, seul, pour l'enchaînement de scripts
/// (`SITE=$(vigiechiro recuperer-carre --carre 130711)`) - même contrat que `creer-site`. Le compte
/// rendu lisible part sur la sortie d'**erreur** : le mélanger au premier casserait la substitution.
@Command(
        name = "recuperer-carre",
        description = "Récupère un carré depuis Vigie-Chiro (site rattaché, points positionnés) et écrit son"
                + " identifiant.")
public final class RecupererCarre implements Callable<Integer> {

    @Option(names = "--carre", required = true, paramLabel = "<n>", description = "Numéro de carré (6 chiffres).")
    private String carre;

    @Option(names = "--nom", paramLabel = "<nom>", description = "Nom convivial du site (optionnel).")
    private String nom;

    @Option(
            names = "--protocole",
            paramLabel = "<protocole>",
            description = "Protocole de suivi : ${COMPLETION-CANDIDATES} (insensible à la casse). Défaut : STANDARD.")
    private Protocole protocole;

    @Option(names = "--commentaire", paramLabel = "<texte>", description = "Commentaire libre (optionnel).")
    private String commentaire;

    @Spec
    private CommandSpec spec;

    /// Le rapatriement, **optionnel** : il vit derrière la fonctionnalité « vérifier et récupérer un
    /// carré ». Éteinte, la commande le dit plutôt que d'échouer par une pile.
    ///
    /// Résolu **paresseusement** : picocli instancie toutes les sous-commandes à la construction de la
    /// CLI, avant la migration du schéma, et résoudre ce port touche la base.
    private final Provider<Optional<RapatriementCarre>> rapatriement;

    @Inject
    public RecupererCarre(Provider<Optional<RapatriementCarre>> rapatriement) {
        this.rapatriement = Objects.requireNonNull(rapatriement, "rapatriement");
    }

    @Override
    public Integer call() {
        RapatriementCarre service = rapatriement
                .get()
                .orElseThrow(() -> new RegleMetierException("La récupération d'un carré est désactivée :"
                        + " activez la fonctionnalité « Vérifier et récupérer un carré depuis Vigie-Chiro »"
                        + " dans les Réglages, puis relancez l'application."));

        // Le protocole a un défaut annoncé dans l'aide, et `SouhaitDeclaration` n'accepte pas de
        // protocole nul : c'est ici qu'il se pose, comme `ServiceSites` le fait pour `creer-site`.
        RapatriementCarre.Resultat resultat = service.rapatrier(
                new SouhaitDeclaration(carre, protocole == null ? Protocole.STANDARD : protocole, nom, commentaire));

        // Tout ce qui n'est pas un rapatriement est un refus : rien n'a été créé, et le dire par un code
        // de sortie non nul évite qu'un script enchaîne sur un site qui n'existe pas.
        if (!(resultat instanceof RapatriementCarre.Resultat.Rapatrie rapatrie)) {
            throw new RegleMetierException(resultat.message());
        }
        spec.commandLine().getErr().println(rapatrie.message());
        spec.commandLine().getOut().println(rapatrie.site().id());
        return 0;
    }
}

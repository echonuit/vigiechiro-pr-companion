package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanVerification;
import fr.univ_amu.iut.lot.model.VerificationDepot;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `verifier-depot-vigiechiro` (#1132) : confronte le plan de dépôt local d’un passage à ce que la
/// plateforme a **réellement traité** : journal de traitement de la participation (archives
/// extraites, WAV passés à Tadarida) et titres des `donnees`. Lecture seule : rien n’est modifié.
/// Compagnon naturel de `deposer-vigiechiro` : après le traitement serveur (24-48 h), cette commande
/// donne le verdict fichier par fichier.
///
/// **Jeton** : `--token`, sinon la variable d’environnement `VIGIECHIRO_TOKEN`, sinon la connexion
/// enregistrée dans l’application.
///
/// Code retour `0` seulement si **toutes** les unités du plan local sont retrouvées côté plateforme
/// (scriptable) ; `1` sinon (fichiers manquants, ou journal pas encore disponible pour un dépôt ZIP).
@Command(
        name = "verifier-depot-vigiechiro",
        description = "Vérifie côté plateforme (journal de traitement + données) qu’un dépôt a bien été traité.")
public final class VerifierDepotVigieChiro implements Callable<Integer> {

    /// Limite d’affichage des fichiers manquants (au-delà : « … et N autres »).
    private static final int MANQUANTES_AFFICHEES = 20;

    @Option(names = "--passage", required = true, paramLabel = "<id>", description = "Passage à vérifier.")
    private Long idPassage;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton Vigie-Chiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l’application).")
    private String token;

    @Spec
    private CommandSpec spec;

    private final Optional<VerificationDepot> verification;

    @Inject
    public VerifierDepotVigieChiro(Optional<VerificationDepot> verification) {
        this.verification = Objects.requireNonNull(verification, "verification");
    }

    @Override
    public Integer call() {
        VerificationDepot moteur = verification.orElseThrow(
                () -> new RegleMetierException("Vérification Vigie-Chiro indisponible dans ce contexte d’exécution."));
        if (token != null && !token.isBlank()) {
            System.setProperty("vigiechiro.token", token);
        }
        BilanVerification bilan = moteur.verifier(idPassage);
        spec.commandLine().getOut().println(rendre(bilan));
        return bilan.estComplet() ? 0 : 1;
    }

    /// Bilan lisible et scriptable. Fonction pure (testable sans base ni réseau).
    static String rendre(BilanVerification bilan) {
        StringBuilder sortie = new StringBuilder();
        sortie.append("Participation ")
                .append(bilan.participationId())
                .append(" : journal de traitement ")
                .append(bilan.journalDisponible() ? "disponible" : "INDISPONIBLE (traitement pas encore lancé ?)")
                .append(", ")
                .append(bilan.nombreDonnees())
                .append(" donnée(s) Tadarida en ligne.\n");
        int total = bilan.retrouvees().size() + bilan.manquantes().size();
        if (bilan.estComplet()) {
            sortie.append(total)
                    .append("/")
                    .append(total)
                    .append(" fichier(s) du plan local retrouvé(s) côté plateforme : dépôt vérifié.");
            return sortie.toString();
        }
        sortie.append(bilan.manquantes().size())
                .append("/")
                .append(total)
                .append(" fichier(s) NON retrouvé(s) côté plateforme :");
        bilan.manquantes().stream()
                .limit(MANQUANTES_AFFICHEES)
                .forEach(nom -> sortie.append("\n  ! ").append(nom));
        if (bilan.manquantes().size() > MANQUANTES_AFFICHEES) {
            sortie.append("\n  … et ")
                    .append(bilan.manquantes().size() - MANQUANTES_AFFICHEES)
                    .append(" autre(s).");
        }
        sortie.append("\nRelancez le dépôt (deposer-vigiechiro), ou réessayez après le traitement serveur.");
        return sortie.toString();
    }
}

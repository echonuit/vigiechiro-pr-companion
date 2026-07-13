package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanPublication;
import fr.univ_amu.iut.validation.model.PublicationCorrections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `publier-corrections-vigiechiro` (#723) : publie les **corrections observateur** d'un passage
/// vers la plateforme (un `PATCH` par observation publiable : taxon + certitude déclarés, ancrage
/// présent) - l'équivalent scriptable de l'action ☰ « Publier les corrections vers VigieChiro » de
/// Sons & validation. **Idempotente** : relancer re-pousse les mêmes valeurs, sans dégât.
///
/// Contrairement à l'IHM (confirmation récapitulative), la CLI publie **directement** : c'est le
/// comportement attendu d'une commande scriptable, l'appelant a déjà décidé.
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la connexion
/// enregistrée dans l'application.
///
/// Code retour `0` si tout ce qui était publiable a été écrit (même s'il reste des écartées, listées
/// sur la sortie standard) ; `1` si au moins un envoi a été **refusé** (détail sur la sortie
/// d'erreur) ou si rien n'est revu.
@Command(
        name = "publier-corrections-vigiechiro",
        description = "Publie les corrections observateur d'un passage vers VigieChiro (taxon +"
                + " certitude, un PATCH par observation).")
public final class PublierCorrectionsVigieChiro implements Callable<Integer> {

    @Option(names = "--passage", required = true, paramLabel = "<id>", description = "Passage à publier.")
    private Long idPassage;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton VigieChiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Spec
    private CommandSpec spec;

    private final Optional<PublicationCorrections> publication;

    @Inject
    public PublierCorrectionsVigieChiro(Optional<PublicationCorrections> publication) {
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    @Override
    public Integer call() {
        PublicationCorrections moteur = publication.orElseThrow(
                () -> new RegleMetierException("Publication des corrections indisponible dans ce contexte"
                        + " d'exécution (fonctionnalité « publier-corrections » désactivée ?)."));
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel : consulté par le client à chaque requête (cf. ConnexionModule), sans rien
            // persister — la connexion enregistrée de l'application n'est pas modifiée.
            System.setProperty("vigiechiro.token", token);
        }
        BilanPublication bilan = moteur.publier(idPassage);
        spec.commandLine().getOut().println(rendre(bilan));
        for (String echec : bilan.echecs()) {
            spec.commandLine().getErr().println("REFUS : " + echec);
        }
        return bilan.sansEchec() ? 0 : 1;
    }

    /// Rendu du bilan sur une ligne : envoyées, puis les écarts par cause (seulement s'il y en a).
    static String rendre(BilanPublication bilan) {
        StringBuilder rendu = new StringBuilder("Corrections publiées : ")
                .append(bilan.poussees())
                .append(" envoyée(s)");
        if (bilan.ecartees() > 0) {
            rendu.append(" ; écartées : ")
                    .append(bilan.sansCertitude())
                    .append(" à compléter (certitude), ")
                    .append(bilan.sansAncrage())
                    .append(" sans ancrage, ")
                    .append(bilan.horsReferentiel())
                    .append(" hors référentiel");
        }
        if (!bilan.sansEchec()) {
            rendu.append(" ; ").append(bilan.echecs().size()).append(" refus (détail sur la sortie d'erreur)");
        }
        return rendu.append('.').toString();
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `modifier-site` (#1383) : corrige l'identité d'un site déjà déclaré.
///
/// Parité avec la modale « Modifier le site » de l'écran Sites : mêmes champs, mêmes refus. Le carré
/// mal formé (R1) et le carré déjà déclaré par un **autre** site de l'utilisateur (R5) sont refusés
/// par [ServiceSites#modifierSite], et sortent en **refus métier** (code 2, état intact).
///
/// Le protocole omis **conserve** celui du site, il ne retombe pas sur un défaut : c'est le
/// comportement du service, et le taire ici ferait diverger les deux surfaces.
@Command(
        name = "modifier-site",
        description = "Corrige le carré, le nom, le protocole ou le commentaire d'un site déclaré.")
public final class ModifierSite implements Callable<Integer> {

    @Option(names = "--site", required = true, paramLabel = "<id>", description = "Site à modifier.")
    private Long idSite;

    @Option(
            names = "--carre",
            required = true,
            paramLabel = "<n>",
            description = "Numéro de carré (6 chiffres). Requis : c'est l'identité du site.")
    private String carre;

    @Option(names = "--nom", paramLabel = "<nom>", description = "Nom convivial (optionnel).")
    private String nom;

    @Option(
            names = "--protocole",
            paramLabel = "<protocole>",
            description = "Protocole : ${COMPLETION-CANDIDATES}. Omis, le protocole actuel est conservé.")
    private Protocole protocole;

    @Option(names = "--commentaire", paramLabel = "<texte>", description = "Commentaire libre (optionnel).")
    private String commentaire;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceSites> service;

    @Inject
    public ModifierSite(Provider<ServiceSites> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        Site site = service.get().modifierSite(idSite, carre, nom, protocole, commentaire);
        spec.commandLine()
                .getOut()
                .println("Site " + site.id() + " modifié : carré " + site.numeroCarre()
                        + (site.nomConvivial() == null || site.nomConvivial().isBlank()
                                ? ""
                                : " · " + site.nomConvivial()));
        return 0;
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `creer-site` (#615) : crée un site (carré) et **écrit son identifiant** sur la sortie standard, pour
/// l'enchaînement de scripts — par exemple `SITE=$(vigiechiro creer-site --carre 640380)`. Réutilise
/// [ServiceSites#creerSite] sans logique nouvelle ; les refus métier (carré mal formé R1, carré déjà
/// déclaré R5) sortent en échec d'exécution (code 1).
@Command(name = "creer-site", description = "Crée un site (carré) et écrit son identifiant (exploitable en script).")
public final class CreerSite implements Callable<Integer> {

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

    private final ServiceSites service;
    // Provider (résolu paresseusement) : le fournisseur d'utilisateur courant interroge la base, or picocli
    // instancie les sous-commandes au parsing, avant la migration. On ne lit l'id que dans call().
    private final Provider<String> idUtilisateur;

    @Inject
    public CreerSite(ServiceSites service, @Named("idUtilisateurCourant") Provider<String> idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public Integer call() {
        Site site = service.creerSite(carre, nom, protocole, commentaire, idUtilisateur.get());
        spec.commandLine().getOut().println(site.id());
        return 0;
    }
}

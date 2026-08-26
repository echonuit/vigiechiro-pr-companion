package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `creer-site` (#615) : crée un site (carré) et **écrit son identifiant** sur la sortie standard, pour
/// l'enchaînement de scripts : par exemple `SITE=$(vigiechiro creer-site --carre 640380)`. Réutilise
/// [ServiceSites#creerSite] sans logique nouvelle ; les refus métier (carré mal formé R1, carré déjà
/// déclaré R5) sortent en refus métier (code 2, état intact).
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

    @Option(
            names = "--sans-verification",
            description = "Ne demande pas à Vigie-Chiro si ce carré y existe déjà (hors connexion, ou script"
                    + " qui sait ce qu'il fait).")
    private boolean sansVerification;

    @Spec
    private CommandSpec spec;

    private final ServiceSites service;

    /// Pour demander à la plateforme si ce carré y existe déjà (#3856). Injecté directement : le client
    /// ne touche ni la base ni le réseau à la construction.
    private final ClientVigieChiro client;
    // Provider (résolu paresseusement) : le fournisseur d'utilisateur courant interroge la base, or picocli
    // instancie les sous-commandes au parsing, avant la migration. On ne lit l'id que dans call().
    private final Provider<String> idUtilisateur;

    @Inject
    public CreerSite(
            ServiceSites service,
            ClientVigieChiro client,
            @Named("idUtilisateurCourant") Provider<String> idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.client = Objects.requireNonNull(client, "client");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public Integer call() {
        if (!sansVerification) {
            refuserSiLeCarreExisteLaBas();
        }
        Site site = service.creerSite(carre, nom, protocole, commentaire, idUtilisateur.get());
        spec.commandLine().getOut().println(site.id());
        return 0;
    }

    /// Refuse de créer un carré que la plateforme porte **déjà en Point Fixe** (#3856).
    ///
    /// ## Le doublon que cette garde empêche
    ///
    /// Créer ici un carré qui existe là-bas produit **deux sites pour un même carré**, dont le local
    /// n'est rattaché à rien : le dépôt de la nuit échoue ensuite, loin de sa cause. C'est l'incident
    /// d'origine de #3458, et l'écran l'empêche depuis #3806 - la ligne de commande, non.
    ///
    /// ## Ce qui se passe quand on ne peut pas demander
    ///
    /// **Une plateforme injoignable n'empêche pas de créer.** L'application sert sur le terrain, hors
    /// connexion : refuser faute d'avoir pu vérifier rendrait la commande inutilisable là où elle est le
    /// plus utile. Mais la sortie **dit** que la vérification n'a pas eu lieu, plutôt que de laisser
    /// croire qu'elle a réussi - c'est la règle de l'ADR 3854, appliquée dans l'autre sens.
    private void refuserSiLeCarreExisteLaBas() {
        switch (client.chercherCarre(carre)) {
            case ReponseApi.Succes<List<SiteVigieChiro>>(List<SiteVigieChiro> trouves) -> refuserSiPointFixe(trouves);
            case ReponseApi.NonConnecte<List<SiteVigieChiro>> nonConnecte ->
                prevenirSansVerifier("vous n'êtes pas connecté");
            case ReponseApi.Injoignable<List<SiteVigieChiro>>(String cause) ->
                prevenirSansVerifier("Vigie-Chiro est injoignable : " + cause);
            case ReponseApi.Refuse<List<SiteVigieChiro>>(int statut, String corps) ->
                prevenirSansVerifier("Vigie-Chiro a répondu HTTP " + statut);
        }
    }

    private void refuserSiPointFixe(List<SiteVigieChiro> trouves) {
        trouves.stream().filter(SiteVigieChiro::estPointFixe).findFirst().ifPresent(existant -> {
            throw new RegleMetierException("Le carré " + carre + " existe déjà sur Vigie-Chiro en Point Fixe ("
                    + existant.titre() + "). Le créer ici produirait un doublon, et le dépôt échouerait faute"
                    + " de rattachement. Récupérez-le depuis l'application (« Mes sites » › « Nouveau site »"
                    + " › « Récupérer ce carré »), ou passez --sans-verification pour créer quand même.");
        });
    }

    private void prevenirSansVerifier(String cause) {
        spec.commandLine()
                .getErr()
                .println("Carré non vérifié sur Vigie-Chiro (" + cause + ") : s'il y existe déjà, ce site"
                        + " local ne sera pas rattaché.");
    }
}

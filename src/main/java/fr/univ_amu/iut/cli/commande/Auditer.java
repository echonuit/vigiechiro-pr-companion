package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.model.RapportAudit;
import fr.univ_amu.iut.audit.model.ServiceAuditCoherence;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.Severite;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Commande `audit-coherence` : audit de cohérence disque / base en lecture seule. Code de sortie `1`
/// dès qu'un constat de gravité [Severite#ERREUR] est relevé (pilotage par script), `0` sinon.
@Command(
        name = "audit-coherence",
        description = "Audite la cohérence disque / base en lecture seule : fichiers manquants ou orphelins, "
                + "préfixes non conformes, unités déposées divergentes.")
public final class Auditer implements Callable<Integer> {

    @Option(
            names = "--passage",
            paramLabel = "<id>",
            description = "Limite l'audit à ce passage. Sans cette option, audite tout le workspace.")
    private Long idPassage;

    @Option(names = "--json", description = "Émet les constats au format JSON (tableau) plutôt qu'en texte.")
    private boolean json;

    @Option(
            names = "--online",
            description = "Ajoute la vérification en ligne : confronte le dépôt au serveur Vigie-Chiro (#1132).")
    private boolean online;

    @Option(
            names = "--token",
            paramLabel = "<token>",
            description = "Token Vigie-Chiro pour --online (sinon $VIGIECHIRO_TOKEN ou la connexion enregistrée).")
    private String token;

    @Option(
            names = "--gravite",
            paramLabel = "<gravite>",
            description = "Ne garde que cette gravité : ${COMPLETION-CANDIDATES}.")
    private Severite gravite;

    @Option(
            names = "--categorie",
            paramLabel = "<categorie>",
            description = "Ne garde que cette nature de constat : ${COMPLETION-CANDIDATES}.")
    private CategorieConstat categorie;

    @Option(
            names = "--contient",
            paramLabel = "<texte>",
            description = "Ne garde que les constats dont la cible ou le détail contient ce texte "
                    + "(casse et accents ignorés), comme la recherche de l'écran.")
    private String contient;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma ;
    // résoudre ServiceAuditCoherence ici tirerait AuditPointsServeur → idUtilisateurCourant (requête SQL) sur
    // une base non migrée. On résout donc paresseusement, à l'exécution de la commande (cf. RecupererVigieChiro).
    private final Provider<ServiceAuditCoherence> service;

    @Inject
    public Auditer(Provider<ServiceAuditCoherence> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        if (online && token != null && !token.isBlank()) {
            System.setProperty("vigiechiro.token", token);
        }
        RapportAudit rapport = filtrer(calculer());
        sortie.println(json ? enJson(rapport) : enTexte(rapport));
        return rapport.aDesErreurs() ? 1 : 0;
    }

    /// Applique les critères de l'écran (#3258) : gravité, catégorie, recherche libre. Sans option,
    /// rend le rapport tel quel.
    ///
    /// **Le filtre s'applique avant le code de sortie**, et c'est la décision qui compte ici : le `1`
    /// décrit **ce qui a été imprimé**, pas la base entière. Un `audit-coherence --categorie
    /// DEPARTEMENT_DIVERGENT` qui n'affiche aucune erreur et rend pourtant `1` mentirait à qui le lit,
    /// et rendrait l'option inutilisable en script - or c'est son seul usage.
    ///
    /// Les deux énumérations sont typées : picocli **refuse** une valeur hors liste (erreur d'usage,
    /// code `2`) sans que la commande démarre. Une valeur valide qui ne correspond à rien rend, elle,
    /// un rapport **vide** et le code `0` - c'est une réponse, pas une faute ([ADR 3082](
    /// https://companion-dev.echonuit.fr/decisions/3082-designer-refuse-qualifier-rend-vide/) : un
    /// critère qui **qualifie** rend vide sans refuser).
    private RapportAudit filtrer(RapportAudit rapport) {
        if (gravite == null && categorie == null && contient == null) {
            return rapport;
        }
        return new RapportAudit(rapport.constats().stream().filter(this::retenu).toList());
    }

    private boolean retenu(ConstatAudit constat) {
        return (gravite == null || constat.severite() == gravite)
                && (categorie == null || constat.categorie() == categorie)
                && (contient == null || correspond(constat));
    }

    /// Même recherche que la barre de l'écran (`CriteresAudit.rechercheTexte`) : la **cible** et le
    /// **détail**, les deux colonnes en texte libre, casse et accents ignorés. Les autres colonnes ont
    /// leur option ; les inclure ici ferait répondre `--contient erreur` sur toutes les lignes en
    /// erreur, ce que `--gravite` dit mieux.
    private boolean correspond(ConstatAudit constat) {
        String aiguille = NormalisationTexte.normaliser(contient);
        return contientNormalise(constat.cible(), aiguille) || contientNormalise(constat.detail(), aiguille);
    }

    private static boolean contientNormalise(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }

    private RapportAudit calculer() {
        ServiceAuditCoherence audit = service.get();
        if (online) {
            List<ConstatAudit> tous = new ArrayList<>(audit.auditerTout().constats());
            tous.addAll(audit.auditerEnLigne().constats());
            return new RapportAudit(tous);
        }
        return idPassage == null ? audit.auditerTout() : audit.auditerPassage(idPassage);
    }

    private static String enJson(RapportAudit rapport) {
        List<Map<String, ?>> lignes =
                rapport.constats().stream().map(Auditer::projeter).toList();
        return FormatJson.tableau(lignes);
    }

    private static Map<String, ?> projeter(ConstatAudit constat) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("severite", constat.severite().name());
        champs.put("categorie", constat.categorie().name());
        champs.put("idPassage", constat.idPassage());
        champs.put("cible", constat.cible());
        champs.put("detail", constat.detail());
        return champs;
    }

    private static String enTexte(RapportAudit rapport) {
        if (rapport.sain()) {
            return "Cohérence disque / base : aucun écart détecté.";
        }
        StringBuilder texte = new StringBuilder();
        for (ConstatAudit constat : rapport.constats()) {
            texte.append('[').append(constat.severite()).append("] ").append(constat.categorie());
            if (constat.idPassage() != null) {
                texte.append(" (passage ").append(constat.idPassage()).append(')');
            }
            texte.append(' ')
                    .append(constat.cible())
                    .append(" - ")
                    .append(constat.detail())
                    .append(System.lineSeparator());
        }
        texte.append(rapport.constats().size())
                .append(" constat(s) : ")
                .append(rapport.nombre(Severite.ERREUR))
                .append(" erreur(s), ")
                .append(rapport.nombre(Severite.AVERTISSEMENT))
                .append(" avertissement(s), ")
                .append(rapport.nombre(Severite.INFO))
                .append(" info(s).");
        return texte.toString();
    }
}

package fr.univ_amu.iut.cli.commande.api;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `vigiechiro api lire --chemin <chemin>` : un **GET** sur l'API, dont le corps sort tel quel.
///
/// ## Ce qu'elle refuse, et pourquoi
///
/// Un tuyau générique rend à l'utilisateur les pièges que le client encapsule. Deux d'entre eux ne
/// **préviennent pas** quand on tombe dedans, et c'est précisément ce qui les rend coûteux :
///
/// - `max_results` au-delà de **100** : le serveur ne tronque pas, il **rejette** (`422`). Quand le
///   transport dégradait les échecs en silence, une collection entière revenait vide (#1277) ;
/// - `where=` : le serveur l'**accepte puis l'ignore**. Le filtre ne filtre rien, le total annoncé ne
///   bouge pas, et l'on croit avoir isolé ce qu'on cherchait.
///
/// Cette commande les **refuse avant d'émettre**, en disant lequel des deux et pourquoi. C'est la
/// condition qui rend l'échappatoire acceptable : elle rend la main sur les chemins, pas sur les
/// pièges.
///
/// ## Ce qu'elle ne fait pas
///
/// Aucun verbe configurable : **GET seulement** (ADR 0020, « écrire sur la plateforme : ne rien
/// inventer ni effacer »). Aucun sondage répété non plus (#1338) : on interroge le serveur quand
/// l'utilisateur le demande, pas en boucle.
@Command(name = "lire", description = "Lit un chemin de l'API (GET) et rend le corps tel quel.")
public final class ApiLire implements Callable<Integer>, LectureSeule {

    /// `max_results=<n>` dans la requête, quel que soit ce qui l'entoure.
    private static final Pattern MAX_RESULTS = Pattern.compile("max_results=(\\d+)");

    /// Plafond du `Paginator` du backend : au-delà, `422`.
    private static final int PLAFOND = 100;

    @Option(
            names = "--chemin",
            required = true,
            paramLabel = "<chemin>",
            description = "Chemin relatif à la base de l'API (ex. « /sites », « /moi/participations »).")
    private String chemin;

    @Option(names = "--page", paramLabel = "<n>", description = "Ajoute « ?max_results=100&page=<n> » au chemin.")
    private Integer page;

    @Option(names = "--token", paramLabel = "<jeton>", description = "Jeton VigieChiro, s'il n'est pas enregistré.")
    private String token;

    @Spec
    private CommandSpec spec;

    private final ClientVigieChiro client;

    @Inject
    public ApiLire(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Integer call() {
        if (token != null && !token.isBlank()) {
            System.setProperty("vigiechiro.token", token);
        }
        String demande = chemin.startsWith("/") ? chemin : "/" + chemin;
        refuserLesPieges(demande);
        if (page != null) {
            demande += (demande.contains("?") ? "&" : "?") + "max_results=" + PLAFOND + "&page=" + page;
        }
        spec.commandLine().getOut().println(exiger(client.lectureBrute(demande), demande));
        return 0;
    }

    /// Refuse **avant tout appel** un chemin qui tomberait dans un piège connu. Un refus explicite vaut
    /// mieux qu'une requête dont on ne saura pas lire le résultat.
    private static void refuserLesPieges(String chemin) {
        Matcher plafond = MAX_RESULTS.matcher(chemin);
        if (plafond.find() && Integer.parseInt(plafond.group(1)) > PLAFOND) {
            throw new RegleMetierException("« max_results » est plafonné à " + PLAFOND
                    + " : au-delà, le serveur rejette la requête (HTTP 422) au lieu de tronquer."
                    + " Demandez " + PLAFOND + " au plus, et paginez avec « --page ».");
        }
        if (chemin.toLowerCase(Locale.ROOT).contains("where=")) {
            throw new RegleMetierException("Ce backend accepte « where= » puis l'IGNORE : le filtre ne"
                    + " filtrerait rien, et le total annoncé ne bougerait pas - vous croiriez avoir isolé"
                    + " ce que vous cherchez. Lisez sans filtre, puis triez chez vous.");
        }
    }

    /// Traduit une issue d'API en corps lisible, ou en refus motivé (code 2).
    private static String exiger(ReponseApi<String> reponse, String chemin) {
        return switch (reponse) {
            case ReponseApi.Succes<String>(String corps) -> corps;
            case ReponseApi.NonConnecte<String> ignore ->
                throw new RegleMetierException(
                        "Non connecté à Vigie-Chiro : « " + chemin + " » n'a pas pu être lu.", new Besoin.Connexion());
            case ReponseApi.Injoignable<String>(String cause) ->
                throw new RegleMetierException(
                        "Vigie-Chiro est injoignable (" + cause + ") : « " + chemin + " » n'a pas pu être lu.");
            case ReponseApi.Refuse<String>(int statut, String corps) ->
                throw new RegleMetierException("Vigie-Chiro a refusé « " + chemin + " » (HTTP " + statut + " : " + corps
                        + "). Le chemin existe-t-il ? « vigiechiro api ressources » les liste.");
        };
    }
}

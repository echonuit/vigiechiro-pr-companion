package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.FuseauDuSite;
import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.commun.model.RegleMetierException;
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

/// `lister-participations-vigiechiro` (#3005) : vos **nuits déposées sur la plateforme**, avec leur
/// identifiant.
///
/// ## Le trou qu'elle comble
///
/// Plusieurs commandes réclament un `objectid` de participation (`importer-vigiechiro --participation`,
/// `reconstruire-passage --participation`) et **aucune ne le donnait**. `reconstruire-passage` sans
/// argument liste les participations **orphelines** - celles qui n'ont pas de passage local ; une nuit
/// déjà rattachée, elle, n'apparaissait nulle part. Retrouver son identifiant demandait d'ouvrir le
/// site web.
@Command(
        name = "lister-participations-vigiechiro",
        description = "Liste vos participations Vigie-Chiro (identifiant, site, point, date).")
public final class ListerParticipationsVigieChiro implements Callable<Integer>, LectureSeule {

    @Option(names = "--json", description = "Émet une enveloppe JSON plutôt qu'un tableau texte.")
    private boolean json;

    @Option(names = "--token", paramLabel = "<jeton>", description = "Jeton VigieChiro, s'il n'est pas enregistré.")
    private String token;

    @Spec
    private CommandSpec spec;

    private final ClientVigieChiro client;

    @Inject
    public ListerParticipationsVigieChiro(ClientVigieChiro client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Integer call() {
        if (token != null && !token.isBlank()) {
            System.setProperty("vigiechiro.token", token);
        }
        List<ParticipationVigieChiro> participations =
                exiger(client.mesParticipations(), "la liste de vos participations");
        PrintWriter sortie = spec.commandLine().getOut();

        if (json) {
            sortie.println(FormatJson.objet(enveloppe(participations)));
            return 0;
        }
        if (participations.isEmpty()) {
            // Zéro participation est une réponse, pas un échec : un compte neuf n'a rien déposé.
            sortie.println("Aucune participation sur Vigie-Chiro.");
            return 0;
        }
        sortie.printf("%-26s %-12s %-8s %s%n", "PARTICIPATION", "DATE", "POINT", "SITE");
        for (ParticipationVigieChiro participation : participations) {
            sortie.printf(
                    "%-26s %-12s %-8s %s%n",
                    participation.id(),
                    date(participation.dateDebut()),
                    ouTiret(participation.point()),
                    ouTiret(participation.siteTitre()));
        }
        sortie.println();
        sortie.println(participations.size() + " participation(s).");
        return 0;
    }

    /// La date arrive en ISO 8601 complet, **datée d'un décalage** ; on rend le **jour** de la nuit, lu
    /// dans le fuseau du site (#4017).
    ///
    /// Le jour seul, et non l'instant complet : c'est ce qui distingue deux nuits dans une liste, et
    /// c'est l'intention que portait déjà cette colonne. Ce qui change, c'est qu'on **convertit avant de
    /// couper**.
    ///
    /// ⚠️ Cette méthode coupait la chaîne au `T`. C'est plus qu'un défaut de format : la troncature
    /// change le **jour** dès que le décalage traverse minuit. `2026-07-03T23:30:00Z` est une nuit du
    /// **4** à Paris ; on annonçait le **3**.
    private static String date(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return "-";
        }
        return Horodatage.dateMuraleLisible(valeur, FuseauDuSite.ZONE);
    }

    /// Une valeur textuelle, ou le tiret des colonnes vides.
    private static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "-" : valeur;
    }

    private static Map<String, Object> enveloppe(List<ParticipationVigieChiro> participations) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("participations", participations.size());
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (ParticipationVigieChiro participation : participations) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("participation", participation.id());
            ligne.put("dateDebut", participation.dateDebut());
            ligne.put("point", participation.point());
            ligne.put("site", participation.siteTitre());
            lignes.add(ligne);
        }
        champs.put("elements", lignes);
        return champs;
    }

    /// Traduit une issue d'API en valeur, ou en refus motivé (code 2) - même patron que les autres
    /// commandes réseau, l'absence de jeton portant le [Besoin] dont la CLI tire le geste à taper.
    private static <T> T exiger(ReponseApi<T> reponse, String quoi) {
        return switch (reponse) {
            case ReponseApi.Succes<T>(T valeur) -> valeur;
            case ReponseApi.NonConnecte<T> ignore ->
                throw new RegleMetierException(
                        "Non connecté à Vigie-Chiro : " + quoi + " n'a pas pu être lue.", new Besoin.Connexion());
            case ReponseApi.Injoignable<T>(String cause) ->
                throw new RegleMetierException(
                        "Vigie-Chiro est injoignable (" + cause + ") : " + quoi + " n'a pas pu être lue.");
            case ReponseApi.Refuse<T>(int statut, String corps) ->
                throw new RegleMetierException(
                        "Vigie-Chiro a refusé de rendre " + quoi + " (HTTP " + statut + " : " + corps + ").");
        };
    }
}

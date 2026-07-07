package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `lister-sites` (#615) : liste les sites de l'utilisateur courant **avec leurs points d'écoute**, pour
/// retrouver les identifiants nécessaires aux autres commandes (`ajouter-point --site …`,
/// `importer --point …`). Lecture pure via [ServiceSites]. En `--json`, une ligne plate par point (avec son
/// site) — les sites sans point apparaissent avec des champs point à `null` — de façon à retrouver **tous**
/// les identifiants dans un seul tableau exploitable en script.
@Command(
        name = "lister-sites",
        description = "Liste les sites et leurs points d'écoute (pour retrouver les identifiants).")
public final class ListerSites implements Callable<Integer> {

    @Option(
            names = "--json",
            description = "Émet la liste au format JSON (une ligne par point, avec son site) plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final ServiceSites service;
    // Provider (résolu paresseusement) : le fournisseur d'utilisateur courant interroge la base, or picocli
    // instancie les sous-commandes au parsing, avant la migration. On ne lit l'id que dans call().
    private final Provider<String> idUtilisateur;

    @Inject
    public ListerSites(ServiceSites service, @Named("idUtilisateurCourant") Provider<String> idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<Site> sites = service.listerSites(idUtilisateur.get());

        if (json) {
            sortie.println(FormatJson.tableau(lignesJson(sites)));
            return 0;
        }
        if (sites.isEmpty()) {
            sortie.println("Aucun site enregistré.");
            return 0;
        }
        sortie.println(sites.size() + " site(s) :");
        for (Site site : sites) {
            sortie.println(enTeteSite(site));
            List<PointDEcoute> points = service.listerPoints(site.id());
            if (points.isEmpty()) {
                sortie.println("      (aucun point)");
            } else {
                for (PointDEcoute point : points) {
                    sortie.println("      Point #" + point.id() + "  " + point.code() + "  " + gps(point));
                }
            }
        }
        return 0;
    }

    /// En-tête lisible d'un site : « Site #id  carré NNNNNN  « nom »  [protocole] » (le nom est omis s'il
    /// est vide).
    private static String enTeteSite(Site site) {
        String nom =
                site.nomConvivial() == null || site.nomConvivial().isBlank() ? "" : "  « " + site.nomConvivial() + " »";
        return "  Site #" + site.id() + "  carré " + site.numeroCarre() + nom + "  ["
                + site.protocole().libelle() + "]";
    }

    /// Coordonnées lisibles d'un point (degrés décimaux, point décimal), ou `(sans GPS)` si absentes.
    private static String gps(PointDEcoute point) {
        if (point.latitude() == null || point.longitude() == null) {
            return "(sans GPS)";
        }
        return String.format(Locale.ROOT, "(%.5f, %.5f)", point.latitude(), point.longitude());
    }

    /// Table plate site×point : une ligne par point ; un site sans point donne une ligne aux champs point
    /// à `null` (son identifiant reste ainsi récupérable).
    private List<Map<String, Object>> lignesJson(List<Site> sites) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Site site : sites) {
            List<PointDEcoute> points = service.listerPoints(site.id());
            if (points.isEmpty()) {
                lignes.add(ligneJson(site, null));
            } else {
                for (PointDEcoute point : points) {
                    lignes.add(ligneJson(site, point));
                }
            }
        }
        return lignes;
    }

    private static Map<String, Object> ligneJson(Site site, PointDEcoute point) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("site", site.id());
        objet.put("carre", site.numeroCarre());
        objet.put("nom", site.nomConvivial());
        objet.put("protocole", site.protocole().libelle());
        objet.put("point", point == null ? null : point.id());
        objet.put("code", point == null ? null : point.code());
        objet.put("latitude", point == null ? null : point.latitude());
        objet.put("longitude", point == null ? null : point.longitude());
        return objet;
    }
}

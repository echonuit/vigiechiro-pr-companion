package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.EcrivainCsv;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

/// `solde-saison` (#2356) : ce qu'il reste à faire, point par point, pour une saison. Restitue les
/// règles R3/R4 via le **même** [ServiceSoldeSaison] que l'écran « Ma saison » (parité IHM/CLI garantie
/// par le service partagé). Trois formats : `texte` (défaut, lisible), `csv` et `json` (scriptables).
@Command(
        name = "solde-saison",
        description = "Solde d'une saison : passages faits, manquants et action restante, point par point.")
public final class AfficherSoldeSaison implements Callable<Integer> {

    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");

    private static final List<String> ENTETE_CSV = List.of(
            "Carré",
            "Point",
            "Statut P1",
            "Date P1",
            "Verdict P1",
            "Statut P2",
            "Date P2",
            "Verdict P2",
            "Reste à faire");

    @Option(names = "--annee", description = "Année de la saison (par défaut : la saison courante).")
    private Integer annee;

    @Option(names = "--format", description = "Format de sortie : texte (défaut), csv ou json.")
    private String format = "texte";

    @Spec
    private CommandSpec spec;

    private final ServiceSoldeSaison service;
    // Provider paresseux : le fournisseur d'utilisateur courant interroge la base, or picocli instancie
    // les sous-commandes au parsing, avant la migration. On ne lit l'id que dans call().
    private final Provider<String> idUtilisateur;

    @Inject
    public AfficherSoldeSaison(
            ServiceSoldeSaison service, @Named("idUtilisateurCourant") Provider<String> idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    @Override
    public Integer call() {
        String id = idUtilisateur.get();
        SoldeSaison solde = annee != null ? service.soldePour(id, annee) : service.soldeCourant(id);
        PrintWriter sortie = spec.commandLine().getOut();
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> {
                sortie.println(FormatJson.tableau(lignesJson(solde)));
                yield 0;
            }
            case "csv" -> {
                // print (et non println) pour préserver le formatage exact d'EcrivainCsv ; flush explicite
                // car le PrintWriter de picocli n'auto-flush que sur println.
                sortie.print(new EcrivainCsv().versChaine(lignesCsv(solde)));
                sortie.flush();
                yield 0;
            }
            case "texte" -> {
                renduTexte(solde, sortie);
                yield 0;
            }
            default -> {
                spec.commandLine().getErr().println("Format inconnu : " + format + " (attendu : texte, csv ou json).");
                yield 2;
            }
        };
    }

    private void renduTexte(SoldeSaison solde, PrintWriter sortie) {
        if (solde.lignes().isEmpty()) {
            sortie.println("Aucun point suivi pour la saison " + solde.annee() + ".");
            return;
        }
        sortie.println("Solde de la saison " + solde.annee() + " : " + solde.pointsSuivis()
                + " point(s) suivi(s), " + solde.passagesFaits() + "/" + solde.passagesAttendus()
                + " passage(s) fait(s).");
        sortie.println("Fenêtre du second passage : jusqu'au "
                + solde.echeanceSecondPassage().format(JOUR_MOIS) + " (" + solde.pointsSecondPassageEnAttente()
                + " point(s) en attente).");
        for (LigneSaison ligne : solde.lignes()) {
            String reste = ligne.resteAFaire().isEmpty() ? "rien" : ligne.resteAFaire();
            sortie.println("  " + ligne.numeroCarre() + " / " + ligne.codePoint()
                    + "   P1 " + descriptif(ligne.passage1())
                    + "   P2 " + descriptif(ligne.passage2())
                    + "   -> " + reste);
        }
    }

    private static String descriptif(CasePassage cas) {
        if (!cas.presente()) {
            return "absent";
        }
        String base = "[" + cas.statut().libelle() + "]";
        if (cas.date() != null) {
            base += " " + cas.date().format(JOUR_MOIS);
        }
        if (cas.inexploitable()) {
            base += " inexploitable";
        }
        return base;
    }

    private static List<List<String>> lignesCsv(SoldeSaison solde) {
        List<List<String>> lignes = new ArrayList<>();
        lignes.add(ENTETE_CSV);
        for (LigneSaison ligne : solde.lignes()) {
            lignes.add(List.of(
                    ligne.numeroCarre(),
                    ligne.codePoint(),
                    statut(ligne.passage1()),
                    date(ligne.passage1()),
                    verdict(ligne.passage1()),
                    statut(ligne.passage2()),
                    date(ligne.passage2()),
                    verdict(ligne.passage2()),
                    ligne.resteAFaire()));
        }
        return lignes;
    }

    private static List<Map<String, Object>> lignesJson(SoldeSaison solde) {
        List<Map<String, Object>> objets = new ArrayList<>();
        for (LigneSaison ligne : solde.lignes()) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("carre", ligne.numeroCarre());
            objet.put("point", ligne.codePoint());
            objet.put("statut1", champ(statut(ligne.passage1())));
            objet.put("date1", champ(date(ligne.passage1())));
            objet.put("verdict1", champ(verdict(ligne.passage1())));
            objet.put("statut2", champ(statut(ligne.passage2())));
            objet.put("date2", champ(date(ligne.passage2())));
            objet.put("verdict2", champ(verdict(ligne.passage2())));
            objet.put("resteAFaire", ligne.resteAFaire());
            objets.add(objet);
        }
        return objets;
    }

    private static String statut(CasePassage cas) {
        return cas.presente() ? cas.statut().libelle() : "";
    }

    private static String date(CasePassage cas) {
        LocalDate date = cas.date();
        return date == null ? "" : date.toString();
    }

    private static String verdict(CasePassage cas) {
        return cas.verdict() == null ? "" : cas.verdict().libelle();
    }

    /// Une chaîne vide (champ de passage absent) devient `null` en JSON, plus juste pour un script.
    private static Object champ(String valeur) {
        return valeur.isEmpty() ? null : valeur;
    }
}

package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.commun.model.EcrivainCsv;
import fr.univ_amu.iut.commun.model.LieuQualifie;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.FiltresSaison;
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
import java.util.stream.Collectors;
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
public final class AfficherSoldeSaison implements Callable<Integer>, LectureSeule {

    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");

    private static final List<String> ENTETE_CSV = List.of(
            "Carré",
            "Nom du carré",
            "Point",
            "Commune",
            "Statut P1",
            "Date P1",
            "Verdict P1",
            "Statut P2",
            "Date P2",
            "Verdict P2",
            "Hors protocole",
            "Reste à faire");

    @Option(names = "--annee", description = "Année de la saison (par défaut : la saison courante).")
    private Integer annee;

    @Option(
            names = "--campagne",
            description = "Ne garder que les points d'une campagne (fragment du nom, insensible à la casse).")
    private String campagne;

    @Option(
            names = "--lieu",
            description = "Ne garder que les points dont le carré, le nom qu'on lui a donné ou le code"
                    + " du point contient ce texte (insensible à la casse et aux accents).")
    private String lieu;

    @Option(
            names = "--reste-a-faire",
            description =
                    "Ne garder que les points qui ne sont pas à jour : ceux dont il reste une" + " action à mener.")
    private boolean resteAFaire;

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
        SoldeSaison solde = annee != null ? service.soldePour(id, annee, campagne) : service.soldeCourant(id, campagne);
        PrintWriter sortie = spec.commandLine().getOut();
        // Les deux filtres de l'écran « Ma saison » (#3103), portés ici à la clôture de #3092. Ils ne
        // touchent QUE la liste des points : le solde reste celui de la saison entière, comme à l'écran.
        // Filtrer change ce qu'on regarde, pas ce qu'il y a à faire - et les compteurs de SoldeSaison se
        // déduisant de ses lignes, reconstruire le record ferait mentir l'en-tête.
        List<LigneSaison> retenues = restreindre(solde.lignes());
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> {
                sortie.println(FormatJson.tableau(lignesJson(retenues)));
                yield 0;
            }
            case "csv" -> {
                // print (et non println) pour préserver le formatage exact d'EcrivainCsv ; flush explicite
                // car le PrintWriter de picocli n'auto-flush que sur println.
                sortie.print(new EcrivainCsv().versChaine(lignesCsv(retenues)));
                sortie.flush();
                yield 0;
            }
            case "texte" -> {
                renduTexte(solde, retenues, sortie);
                yield 0;
            }
            default -> {
                spec.commandLine().getErr().println("Format inconnu : " + format + " (attendu : texte, csv ou json).");
                yield 2;
            }
        };
    }

    private void renduTexte(SoldeSaison solde, List<LigneSaison> retenues, PrintWriter sortie) {
        if (solde.lignes().isEmpty()) {
            sortie.println("Aucun point suivi pour la saison " + solde.annee() + ".");
            return;
        }
        // Même ventilation exhaustive que l'en-tête de l'écran « Ma saison » : les trois nombres somment
        // aux passages attendus, et les nuits hors protocole se disent à part (elles ne sont pas attendues).
        sortie.println("Solde de la saison " + solde.annee() + " : " + solde.pointsSuivis()
                + " point(s) suivi(s), " + solde.passagesFaits() + " faits, " + solde.passagesARefaire()
                + " à refaire, " + solde.passagesARealiser() + " à réaliser sur " + solde.passagesAttendus()
                + " attendus"
                + (solde.nuitsHorsProtocole() == 0 ? "" : " (" + solde.nuitsHorsProtocole() + " hors protocole)")
                + ".");
        sortie.println("Fenêtre du second passage : jusqu'au "
                + solde.echeanceSecondPassage().format(JOUR_MOIS) + " (" + solde.pointsSecondPassageEnAttente()
                + " point(s) en attente).");
        if (retenues.isEmpty()) {
            // Un filtre qui ne retient rien se DIT : sans cette ligne, la commande afficherait l'en-tête
            // puis rien, et l'on croirait la saison sans point plutôt que le filtre trop étroit.
            sortie.println("  (aucun point ne correspond aux filtres)");
            return;
        }
        for (LigneSaison ligne : retenues) {
            String reste = ligne.resteAFaire().isEmpty() ? "rien" : ligne.resteAFaire();
            String hors = horsProtocole(ligne);
            sortie.println("  " + LieuQualifie.qualifier(ligne.numeroCarre(), ligne.nomSite())
                    + " / " + ligne.codePoint()
                    + (ligne.commune() == null ? "" : " (" + ligne.commune() + ")")
                    + "   P1 " + descriptif(ligne.passage1())
                    + "   P2 " + descriptif(ligne.passage2())
                    + (hors.isEmpty() ? "" : "   [hors protocole : " + hors + "]")
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
        if (cas.opportuniste()) {
            base += " opportuniste";
        }
        if (cas.inexploitable()) {
            base += " inexploitable";
        }
        return base;
    }

    /// Applique `--lieu` puis `--reste-a-faire`. Les deux règles sont lues sur [FiltresSaison], la
    /// même écriture que la barre de filtres de l'écran « Ma saison ».
    private List<LigneSaison> restreindre(List<LigneSaison> lignes) {
        List<LigneSaison> retenues = FiltresSaison.parLieu(lignes, lieu);
        return resteAFaire ? FiltresSaison.resteAFaire(retenues) : retenues;
    }

    private static List<List<String>> lignesCsv(List<LigneSaison> retenues) {
        List<List<String>> lignes = new ArrayList<>();
        lignes.add(ENTETE_CSV);
        for (LigneSaison ligne : retenues) {
            lignes.add(List.of(
                    ligne.numeroCarre(),
                    ligne.nomSite() == null ? "" : ligne.nomSite(),
                    ligne.codePoint(),
                    ligne.commune() == null ? "" : ligne.commune(),
                    statut(ligne.passage1()),
                    date(ligne.passage1()),
                    verdict(ligne.passage1()),
                    statut(ligne.passage2()),
                    date(ligne.passage2()),
                    verdict(ligne.passage2()),
                    horsProtocole(ligne),
                    ligne.resteAFaire()));
        }
        return lignes;
    }

    private static List<Map<String, Object>> lignesJson(List<LigneSaison> retenues) {
        List<Map<String, Object>> objets = new ArrayList<>();
        for (LigneSaison ligne : retenues) {
            Map<String, Object> objet = new LinkedHashMap<>();
            objet.put("carre", ligne.numeroCarre());
            objet.put("nom_site", champ(ligne.nomSite()));
            objet.put("point", ligne.codePoint());
            objet.put("commune", champ(ligne.commune()));
            objet.put("statut1", champ(statut(ligne.passage1())));
            objet.put("date1", champ(date(ligne.passage1())));
            objet.put("verdict1", champ(verdict(ligne.passage1())));
            objet.put("statut2", champ(statut(ligne.passage2())));
            objet.put("date2", champ(date(ligne.passage2())));
            objet.put("verdict2", champ(verdict(ligne.passage2())));
            objet.put("horsProtocole", champ(horsProtocole(ligne)));
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

    /// Les nuits **hors protocole** du point (#2525), datées, séparées par un point médian ; vide
    /// dans le cas courant. Elles ne figurent pas dans les colonnes de passage : elles y prendraient la
    /// place d'un passage attendu qui, lui, reste à faire.
    private static String horsProtocole(LigneSaison ligne) {
        return ligne.horsProtocole().stream()
                .map(cas -> cas.date() == null
                        ? "opportuniste"
                        : "opportuniste " + cas.date().format(JOUR_MOIS))
                .collect(Collectors.joining(" · "));
    }

    /// Une chaîne vide (champ de passage absent) devient `null` en JSON, plus juste pour un script.
    ///
    /// Elle accepte aussi le `null` d'entrée depuis #3313. Les champs de passage venaient toujours
    /// non nuls ; le **nom du carré** (#3289) et la **commune** (#3313), eux, sont absents quand
    /// l'utilisateur n'en a pas donné ou que la résolution n'a pas eu lieu. Sans cette garde, un carré
    /// sans nom faisait échouer `--format json` sur un NullPointerException - un défaut latent que
    /// #3289 avait introduit et qu'aucun test n'attrapait, faute d'un cas croisant « sans nom » et JSON.
    private static Object champ(String valeur) {
        return valeur == null || valeur.isEmpty() ? null : valeur;
    }
}

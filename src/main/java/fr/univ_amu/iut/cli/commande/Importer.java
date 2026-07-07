package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `importer` (P2) : importe une nuit d'enregistrement (copie protégée + renommage + transformation) pour
/// un point d'écoute, via [ServiceImport]. L'année et le numéro de passage sont **déduits** si omis (année
/// courante, prochain numéro libre du point).
@Command(
        name = "importer",
        description = "Importe une nuit d'enregistrement (copie, renommage, transformation) pour un point.")
public final class Importer implements Callable<Integer> {

    @Option(
            names = "--source",
            required = true,
            paramLabel = "<dir>",
            description = "Dossier de la carte SD (ou copie) à importer. Lu en seule lecture.")
    private Path source;

    @Option(
            names = "--point",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du point d'écoute auquel rattacher la nuit (voir « lister-passages »).")
    private long point;

    @Option(names = "--annee", paramLabel = "<N>", description = "Année du passage. Défaut : année courante.")
    private Integer annee;

    @Option(
            names = "--passage",
            paramLabel = "<N>",
            description = "Numéro de passage. Défaut : prochain numéro libre pour ce point.")
    private Integer numeroPassage;

    @Spec
    private CommandSpec spec;

    private final PointDao pointDao;
    private final SiteDao siteDao;
    private final PassageDao passageDao;
    private final ServiceImport service;
    private final Horloge horloge;

    @Inject
    public Importer(PointDao pointDao, SiteDao siteDao, PassageDao passageDao, ServiceImport service, Horloge horloge) {
        this.pointDao = Objects.requireNonNull(pointDao, "pointDao");
        this.siteDao = Objects.requireNonNull(siteDao, "siteDao");
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
        this.service = Objects.requireNonNull(service, "service");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();

        PointDEcoute pointDEcoute = pointDao.findById(point)
                .orElseThrow(() -> new ErreurUsage("Point d'écoute introuvable : --point " + point + "."));
        Site site = siteDao.findById(pointDEcoute.idSite())
                .orElseThrow(
                        () -> new ErreurUsage("Site introuvable pour le point " + point + " (incohérence en base)."));

        int anneeEffective = annee != null ? annee : horloge.aujourdhui().getYear();
        int numeroEffectif = numeroPassage != null ? numeroPassage : prochainNumero(passageDao, point);
        Prefixe prefixe = new Prefixe(site.numeroCarre(), anneeEffective, numeroEffectif, pointDEcoute.code());

        ResultatImport resultat = service.importer(source, point, prefixe);

        sortie.println("Import réussi.");
        sortie.println("  Passage     : #" + resultat.passage().id());
        sortie.println("  Quadruplet  : carré " + site.numeroCarre()
                + " / point " + pointDEcoute.code()
                + " / " + anneeEffective + " / passage " + numeroEffectif);
        sortie.println("  Statut      : " + resultat.passage().statutWorkflow().libelle());
        sortie.println("  Enregistreur: " + resultat.numeroSerieEnregistreur());
        sortie.println("  Originaux   : " + resultat.nombreOriginaux());
        sortie.println("  Séquences   : " + resultat.nombreSequences());

        RapportImport rapport = resultat.rapport();
        sortie.println("  Rapport     : " + rapport.resume());
        Path rapportCsv = Path.of(resultat.session().cheminRacine()).resolve("rapport-import.csv");
        try {
            Files.writeString(rapportCsv, rapport.versCsv());
            sortie.println("  Rapport CSV : " + rapportCsv.toAbsolutePath());
        } catch (IOException echec) {
            sortie.println("  (rapport CSV non écrit : " + echec.getMessage() + ")");
        }
        return 0;
    }

    private static int prochainNumero(PassageDao passageDao, long idPoint) {
        return passageDao.findByPoint(idPoint).stream()
                        .mapToInt(Passage::numeroPassage)
                        .max()
                        .orElse(0)
                + 1;
    }
}

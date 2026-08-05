package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.EcrivainCsv;
import fr.univ_amu.iut.passage.model.NuitsOpportunistes;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAnalyseDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Service de la feature **`analyse`** (prisme « Espèces & biodiversité ») : expose la **lecture
/// transverse** des observations de l'utilisateur, pour répondre à « quelles espèces, où, combien ». Pur
/// **model** (aucune dépendance IHM/navigation).
///
/// Fournit les **observations enrichies** de [ProjectionsAnalyseDao] (feature `validation`) ; le **filtrage**
/// (statut, taxon parent, texte) et l'**agrégation** (par espèce / par carré, via [AgregationAnalyse]) se
/// font **côté client** dans le ViewModel (#537), sur le socle partagé de filtres. À l'échelle visée
/// (~4000 observations), tout se fait en mémoire, sans ré-interroger la base à chaque changement de filtre.
public class ServiceAnalyse {

    private final ProjectionsAnalyseDao observationDao;
    private final NuitsOpportunistes nuitsOpportunistes;

    public ServiceAnalyse(ProjectionsAnalyseDao observationDao, NuitsOpportunistes nuitsOpportunistes) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.nuitsOpportunistes = Objects.requireNonNull(nuitsOpportunistes, "nuitsOpportunistes");
    }

    /// Les passages marqués **opportunistes** (#2525), pour la dimension de filtre « Nature de la nuit »
    /// (#2614) : une nuit réalisée sur le carré d'un tiers ne compte pas comme une nuit du protocole.
    /// Lecture groupée, dont le ViewModel garde un instantané le temps d'un chargement.
    public Set<Long> nuitsOpportunistes() {
        return nuitsOpportunistes.identifiants();
    }

    /// **Observations enrichies** de l'utilisateur (#537 étape 4) : la matière **filtrée et agrégée côté
    /// client** par le ViewModel (statut, taxon parent, texte via le socle [Filtres], puis [AgregationAnalyse]
    /// par espèce / par carré). Chargées une fois à l'ouverture de l'écran.
    public List<ObservationAnalyse> observationsAnalyse(String idUtilisateur) {
        return observationDao.observationsAnalyse(idUtilisateur);
    }

    /// **Détail** d'une espèce : ses observations à travers les passages de l'utilisateur, filtrées par
    /// `statut` (`null` = tous). Alimente le panneau maître-détail de l'écran.
    public List<ObservationEspece> observationsDeLEspece(
            String idUtilisateur, String codeEspece, StatutObservation statut) {
        return observationDao.observationsDeLEspece(idUtilisateur, codeEspece, statut);
    }

    /// En-têtes CSV des deux inventaires (séparés ici : un seul endroit où vit le libellé, cf. PMD
    /// `AvoidDuplicateLiterals`).
    private static final List<String> ENTETE_ESPECES = List.of(
            "code",
            "nom_latin",
            "nom_vernaculaire",
            "groupe",
            "detections",
            "passages",
            "carres",
            "points",
            "annee_min",
            "annee_max");

    private static final List<String> ENTETE_CARRES =
            List.of("carre", "site", "richesse", "detections", "annee_min", "annee_max");

    /// Exporte l'inventaire **par espèce** (les lignes fournies, dans l'ordre voulu) en CSV.
    public void exporterEspeces(Path destination, List<EspeceAgregee> especes) {
        Objects.requireNonNull(destination, "destination");
        new EcrivainCsv().ecrire(destination, tableEspeces(especes));
    }

    /// Exporte l'inventaire **par carré** (les lignes fournies) en CSV.
    public void exporterCarres(Path destination, List<CarreEspeces> carres) {
        Objects.requireNonNull(destination, "destination");
        new EcrivainCsv().ecrire(destination, tableCarres(carres));
    }

    /// L'inventaire par espèce **en lignes de cellules**, en-tête compris.
    ///
    /// Extraite de l'export (#3269) pour que `lister-especes` rende sur la sortie standard exactement ce
    /// que `--sortie` écrirait : mêmes colonnes, mêmes valeurs, même échappement, puisque c'est le même
    /// [EcrivainCsv] qui met en forme. Deux CSV qui différeraient selon leur destination seraient un
    /// piège à script.
    public static List<List<String>> tableEspeces(List<EspeceAgregee> especes) {
        List<List<String>> table = new ArrayList<>();
        table.add(ENTETE_ESPECES);
        for (EspeceAgregee espece : especes) {
            table.add(Arrays.asList(
                    espece.code(),
                    chaine(espece.nomLatin()),
                    chaine(espece.nomVernaculaireFr()),
                    chaine(espece.groupe()),
                    Integer.toString(espece.nbObservations()),
                    Integer.toString(espece.nbPassages()),
                    Integer.toString(espece.nbCarres()),
                    Integer.toString(espece.nbPoints()),
                    Integer.toString(espece.anneeMin()),
                    Integer.toString(espece.anneeMax())));
        }
        return table;
    }

    /// L'inventaire par carré **en lignes de cellules**, en-tête compris. Voir [#tableEspeces].
    public static List<List<String>> tableCarres(List<CarreEspeces> carres) {
        List<List<String>> table = new ArrayList<>();
        table.add(ENTETE_CARRES);
        for (CarreEspeces carre : carres) {
            table.add(Arrays.asList(
                    carre.numeroCarre(),
                    chaine(carre.nomSite()),
                    Integer.toString(carre.richesse()),
                    Integer.toString(carre.nbObservations()),
                    Integer.toString(carre.anneeMin()),
                    Integer.toString(carre.anneeMax())));
        }
        return table;
    }

    private static String chaine(String valeur) {
        return valeur == null ? "" : valeur;
    }
}

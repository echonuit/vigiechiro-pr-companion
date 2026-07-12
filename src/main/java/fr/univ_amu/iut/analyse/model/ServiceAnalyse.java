package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.EcrivainCsv;
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

    public ServiceAnalyse(ProjectionsAnalyseDao observationDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
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
        new EcrivainCsv().ecrire(destination, table);
    }

    /// Exporte l'inventaire **par carré** (les lignes fournies) en CSV.
    public void exporterCarres(Path destination, List<CarreEspeces> carres) {
        Objects.requireNonNull(destination, "destination");
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
        new EcrivainCsv().ecrire(destination, table);
    }

    private static String chaine(String valeur) {
        return valeur == null ? "" : valeur;
    }
}

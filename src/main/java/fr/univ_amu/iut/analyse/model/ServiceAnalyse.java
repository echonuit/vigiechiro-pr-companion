package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.EcrivainCsv;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// Service de la feature **`analyse`** (prisme « Espèces & biodiversité ») : expose la **lecture
/// transverse** des observations, agrégées par **espèce** ou par **carré** (richesse spécifique), pour
/// répondre à « quelles espèces, où, combien ». Pur **model** (aucune dépendance IHM/navigation).
///
/// S'appuie sur les projections de [ObservationDao] (feature `validation`) — comme [ServiceMultisite]
/// s'appuie sur les DAO de `sites`/`passage` — sans redéfinir l'agrégation. Le **filtre de statut** de
/// revue (`null` = toutes les observations) est appliqué à la source (SQL).
public class ServiceAnalyse {

    private final ObservationDao observationDao;

    public ServiceAnalyse(ObservationDao observationDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
    }

    /// Inventaire **par espèce** des observations de l'utilisateur, filtré par `statut` (`null` = tous).
    public List<EspeceAgregee> inventaireParEspece(String idUtilisateur, StatutObservation statut) {
        return observationDao.inventaireParEspece(idUtilisateur, statut);
    }

    /// Inventaire **par carré** (richesse spécifique) des observations de l'utilisateur, filtré par
    /// `statut` (`null` = tous).
    public List<CarreEspeces> inventaireParCarre(String idUtilisateur, StatutObservation statut) {
        return observationDao.inventaireParCarre(idUtilisateur, statut);
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

package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.EcrivainCsv;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
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
/// S'appuie sur les **observations enrichies** de [ObservationDao] (feature `validation`), puis **filtre
/// (statut) et agrège côté client** via [AgregationAnalyse] (#537 étape 4) — l'agrégation n'est plus faite
/// en SQL. À l'échelle visée (~4000 observations), le regroupement en mémoire est immédiat et évite de
/// ré-interroger la base à chaque changement de filtre.
public class ServiceAnalyse {

    private final ObservationDao observationDao;

    public ServiceAnalyse(ObservationDao observationDao) {
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
    }

    /// Inventaire **par espèce** des observations de l'utilisateur, filtré par `statut` (`null` = tous).
    /// L'agrégation se fait **côté client** (#537 étape 4) : on lit les observations enrichies puis on les
    /// regroupe via [AgregationAnalyse], plutôt qu'en SQL.
    public List<EspeceAgregee> inventaireParEspece(String idUtilisateur, StatutObservation statut) {
        return AgregationAnalyse.parEspece(observationsFiltrees(idUtilisateur, statut));
    }

    /// Inventaire **par carré** (richesse spécifique) des observations de l'utilisateur, filtré par
    /// `statut` (`null` = tous). Agrégation **côté client** (cf. [#inventaireParEspece]).
    public List<CarreEspeces> inventaireParCarre(String idUtilisateur, StatutObservation statut) {
        return AgregationAnalyse.parCarre(observationsFiltrees(idUtilisateur, statut));
    }

    /// Observations enrichies de l'utilisateur, **filtrées par statut** en mémoire (`null` = toutes) :
    /// matière commune des deux agrégations. Un [ObservationAnalyse#statut()] est comparé au statut demandé.
    private List<ObservationAnalyse> observationsFiltrees(String idUtilisateur, StatutObservation statut) {
        return observationDao.observationsAnalyse(idUtilisateur).stream()
                .filter(observation -> statut == null || observation.statut() == statut)
                .toList();
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

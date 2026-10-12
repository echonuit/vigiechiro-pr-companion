package fr.univ_amu.iut.perf.outils;

import static fr.univ_amu.iut.perf.outils.EcranDuBanc.RELEVES;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.surLeFil;
import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Releve;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Locale;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Banc de l'**IHM** pour l'objectif O5 (#105) : l'écran des sons à 4031 observations, la vue multisite à
/// 1000 passages, et la topologie de 150 carrés de 2 points. Étiqueté `banc-ihm`, exclu par défaut et
/// jamais joué en CI : un chiffre ne vaut que pour la machine qui l'a produit. Il se lance à la main, sur
/// l'affichage réel, et ajoute son relevé à `releve.tsv` sous `perf.ihm.racine` :
///
/// ```bash
/// ./mvnw test -Precette-filmee -Dsurefire.groups=banc-ihm -Dsurefire.excludedGroups=api-live -Dtest='BancIhm*'
/// ```
///
/// Le robot reste hors du chronomètre : chaque geste passe par l'API du contrôle, et le temps court
/// jusqu'au premier pulse, après mise en page, où l'écran montre le résultat (`InstrumentsDuBanc`). Le
/// protocole, ce que le banc ne mesure pas et ses contrôles sont dans `docs/benchmarks/README.md`.
@Tag("banc-ihm")
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class BancIhmO5Test {

    private static final int OBSERVATIONS = Integer.getInteger("perf.ihm.observations", 4031);
    private static final int PASSAGES = Integer.getInteger("perf.ihm.passages", 1000);
    private static final String TABLE_SONS = "#tableObservations";
    private static final String TABLE_PASSAGES = "#tableLignes";

    private EcranDuBanc ecran;

    @Start
    void start(Stage stage) {
        ecran = new EcranDuBanc(stage);
    }

    @AfterEach
    void ranger() throws Exception {
        ecran.arreterLaSonde();
        System.clearProperty("vigiechiro.workspace");
        System.clearProperty("perf.sites");
        System.clearProperty("perf.points");
    }

    @Test
    @DisplayName("O5 : l'écran des sons à 4031 observations (ouverture, tri, filtre, clic, revue)")
    void o5_observations() throws Exception {
        monterLeJeu("o5-observations");
        Releve releve = ecran.releve("O5 observations");
        ecran.controlerLInstrument(releve);
        ecran.temoinAVide(releve);
        SourceObservations sons = sourceDesObservations();
        ecran.mesurerOuverture(
                releve,
                "ouverture de l'écran des sons",
                ecran::ouvrirMesSites,
                () -> ecran.ouvrirSons(sons),
                () -> ecran.lignes(TABLE_SONS) == OBSERVATIONS);

        @SuppressWarnings("unchecked")
        TableView<LigneObservationAudio> table = ecran.noeud(TABLE_SONS, TableView.class);
        TableColumn<?, ?> proba = colonne(table, "colProba");
        for (int i = 0; i <= RELEVES; i++) {
            TableColumn.SortType sens = sensDu(i);
            releve.noter("tri par probabilité", i, ecran.chrono(() -> trier(table, proba, sens), () -> true));
        }

        TextField recherche = surLeFil(() -> ecran.dansLEcran("#champRecherche", TextField.class));
        String[] taxons = {"Nyclei", "Tadten", "Rhihip", "Pippip"};
        for (int i = 0; i <= RELEVES; i++) {
            String taxon = taxons[i % taxons.length];
            releve.noter("filtre par taxon", i, ecran.chrono(() -> recherche.setText(taxon), () -> {
                int n = ecran.lignes(TABLE_SONS);
                return n > 0 && n < OBSERVATIONS;
            }));
            releve.noter(
                    "retrait du filtre",
                    i,
                    ecran.chrono(() -> recherche.setText(""), () -> ecran.lignes(TABLE_SONS) == OBSERVATIONS));
        }

        for (int i = 0; i <= RELEVES; i++) {
            int rang = 2 + 3 * i;
            releve.noter(
                    "clic sur une ligne (sélection)",
                    i,
                    ecran.chrono(() -> table.getSelectionModel().clearAndSelect(rang), () -> true));
        }

        Button valider = ecran.noeud("#btnValider", Button.class);
        for (int i = 0; i <= RELEVES; i++) {
            int rang = 20 + i;
            surLeFil(() -> {
                table.getSelectionModel().clearAndSelect(rang);
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
            LigneObservationAudio visee = surLeFil(() -> table.getItems().get(rang));
            assertThat(visee.statut())
                    .as("la ligne visée doit être à revoir, sinon la valider ne change rien")
                    .isEqualTo(StatutObservation.NON_TOUCHEE);
            assertThat(surLeFil(valider::isDisabled))
                    .as("« Valider » doit être actif sur une ligne non revue")
                    .isFalse();
            releve.noter(
                    "action de revue (valider)",
                    i,
                    ecran.chrono(
                            valider::fire, () -> statutDe(table, visee.idObservation()) == StatutObservation.VALIDEE));
        }
        releve.noterGel("sonde sur tout le banc", ecran.sonde().relever());
        releve.ecrire();
    }

    @Test
    @DisplayName("O5 : la vue multisite à 1000 passages (ouverture, tri par colonne, filtre)")
    void o5_passages() throws Exception {
        monterLeJeu("o5-passages");
        Releve releve = ecran.releve("O5 passages");
        ecran.temoinAVide(releve);
        ecran.mesurerOuverture(
                releve,
                "ouverture de la vue multisite",
                ecran::ouvrirMesSites,
                ecran::ouvrirMultisite,
                () -> ecran.lignes(TABLE_PASSAGES) == PASSAGES);

        TableView<?> table = ecran.noeud(TABLE_PASSAGES, TableView.class);
        TableColumn<?, ?> date = colonne(table, "colDate");
        for (int i = 0; i <= RELEVES; i++) {
            TableColumn.SortType sens = sensDu(i);
            releve.noter("tri par colonne (date)", i, ecran.chrono(() -> trier(table, date, sens), () -> true));
        }

        TextField recherche = surLeFil(() -> ecran.dansLEcran("#champRecherche", TextField.class));
        for (int i = 0; i <= RELEVES; i++) {
            String carre = String.format(Locale.ROOT, "%06d", 640380 + 3 + i);
            releve.noter("filtre (carré)", i, ecran.chrono(() -> recherche.setText(carre), () -> {
                int n = ecran.lignes(TABLE_PASSAGES);
                return n > 0 && n < PASSAGES;
            }));
            releve.noter(
                    "retrait du filtre",
                    i,
                    ecran.chrono(() -> recherche.setText(""), () -> ecran.lignes(TABLE_PASSAGES) == PASSAGES));
        }
        releve.noterGel("sonde sur tout le banc", ecran.sonde().relever());
        releve.ecrire();
    }

    @Test
    @DisplayName("O5 : la topologie, 150 carrés de 2 points contre 15 : l'ouverture des écrans")
    void o5_topologie() throws Exception {
        for (int carres : new int[] {15, 150}) {
            System.setProperty("perf.sites", String.valueOf(carres));
            System.setProperty("perf.points", "2");
            monterLeJeu("o5-topologie-" + carres);
            Releve releve = ecran.releve("O5 topologie " + carres + " carrés de 2 points");
            ecran.temoinAVide(releve);
            SourceObservations sons = sourceDesObservations();
            ecran.mesurerOuverture(
                    releve,
                    "ouverture de Mes sites",
                    ecran::ouvrirMultisite,
                    ecran::ouvrirMesSites,
                    () -> ecran.cartesSites() == carres);
            ecran.mesurerOuverture(
                    releve,
                    "ouverture de la vue multisite",
                    ecran::ouvrirMesSites,
                    ecran::ouvrirMultisite,
                    () -> ecran.lignes(TABLE_PASSAGES) == PASSAGES);
            ecran.mesurerOuverture(
                    releve,
                    "ouverture de l'écran des sons",
                    ecran::ouvrirMesSites,
                    () -> ecran.ouvrirSons(sons),
                    () -> ecran.lignes(TABLE_SONS) == OBSERVATIONS);
            releve.noterGel("sonde sur tout le banc", ecran.sonde().relever());
            releve.ecrire();
        }
    }

    private void monterLeJeu(String nom) throws Exception {
        ecran.monter(
                EcranDuBanc.espace(nom),
                inj -> GenerateurJeuDeDonnees.peupler(inj.getInstance(SourceDeDonnees.class), PASSAGES, OBSERVATIONS));
    }

    /// Le passage qui porte les observations du jeu, et l'écran des sons qui les montre.
    private SourceObservations sourceDesObservations() {
        SourceDeDonnees source = ecran.injecteur().getInstance(SourceDeDonnees.class);
        Long idPassage =
                new ResultatsIdentificationDao(source).findAll().getFirst().idPassage();
        Passage passage = new PassageDao(source).findById(idPassage).orElseThrow();
        return new SourceObservations.ParPassage(
                new ContextePassage(passage.id(), passage.numeroPassage(), new ContexteSite("640380", "P0", null)));
    }

    private static TableColumn.SortType sensDu(int rang) {
        return rang % 2 == 0 ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING;
    }

    private static void trier(TableView<?> table, TableColumn<?, ?> colonne, TableColumn.SortType sens) {
        colonne.setSortType(sens);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ObservableList<TableColumn> ordre = (ObservableList) table.getSortOrder();
        ordre.setAll(colonne);
    }

    private static TableColumn<?, ?> colonne(TableView<?> table, String id) {
        return table.getColumns().stream()
                .filter(colonne -> id.equals(colonne.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("colonne " + id + " absente"));
    }

    private static StatutObservation statutDe(TableView<LigneObservationAudio> table, Long idObservation) {
        return table.getItems().stream()
                .filter(ligne -> idObservation.equals(ligne.idObservation()))
                .findFirst()
                .map(LigneObservationAudio::statut)
                .orElse(null);
    }
}

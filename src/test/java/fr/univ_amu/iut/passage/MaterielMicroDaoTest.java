package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.dao.MaterielMicroDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// CRUD du [MaterielMicroDao] (table 1:1 `passage_equipment`, clé naturelle `passage_id`) : upsert,
/// lecture tolérante (position inconnue, hauteur `REAL` nulle), effacement d'un relevé vide, et
/// **cascade** à la suppression du passage.
class MaterielMicroDaoTest {

    private static final String SERIE = "1925492";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private PassageDao passages;
    private MaterielMicroDao dao;
    private long idPassage;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        passages = new PassageDao(source);
        idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("040962")
                .nomSite("Étang")
                .point("A1")
                .enregistreur(SERIE)
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.IMPORTE)
                .semerSquelette()
                .idPassage();
        dao = new MaterielMicroDao(source);
    }

    @Test
    @DisplayName("pour renvoie un matériel VIDE quand aucune ligne n'existe pour le passage")
    void pour_sans_ligne_renvoie_vide() {
        MaterielMicro materiel = dao.pour(idPassage);
        assertThat(materiel.idPassage()).isEqualTo(idPassage);
        assertThat(materiel.estVide()).isTrue();
    }

    @Test
    @DisplayName("insert puis pour : round-trip des trois grandeurs (position, hauteur, type)")
    void upsert_round_trip() {
        dao.insert(new MaterielMicro(idPassage, PositionMicro.CANOPEE, 4.5, "SM4 externe"));

        MaterielMicro relu = dao.pour(idPassage);
        assertThat(relu.positionMicro()).isEqualTo(PositionMicro.CANOPEE);
        assertThat(relu.hauteurMetres()).isEqualTo(4.5);
        assertThat(relu.typeMicro()).isEqualTo("SM4 externe");
    }

    @Test
    @DisplayName("insert est un upsert : une 2e écriture pour le même passage remplace la 1re")
    void upsert_remplace() {
        dao.insert(new MaterielMicro(idPassage, PositionMicro.SOL, 1.5, "interne"));
        dao.insert(new MaterielMicro(idPassage, PositionMicro.CANOPEE, 6.0, "externe"));

        assertThat(dao.pour(idPassage)).isEqualTo(new MaterielMicro(idPassage, PositionMicro.CANOPEE, 6.0, "externe"));
        assertThat(passages.findById(idPassage)).isPresent();
    }

    @Test
    @DisplayName("grandeurs nulles relisibles : hauteur REAL nulle → null (pas 0.0), position absente → null")
    void nulls_relisibles() {
        dao.insert(new MaterielMicro(idPassage, null, null, "type seul"));

        MaterielMicro relu = dao.pour(idPassage);
        assertThat(relu.positionMicro()).isNull();
        assertThat(relu.hauteurMetres()).isNull();
        assertThat(relu.typeMicro()).isEqualTo("type seul");
    }

    @Test
    @DisplayName("definir efface la ligne si le relevé est vide, l'écrit sinon")
    void definir_efface_si_vide() {
        dao.insert(new MaterielMicro(idPassage, PositionMicro.SOL, 2.0, "interne"));
        assertThat(dao.findById(idPassage)).isPresent();

        dao.definir(MaterielMicro.vide(idPassage));
        assertThat(dao.findById(idPassage)).as("relevé vide → ligne supprimée").isEmpty();
        assertThat(dao.pour(idPassage).estVide()).isTrue();

        dao.definir(new MaterielMicro(idPassage, PositionMicro.CANOPEE, null, null));
        assertThat(dao.pour(idPassage).positionMicro()).isEqualTo(PositionMicro.CANOPEE);
    }

    @Test
    @DisplayName("supprimer le passage supprime son matériel en cascade")
    void cascade_suppression_passage() {
        dao.insert(new MaterielMicro(idPassage, PositionMicro.SOL, 2.0, "interne"));

        passages.delete(idPassage);

        assertThat(dao.findById(idPassage)).as("ON DELETE CASCADE").isEmpty();
    }
}

package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.BackfillHorodatageCapture;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Backfill applicatif de l'horodatage de capture (#530) : re-parse le nom des séquences sans `recorded_at`
/// pour le renseigner, en ignorant les noms non horodatés.
class BackfillHorodatageCaptureTest {

    @TempDir
    Path dossier;

    private SequenceDao sequenceDao;
    private BackfillHorodatageCapture backfill;
    private Long idOriginal;
    private Long idSession;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        Long idPassage = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("040962")
                .point("A1")
                .enregistreur("1925492")
                .nuit(1, 2026, "2026-04-22")
                .statut(StatutWorkflow.IMPORTE)
                .semerPassage()
                .idPassage();
        idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "racine", null, null, idPassage))
                .id();
        idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "orig.wav", "bruts/orig.wav", 12.0, 384000, null, idSession))
                .id();
        sequenceDao = new SequenceDao(source);
        backfill = new BackfillHorodatageCapture(sequenceDao, new UniteDeTravail(source));
    }

    private long insererSansHorodatage(String nom) {
        return sequenceDao
                .insert(new SequenceDEcoute(null, nom, idOriginal, 0, 0.0, 5.0, "transformes/" + nom, false, idSession))
                .id();
    }

    @Test
    @DisplayName("remplir renseigne les séquences au nom horodaté et ignore les autres")
    void remplir_renseigne_les_horodates_et_ignore_les_autres() {
        long horodate = insererSansHorodatage("PaRecPR1925492_20260422_225859_000.wav");
        long nonHorodate = insererSansHorodatage("seqB_000.wav");

        int remplis = backfill.remplir();

        assertThat(remplis).isEqualTo(1);
        assertThat(sequenceDao.findById(horodate).orElseThrow().horodatageCapture())
                .isEqualTo(LocalDateTime.of(2026, 4, 22, 22, 58, 59));
        assertThat(sequenceDao.findById(nonHorodate).orElseThrow().horodatageCapture())
                .as("un nom non horodaté reste sans heure")
                .isNull();
    }

    @Test
    @DisplayName("remplir est idempotent : un second passage ne renseigne plus rien")
    void remplir_est_idempotent() {
        insererSansHorodatage("PaRecPR1925492_20260422_225859_000.wav");

        assertThat(backfill.remplir()).isEqualTo(1);
        assertThat(backfill.remplir()).as("déjà renseigné → plus rien à faire").isZero();
    }
}

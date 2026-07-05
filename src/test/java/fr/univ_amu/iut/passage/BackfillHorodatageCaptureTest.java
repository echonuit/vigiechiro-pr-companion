package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.BackfillHorodatageCapture;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
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
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", null, Protocole.STANDARD, null, "2026-05-01", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur("1925492", null, null));
        Long idPassage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-04-22",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.IMPORTE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        "1925492"))
                .id();
        idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, "racine", null, null, idPassage))
                .id();
        idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(null, "orig.wav", "bruts/orig.wav", 12.0, 384000, null, idSession))
                .id();
        sequenceDao = new SequenceDao(source);
        backfill = new BackfillHorodatageCapture(sequenceDao);
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

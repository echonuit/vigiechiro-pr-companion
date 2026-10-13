package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.ParallelismeDeFond;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.OutilsImport;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.TransformationOriginal;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// L'import mène de front la borne de [ParallelismeDeFond], pas tous les cœurs du poste (#6175).
///
/// Avec autant de transformations que de cœurs, l'interface n'avait plus de cœur à elle : le fil
/// JavaFX attendait son tour, et l'écran gelait jusqu'à 442 ms pendant un import. Ce cas compte les
/// transformations en vol dans un vrai import, base et moteurs réels, et refuse qu'elles dépassent la
/// borne. Il la veut aussi atteinte : une borne qu'on n'emploie pas ralentit l'import pour rien.
class ParallelismeDeLImportTest {

    private static final String SERIE = "1925492";
    private static final int FREQUENCE_WAV = 384_000;
    private static final int TRAMES = 38_400; // 0,1 s : une séquence par original
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HHmmss");

    @TempDir
    Path racine;

    @Test
    void l_import_mene_de_front_la_borne_du_poste_et_pas_tous_ses_coeurs() throws IOException {
        Workspace workspace = new Workspace(racine.resolve("ws"));
        SourceDeDonnees source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();
        Long idPoint = JeuDeDonneesPassage.dans(source)
                .utilisateur("u-1")
                .carre("640380")
                .nomSite("Étang")
                .point("Z1")
                .position(43.5, 5.4)
                .semerSiteEtPoint()
                .idPoint();
        HorlogeFigee horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        TransformationComptee transformation = new TransformationComptee();
        ServiceImport service = new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                OutilsImport.reels(new CopieProtegee(), new Renommeur(), transformation),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                workspace,
                horloge,
                idPassage -> 0,
                new ServiceSauvegarde(source, horloge, () -> {}),
                Optional.empty(),
                () -> {});
        int originaux = 4 * Runtime.getRuntime().availableProcessors();
        Path sd = carteDe(originaux);

        var resultat = service.importer(
                sd,
                idPoint,
                new Prefixe("640380", 2026, 2, "Z1"),
                progression -> {},
                JetonAnnulation.neutre(),
                false,
                SuiviFichiers.inerte());

        assertThat(resultat.nombreOriginaux()).isEqualTo(originaux);
        assertThat(transformation.pic.get())
                .as(
                        "transformations menées de front, pour %d cœurs",
                        Runtime.getRuntime().availableProcessors())
                .isEqualTo(ParallelismeDeFond.duPoste());
    }

    /// Une carte de `originaux` enregistrements, espacés d'une minute.
    private Path carteDe(int originaux) throws IOException {
        Path sd = racine.resolve("sd");
        Files.createDirectories(sd);
        JournalDeCapteur.ecrire(sd, SERIE, LocalDate.of(2026, 4, 22));
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short echantillon = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (echantillon & 0xFF);
            pcm[2 * i + 1] = (byte) ((echantillon >> 8) & 0xFF);
        }
        for (int i = 0; i < originaux; i++) {
            String heure = LocalTime.of(20, 0).plusMinutes(i).format(HEURE);
            FichierWav.ecrire(
                    sd.resolve("PaRecPR" + SERIE + "_20260422_" + heure + ".wav"),
                    1,
                    FREQUENCE_WAV,
                    16,
                    pcm,
                    0,
                    pcm.length);
        }
        return sd;
    }

    /// La transformation réelle, qui compte combien d'elles tournent au même instant. Elle s'attarde un
    /// peu en entrant : sans cela, sur des WAV aussi courts, deux transformations se croiseraient à peine
    /// et le pic dirait la chance plutôt que la borne.
    private static final class TransformationComptee extends TransformationAudio {

        private final AtomicInteger enVol = new AtomicInteger();
        private final AtomicInteger pic = new AtomicInteger();

        @Override
        public TransformationOriginal transformer(
                Path originalWav,
                String nomR6,
                Path dossierSortie,
                Prefixe prefixe,
                Integer frequenceAcquisitionLogHz) {
            pic.accumulateAndGet(enVol.incrementAndGet(), Math::max);
            try {
                Thread.sleep(40);
                return super.transformer(originalWav, nomR6, dossierSortie, prefixe, frequenceAcquisitionLogHz);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruption);
            } finally {
                enVol.decrementAndGet();
            }
        }
    }
}

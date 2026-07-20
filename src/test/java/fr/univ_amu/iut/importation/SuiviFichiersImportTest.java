package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.CopieProtegee;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.NuitAImporter;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import fr.univ_amu.iut.importation.model.OutilsImport;
import fr.univ_amu.iut.importation.model.Renommeur;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.dao.AgregatImportDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Suivi **par fichier** de l'import (#947) : vérifie que [ServiceImport] émet le plan de la nuit puis
/// les événements de copie, de transformation, de fin et de rejet (#155) attendus, dans les deux modes
/// de conservation et en multi-nuits (replanification par nuit). Base SQLite jetable et vrais moteurs
/// (pas de mock), comme `ServiceImportTest` ; les événements parallèles sont ciblés par numéro, donc
/// les assertions d'ordre ne portent que sur la phase de copie (séquentielle).
class SuiviFichiersImportTest {

    private static final String ID_USER = "u-1";
    private static final int FREQUENCE_WAV = 384_000; // Hz, = Fe du log (l'arithmétique se pilote sur le log)
    private static final int TRAMES = 576_000; // 1,5 s à 384 kHz -> 1 séquence par original

    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
                    + " les 600s\n"
                    + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH"
                    + " 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%\n";

    @TempDir
    Path racine;

    private ServiceImport service;
    private Long idPoint;
    private final Prefixe prefixe = new Prefixe("640380", 2026, 2, "Z1");

    @BeforeEach
    void preparer() {
        Workspace workspace = new Workspace(racine.resolve("ws"));
        SourceDeDonnees source = new SourceDeDonnees(workspace);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "640380", "Étang", Protocole.STANDARD, null, "2026-05-31", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "Z1", 43.5, 5.4, null, site.id()))
                .id();

        HorlogeFigee horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
        service = new ServiceImport(
                new InspecteurDossier(new AnalyseurLogPR()),
                OutilsImport.reels(new CopieProtegee(), new Renommeur(), new TransformationAudio()),
                new AgregatImportDao(source),
                new UniteDeTravail(source),
                workspace,
                horloge,
                idPassage -> 0,
                new ServiceSauvegarde(source, horloge),
                Optional.empty());
    }

    @Test
    @DisplayName("Conservation : plan puis copie séquentielle (1,2) puis transformation et fin par numéro")
    void conservation_emet_plan_copies_transformations() throws IOException {
        Path sd = preparerCarteSD(racine.resolve("sd"));
        SuiviEnregistre suivi = new SuiviEnregistre();

        service.importer(sd, idPoint, prefixe, progression -> {}, JetonAnnulation.neutre(), true, suivi);

        assertThat(suivi.plans).hasSize(1);
        assertThat(suivi.plans.getFirst())
                .containsExactly("PaRecPR1925492_20260422_203922.wav", "PaRecPR1925492_20260422_204326.wav");
        // Copie (#948) et transformation (#12) sont parallèles : chaque fichier démarre et finit une
        // fois, ordre d'arrivée indifférent (le ciblage se fait par numéro de plan).
        assertThat(suivi.copiesDemarrees).containsExactlyInAnyOrder(1, 2);
        assertThat(suivi.copiesTerminees).containsExactlyInAnyOrder(1, 2);
        assertThat(suivi.transformationsDemarrees).containsExactlyInAnyOrder(1, 2);
        assertThat(suivi.termines).containsExactlyInAnyOrder(1, 2);
        assertThat(suivi.rejets).isEmpty();
    }

    @Test
    @DisplayName("#948 : la copie parallèle traite tous les fichiers d'une grosse nuit, sans perte ni doublon")
    void copie_parallele_traite_tous_les_fichiers() throws IOException {
        Path sd = racine.resolve("sd-grosse-nuit");
        Files.createDirectories(sd);
        Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        List<Integer> attendus = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ecrireWav(sd.resolve("PaRecPR1925492_20260422_20%02d00.wav".formatted(10 + i)));
            attendus.add(i + 1);
        }

        SuiviEnregistre suivi = new SuiviEnregistre();
        var resultat = service.importer(sd, idPoint, prefixe, progression -> {}, JetonAnnulation.neutre(), true, suivi);

        // Chaque fichier est copié puis transformé exactement une fois (numéros 1..8, ordre libre),
        // et l'agrégat persisté est complet : la parallélisation ne perd ni ne duplique rien.
        assertThat(suivi.copiesDemarrees).containsExactlyInAnyOrderElementsOf(attendus);
        assertThat(suivi.copiesTerminees).containsExactlyInAnyOrderElementsOf(attendus);
        assertThat(suivi.termines).containsExactlyInAnyOrderElementsOf(attendus);
        assertThat(suivi.rejets).isEmpty();
        assertThat(resultat.nombreOriginaux()).isEqualTo(8);
        assertThat(resultat.nombreSequences()).isEqualTo(8); // 1 séquence par original (1,5 s < 5 s)
    }

    @Test
    @DisplayName("Sans copie : aucun événement de copie, la transformation suit chaque fichier")
    void sans_copie_n_emet_pas_d_evenement_de_copie() throws IOException {
        Path sd = preparerCarteSD(racine.resolve("sd-sans-copie"));
        SuiviEnregistre suivi = new SuiviEnregistre();

        service.importer(sd, idPoint, prefixe, progression -> {}, JetonAnnulation.neutre(), false, suivi);

        assertThat(suivi.plans).hasSize(1);
        assertThat(suivi.copiesDemarrees).isEmpty();
        assertThat(suivi.copiesTerminees).isEmpty();
        assertThat(suivi.transformationsDemarrees).containsExactlyInAnyOrder(1, 2);
        assertThat(suivi.termines).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("#155 : un original illisible est signalé rejeté (avec sa raison), les autres terminent")
    void original_illisible_signale_rejete() throws IOException {
        Path corrompu = racine.resolve("sd-corrompu");
        Files.createDirectories(corrompu);
        Files.writeString(corrompu.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        ecrireWav(corrompu.resolve("PaRecPR1925492_20260422_203922.wav")); // n° 1, valide
        Files.writeString(corrompu.resolve("PaRecPR1925492_20260422_204326.wav"), "pas un WAV"); // n° 2

        SuiviEnregistre suivi = new SuiviEnregistre();
        service.importer(corrompu, idPoint, prefixe, progression -> {}, JetonAnnulation.neutre(), true, suivi);

        assertThat(suivi.termines).containsExactly(1);
        assertThat(suivi.rejets).containsOnlyKeys(2);
        assertThat(suivi.rejets.get(2)).isNotBlank();
    }

    @Test
    @DisplayName("Multi-nuits : la table se replanifie à chaque nuit (un plan par nuit, fichiers de la nuit)")
    void multi_nuits_replanifie_chaque_nuit() throws IOException {
        Path carte = racine.resolve("sd-multi");
        Files.createDirectories(carte);
        Files.writeString(carte.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        for (String jour : List.of("20260422", "20260423", "20260424")) {
            ecrireWav(carte.resolve("PaRecPR1925492_" + jour + "_203922.wav"));
            ecrireWav(carte.resolve("PaRecPR1925492_" + jour + "_204326.wav"));
        }
        List<NuitDetectee> nuits =
                new InspecteurDossier(new AnalyseurLogPR()).inspecter(carte).partitionNuits();
        List<NuitAImporter> aImporter = new ArrayList<>();
        for (int i = 0; i < nuits.size(); i++) {
            aImporter.add(new NuitAImporter(i + 1, nuits.get(i)));
        }

        SuiviEnregistre suivi = new SuiviEnregistre();
        service.importerNuits(
                carte, idPoint, prefixe, aImporter, true, progression -> {}, JetonAnnulation.neutre(), suivi);

        assertThat(suivi.plans).hasSize(3);
        assertThat(suivi.plans.get(0)).allSatisfy(nom -> assertThat(nom).contains("20260422"));
        assertThat(suivi.plans.get(1)).allSatisfy(nom -> assertThat(nom).contains("20260423"));
        assertThat(suivi.plans.get(2)).allSatisfy(nom -> assertThat(nom).contains("20260424"));
        assertThat(suivi.plans).allSatisfy(plan -> assertThat(plan).hasSize(2));
        // Chaque nuit renumérote de 1 : 3 nuits × 2 fichiers terminés aux numéros 1 et 2.
        assertThat(suivi.termines).hasSize(6).containsOnly(1, 2);
    }

    /// Implémentation **enregistreuse** de [SuiviFichiers] : mémorise les événements pour les assertions.
    /// Collections synchronisées : la transformation notifie depuis plusieurs threads (#12).
    private static final class SuiviEnregistre implements SuiviFichiers {
        final List<List<String>> plans = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> copiesDemarrees = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> copiesTerminees = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> transformationsDemarrees = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> termines = Collections.synchronizedList(new ArrayList<>());
        final Map<Integer, String> rejets = Collections.synchronizedMap(new LinkedHashMap<>());

        @Override
        public void planEtabli(List<String> noms) {
            plans.add(List.copyOf(noms));
        }

        @Override
        public void copieDemarree(int numero) {
            copiesDemarrees.add(numero);
        }

        @Override
        public void copieTerminee(int numero) {
            copiesTerminees.add(numero);
        }

        @Override
        public void transformationDemarree(int numero) {
            transformationsDemarrees.add(numero);
        }

        @Override
        public void fichierTermine(int numero) {
            termines.add(numero);
        }

        @Override
        public void fichierRejete(int numero, String raison) {
            rejets.put(numero, raison);
        }
    }

    private Path preparerCarteSD(Path dossier) throws IOException {
        Files.createDirectories(dossier);
        Files.writeString(dossier.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_203922.wav"));
        ecrireWav(dossier.resolve("PaRecPR1925492_20260422_204326.wav"));
        return dossier;
    }

    /// Écrit un WAV mono 16 bits de `TRAMES` trames (1,5 s), en-tête à la Fe du log (384 kHz).
    private static void ecrireWav(Path fichier) throws IOException {
        byte[] pcm = new byte[TRAMES * 2];
        for (int i = 0; i < TRAMES; i++) {
            short e = (short) (((i * 41) % 1000) - 500);
            pcm[2 * i] = (byte) (e & 0xFF);
            pcm[2 * i + 1] = (byte) ((e >> 8) & 0xFF);
        }
        ByteBuffer buf = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + pcm.length);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(FREQUENCE_WAV);
        buf.putInt(FREQUENCE_WAV * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(pcm.length);
        buf.put(pcm);
        Files.write(fichier, buf.array());
    }
}

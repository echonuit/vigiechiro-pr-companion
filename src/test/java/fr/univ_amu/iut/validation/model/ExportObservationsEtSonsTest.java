package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Export ZIP « observations + sons » (#2792) : CSV octet-identique à l'export CSV seul, séquences
/// dédupliquées, son introuvable compté sans bloquer, sessions homonymes départagées, annulation
/// sans archive partielle. Base réelle et fichiers réels sur disque - le résolveur de chemins et la
/// structure d'archive sont exactement ceux de production.
class ExportObservationsEtSonsTest {

    @TempDir
    Path workspace;

    private SourceDeDonnees source;
    private SequenceDao sequenceDao;
    private ExportObservationsEtSons export;

    /// Numéro de passage auto-incrémenté : l'unicité (point, année, n°) refuse deux passages identiques.
    private int prochainNumeroPassage = 1;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(workspace));
        new MigrationSchema(source).migrer();
        sequenceDao = new SequenceDao(source);
        export = new ExportObservationsEtSons(sequenceDao, new SessionDao(source));
    }

    @Test
    @DisplayName("CSV octet-identique à l'export CSV seul, séquence partagée emballée une seule fois")
    void nominal_avec_deduplication() throws IOException {
        long idSequence = creerSequence("Car640380-2026-Pass1-Z1", "a_000.wav", new byte[] {1, 2, 3});
        List<LigneObservationAudio> lignes = List.of(ligne(idSequence, "a_000.wav"), ligne(idSequence, "a_000.wav"));
        Path archive = workspace.resolve("export.zip");

        ExportObservationsEtSons.Bilan bilan =
                export.exporter(lignes, archive, taxon -> false, progression -> {}, JetonAnnulation.neutre());

        assertThat(bilan.observations()).isEqualTo(2);
        assertThat(bilan.sonsCopies())
                .as("deux observations, une seule séquence : un seul son")
                .isEqualTo(1);
        assertThat(bilan.sonsIntrouvables()).isEmpty();
        assertThat(bilan.octets()).isEqualTo(Files.size(archive));
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName))
                    .containsExactly("observations.csv", "sons/Car640380-2026-Pass1-Z1/a_000.wav");
            String csv = new String(
                    zip.getInputStream(zip.getEntry("observations.csv")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(csv)
                    .as("le CSV de l'archive est celui de l'export CSV seul, à l'octet près")
                    .isEqualTo(ExportObservationsCsv.contenu(lignes, taxon -> false));
        }
    }

    @Test
    @DisplayName("Un son introuvable est compté et nommé, l'archive s'écrit avec le reste")
    void son_introuvable_compte_sans_bloquer() throws IOException {
        long presente = creerSequence("Car640380-2026-Pass1-Z1", "a_000.wav", new byte[] {1});
        long partie = creerSequence("Car640380-2026-Pass2-Z1", "b_000.wav", null);
        Path archive = workspace.resolve("export.zip");

        ExportObservationsEtSons.Bilan bilan = export.exporter(
                List.of(ligne(presente, "a_000.wav"), ligne(partie, "b_000.wav")),
                archive,
                taxon -> false,
                progression -> {},
                JetonAnnulation.neutre());

        assertThat(bilan.sonsCopies()).isEqualTo(1);
        assertThat(bilan.sonsIntrouvables()).containsExactly("b_000.wav");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName))
                    .containsExactly("observations.csv", "sons/Car640380-2026-Pass1-Z1/a_000.wav");
        }
    }

    @Test
    @DisplayName("Deux sessions aux dossiers homonymes sont départagées : aucune collision d'entrées")
    void sessions_homonymes_departagees() throws IOException {
        long premiere = creerSequenceSousRacine(workspace.resolve("disque-a").resolve("Nuit-01"), "s_000.wav", 1);
        long seconde = creerSequenceSousRacine(workspace.resolve("disque-b").resolve("Nuit-01"), "s_000.wav", 2);
        Path archive = workspace.resolve("export.zip");

        export.exporter(
                List.of(ligne(premiere, "s_000.wav"), ligne(seconde, "s_000.wav")),
                archive,
                taxon -> false,
                progression -> {},
                JetonAnnulation.neutre());

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<String> entrees = zip.stream().map(ZipEntry::getName).toList();
            assertThat(entrees).hasSize(3).contains("sons/Nuit-01/s_000.wav");
            assertThat(entrees)
                    .as("la session homonyme est suffixée par son identifiant, pas fusionnée")
                    .anyMatch(nom -> nom.matches("sons/Nuit-01-s\\d+/s_000\\.wav"));
        }
    }

    @Test
    @DisplayName("Un chemin hérité relatif se résout contre la racine de sa session (survivant PIT)")
    void chemin_relatif_resolu_contre_la_session() throws IOException {
        long idSequence = creerSequence("Car640380-2026-Pass1-Z1", "r_000.wav", new byte[] {5});
        SequenceDEcoute sequence = sequenceDao.findById(idSequence).orElseThrow();
        // Donnée héritée : le chemin stocké est RELATIF à la racine de session (repli du dépôt).
        // majChemin force l'absolu, on passe donc par update() avec le record modifié.
        sequenceDao.update(new SequenceDEcoute(
                sequence.id(),
                sequence.nomFichier(),
                sequence.idEnregistrementOriginal(),
                sequence.indexSource(),
                sequence.offsetSourceSecondes(),
                sequence.dureeSecondes(),
                "transformes/r_000.wav",
                sequence.dansSelection(),
                sequence.idSession(),
                sequence.horodatageCapture(),
                sequence.empreinteContenu()));
        Path archive = workspace.resolve("export.zip");

        ExportObservationsEtSons.Bilan bilan = export.exporter(
                List.of(ligne(idSequence, "r_000.wav")),
                archive,
                taxon -> false,
                progression -> {},
                JetonAnnulation.neutre());

        assertThat(bilan.sonsCopies()).isEqualTo(1);
        assertThat(bilan.sonsIntrouvables()).isEmpty();
        assertThat(sequence.idSession()).isNotNull();
    }

    @Test
    @DisplayName("Annulée : exception dédiée, aucune archive partielle")
    void annulation_sans_archive() throws IOException {
        long idSequence = creerSequence("Car640380-2026-Pass1-Z1", "a_000.wav", new byte[] {1});
        Path archive = workspace.resolve("export.zip");
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> export.exporter(
                        List.of(ligne(idSequence, "a_000.wav")), archive, taxon -> false, progression -> {}, jeton));

        assertThat(archive).doesNotExist();
    }

    /// Une session complète sous `<workspace>/<dossier>`, semée par la **fabrique partagée**
    /// (cliquet #1771) ; seule la séquence, dont le fichier doit réellement exister, est insérée ici.
    /// `contenu` nul = le fichier n'existe pas sur disque (son parti).
    private long creerSequence(String dossierSession, String nomFichier, byte[] contenu) throws IOException {
        return creerSequenceSousRacine(workspace.resolve(dossierSession), nomFichier, contenu, prochainNumeroPassage);
    }

    /// Variante à racine arbitraire, pour fabriquer deux sessions aux dossiers **homonymes**.
    private long creerSequenceSousRacine(Path racineSession, String nomFichier, int numeroPassage) throws IOException {
        return creerSequenceSousRacine(racineSession, nomFichier, new byte[] {7}, numeroPassage);
    }

    private long creerSequenceSousRacine(Path racineSession, String nomFichier, byte[] contenu, int numeroPassage)
            throws IOException {
        prochainNumeroPassage = Math.max(prochainNumeroPassage, numeroPassage) + 1;
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .nomSite("Étang")
                .point("A1")
                .cheminSession(racineSession.toString())
                .nuit(numeroPassage, 2026, "2026-06-2" + Math.min(numeroPassage, 9))
                .semer();
        Path transformes = Files.createDirectories(racineSession.resolve("transformes"));
        Path fichier = transformes.resolve(nomFichier);
        if (contenu != null) {
            Files.write(fichier, contenu);
        }
        return sequenceDao
                .insert(new SequenceDEcoute(
                        null,
                        nomFichier,
                        jeu.idOriginal(),
                        0,
                        0.0,
                        5.0,
                        fichier.toString(),
                        false,
                        jeu.idSession(),
                        null,
                        null))
                .id();
    }

    /// Une ligne d'observation minimale pointant `idSequence` : seuls les champs du CSV et la
    /// séquence comptent ici.
    private static LigneObservationAudio ligne(long idSequence, String nomFichier) {
        return new LigneObservationAudio(
                idSequence,
                idSequence,
                1L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Étang",
                "Rhifer",
                0.91,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                null,
                "Grand rhinolophe",
                "Grand rhinolophe",
                "Rhinolophus ferrumequinum",
                "Chiroptères",
                nomFichier,
                0.5,
                3.2,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                "Aix-en-Provence");
    }
}

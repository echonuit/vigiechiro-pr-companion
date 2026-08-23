package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

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
    @DisplayName("La première progression annonce le contenu : observations, sons et volume (#2793)")
    void annonce_le_contenu_avant_la_copie() throws IOException {
        // Des tailles qui PÈSENT : avec quelques kilo-octets, « ~0,0 Mo » resterait vrai même si le
        // volume n'était jamais lu (mutant survivant au premier jet). 1,5 Mo au total le prouve.
        long premiere = creerSequence("Car640380-2026-Pass1-Z1", "a_000.wav", new byte[1_048_576]);
        long seconde = creerSequence("Car640380-2026-Pass2-Z1", "b_000.wav", new byte[524_288]);
        List<Progression> etapes = new ArrayList<>();
        Path archive = workspace.resolve("export.zip");

        export.exporter(
                List.of(ligne(premiere, "a_000.wav"), ligne(seconde, "b_000.wav")),
                archive,
                taxon -> false,
                etapes::add,
                JetonAnnulation.neutre());

        // La modale doit dire ce qui va se passer AVANT la première copie : sans cette ligne, un export
        // de plusieurs centaines de Mo commence sans que personne sache ce qu'il embarque.
        assertThat(etapes).isNotEmpty();
        assertThat(etapes.get(0).libelle())
                .as("annonce d'ouverture : observations, sons dédupliqués, volume lu sur le disque")
                .isEqualTo("2 observation(s) · 2 son(s) · ~1,6 Mo");
        assertThat(etapes.get(1).libelle())
                .as("la copie suit l'annonce, entrée par entrée")
                .startsWith("Archive : ");
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
    @DisplayName("Une session au chemin racine vide se range sous « session-N », pas sous un dossier sans nom")
    void session_sans_chemin_se_range_sous_un_nom_de_repli() throws IOException {
        // Chemin racine vide en base (nuit rapatriée en squelette, chemin jamais renseigné) : sans le
        // repli, l'entrée d'archive serait « sons//a_000.wav » - un chemin que les outils d'archive
        // rendent de façons diverses, quand ils ne le refusent pas. Mutant PIT lu en cérémonie.
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("A1")
                .cheminSession("")
                .nuit(prochainNumeroPassage++, 2026, "2026-06-25")
                .semer();
        Path fichier = Files.createDirectories(workspace.resolve("ailleurs")).resolve("a_000.wav");
        Files.write(fichier, new byte[] {9});
        long idSequence = sequenceDao
                .insert(new SequenceDEcoute(
                        null, "a_000.wav", jeu.idOriginal(), 0, 0.0, 5.0, fichier.toString(), false, jeu.idSession()))
                .id();
        Path archive = workspace.resolve("repli.zip");

        export.exporter(
                List.of(ligne(idSequence, "a_000.wav")),
                archive,
                taxon -> false,
                progression -> {},
                JetonAnnulation.neutre());

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName))
                    .containsExactly("observations.csv", "sons/session-" + jeu.idSession() + "/a_000.wav");
        }
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

    @Test
    @DisplayName("#4289 : l'export lit les séquences par LOT, pas un son à la fois")
    void l_export_lit_par_lot() throws IOException {
        List<LigneObservationAudio> lignes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            long id = creerSequence("Car640380-2026-Pass" + (i + 1) + "-Z1", "s" + i + ".wav", new byte[] {1});
            lignes.add(ligne(id, "s" + i + ".wav"));
        }
        SequenceDao sequencesSurveillees = Mockito.spy(sequenceDao);
        SessionDao sessionsSurveillees = Mockito.spy(new SessionDao(source));
        Mockito.clearInvocations(sequencesSurveillees, sessionsSurveillees);

        new ExportObservationsEtSons(sequencesSurveillees, sessionsSurveillees)
                .exporter(
                        lignes,
                        workspace.resolve("lot.zip"),
                        taxon -> false,
                        progression -> {},
                        JetonAnnulation.neutre());

        // ⚠️ Le garde compte des REQUÊTES, pas des millisecondes : la machine des relevés portait un banc
        // filmé (charge 12), et tout chronométrage y variait du simple au double.
        //
        // Le défaut (#4289) : deux requêtes par son exporté - la séquence, puis sa session pour résoudre
        // un chemin relatif. Un export de plusieurs milliers de cris en faisait autant.
        Mockito.verify(sequencesSurveillees, Mockito.never()).findById(Mockito.any());
        Mockito.verify(sequencesSurveillees, Mockito.times(1)).findParIds(Mockito.any());
        Mockito.verify(sessionsSurveillees, Mockito.never()).findById(Mockito.any());
        Mockito.verify(sessionsSurveillees, Mockito.times(1)).findAll();
    }
}

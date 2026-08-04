package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Écrivain ZIP du socle (#2792) : entrées texte et fichier relues telles quelles, et surtout la
/// promesse de propreté - annulation ou échec en cours d'écriture ne laissent **aucune archive
/// partielle** (un fichier absent vaut mieux qu'un fichier menteur).
///
/// Survivants PIT **assumés** (lus un par un) : le contrôle d'annulation avant une entrée texte et
/// les `closeEntry` - la vérification finale et `putNextEntry`/`finish` rattrapent leur absence,
/// aucun effet observable ne les distingue ; la borne de `nomCourt` ne diverge que sur un nom
/// d'entrée commençant par « / », invalide en pratique.
class EcrivainZipTest {

    /// Taille d'une entrée « volumineuse » : au-delà de plusieurs paliers de progression intra-entrée,
    /// pour que l'annulation puisse se produire au milieu du fichier.
    private static final int TAILLE_GROSSE_ENTREE = 12 * 1024 * 1024;

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Texte et fichiers se relisent tels quels ; le retour est la taille de l'archive")
    void ecriture_nominale() throws IOException {
        Path source = Files.write(dossier.resolve("a.wav"), new byte[] {1, 2, 3});
        Path archive = dossier.resolve("export.zip");

        long octets = EcrivainZip.ecrire(
                archive,
                List.of(new EcrivainZip.EntreeTexte("observations.csv", "Carré;Point\n640380;A1\n")),
                List.of(new EcrivainZip.EntreeFichier("sons/session/a.wav", source)),
                progression -> {},
                JetonAnnulation.neutre());

        assertThat(octets).isEqualTo(Files.size(archive));
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName)).containsExactly("observations.csv", "sons/session/a.wav");
            assertThat(new String(
                            zip.getInputStream(zip.getEntry("observations.csv")).readAllBytes(),
                            StandardCharsets.UTF_8))
                    .isEqualTo("Carré;Point\n640380;A1\n");
            assertThat(zip.getInputStream(zip.getEntry("sons/session/a.wav")).readAllBytes())
                    .containsExactly(1, 2, 3);
        }
    }

    @Test
    @DisplayName("Annulée en cours de boucle : exception dédiée, et l'archive partielle est supprimée")
    void annulation_supprime_le_partiel() throws IOException {
        Path premier = Files.write(dossier.resolve("a.wav"), new byte[100]);
        Path second = Files.write(dossier.resolve("b.wav"), new byte[100]);
        Path archive = dossier.resolve("export.zip");
        JetonAnnulation jeton = new JetonAnnulation();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> EcrivainZip.ecrire(
                        archive,
                        List.of(),
                        List.of(
                                new EcrivainZip.EntreeFichier("sons/a.wav", premier),
                                new EcrivainZip.EntreeFichier("sons/b.wav", second)),
                        progression -> jeton.annuler(), // l'utilisateur annule après le premier fichier
                        jeton));

        assertThat(archive)
                .as("aucune archive partielle ne survit à l'annulation")
                .doesNotExist();
    }

    @Test
    @DisplayName("Source illisible : l'erreur remonte, et l'archive partielle est supprimée")
    void echec_supprime_le_partiel() throws IOException {
        Path present = Files.write(dossier.resolve("a.wav"), new byte[10]);
        Path archive = dossier.resolve("export.zip");

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> EcrivainZip.ecrire(
                        archive,
                        List.of(),
                        List.of(
                                new EcrivainZip.EntreeFichier("sons/a.wav", present),
                                new EcrivainZip.EntreeFichier("sons/parti.wav", dossier.resolve("parti.wav"))),
                        progression -> {},
                        JetonAnnulation.neutre()));

        assertThat(archive).doesNotExist();
    }

    @Test
    @DisplayName("Une destination existante est remplacée (l'écrasement a été confirmé en amont)")
    void destination_remplacee() throws IOException {
        Path archive = Files.write(dossier.resolve("export.zip"), new byte[] {9, 9, 9});

        EcrivainZip.ecrire(
                archive,
                List.of(new EcrivainZip.EntreeTexte("observations.csv", "x")),
                List.of(),
                progression -> {},
                JetonAnnulation.neutre());

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(zip.size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("La progression est déterminée : X / N et le nom du fichier courant, sans son chemin")
    void progression_determinee() throws IOException {
        Path source = Files.write(dossier.resolve("a.wav"), new byte[10]);
        java.util.List<Progression> notifications = new java.util.ArrayList<>();

        EcrivainZip.ecrire(
                dossier.resolve("export.zip"),
                List.of(),
                List.of(new EcrivainZip.EntreeFichier("sons/session/a.wav", source)),
                notifications::add,
                JetonAnnulation.neutre());

        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().libelle()).isEqualTo("Archive : 1 / 1 · a.wav");
        assertThat(notifications.getFirst().fraction()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Les fractions de progression avancent : 1/2 puis 2/2 (survivant PIT : division)")
    void fractions_de_progression() throws IOException {
        Path a = Files.write(dossier.resolve("a.wav"), new byte[1]);
        Path b = Files.write(dossier.resolve("b.wav"), new byte[1]);
        java.util.List<Double> fractions = new java.util.ArrayList<>();

        EcrivainZip.ecrire(
                dossier.resolve("export.zip"),
                List.of(),
                List.of(new EcrivainZip.EntreeFichier("a.wav", a), new EcrivainZip.EntreeFichier("b.wav", b)),
                progression -> fractions.add(progression.fraction()),
                JetonAnnulation.neutre());

        assertThat(fractions).containsExactly(0.5, 1.0);
    }

    @Test
    @DisplayName("Déjà annulée : rien n'est copié, aucune progression n'est émise")
    void deja_annulee_rien_ne_part() throws IOException {
        Path source = Files.write(dossier.resolve("a.wav"), new byte[1]);
        Path archive = dossier.resolve("export.zip");
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();
        java.util.List<Progression> notifications = new java.util.ArrayList<>();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> EcrivainZip.ecrire(
                        archive,
                        List.of(),
                        List.of(new EcrivainZip.EntreeFichier("a.wav", source)),
                        notifications::add,
                        jeton));

        assertThat(notifications)
                .as("le contrôle précède la copie : aucun octet ne part")
                .isEmpty();
        assertThat(archive).doesNotExist();
    }

    @Test
    @DisplayName("Annulée pendant la dernière entrée : la re-vérification finale nettoie quand même")
    void annulation_pendant_la_derniere_entree() throws IOException {
        Path source = Files.write(dossier.resolve("a.wav"), new byte[1]);
        Path archive = dossier.resolve("export.zip");
        JetonAnnulation jeton = new JetonAnnulation();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> EcrivainZip.ecrire(
                        archive,
                        List.of(),
                        List.of(new EcrivainZip.EntreeFichier("a.wav", source)),
                        progression -> jeton.annuler(), // annulé une fois l'unique (donc dernière) entrée close
                        jeton));

        assertThat(archive)
                .as("l'archive ne doit pas « aboutir » sur une annulation tardive")
                .doesNotExist();
    }

    @Test
    @DisplayName("Annulation pendant une entrée volumineuse : l'arrêt n'attend pas la fin du fichier (#2733)")
    void annulation_pendant_une_entree_volumineuse() throws IOException {
        Path source = Files.write(dossier.resolve("gros.wav"), new byte[TAILLE_GROSSE_ENTREE]);
        Path archive = dossier.resolve("export.zip");
        JetonAnnulation jeton = new JetonAnnulation();
        java.util.List<Progression> notifications = new java.util.ArrayList<>();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> EcrivainZip.ecrire(
                        archive,
                        List.of(),
                        List.of(new EcrivainZip.EntreeFichier("sons/gros.wav", source)),
                        progression -> {
                            notifications.add(progression);
                            jeton.annuler();
                        },
                        jeton));

        // « 0 / 1 » : l'entrée n'est pas close quand la notification tombe. C'est ce qui distingue une
        // annulation qui agit PENDANT la copie d'une annulation qui attend poliment la fin du fichier.
        assertThat(notifications.get(0).libelle())
                .as("la première notification doit tomber pendant la copie de l'entrée")
                .contains("0 / 1")
                .contains("Mo");
        assertThat(archive).doesNotExist();
    }

    @Test
    @DisplayName("Un nom d'entrée vide est refusé à la construction")
    void nom_vide_refuse() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EcrivainZip.EntreeTexte(" ", "contenu"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EcrivainZip.EntreeFichier("", dossier.resolve("a.wav")));
    }
}

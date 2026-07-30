package fr.univ_amu.iut.bibliotheque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests unitaires de la matérialisation disque d'[ExportBiblioSons] ([ExportBiblioSons#exporterVers]) :
/// **archive ZIP** (CSV récapitulatif à la racine, sons sous `sons/`), déduplication d'une séquence
/// partagée, sons introuvables **comptés** (séquence déplacée depuis la validation), annonce du
/// contenu et annulation sans archive partielle.
///
/// L'export écrivait auparavant dans un **dossier**, de façon synchrone et sans compter les absents ;
/// il est passé sur le socle [fr.univ_amu.iut.commun.model.EcrivainZip] à la clôture de l'EPIC #2790,
/// pour que les deux exports de sons de l'application se comportent pareil.
class ExportBiblioSonsTest {

    private static EntreeBiblio entree(String taxon, String nom, String chemin) {
        return new EntreeBiblio(taxon, nom, chemin, 45, null);
    }

    @Test
    @DisplayName("exporterVers écrit une archive : le CSV à la racine, les sons existants sous sons/")
    void exporte_csv_et_sons_dans_une_archive(@TempDir Path source, @TempDir Path dest) throws IOException {
        Path wavA = Files.writeString(source.resolve("a_000.wav"), "RIFFa");
        Path wavB = Files.writeString(source.resolve("b_000.wav"), "RIFFb");
        ExportBiblioSons export = new ExportBiblioSons(List.of(
                entree("PIPPIP", "a_000.wav", wavA.toString()), entree("NYCNOC", "b_000.wav", wavB.toString())));
        Path archive = dest.resolve("bibliotheque.zip");

        ExportBiblioSons.Bilan bilan = export.exporterVers(archive, progression -> {}, JetonAnnulation.neutre());

        assertThat(bilan.sonsCopies()).isEqualTo(2);
        assertThat(bilan.sonsIntrouvables()).isEmpty();
        assertThat(bilan.octets()).isEqualTo(Files.size(archive));
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName))
                    .containsExactly(ExportBiblioSons.NOM_CSV, "sons/a_000.wav", "sons/b_000.wav");
            assertThat(new String(
                            zip.getInputStream(zip.getEntry("sons/a_000.wav")).readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("RIFFa");
        }
    }

    @Test
    @DisplayName("Une séquence introuvable est comptée et nommée, l'archive s'écrit avec le reste")
    void source_introuvable_est_comptee(@TempDir Path source, @TempDir Path dest) throws IOException {
        Path present = Files.writeString(source.resolve("present.wav"), "ok");
        ExportBiblioSons export = new ExportBiblioSons(List.of(
                entree("PIPPIP", "present.wav", present.toString()),
                entree("NYCNOC", "disparu.wav", source.resolve("disparu.wav").toString())));
        Path archive = dest.resolve("bibliotheque.zip");

        ExportBiblioSons.Bilan bilan = export.exporterVers(archive, progression -> {}, JetonAnnulation.neutre());

        // Ignorer en silence laissait croire à une bibliothèque complète : le son manquant est nommé.
        assertThat(bilan.sonsCopies()).isEqualTo(1);
        assertThat(bilan.sonsIntrouvables()).containsExactly("disparu.wav");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName))
                    .containsExactly(ExportBiblioSons.NOM_CSV, "sons/present.wav");
            assertThat(new String(
                            zip.getInputStream(zip.getEntry(ExportBiblioSons.NOM_CSV))
                                    .readAllBytes(),
                            StandardCharsets.UTF_8))
                    .as("le CSV trace toujours le chemin du son disparu")
                    .contains("disparu.wav");
        }
    }

    @Test
    @DisplayName("Une séquence partagée par plusieurs entrées n'est emballée qu'une fois")
    void source_partagee_emballee_une_seule_fois(@TempDir Path source, @TempDir Path dest) throws IOException {
        Path wav = Files.writeString(source.resolve("partage.wav"), "x");
        ExportBiblioSons export = new ExportBiblioSons(List.of(
                entree("PIPPIP", "partage.wav", wav.toString()), entree("PIPNAT", "partage.wav", wav.toString())));

        ExportBiblioSons.Bilan bilan =
                export.exporterVers(dest.resolve("b.zip"), progression -> {}, JetonAnnulation.neutre());

        assertThat(bilan.sonsCopies()).isEqualTo(1);
    }

    @Test
    @DisplayName("La première progression annonce le contenu : références, sons et volume")
    void annonce_le_contenu_avant_la_copie(@TempDir Path source, @TempDir Path dest) throws IOException {
        Path wav = Files.write(source.resolve("gros.wav"), new byte[1_048_576]);
        ExportBiblioSons export = new ExportBiblioSons(List.of(entree("PIPPIP", "gros.wav", wav.toString())));
        List<Progression> etapes = new ArrayList<>();

        export.exporterVers(dest.resolve("b.zip"), etapes::add, JetonAnnulation.neutre());

        assertThat(etapes).isNotEmpty();
        assertThat(etapes.get(0).libelle()).isEqualTo("1 référence(s) · 1 son(s) · ~1,0 Mo");
    }

    @Test
    @DisplayName("Annulée : exception dédiée, aucune archive partielle")
    void annulation_sans_archive(@TempDir Path source, @TempDir Path dest) throws IOException {
        Path wav = Files.writeString(source.resolve("a.wav"), "x");
        ExportBiblioSons export = new ExportBiblioSons(List.of(entree("PIPPIP", "a.wav", wav.toString())));
        Path archive = dest.resolve("b.zip");
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> export.exporterVers(archive, progression -> {}, jeton));

        assertThat(archive).doesNotExist();
    }
}

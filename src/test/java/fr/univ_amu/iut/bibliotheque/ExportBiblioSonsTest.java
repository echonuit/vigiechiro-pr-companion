package fr.univ_amu.iut.bibliotheque;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests unitaires de la matérialisation disque d'[ExportBiblioSons]
/// ([ExportBiblioSons#exporterVers(Path)]) : écriture du CSV récapitulatif, copie des fichiers son
/// existants, déduplication d'une séquence partagée, et tolérance aux sources introuvables (séquence
/// déplacée depuis la validation).
class ExportBiblioSonsTest {

    private static EntreeBiblio entree(String taxon, String nom, String chemin) {
        return new EntreeBiblio(taxon, nom, chemin, 45, null);
    }

    @Test
    @DisplayName("exporterVers écrit le CSV récapitulatif et copie les fichiers son existants")
    void exporte_csv_et_copie_les_sons(@TempDir Path source, @TempDir Path dest) throws Exception {
        Path wavA = Files.writeString(source.resolve("a_000.wav"), "RIFFa");
        Path wavB = Files.writeString(source.resolve("b_000.wav"), "RIFFb");
        ExportBiblioSons export = new ExportBiblioSons(List.of(
                entree("PIPPIP", "a_000.wav", wavA.toString()), entree("NYCNOC", "b_000.wav", wavB.toString())));

        int copies = export.exporterVers(dest);

        assertThat(copies).isEqualTo(2);
        assertThat(dest.resolve(ExportBiblioSons.NOM_CSV)).exists();
        assertThat(dest.resolve("a_000.wav")).hasContent("RIFFa");
        assertThat(dest.resolve("b_000.wav")).hasContent("RIFFb");
    }

    @Test
    @DisplayName("Une séquence introuvable sur disque est ignorée mais reste tracée dans le CSV")
    void source_introuvable_est_ignoree(@TempDir Path source, @TempDir Path dest) throws Exception {
        Path present = Files.writeString(source.resolve("present.wav"), "ok");
        ExportBiblioSons export = new ExportBiblioSons(List.of(
                entree("PIPPIP", "present.wav", present.toString()),
                entree("NYCNOC", "disparu.wav", source.resolve("disparu.wav").toString())));

        int copies = export.exporterVers(dest);

        assertThat(copies).isEqualTo(1);
        assertThat(dest.resolve("present.wav")).exists();
        assertThat(dest.resolve("disparu.wav")).doesNotExist();
        assertThat(Files.readString(dest.resolve(ExportBiblioSons.NOM_CSV))).contains("disparu.wav");
    }

    @Test
    @DisplayName("Une séquence partagée par plusieurs entrées n'est copiée qu'une fois")
    void source_partagee_copiee_une_seule_fois(@TempDir Path source, @TempDir Path dest) throws Exception {
        Path wav = Files.writeString(source.resolve("partage.wav"), "x");
        ExportBiblioSons export = new ExportBiblioSons(List.of(
                entree("PIPPIP", "partage.wav", wav.toString()), entree("PIPNAT", "partage.wav", wav.toString())));

        assertThat(export.exporterVers(dest)).isEqualTo(1);
    }
}

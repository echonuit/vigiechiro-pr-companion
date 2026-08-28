package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Ce que l'écriture d'un paquet garantit, et surtout : **ce qu'on retrouve en le relisant** (#4625).
///
/// Un paquet qu'on n'a pas relu n'est pas un paquet, c'est une archive. C'est exactement ce que
/// l'EPIC #3848 reproche au mécanisme existant : « conçu pour faire écouter ; rien ne se réimporte ».
class EcrivainPaquetTest {

    private static final String MANIFESTE = "{\"nuit\":1,\"pseudo\":null}";

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Ce qu'on écrit se relit : manifeste et séquences, aux mêmes octets")
    void ce_qu_on_ecrit_se_relit() throws IOException {
        Path a = fichierDe("seq-a.wav", new byte[] {1, 2, 3});
        Path b = fichierDe("seq-b.wav", new byte[] {4, 5});
        Path paquet = dossier.resolve("paquet.zip");
        PlanDePaquet plan = PlanDePaquet.pour(paquet, MANIFESTE, List.of(a, b));

        long octets = EcrivainPaquet.ecrire(paquet, plan, MANIFESTE, List.of(a, b));

        assertThat(octets)
                .as("l'écriture rend la taille de l'archive qu'elle vient d'écrire, pas un total de sources")
                .isEqualTo(Files.size(paquet));
        assertThat(entrees(paquet))
                .as("le manifeste et les deux séquences, sous les noms que le plan annonçait")
                .containsExactlyInAnyOrder(PlanDePaquet.NOM_MANIFESTE, "sequences/seq-a.wav", "sequences/seq-b.wav");
        assertThat(contenuTexte(paquet, PlanDePaquet.NOM_MANIFESTE)).isEqualTo(MANIFESTE);
        assertThat(contenuBrut(paquet, "sequences/seq-a.wav")).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Le paquet ne porte que ce que le plan annonçait : rien ne s'y ajoute en chemin")
    void le_paquet_ne_porte_que_ce_que_le_plan_annoncait() throws IOException {
        Path sequence = fichierDe("seq.wav", new byte[] {7});
        Path brut = fichierDe("original.wav", new byte[] {9, 9, 9});
        Path paquet = dossier.resolve("paquet.zip");
        PlanDePaquet plan = PlanDePaquet.pour(paquet, MANIFESTE, List.of(sequence));

        long octets = EcrivainPaquet.ecrire(paquet, plan, MANIFESTE, List.of(sequence));

        assertThat(octets)
                .as("la taille rendue est celle de l'archive, qui ne porte pas le brut")
                .isEqualTo(Files.size(paquet))
                .isPositive();
        assertThat(entrees(paquet))
                .as("l'enregistrement brut est sur le disque, et n'entre pas dans le paquet")
                .noneMatch(nom -> nom.contains("original"));
        assertThat(Files.exists(brut)).as("et il reste où il est").isTrue();
    }

    @Test
    @DisplayName("Un plan qui a des avertissements ne s'écrit pas en silence")
    void un_plan_avec_avertissements_refuse_de_s_ecrire() throws IOException {
        Path presente = fichierDe("seq-ok.wav", new byte[] {1});
        Path absente = dossier.resolve("seq-partie.wav");
        Path paquet = dossier.resolve("paquet.zip");
        PlanDePaquet plan = PlanDePaquet.pour(paquet, MANIFESTE, List.of(presente, absente));

        assertThatThrownBy(() -> EcrivainPaquet.ecrire(paquet, plan, MANIFESTE, List.of(presente, absente)))
                .as("écrire un paquet amputé sans le dire serait pire que refuser")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seq-partie.wav");
        assertThat(Files.exists(paquet)).as("et rien n'a été écrit").isFalse();
    }

    private Path fichierDe(String nom, byte[] octets) throws IOException {
        Path fichier = dossier.resolve(nom);
        Files.write(fichier, octets);
        return fichier;
    }

    private static List<String> entrees(Path zip) throws IOException {
        List<String> noms = new ArrayList<>();
        try (ZipInputStream flux = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entree = flux.getNextEntry(); entree != null; entree = flux.getNextEntry()) {
                noms.add(entree.getName());
            }
        }
        return noms;
    }

    private static String contenuTexte(Path zip, String nomEntree) throws IOException {
        return new String(contenuBrut(zip, nomEntree), StandardCharsets.UTF_8);
    }

    private static byte[] contenuBrut(Path zip, String nomEntree) throws IOException {
        try (ZipInputStream flux = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entree = flux.getNextEntry(); entree != null; entree = flux.getNextEntry()) {
                if (entree.getName().equals(nomEntree)) {
                    return flux.readAllBytes();
                }
            }
        }
        throw new IllegalStateException("entrée absente du paquet : " + nomEntree);
    }
}

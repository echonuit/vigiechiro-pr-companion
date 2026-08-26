package fr.univ_amu.iut.importation.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ProtectionFichier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Un dossier de fixture doit montrer **un** état, et ne doit être ni devinable ni inscriptible par un
/// autre utilisateur local (#4044, #4049).
class DossierDeFixtureTest {

    private static final String LOG = "recorder.serial_number: 1925492\n";

    @Test
    @DisplayName("#4049 : deux préparations ne partagent pas leur dossier")
    void deux_preparations_ne_partagent_pas_leur_dossier() throws IOException {
        Path premier = DossierDeFixture.preparer("sd-essai", LOG, List.of("a.wav"));
        Path second = DossierDeFixture.preparer("sd-essai", LOG, List.of("b.wav"));

        assertThat(second)
                .as("un chemin déterministe obligeait à vider avant d'écrire, faute de quoi la capture"
                        + " montrait la SOMME de deux préparations. Un dossier neuf règle cela par"
                        + " construction : il n'y a rien à vider")
                .isNotEqualTo(premier);
        assertThat(wavs(second)).containsExactly("b.wav");
        assertThat(wavs(premier)).containsExactly("a.wav");
    }

    @Test
    @DisplayName("#4049 : le dossier n'est lisible que par son propriétaire")
    void le_dossier_n_est_lisible_que_par_son_proprietaire() throws IOException {
        Path sd = DossierDeFixture.preparer("sd-droits", LOG, List.of("a.wav"));

        // La propriété passe par [ProtectionFichier], et non par `Files.getPosixFilePermissions`, qui
        // lève `UnsupportedOperationException` hors POSIX : la suite hebdomadaire rougissait dessus
        // sous Windows (#4522). Cette couture existe pour cette raison (#3778), elle couvre POSIX et
        // ACL, et elle est plus stricte que l'assertion qu'elle remplace, laquelle tolérait
        // `GROUP_READ`.
        assertThat(ProtectionFichier.restreinteAuProprietaire(sd))
                .as("cet outil fabrique des images PUBLIÉES : un dossier que les autres peuvent écrire"
                        + " laisse un tiers y déposer ce qu'il veut avant le rendu, et la documentation"
                        + " montrerait son contenu (CodeQL"
                        + " java/local-temp-file-or-directory-information-disclosure)")
                .isTrue();
    }

    @Test
    @DisplayName("#4044 : le journal et le relevé sont écrits à côté des WAV")
    void le_journal_et_le_releve_accompagnent_les_wav() throws IOException {
        Path sd = DossierDeFixture.preparer("sd-complet", LOG, List.of("a.wav", "b.wav"));

        assertThat(sd.resolve("LogPR1925492.txt")).exists();
        assertThat(sd.resolve("PaRecPR1925492_THLog.csv")).exists();
        assertThat(Files.readString(sd.resolve("LogPR1925492.txt")))
                .as("le journal porte ce qu'on lui a donné : c'est lui qui fixe la série et la nuit que"
                        + " l'inspection lira")
                .isEqualTo(LOG);
    }

    private static List<String> wavs(Path dossier) throws IOException {
        try (Stream<Path> contenu = Files.list(dossier)) {
            return contenu.map(chemin -> chemin.getFileName().toString())
                    .filter(nom -> nom.endsWith(".wav"))
                    .sorted()
                    .toList();
        }
    }
}

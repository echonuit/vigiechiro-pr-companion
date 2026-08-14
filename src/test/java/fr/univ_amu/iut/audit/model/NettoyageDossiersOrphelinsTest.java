package fr.univ_amu.iut.audit.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.GestesFichiers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Retirer un dossier de session sans passage (#3482), en **rendant compte**.
///
/// ## Pourquoi un service dédié plutôt que le helper existant
///
/// `ExtracteurZip.supprimerRecursivement` existe, mais il est **best-effort et silencieux** : « on
/// n'interrompt pas le flux si un fichier résiste ». C'est le bon comportement pour nettoyer un dossier
/// temporaire, et le mauvais pour effacer les données d'un utilisateur. Un retrait à moitié fait doit se
/// dire - c'est la leçon de #3448 et #3449, appliquée avant plutôt qu'après.
class NettoyageDossiersOrphelinsTest {

    private static final String PASS2 = "Car130711-2026-Pass2-Z1";
    private static final String PASS3 = "Car130711-2026-Pass3-Z1";
    private static final String PASS9 = "Car130711-2026-Pass9-Z1";

    @TempDir
    Path workspace;

    private final NettoyageDossiersOrphelins nettoyage = new NettoyageDossiersOrphelins();

    private Path dossierAvecSequences(String nom, int sequences) throws IOException {
        Path dossier = Files.createDirectories(workspace.resolve(nom));
        for (int numero = 0; numero < sequences; numero++) {
            Files.write(dossier.resolve("sequence-" + numero + ".wav"), new byte[1024]);
        }
        return dossier;
    }

    @Nested
    @DisplayName("Ce qui est retiré")
    class Retrait {

        @Test
        @DisplayName("Le dossier et tout son contenu disparaissent")
        void dossier_et_contenu_disparaissent() throws IOException {
            Path dossier = dossierAvecSequences(PASS2, 3);

            nettoyage.retirer(List.of(dossier));

            assertThat(dossier).doesNotExist();
        }

        @Test
        @DisplayName("#3482 : le bilan chiffre ce qui a été libéré")
        void bilan_chiffre_ce_qui_est_libere() throws IOException {
            Path premier = dossierAvecSequences(PASS2, 3);
            Path second = dossierAvecSequences(PASS3, 2);

            BilanNettoyage bilan = nettoyage.retirer(List.of(premier, second));

            assertThat(bilan.retires()).containsExactlyInAnyOrder(premier, second);
            // L'espace libéré est le chiffre qui décide : c'est pour lui qu'on fait le ménage.
            assertThat(bilan.octetsLiberes()).isEqualTo(5 * 1024L);
        }

        @Test
        @DisplayName("Seuls les dossiers demandés sont touchés")
        void seuls_les_dossiers_demandes_sont_touches() throws IOException {
            Path cible = dossierAvecSequences(PASS2, 1);
            Path voisin = dossierAvecSequences(PASS3, 1);

            nettoyage.retirer(List.of(cible));

            assertThat(voisin).exists();
        }
    }

    @Nested
    @DisplayName("Ce qu'on annonce avant d'agir")
    class Mesure {

        @Test
        @DisplayName("#3482 : la place annoncée est celle qu'occupent les dossiers")
        void mesure_la_place_occupee() throws IOException {
            Path premier = dossierAvecSequences(PASS2, 3);
            Path second = dossierAvecSequences(PASS3, 2);

            assertThat(nettoyage.mesurer(List.of(premier, second))).isEqualTo(5 * 1024L);
        }

        @Test
        @DisplayName("Un dossier absent ne promet rien")
        void dossier_absent_ne_promet_rien() {
            assertThat(nettoyage.mesurer(List.of(workspace.resolve(PASS9)))).isZero();
        }
    }

    @Nested
    @DisplayName("Ce qui résiste")
    class Resistance {

        @Test
        @DisplayName("#3482 : un dossier déjà absent n'est pas compté pour retiré")
        void dossier_deja_absent_n_est_pas_compte() {
            Path fantome = workspace.resolve(PASS9);

            BilanNettoyage bilan = nettoyage.retirer(List.of(fantome));

            // Compter un retrait qui n'a pas eu lieu ferait annoncer un espace libéré imaginaire, et
            // c'est exactement le mode de panne que #3448 vient de corriger ailleurs.
            assertThat(bilan.retires()).isEmpty();
            assertThat(bilan.octetsLiberes()).isZero();
        }

        @Test
        @DisplayName("Rien à retirer : le bilan est vide, pas en erreur")
        void rien_a_retirer() {
            BilanNettoyage bilan = nettoyage.retirer(List.of());

            assertThat(bilan.retires()).isEmpty();
            assertThat(bilan.resistants()).isEmpty();
            assertThat(bilan.octetsLiberes()).isZero();
        }
    }

    @Test
    @DisplayName("#3632 : un sous-dossier illisible compte pour zéro, comme le contrat l'écrit")
    void taille_de_compte_zero_sur_un_dossier_ferme() throws IOException {
        // Le doc-comment de `tailleDe` promet noir sur blanc : « Un fichier illisible compte pour zéro
        // plutôt que de faire échouer la mesure : mieux vaut annoncer un gain prudent qu'aucun gain. »
        // Le code faisait l'inverse : `Files.walk` enveloppe son échec de parcours dans une
        // `UncheckedIOException`, que le `catch (IOException)` ne voit pas, et le gain estimé du
        // nettoyage cassait au lieu d'annoncer un chiffre prudent.
        Path dossier = Files.createDirectories(workspace.resolve("orphelin"));
        Files.writeString(dossier.resolve("lisible.wav"), "12345");
        Path ferme = Files.createDirectories(dossier.resolve("ferme"));
        Files.writeString(ferme.resolve("tenu.wav"), "abc");

        // ⚠️ L'illisibilité est FABRIQUÉE et non demandée au système : `File.setReadable(false)` rend
        // `false` sous Windows, et ce test échouait donc là-bas avant même d'éprouver quoi que ce soit
        // (#3526). Même couture que `GestesFichiers` pour #3525.
        long taille = NettoyageDossiersOrphelins.tailleDe(dossier, new GestesFichiers() {
            @Override
            public java.util.stream.Stream<Path> lister(Path aLister) throws IOException {
                if (aLister.equals(ferme)) {
                    throw new java.nio.file.AccessDeniedException(aLister.toString());
                }
                return Files.list(aLister);
            }
        });

        assertThat(taille)
                .as("ce qui a pu être lu reste compté : observer ne doit pas être plus fragile que ce"
                        + " qu'on observe")
                .isEqualTo(5L);
    }
}

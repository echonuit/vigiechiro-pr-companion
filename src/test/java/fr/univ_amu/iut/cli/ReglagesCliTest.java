package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.CleDeReglage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'option globale `--reglage <cle>=<valeur>` (#4075) : la porte par laquelle une borne se relève
/// depuis un produit **installé**, là où `-D` n'existe pas.
///
/// Ce qui est éprouvé ici est l'analyse et la **validation**, pas l'effet des bornes elles-mêmes : les
/// classes qui les lisent ont leurs propres tests, et le harnais `bats` traverse le refus d'une archive
/// hors bornes sur un vrai processus.
class ReglagesCliTest {

    /// ⚠️ Les propriétés posées par un test survivraient à la JVM de test et fausseraient les suivants :
    /// une borne abaissée ici ferait refuser une archive légitime ailleurs, et le rouge tomberait très
    /// loin de sa cause.
    @AfterEach
    void nettoyerLesProprietes() {
        for (CleDeReglage cle : CleDeReglage.values()) {
            System.clearProperty(cle.propriete());
        }
    }

    @Test
    @DisplayName("Un réglage connu pose sa propriété système")
    void un_reglage_connu_pose_sa_propriete() {
        Optional<String> refus = Cli.poserLesReglages(List.of("import.zip.max-entrees=5000"));

        assertThat(refus).as("un réglage connu n'a aucune raison d'être refusé").isEmpty();
        assertThat(System.getProperty(CleDeReglage.IMPORT_ZIP_MAX_ENTREES.propriete()))
                .isEqualTo("5000");
    }

    @Test
    @DisplayName("Une clé inconnue est refusée, et le refus nomme celles qui existent")
    void une_cle_inconnue_nomme_les_admises() {
        // ⚠️ Le refus ne se contente pas de dire non : sans la liste, l'utilisateur n'a aucun moyen de
        // trouver le nom juste - ces clés ne sont écrites nulle part ailleurs que dans le registre.
        Optional<String> refus = Cli.poserLesReglages(List.of("import.zip.max-entrée=5000"));

        assertThat(refus).isPresent();
        assertThat(refus.get())
                .contains("import.zip.max-entrée")
                .contains(CleDeReglage.IMPORT_ZIP_MAX_ENTREES.nom())
                .contains(CleDeReglage.S3_HOTES.nom());
    }

    @Test
    @DisplayName("Une clé inconnue ne pose AUCUNE propriété")
    void une_cle_inconnue_ne_pose_rien() {
        // Le point qui justifie le registre : `--reglage` écrit une propriété système, donc sans
        // validation elle en écrirait n'importe laquelle, y compris celles de la plateforme.
        Cli.poserLesReglages(List.of("java.home=/tmp/pirate"));

        assertThat(System.getProperty("java.home")).isNotEqualTo("/tmp/pirate");
        assertThat(System.getProperty("vigiechiro.java.home")).isNull();
    }

    @Test
    @DisplayName("Un réglage sans « = », ou sans valeur, est refusé avec la forme attendue")
    void un_reglage_mal_ecrit_est_refuse() {
        assertThat(Cli.poserLesReglages(List.of("import.zip.max-entrees")))
                .as("sans « = », il n'y a pas de valeur à poser")
                .isPresent();
        assertThat(Cli.poserLesReglages(List.of("import.zip.max-entrees=")))
                .as("une valeur vide effacerait la borne sans le dire")
                .isPresent();
        assertThat(Cli.poserLesReglages(List.of("=5000")))
                .as("sans clé, rien ne désigne la borne")
                .isPresent();
    }

    @Test
    @DisplayName("L'option se retire des arguments, où qu'elle soit, et se répète")
    void l_option_se_retire_et_se_repete() {
        // Laissée dans les arguments, picocli la refuserait : aucune sous-commande ne la déclare.
        List<String> restants = new ArrayList<>();
        List<String> reglages = Cli.extraireReglages(
                List.of(
                        "--reglage",
                        "import.zip.max-entrees=5000",
                        "importer",
                        "--point",
                        "12",
                        "--reglage",
                        "s3.hotes=exemple.fr"),
                restants);

        assertThat(reglages).containsExactly("import.zip.max-entrees=5000", "s3.hotes=exemple.fr");
        assertThat(restants).containsExactly("importer", "--point", "12");
    }

    @Test
    @DisplayName("Chaque clé du registre compose une propriété du produit")
    void chaque_cle_compose_une_propriete_du_produit() {
        // Non-vacuité, et garde du préfixe : une clé qui perdrait `vigiechiro.` poserait une propriété
        // de la plateforme, ce que la validation est censée empêcher.
        assertThat(CleDeReglage.values()).isNotEmpty();
        for (CleDeReglage cle : CleDeReglage.values()) {
            assertThat(cle.propriete()).startsWith(CleDeReglage.PREFIXE).endsWith(cle.nom());
            assertThat(cle.commentRelever())
                    .as("le refus doit nommer ce que l'utilisateur tape, pas une propriété JVM")
                    .contains("--reglage " + cle.nom() + "=")
                    .doesNotContain("-D");
        }
    }

    @Test
    @DisplayName("Aucun message du produit ne conseille une propriété JVM")
    void aucun_message_ne_conseille_une_propriete_jvm() throws IOException {
        // ⚠️ Le défaut que #4075 corrige n'était pas une borne mal réglée : c'était un CONSEIL
        // impossible. Trois refus disaient « relancez avec -Dvigiechiro.… », geste qu'un produit
        // installé ne permet pas - le lanceur passe ses arguments à `main`, jamais à la machine
        // virtuelle. Un message exact et inapplicable est la famille de défaut que l'ADR 3470 combat.
        //
        // Les commentaires sont retirés avant la recherche : ils PARLENT de ce conseil, précisément
        // pour dire pourquoi il a disparu. Un garde qui les compterait rougirait sur son propre motif
        // (ADR 3645).
        List<Path> fautifs = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
            for (Path source :
                    sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (sansCommentaires(Files.readString(source)).contains("-Dvigiechiro.")) {
                    fautifs.add(source);
                }
            }
        }

        assertThat(fautifs)
                .as("Ces sources conseillent encore une propriété JVM dans du code exécutable. Un "
                        + "produit installé n'en accepte aucune : le conseil y est inapplicable, et le "
                        + "message reste pourtant d'apparence correcte. Nommer `--reglage <cle>=<valeur>`.")
                .isEmpty();
    }

    /// Le source privé de ses commentaires, pour qu'un garde textuel ne se compte pas lui-même.
    private static String sansCommentaires(String source) {
        return source.lines()
                .map(String::strip)
                .filter(ligne -> !ligne.startsWith("//") && !ligne.startsWith("*") && !ligne.startsWith("/*"))
                .collect(Collectors.joining("\n"));
    }
}

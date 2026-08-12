package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.TailleFichier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Les **deux** contrats d'effacement, et le fait qu'ils ne se confondent pas (#3574).
///
/// Sept variantes de ce geste vivaient dans le dépôt, avec quatre comportements en cas d'échec : lever,
/// lever sans le déclarer, avaler, ou rapporter. Elles portaient toutes des noms qui se ressemblaient,
/// si bien qu'une copie faite depuis le mauvais modèle changeait le comportement en cas de panne sans
/// rien casser de visible.
///
/// ⚠️ Ce qui rend ces tests possibles : un dossier **en lecture seule** refuse qu'on retire ce qu'il
/// contient. C'est la seule façon portable de fabriquer un fichier qui résiste, et elle vaut aussi pour
/// le cas réel - un support monté en lecture seule, un droit retiré.
class ArborescenceFichiersTest {

    @TempDir
    Path racine;

    @Test
    @DisplayName("effacerAuMieux ne lève pas, et rend ce qui a résisté")
    void efface_au_mieux_rend_ce_qui_resiste() throws IOException {
        Path verrouille = arborescenceQuiResiste();

        List<ArborescenceFichiers.EchecEffacement> restants = ArborescenceFichiers.effacerAuMieux(verrouille);

        assertThat(restants)
                .as("un nettoyage de temporaire ne doit pas transformer une opération réussie en échec,"
                        + " mais se taire laisserait l'appelant sans rien à dire")
                .isNotEmpty();
        rendreEffacable(verrouille);
    }

    @Test
    @DisplayName("effacerAuMieux retire tout ce qui peut l'être, sans s'arrêter au premier récalcitrant")
    void efface_au_mieux_continue_apres_un_echec() throws IOException {
        // Un SEUL arbre, mixte : le récalcitrant est rencontré AVANT l'effaçable dans l'ordre inverse.
        // Deux dossiers séparés ne prouveraient rien du parcours - c'est l'erreur que la mutation a
        // révélée : arrêter au premier échec laissait le test vert.
        Path melange = Files.createDirectories(racine.resolve("melange"));
        Path libre = Files.writeString(melange.resolve("libre.txt"), "effaçable");
        Path verrou = Files.createDirectories(melange.resolve("verrou"));
        Files.writeString(verrou.resolve("tenu.txt"), "ne s'en va pas");
        assertThat(verrou.toFile().setWritable(false)).isTrue();

        List<ArborescenceFichiers.EchecEffacement> restants = ArborescenceFichiers.effacerAuMieux(melange);

        assertThat(libre)
                .as("s'arrêter au premier échec laisserait derrière lui tout ce qui était effaçable")
                .doesNotExist();
        assertThat(restants).as("et ce qui a résisté est nommé, avec sa raison").isNotEmpty();
        rendreEffacable(verrou);
    }

    @Test
    @DisplayName("effacerAuMieux sur ce qui n'existe pas : rien à signaler")
    void efface_au_mieux_sur_l_absence() {
        List<ArborescenceFichiers.EchecEffacement> restants =
                ArborescenceFichiers.effacerAuMieux(racine.resolve("jamais-cree"));

        assertThat(restants)
                .as("supprimer ce qui est déjà absent est un succès, pas un cas limite")
                .isEmpty();
    }

    @Test
    @DisplayName("supprimerRecursivement lève quand quelque chose résiste : l'appelant décide")
    void supprimer_recursivement_leve() throws IOException {
        Path verrouille = arborescenceQuiResiste();

        assertThatThrownBy(() -> ArborescenceFichiers.supprimerRecursivement(verrouille))
                .as("une bascule de restauration qui ne parvient pas à retirer l'ancien dossier ne doit"
                        + " pas enchaîner sur le renommage comme si de rien n'était")
                .isInstanceOf(IOException.class);
        rendreEffacable(verrouille);
    }

    @Test
    @DisplayName("supprimerRecursivement sur ce qui n'existe pas ne lève pas")
    void supprimer_recursivement_sur_l_absence() {
        assertThatCode(() -> ArborescenceFichiers.supprimerRecursivement(racine.resolve("jamais-cree")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("#3627 : peser rend le total ET nomme ce qu'elle n'a pas pu lire")
    void peser_nomme_ce_qu_elle_n_a_pas_pu_lire() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("nuit"));
        Files.writeString(dossier.resolve("lisible.wav"), "12345");
        Path opaque = Files.writeString(dossier.resolve("opaque.wav"), "ceci ne sera jamais pesé");

        ArborescenceFichiers.Pesee pesee = ArborescenceFichiers.peser(dossier, illisible(opaque));

        assertThat(pesee.octets())
                .as("le reste du dossier reste mesuré : s'arrêter au premier trou ne dirait pas combien"
                        + " pèse ce qu'on a pu lire, et l'appelant a besoin des deux")
                .isEqualTo(5L);
        assertThat(pesee.illisibles())
                .as("sans cette liste, le zéro du fichier opaque se confond avec un fichier vide")
                .extracting(ArborescenceFichiers.EchecLecture::chemin)
                .containsExactly(opaque);
        assertThat(pesee.complete()).isFalse();
    }

    @Test
    @DisplayName("#3627 : tout lisible, la pesée se déclare complète")
    void peser_sur_un_dossier_entierement_lisible() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("nuit-saine"));
        Files.writeString(dossier.resolve("a.wav"), "123");
        Files.writeString(dossier.resolve("b.wav"), "45");

        ArborescenceFichiers.Pesee pesee = ArborescenceFichiers.peser(dossier, TailleFichier.reelle());

        // Sans ce cas, `complete()` pourrait rendre `false` en toutes circonstances et l'autre test
        // resterait vert : le garde refuserait alors TOUTE sauvegarde, ce qui est une panne aussi.
        assertThat(pesee.octets()).isEqualTo(5L);
        assertThat(pesee.complete()).isTrue();
    }

    /// Une pesée qui échoue sur un fichier précis, et se comporte normalement sur les autres.
    ///
    /// ⚠️ C'est le port qui rend ce test possible : `Files.size` ne lève pas sur commande. Un dossier
    /// en `chmod 000` ferait échouer le **parcours** avant la pesée, et un lien mort est écarté par
    /// `isRegularFile` - la panne réelle n'a pas d'équivalent portable qu'on puisse fabriquer.
    private TailleFichier illisible(Path interdit) {
        return fichier -> {
            if (fichier.equals(interdit)) {
                throw new IOException("Permission non accordée");
            }
            return Files.size(fichier);
        };
    }

    /// Un dossier dont le contenu ne peut pas être retiré : le parent est en lecture seule.
    private Path arborescenceQuiResiste() throws IOException {
        Path dossier = Files.createDirectories(racine.resolve("verrouille"));
        Files.writeString(dossier.resolve("tenu.txt"), "ne s'en va pas");
        assertThat(dossier.toFile().setWritable(false))
                .as("sans ce droit retiré, le test ne prouverait rien : il faut que la suppression ÉCHOUE")
                .isTrue();
        return dossier;
    }

    /// Rend le dossier effaçable, sans quoi `@TempDir` échoue à nettoyer et fait rougir un test voisin.
    private void rendreEffacable(Path dossier) {
        assertThat(dossier.toFile().setWritable(true)).isTrue();
    }
}

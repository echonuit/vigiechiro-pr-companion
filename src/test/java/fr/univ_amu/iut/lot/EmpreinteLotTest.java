package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.lot.model.EmpreinteLot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// [EmpreinteLot] (#1993) : l'empreinte de la liste source qui rend détectable un lot ayant changé
/// entre deux reprises.
///
/// Ce qu'on vérifie ici est précisément ce dont dépend la partition en archives
/// ([fr.univ_amu.iut.lot.model.PlanificateurArchives] : glouton sur les tailles, à ordre préservé).
/// Chaque test correspond donc à une façon dont l'archive `N` pourrait changer de contenu tout en
/// gardant son nom.
class EmpreinteLotTest {

    @Test
    @DisplayName("liste inchangée : même empreinte (la reprise doit pouvoir conclure « rien n'a bougé »)")
    void meme_liste_meme_empreinte(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav", 10);
        Path b = fichier(dossier, "b.wav", 20);

        assertThat(EmpreinteLot.de(List.of(a, b))).isEqualTo(EmpreinteLot.de(List.of(a, b)));
    }

    @Test
    @DisplayName("une séquence ajoutée décale la partition : empreinte différente")
    void fichier_ajoute_change_l_empreinte(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav", 10);
        Path b = fichier(dossier, "b.wav", 20);

        assertThat(EmpreinteLot.de(List.of(a))).isNotEqualTo(EmpreinteLot.de(List.of(a, b)));
    }

    @Test
    @DisplayName("une séquence re-transformée change de taille : empreinte différente")
    void taille_differente_change_l_empreinte(@TempDir Path dossier) throws IOException {
        Path avant = fichier(dossier, "a.wav", 10);
        String empreinteAvant = EmpreinteLot.de(List.of(avant));
        Files.write(avant, new byte[999]);

        assertThat(EmpreinteLot.de(List.of(avant))).isNotEqualTo(empreinteAvant);
    }

    @Test
    @DisplayName("l'ordre compte : la partition est un glouton à ordre préservé")
    void ordre_different_change_l_empreinte(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav", 10);
        Path b = fichier(dossier, "b.wav", 20);

        assertThat(EmpreinteLot.de(List.of(a, b))).isNotEqualTo(EmpreinteLot.de(List.of(b, a)));
    }

    @Test
    @DisplayName("le nom compte, à taille égale : deux séquences distinctes ne se confondent pas")
    void nom_different_a_taille_egale_change_l_empreinte(@TempDir Path dossier) throws IOException {
        Path a = fichier(dossier, "a.wav", 10);
        Path b = fichier(dossier, "b.wav", 10);

        assertThat(EmpreinteLot.de(List.of(a))).isNotEqualTo(EmpreinteLot.de(List.of(b)));
    }

    @Test
    @DisplayName("aucune concaténation de noms ne peut usurper une autre liste (séparateur explicite)")
    void noms_concatenables_ne_se_confondent_pas(@TempDir Path dossier) throws IOException {
        // Sans séparation nom/taille, « ab » + 1 octet et « a » + « b1… » pourraient produire la même
        // chaîne. Le format « nom taille\n » l'interdit.
        Path ensemble = fichier(dossier, "ab", 1);
        Path premier = fichier(dossier, "a", 1);
        Path second = fichier(dossier, "b", 1);

        assertThat(EmpreinteLot.de(List.of(ensemble))).isNotEqualTo(EmpreinteLot.de(List.of(premier, second)));
    }

    @Test
    @DisplayName("liste vide : empreinte stable, pas d'exception (aucune séquence à déposer)")
    void liste_vide(@TempDir Path dossier) {
        assertThat(EmpreinteLot.de(List.of())).isEqualTo(EmpreinteLot.de(List.of()));
    }

    @Test
    @DisplayName("fichier absent : échec franc plutôt qu'une empreinte inventée")
    void fichier_absent_echoue(@TempDir Path dossier) {
        assertThatThrownBy(() -> EmpreinteLot.de(List.of(dossier.resolve("fantome.wav"))))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("fantome.wav");
    }

    private static Path fichier(Path dossier, String nom, int octets) throws IOException {
        Path chemin = dossier.resolve(nom);
        Files.write(chemin, new byte[octets]);
        return chemin;
    }
}

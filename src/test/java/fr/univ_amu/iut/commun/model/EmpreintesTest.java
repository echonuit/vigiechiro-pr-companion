package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Empreinte courte (#1299) : le contrat qui rend la réactivation sûre. Deux fichiers homonymes de
/// contenus différents doivent diverger ; le même contenu doit redonner la même empreinte, y
/// compris relu depuis un autre chemin (c'est tout l'objet : reconnaître un fichier réimporté).
class EmpreintesTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Même contenu, chemins différents : même empreinte courte (fichier réimporté reconnu)")
    void meme_contenu_meme_empreinte() throws IOException {
        byte[] contenu = contenuPseudoAleatoire(100_000, 7);
        Path original = Files.write(dossier.resolve("seq_000.wav"), contenu);
        Path ailleurs = Files.createDirectories(dossier.resolve("ailleurs"));
        Path reimporte = Files.write(ailleurs.resolve("seq_000.wav"), contenu);

        assertThat(Empreintes.empreinteCourte(reimporte)).isEqualTo(Empreintes.empreinteCourte(original));
    }

    @Test
    @DisplayName("Deux homonymes de contenus différents : empreintes différentes")
    void homonymes_differents_empreintes_differentes() throws IOException {
        Path nuit1 = Files.write(dossier.resolve("nuit1-seq_000.wav"), contenuPseudoAleatoire(100_000, 7));
        Path nuit2 = Files.write(dossier.resolve("nuit2-seq_000.wav"), contenuPseudoAleatoire(100_000, 13));

        assertThat(Empreintes.empreinteCourte(nuit1)).isNotEqualTo(Empreintes.empreinteCourte(nuit2));
    }

    @Test
    @DisplayName("Seuls les 64 premiers Kio comptent : un ajout au-delà ne change pas l'empreinte")
    void seuls_les_64_premiers_kio_comptent() throws IOException {
        byte[] tete = contenuPseudoAleatoire(Empreintes.OCTETS_EMPREINTE_COURTE, 7);
        Path court = Files.write(dossier.resolve("court.wav"), tete);
        byte[] etendu = Arrays.copyOf(tete, tete.length + 500_000);
        Arrays.fill(etendu, tete.length, etendu.length, (byte) 42);
        Path long_ = Files.write(dossier.resolve("long.wav"), etendu);

        assertThat(Empreintes.empreinteCourte(long_)).isEqualTo(Empreintes.empreinteCourte(court));
    }

    @Test
    @DisplayName("Fichier plus petit que 64 Kio : l'empreinte couvre le fichier entier")
    void fichier_plus_petit_que_la_fenetre() throws IOException {
        byte[] contenu = contenuPseudoAleatoire(1_000, 7);
        Path petit = Files.write(dossier.resolve("petit.wav"), contenu);
        byte[] modifie = contenu.clone();
        modifie[999] ^= 1;
        Path different = Files.write(dossier.resolve("petit-modifie.wav"), modifie);

        assertThat(Empreintes.empreinteCourte(petit)).isNotEqualTo(Empreintes.empreinteCourte(different));
        assertThat(Empreintes.empreinteCourte(petit)).isEqualTo(Empreintes.sha256Hex(contenu));
    }

    /// Contenu déterministe par graine (pas de Random partagé : chaque test décrit son contenu).
    private static byte[] contenuPseudoAleatoire(int taille, int graine) {
        byte[] octets = new byte[taille];
        int valeur = graine;
        for (int i = 0; i < taille; i++) {
            valeur = valeur * 31 + 17;
            octets[i] = (byte) valeur;
        }
        return octets;
    }
}

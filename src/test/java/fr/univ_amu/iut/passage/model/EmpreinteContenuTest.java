package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le type valeur [EmpreinteContenu] (#2484, EPIC #2483) et son intégration dans [SequenceDEcoute] :
/// regroupement de (taille, empreinte), accès de compatibilité, normalisation du nul.
class EmpreinteContenuTest {

    @Test
    @DisplayName("ABSENTE ne porte ni taille ni empreinte")
    void absente_est_vide() {
        assertThat(EmpreinteContenu.ABSENTE.tailleOctets()).isNull();
        assertThat(EmpreinteContenu.ABSENTE.empreinte()).isNull();
    }

    @Test
    @DisplayName("deux signatures de mêmes valeurs sont égales (valeur, pas identité)")
    void egalite_de_valeur() {
        assertThat(new EmpreinteContenu(64L, "abcd")).isEqualTo(new EmpreinteContenu(64L, "abcd"));
    }

    @Test
    @DisplayName("SequenceDEcoute : empreinte()/tailleOctets() délèguent à l'EmpreinteContenu")
    void accesseurs_delegues() {
        SequenceDEcoute seq = sequence(new EmpreinteContenu(2048L, "sha-court"));

        assertThat(seq.empreinteContenu()).isEqualTo(new EmpreinteContenu(2048L, "sha-court"));
        assertThat(seq.tailleOctets()).isEqualTo(2048L);
        assertThat(seq.empreinte()).isEqualTo("sha-court");
    }

    @Test
    @DisplayName("SequenceDEcoute : un empreinteContenu nul est normalisé en ABSENTE (lecteurs sans test du nul)")
    void nul_normalise_en_absente() {
        SequenceDEcoute seq = sequence(null);

        assertThat(seq.empreinteContenu()).isSameAs(EmpreinteContenu.ABSENTE);
        assertThat(seq.tailleOctets()).isNull();
        assertThat(seq.empreinte()).isNull();
    }

    private static SequenceDEcoute sequence(EmpreinteContenu empreinte) {
        return new SequenceDEcoute(1L, "f.wav", 2L, 0, 0.0, 5.0, "/t/f.wav", false, 3L, null, empreinte);
    }
}

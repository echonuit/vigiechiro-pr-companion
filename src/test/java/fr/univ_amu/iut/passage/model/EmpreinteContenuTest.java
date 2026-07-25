package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le type valeur [EmpreinteContenu] (EPIC #2483) et son intégration dans [SequenceDEcoute] (#2484) et
/// [EnregistrementOriginal] (#2492) : regroupement de (taille, empreinte), accès de compatibilité,
/// normalisation du nul.
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

    @Test
    @DisplayName("EnregistrementOriginal : sha256()/tailleOctets() délèguent à l'EmpreinteContenu")
    void enregistrement_accesseurs_delegues() {
        EnregistrementOriginal original = original(new EmpreinteContenu(4096L, "sha-complet"));

        assertThat(original.empreinteContenu()).isEqualTo(new EmpreinteContenu(4096L, "sha-complet"));
        assertThat(original.tailleOctets()).isEqualTo(4096L);
        assertThat(original.sha256()).isEqualTo("sha-complet");
    }

    @Test
    @DisplayName("EnregistrementOriginal : un empreinteContenu nul est normalisé en ABSENTE")
    void enregistrement_nul_normalise_en_absente() {
        EnregistrementOriginal original = original(null);

        assertThat(original.empreinteContenu()).isSameAs(EmpreinteContenu.ABSENTE);
        assertThat(original.tailleOctets()).isNull();
        assertThat(original.sha256()).isNull();
    }

    @Test
    @DisplayName("EnregistrementOriginal : le constructeur de compatibilité 7-arg regroupe le sha256 (sans taille)")
    void enregistrement_compat_7arg_regroupe_le_sha() {
        EnregistrementOriginal original = new EnregistrementOriginal(1L, "o.wav", "/b/o.wav", 5.0, 384_000, "sha", 3L);

        assertThat(original.empreinteContenu()).isEqualTo(new EmpreinteContenu(null, "sha"));
        assertThat(original.sha256()).isEqualTo("sha");
        assertThat(original.tailleOctets()).isNull();
    }

    private static SequenceDEcoute sequence(EmpreinteContenu empreinte) {
        return new SequenceDEcoute(1L, "f.wav", 2L, 0, 0.0, 5.0, "/t/f.wav", false, 3L, null, empreinte);
    }

    private static EnregistrementOriginal original(EmpreinteContenu empreinte) {
        return new EnregistrementOriginal(1L, "o.wav", "/b/o.wav", 5.0, 384_000, 3L, empreinte);
    }
}

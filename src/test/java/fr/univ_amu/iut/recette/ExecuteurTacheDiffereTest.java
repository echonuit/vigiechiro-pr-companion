package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Ce que [ExecuteurTacheDiffere] doit faire, et ce qu'il ne doit pas faire.
///
/// Un dispositif qui sert à faire rougir doit d'abord prouver **qu'il fait rougir**, et qu'il ne
/// rougit pas sur du bon travail. Sans les deux, il désignerait tout ou rien, et un banc qui accuse
/// tout le monde s'apprend à ignorer (ADR 4002).
///
/// **Le toolkit JavaFX est monté**, et pas par habitude : `ExecuteurTacheAsynchrone` rend son
/// résultat par `Platform.runLater`, qui n'a nulle part où le poster sans lui. Sans cette extension,
/// `succes` et `echec` ne sont jamais appelés, l'exception se perd sur le fil virtuel, et les cas
/// expirent en accusant le retard là où il n'y a qu'un toolkit absent.
@ExtendWith(ApplicationExtension.class)
class ExecuteurTacheDiffereTest {

    /// Court, pour que ces cas restent en dixièmes de seconde.
    private static final long RETARD_MS = 150;

    private static final long MARGE_MS = 5_000;

    private final ExecuteurTache reel = new ExecuteurTacheAsynchrone();

    @Test
    @DisplayName("une lecture IMMÉDIATE ne voit rien : c'est le trou qu'on cherche à révéler")
    void une_lecture_immediate_ne_voit_rien() {
        AtomicReference<String> port = new AtomicReference<>();
        ExecuteurTache differe = new ExecuteurTacheDiffere(reel, RETARD_MS);

        differe.executer(() -> "écrit", port::set, erreur -> {});

        // L'assertion pressée, celle que ce dispositif doit faire échouer. Sans retard, elle passerait
        // le plus souvent - c'est ce qui rend le défaut coûteux : il a l'AIR de tenir.
        assertThat(port.get())
                .as("sous l'exécuteur différé, le port n'est pas encore écrit quand une assertion"
                        + " immédiate le lit")
                .isNull();
    }

    @Test
    @DisplayName("une vraie ATTENTE le voit : le dispositif n'invente pas d'échec")
    void une_vraie_attente_le_voit() {
        AtomicReference<String> port = new AtomicReference<>();
        ExecuteurTache differe = new ExecuteurTacheDiffere(reel, RETARD_MS);

        differe.executer(() -> "écrit", port::set, erreur -> {});
        Attente.que(() -> port.get() != null, "que le travail différé ait écrit le port", MARGE_MS);

        // Le contrôle NÉGATIF, et il porte tout : sans lui, un exécuteur qui n'exécuterait jamais
        // rien passerait le premier cas et paraîtrait bon.
        assertThat(port.get()).isEqualTo("écrit");
    }

    @Test
    @DisplayName("le travail est bien RETARDÉ, et pas seulement l'écriture du port")
    void le_travail_est_retarde() {
        AtomicReference<Long> debutDuTravail = new AtomicReference<>();
        long avant = System.nanoTime();
        ExecuteurTache differe = new ExecuteurTacheDiffere(reel, RETARD_MS);

        differe.executer(
                () -> {
                    debutDuTravail.set(System.nanoTime());
                    return "écrit";
                },
                resultat -> {},
                erreur -> {});
        Attente.que(() -> debutDuTravail.get() != null, "que le travail différé ait commencé", MARGE_MS);

        // Le retard porte sur le TRAVAIL, pas sur le compte rendu : c'est ce qui distingue ce jumeau
        // d'`ExecuteurTacheRalenti`, qui freine le relais de progression.
        assertThat((debutDuTravail.get() - avant) / 1_000_000)
                .as("le travail ne commence qu'après le retard")
                .isGreaterThanOrEqualTo(RETARD_MS);
    }

    @Test
    @DisplayName("une erreur du travail remonte, au lieu de se perdre dans le retard")
    void une_erreur_remonte() {
        AtomicReference<Throwable> vu = new AtomicReference<>();
        ExecuteurTache differe = new ExecuteurTacheDiffere(reel, RETARD_MS);

        differe.executer(
                () -> {
                    throw new IllegalStateException("le travail a échoué");
                },
                resultat -> {},
                vu::set);
        Attente.que(() -> vu.get() != null, "que l'échec du travail différé remonte", MARGE_MS);

        assertThat(vu.get()).hasMessage("le travail a échoué");
    }
}

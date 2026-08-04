package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Recopie interruptible du socle (#2733) : ce qui distingue cette boucle d'un `transferTo`, c'est
/// qu'on peut l'arrêter au milieu d'une entrée et qu'elle donne des nouvelles pendant qu'elle dure.
///
/// Les deux appelants (décompression d'une carte SD, écriture d'une archive d'export) ont chacun leur
/// test d'annulation en cours d'entrée : ici on éprouve le mécanisme lui-même, hors de tout ZIP.
///
/// Survivant PIT **assumé** (lu après coup) : le `>=` du palier remplacé par `>`. La notification tombe
/// alors un bloc plus tard, ce que personne ne peut observer ; épingler la valeur exacte du palier
/// figerait un seuil arbitraire. Le mutant voisin, lui, était un **vrai trou** : remplacer la
/// soustraction du cumul par une addition faisait notifier à chaque bloc, et rien ne rougissait tant
/// que le test ne bornait pas le nombre de paliers **par le haut**.
class CopieInterruptibleTest {

    /// Au-delà de plusieurs paliers de progression : c'est la taille à partir de laquelle une entrée
    /// « dure » du point de vue de l'utilisateur.
    private static final int VOLUMINEUX = 12 * 1024 * 1024;

    @Test
    @DisplayName("Le flux est recopié tel quel, octet pour octet")
    void copie_fidele() throws IOException {
        byte[] contenu = "Carré 640380, point A1".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        CopieInterruptible.copier(
                new ByteArrayInputStream(contenu), destination, JetonAnnulation.neutre(), octets -> {});

        assertThat(destination.toByteArray()).isEqualTo(contenu);
    }

    @Test
    @DisplayName("Un flux vide se recopie sans rien écrire ni notifier")
    void source_vide() throws IOException {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        List<Long> paliers = new ArrayList<>();

        CopieInterruptible.copier(
                new ByteArrayInputStream(new byte[0]), destination, JetonAnnulation.neutre(), paliers::add);

        assertThat(destination.size()).isZero();
        assertThat(paliers).isEmpty();
    }

    @Test
    @DisplayName("Un jeton déjà annulé arrête la copie avant d'écrire le premier octet")
    void deja_annule_n_ecrit_rien() {
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler();
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> CopieInterruptible.copier(
                        new ByteArrayInputStream(new byte[VOLUMINEUX]), destination, jeton, octets -> {}));

        assertThat(destination.size()).isZero();
    }

    @Test
    @DisplayName("Annulation en cours de route : la copie s'arrête loin de la fin du flux")
    void annulation_en_cours_de_copie() {
        JetonAnnulation jeton = new JetonAnnulation();
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        assertThatExceptionOfType(OperationAnnuleeException.class)
                .isThrownBy(() -> CopieInterruptible.copier(
                        new ByteArrayInputStream(new byte[VOLUMINEUX]), destination, jeton, octets -> jeton.annuler()));

        // Annulé au premier palier (4 Mio), plus un bloc au plus : très en deçà des 12 Mio du flux. Un
        // `transferTo` aurait recopié la totalité avant que quiconque puisse dire quoi que ce soit.
        assertThat(destination.size()).isPositive().isLessThan(VOLUMINEUX / 2);
    }

    @Test
    @DisplayName("Les paliers notifient un cumul croissant ; un petit flux n'en produit aucun")
    void paliers_de_progression() throws IOException {
        List<Long> paliers = new ArrayList<>();

        CopieInterruptible.copier(
                new ByteArrayInputStream(new byte[VOLUMINEUX]),
                new ByteArrayOutputStream(),
                JetonAnnulation.neutre(),
                paliers::add);

        // La borne HAUTE compte autant que la basse : le palier existe pour que l'appelant ne soit pas
        // inondé (chaque notification est marshalée vers le fil JavaFX). Trois paliers de 4 Mio dans
        // 12 Mio, pas un de plus. Sans cette borne, un cumul mal remis à zéro notifierait à chaque bloc,
        // soit près de deux cents fois ici, et rien ne rougirait.
        assertThat(paliers).hasSizeBetween(2, 3).isSorted();
        assertThat(paliers.get(0)).isGreaterThanOrEqualTo(4L * 1024 * 1024);
        assertThat(paliers.get(paliers.size() - 1)).isLessThanOrEqualTo((long) VOLUMINEUX);

        List<Long> aucun = new ArrayList<>();
        CopieInterruptible.copier(
                new ByteArrayInputStream(new byte[1024]),
                new ByteArrayOutputStream(),
                JetonAnnulation.neutre(),
                aucun::add);

        assertThat(aucun)
                .as("une tranche ordinaire ne doit coûter aucune notification supplémentaire")
                .isEmpty();
    }

    @Test
    @DisplayName("Ni la source ni la destination ne sont fermées : l'appelant continue d'écrire après")
    void ne_ferme_aucun_flux() throws IOException {
        // Les deux appelants recopient DANS un flux d'archive qu'ils alimentent encore après (entrée
        // suivante, puis fin d'archive) : une fermeture discrète casserait le ZIP à la deuxième entrée.
        InputStream source = new ByteArrayInputStream("un".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        CopieInterruptible.copier(source, destination, JetonAnnulation.neutre(), octets -> {});
        destination.write("deux".getBytes(StandardCharsets.UTF_8));

        assertThat(source.read())
                .as("la source reste ouverte, simplement épuisée")
                .isEqualTo(-1);
        assertThat(destination.toString(StandardCharsets.UTF_8)).isEqualTo("undeux");
    }
}

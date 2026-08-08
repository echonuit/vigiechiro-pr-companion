package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les **bornes** des critères de la barre de filtres audio (#3520).
///
/// Ces prédicats décident de ce qu'un naturaliste voit. Une borne fausse **exactement au seuil**
/// n'affiche aucune erreur : elle masque ou ajoute des observations, en silence, et rien dans l'écran
/// ne le signale.
///
/// La mesure de mutation les a désignés : `CriteresAudio` comptait **9 mutants survivants**, dont
/// **six `ConditionalsBoundary`** - un sur le seuil de probabilité, cinq sur la plage horaire. Les
/// lignes étaient donc bien exécutées par les tests existants, qui ne regardaient simplement jamais
/// ce qui se passe **à la borne**.
///
/// Chaque cas ci-dessous suit la même forme : **sous**, **exactement à**, et **au-dessus** de la
/// borne. C'est le cas du milieu qui manquait partout.
@ExtendWith(ApplicationExtension.class)
class BornesCriteresAudioTest {

    @Start
    void start(Stage stage) {
        // Toolkit JavaFX initialisé : les éditeurs de critères sont des nœuds (Slider, ComboBox).
    }

    // ------------------------------------------------------------------ seuil de probabilité

    @Test
    @DisplayName("#3520 : le seuil de probabilité garde la valeur EXACTEMENT égale (« au moins »)")
    void seuil_de_probabilite_est_inclusif() {
        AtomicReference<Predicate<LigneObservationAudio>> predicat = new AtomicReference<>();
        Node editeur = CriteresAudio.probabilite().editeur(predicat::set);
        curseur(editeur).setValue(0.9);

        assertThat(predicat.get().test(avecProbabilite(0.89))).isFalse();
        assertThat(predicat.get().test(avecProbabilite(0.90))).isTrue(); // la borne, et le cas qui manquait
        assertThat(predicat.get().test(avecProbabilite(0.91))).isTrue();
    }

    @Test
    @DisplayName("#3520 : une observation SANS probabilité passe tous les seuils")
    void sans_probabilite_traverse_le_seuil() {
        // La règle de l'écran, et elle surprend : écarter une détection sans probabilité reviendrait à
        // décider qu'elle est mauvaise alors qu'on n'en sait rien - en perdant une ligne à revoir.
        AtomicReference<Predicate<LigneObservationAudio>> predicat = new AtomicReference<>();
        Node editeur = CriteresAudio.probabilite().editeur(predicat::set);
        curseur(editeur).setValue(1.0);

        assertThat(predicat.get().test(avecProbabilite(null))).isTrue();
    }

    // ------------------------------------------------------------------ plage horaire

    @Test
    @DisplayName("#3520 : une plage sans passage à minuit inclut ses DEUX bornes")
    void plage_de_jour_inclut_ses_deux_bornes() {
        Predicate<LigneObservationAudio> entre9h17h = plage(9, 17);

        assertThat(entre9h17h.test(aHeure(8))).isFalse();
        assertThat(entre9h17h.test(aHeure(9))).isTrue(); // borne basse
        assertThat(entre9h17h.test(aHeure(13))).isTrue();
        assertThat(entre9h17h.test(aHeure(17))).isTrue(); // borne haute
        assertThat(entre9h17h.test(aHeure(18))).isFalse();
    }

    @Test
    @DisplayName("#3520 : une plage qui franchit minuit inclut ses deux bornes et exclut le milieu")
    void plage_de_nuit_franchit_minuit() {
        // Le défaut de l'écran : la nuit, 21 h → 6 h. C'est ici que `de > à` bascule sur l'autre branche.
        Predicate<LigneObservationAudio> nuit = plage(21, 6);

        assertThat(nuit.test(aHeure(20))).isFalse();
        assertThat(nuit.test(aHeure(21))).isTrue(); // borne du soir
        assertThat(nuit.test(aHeure(0))).isTrue(); // après minuit
        assertThat(nuit.test(aHeure(6))).isTrue(); // borne du matin
        assertThat(nuit.test(aHeure(7))).isFalse();
        assertThat(nuit.test(aHeure(13))).isFalse(); // plein jour, exclu des deux côtés
    }

    @Test
    @DisplayName("#3520 : une plage d'UNE heure (de == à) ne retient que cette heure-là")
    void plage_d_une_seule_heure() {
        // C'est ce cas qui tient la borne `de <= à` : la relâcher en `de < à` ferait basculer une plage
        // d'une heure sur la branche « franchit minuit », où `h >= 3 || h <= 3` retient TOUTE la journée.
        // Le filtre cesserait alors de filtrer, sans rien afficher d'anormal.
        Predicate<LigneObservationAudio> troisHeures = plage(3, 3);

        assertThat(troisHeures.test(aHeure(2))).isFalse();
        assertThat(troisHeures.test(aHeure(3))).isTrue();
        assertThat(troisHeures.test(aHeure(4))).isFalse();
    }

    @Test
    @DisplayName("#3520 : une observation SANS heure de capture traverse toute plage")
    void sans_heure_traverse_la_plage() {
        assertThat(plage(21, 6).test(aHeure(null))).isTrue();
        assertThat(plage(9, 17).test(aHeure(null))).isTrue();
    }

    // ------------------------------------------------------------------ liste des espèces

    @Test
    @DisplayName("#3520 : une séquence non identifiée ne peuple pas la liste des espèces")
    void sequence_non_identifiee_absente_de_la_liste_des_especes() {
        // La règle est écrite en commentaire dans le code depuis toujours ; rien ne la vérifiait, et le
        // mutant qui neutralise le filtre survivait. Une entrée sans taxon retenu polluerait la liste
        // déroulante d'un choix qui ne désigne rien.
        List<LigneObservationAudio> lignes = List.of(identifiee(1, "Rhifer", "Grand rhinolophe"), nonIdentifiee(2));

        // Le domaine du critère porte des enregistrements « espèce présente » (code + libellé), type
        // interne à `CriteresAudio` : on l'observe donc par sa forme rendue, pas par son type.
        ComboBox<?> choix = (ComboBox<?>) CriteresAudio.taxon(() -> lignes).editeur(predicat -> {});

        assertThat(choix.getItems()).singleElement().asString().contains("Rhifer", "Grand rhinolophe");
    }

    // ------------------------------------------------------------------ utilitaires

    private static Slider curseur(Node editeur) {
        return (Slider) ((HBox) editeur).getChildren().get(0);
    }

    /// Le prédicat de plage horaire, obtenu en posant les deux heures **par défaut** du critère : c'est
    /// le seul chemin public vers `dansPlage`, et il applique son prédicat dès la construction.
    private static Predicate<LigneObservationAudio> plage(int de, int a) {
        AtomicReference<Predicate<LigneObservationAudio>> predicat = new AtomicReference<>();
        CriteresAudio.heure(() -> Optional.of(new PlageNuit(de, a))).editeur(predicat::set);
        return predicat.get();
    }

    private static LigneObservationAudio avecProbabilite(Double proba) {
        return ligne(1, "Rhifer", "Grand rhinolophe", proba, LocalDateTime.of(2026, 6, 22, 23, 12));
    }

    private static LigneObservationAudio aHeure(Integer heure) {
        LocalDateTime capture = heure == null ? null : LocalDateTime.of(2026, 6, 22, heure, 12);
        return ligne(1, "Rhifer", "Grand rhinolophe", 0.8, capture);
    }

    private static LigneObservationAudio identifiee(long id, String taxon, String vernaculaire) {
        return ligne(id, taxon, vernaculaire, 0.8, LocalDateTime.of(2026, 6, 22, 23, 12));
    }

    /// Séquence **non identifiée** : ni Tadarida ni observateur n'ont retenu de taxon.
    private static LigneObservationAudio nonIdentifiee(long id) {
        return ligne(id, null, null, null, LocalDateTime.of(2026, 6, 22, 23, 30));
    }

    private static LigneObservationAudio ligne(
            long id, String taxon, String vernaculaire, Double proba, LocalDateTime capture) {
        return new LigneObservationAudio(
                id,
                id,
                1L,
                2,
                "2026-06-22",
                "640380",
                "A1",
                "Étang de la Tuilière",
                taxon,
                proba,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                null,
                vernaculaire,
                null,
                null,
                "Chiroptères",
                "seq" + id + ".wav",
                null,
                null,
                capture,
                false,
                null,
                null,
                null,
                null,
                0,
                "Ahetze");
    }
}

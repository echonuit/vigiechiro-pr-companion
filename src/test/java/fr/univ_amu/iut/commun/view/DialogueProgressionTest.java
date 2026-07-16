package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Orchestration de [DialogueProgression] (#1597), **sans fenêtre** : sur un [ExecuteurTacheSynchrone], on
/// vérifie que le succès ferme et restitue (et que la progression émise avance la barre), qu'une
/// annulation ferme **sans rien restituer** (renoncer n'est pas échouer), et qu'une erreur ferme et
/// remonte l'échec.
class DialogueProgressionTest {

    private final DialogueProgression dialogue = new DialogueProgression(new ExecuteurTacheSynchrone());

    @Test
    @DisplayName("Succès : la modale se ferme, le résultat est restitué, et la progression a avancé la barre")
    void succes_ferme_et_restitue() {
        AtomicBoolean ferme = new AtomicBoolean(false);
        List<String> journal = new ArrayList<>();
        ProgressionOperation progression = new ProgressionOperation();

        dialogue.executer(
                () -> ferme.set(true),
                new JetonAnnulation(),
                progression,
                (progres, jeton) -> {
                    progres.accept(new Progression("mi-chemin", 0.5));
                    return "ok";
                },
                resultat -> journal.add("succes:" + resultat),
                erreur -> journal.add("echec:" + erreur.getMessage()));

        assertThat(ferme).as("la modale se ferme à la fin").isTrue();
        assertThat(journal).containsExactly("succes:ok");
        assertThat(progression.fractionProperty().get())
                .as("le point de progression émis par le travail a avancé la barre")
                .isEqualTo(0.5);
    }

    @Test
    @DisplayName("Annulation : la modale se ferme, ni succès ni échec (renoncer n'est pas échouer)")
    void annulation_ferme_sans_rien_restituer() {
        AtomicBoolean ferme = new AtomicBoolean(false);
        List<String> journal = new ArrayList<>();
        JetonAnnulation jeton = new JetonAnnulation();
        jeton.annuler(); // l'utilisateur a cliqué « Annuler »

        dialogue.executer(
                () -> ferme.set(true),
                jeton,
                new ProgressionOperation(),
                (progres, j) -> {
                    j.leverSiAnnule(); // -> OperationAnnuleeException
                    return "ok";
                },
                resultat -> journal.add("succes"),
                erreur -> journal.add("echec"));

        assertThat(ferme).isTrue();
        assertThat(journal).as("une annulation ne restitue ni succès ni échec").isEmpty();
    }

    @Test
    @DisplayName("Erreur : la modale se ferme et l'échec est remonté")
    void erreur_ferme_et_remonte_l_echec() {
        AtomicBoolean ferme = new AtomicBoolean(false);
        List<String> journal = new ArrayList<>();

        dialogue.executer(
                () -> ferme.set(true),
                new JetonAnnulation(),
                new ProgressionOperation(),
                (progres, jeton) -> {
                    throw new IllegalStateException("boum");
                },
                resultat -> journal.add("succes"),
                erreur -> journal.add("echec:" + erreur.getMessage()));

        assertThat(ferme).isTrue();
        assertThat(journal).containsExactly("echec:boum");
    }
}

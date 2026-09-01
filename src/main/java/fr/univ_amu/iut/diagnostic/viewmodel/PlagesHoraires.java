package fr.univ_amu.iut.diagnostic.viewmodel;

import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/// Ce que l'observateur lit sous la fenêtre nocturne : ce que le protocole attendait, et ce qu'il a
/// obtenu.
///
/// Le story mapping le demandait depuis longtemps sans que ce soit livré ; l'écran ne montrait qu'un
/// verdict, qu'il fallait croire sur parole. Voir les deux plages permet de le comprendre au lieu de
/// le subir (#4988).
///
/// Rend une chaîne **vide** quand la vérification n'a pas pu se faire : un attendu montré sans son
/// obtenu laisserait croire à une mesure qui n'a pas eu lieu.
public final class PlagesHoraires {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private PlagesHoraires() {}

    /// Les deux plages sur une ligne, ou la chaîne vide si la cohérence est indisponible.
    public static String lisible(CoherenceHoraire coherence) {
        if (!coherence.disponible()) {
            return "";
        }
        return "Protocole : " + plage(coherence.debutExige(), coherence.finExigee()) + " · Enregistré : "
                + plage(coherence.debutEnregistre(), coherence.finEnregistree());
    }

    /// Une plage, bornes séparées par un mot et non par une flèche.
    ///
    /// `U+2192` n'est pas dans la Noto Sans embarquée : il partirait en repli vers une police du
    /// système, et deux utilisateurs ne verraient pas le même glyphe sans que rien ne le signale
    /// (ADR 0035). La ligne de commande la garde, son garde ne portant que sur l'IHM.
    private static String plage(java.time.LocalTime debut, java.time.LocalTime fin) {
        return HEURE.format(debut) + " à " + HEURE.format(fin);
    }
}

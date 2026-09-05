package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.diagnostic.model.AnalyseCoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/// La fenêtre à laquelle la couverture d'une nuit se compare, et **d'où elle vient** (#5055).
///
/// ## Pourquoi ce type existe
///
/// La qualification mesurait la couverture contre les heures **déclarées** du passage, quand le
/// diagnostic la mesurait contre le coucher et le lever réels. Deux écrans répondaient donc
/// différemment à la même question, et rien n'indiquait lequel croire.
///
/// Le calcul vit ici plutôt que dans `ServiceQualification` : il est **pur**, il se teste seul, et
/// l'y laisser faisait franchir à ce service le seuil de classe-dieu du portail.
public final class FenetreDeCouverture {

    private FenetreDeCouverture() {}

    /// Les bornes de la fenêtre, et si elles viennent des éphémérides.
    ///
    /// @param bornes début et fin
    /// @param depuisLesEphemerides vrai pour la fenêtre du protocole, faux pour le repli déclaré
    public record Fenetre(LocalDateTime[] bornes, boolean depuisLesEphemerides) {}

    /// La fenêtre du PROTOCOLE quand le point est géolocalisé, celle des heures déclarées sinon.
    ///
    /// **Le repli n'est pas un silence.** Sans coordonnées, la couverture se mesure encore sur les
    /// heures déclarées et le DIT. Le diagnostic, lui, se tait dans ce cas : les deux comportements
    /// sont défendables, et celui-ci a été choisi parce qu'un feu qui disparaît est un feu qu'on
    /// cesse de regarder.
    public static Optional<Fenetre> deReference(PointDEcoute point, Passage passage) {
        Optional<LocalDateTime[]> declarees = declaree(passage);
        if (point == null || declarees.isEmpty()) {
            return declarees.map(bornes -> new Fenetre(bornes, false));
        }
        CoherenceHoraire coherence = AnalyseCoherenceHoraire.analyser(
                point.latitude(),
                point.longitude(),
                passage.dateEnregistrement(),
                passage.heureDebut(),
                passage.heureFin());
        if (!coherence.disponible()) {
            return declarees.map(bornes -> new Fenetre(bornes, false));
        }
        return Optional.of(new Fenetre(datees(declarees.get(), coherence.debutExige(), coherence.finExigee()), true));
    }

    /// Les heures exigées portées sur les dates de la nuit.
    ///
    /// [CoherenceHoraire] ne rend que des heures : une fin qui n'est pas après son début est un
    /// lendemain, comme pour la fenêtre déclarée.
    private static LocalDateTime[] datees(LocalDateTime[] declarees, LocalTime debutExige, LocalTime finExigee) {
        LocalDate jour = declarees[0].toLocalDate();
        LocalDateTime debut = LocalDateTime.of(jour, debutExige);
        LocalDateTime fin = LocalDateTime.of(jour, finExigee);
        return new LocalDateTime[] {debut, fin.isAfter(debut) ? fin : fin.plusDays(1)};
    }

    /// Fenêtre théorique déduite de `start_time` et `end_time` du passage, ou rien si elles sont
    /// illisibles.
    private static Optional<LocalDateTime[]> declaree(Passage passage) {
        try {
            LocalDate date = LocalDate.parse(passage.dateEnregistrement());
            LocalDateTime debut = LocalDateTime.of(date, LocalTime.parse(passage.heureDebut()));
            LocalDateTime fin = LocalDateTime.of(date, LocalTime.parse(passage.heureFin()));
            return Optional.of(new LocalDateTime[] {debut, fin.isAfter(debut) ? fin : fin.plusDays(1)});
        } catch (RuntimeException invalide) {
            return Optional.empty();
        }
    }
}

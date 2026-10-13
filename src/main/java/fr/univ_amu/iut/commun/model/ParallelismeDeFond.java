package fr.univ_amu.iut.commun.model;

/// Le nombre de traitements qu'une tâche de fond **qui calcule** mène de front : la moitié des cœurs
/// du poste, l'autre moitié restant à l'interface (#6175, ADR 6175).
///
/// Une transformation audio occupe un cœur entier. Menées de front sur tous les cœurs, elles n'en
/// laissent aucun au fil JavaFX ni à celui qui dessine : l'écran gelait jusqu'à 442 ms pendant un
/// import, et la sonde du banc ne voyait plus que 16 à 42 images par seconde. À la moitié des cœurs,
/// plus aucun écart de plus de 100 ms ne tombe pendant la transformation, pour 15 % de durée en plus
/// sur une nuit de 20 Gio importée dans l'interface (`docs/benchmarks/README.md`).
///
/// Cette borne protège l'interface. Elle ne vaut que pour du calcul : une tâche qui attend le réseau
/// ou le disque se borne sur ce qu'elle ménage, pas sur les cœurs (ADR 0044).
public final class ParallelismeDeFond {

    private ParallelismeDeFond() {}

    /// La borne de ce poste, pour ses cœurs tels que la JVM les voit.
    public static int duPoste() {
        return pour(Runtime.getRuntime().availableProcessors());
    }

    /// La borne d'un poste de `coeurs` cœurs : leur moitié, arrondie au profit de l'interface, et jamais
    /// moins d'un traitement.
    static int pour(int coeurs) {
        if (coeurs < 1) {
            throw new IllegalArgumentException("un poste a au moins un cœur, reçu " + coeurs);
        }
        return Math.max(1, coeurs / 2);
    }
}

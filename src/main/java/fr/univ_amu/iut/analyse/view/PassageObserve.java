package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import java.time.LocalDate;
import java.util.Comparator;

/// Le passage d'une observation, tel que sa colonne le **trie** et l'**affiche** (#5901).
///
/// La colonne « Passage » portait une chaîne, `date · n°X`, avec la date telle que la base la stocke.
/// Elle y restait pour une raison qui se tient : une colonne de chaînes trie lexicalement, et l'ISO est
/// le seul format où ce tri reste chronologique. La franciser telle quelle aurait rangé `01/07/2026`
/// avant `22/06/2026`, sans rien faire rougir.
///
/// Comme [fr.univ_amu.iut.commun.view.ColonneDate], ce type sépare donc ce qui se trie de ce qui
/// s'affiche : la valeur de la cellule porte la date et le numéro, et se compare sur eux ; son libellé
/// est ce que la cellule dessine. Le comparateur vit avec la valeur, pas sur la colonne, où il se
/// perdrait à la première colonne recopiée.
///
/// @param date la date de la nuit, ou `null` si la base n'en porte pas de lisible
/// @param numero le numéro du passage dans l'année
/// @param libelle ce que la cellule affiche
record PassageObserve(LocalDate date, int numero, String libelle) implements Comparable<PassageObserve> {

    /// Dans le temps, puis par numéro de passage ; une date illisible en dernier.
    private static final Comparator<PassageObserve> ORDRE = Comparator.comparing(
                    PassageObserve::date, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparingInt(PassageObserve::numero);

    static PassageObserve de(ObservationEspece observation) {
        return new PassageObserve(
                Horodatage.dateDe(observation.dateEnregistrement()).orElse(null),
                observation.numeroPassage(),
                FormatAnalyse.libellePassage(observation));
    }

    @Override
    public int compareTo(PassageObserve autre) {
        return ORDRE.compare(this, autre);
    }

    /// Le libellé : c'est ce que rendent la cellule par défaut, la copie et l'export.
    @Override
    public String toString() {
        return libelle;
    }
}

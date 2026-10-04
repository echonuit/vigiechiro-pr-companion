package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.LecturePosition;
import fr.univ_amu.iut.sites.model.PositionCollee;
import java.util.Locale;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// La **position d'un point d'écoute**, saisie dans un seul champ (#5688).
///
/// Elle se lit par la règle de la déclaration du site, [PositionCollee] : une seule manière de saisir
/// des coordonnées dans toute l'interface. Extraite de [PointEditViewModel] pour la même raison que
/// [PositionColleeViewModel] l'a été de la modale de site : ce concern a son texte, sa lecture et son
/// motif, et ne partage rien avec le code ni la description du point.
///
/// Un champ vide est valide : la position d'un point reste optionnelle.
final class PositionSaisie {

    private static final double LATITUDE_MAX = 90.0;
    private static final double LONGITUDE_MAX = 180.0;

    private final StringProperty texte = new SimpleStringProperty(this, "position", "");
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retourPosition", RetourOperation.AUCUN);
    private final BooleanBinding valide;

    PositionSaisie() {
        valide = Bindings.createBooleanBinding(() -> motif(texte.get()).isEmpty(), texte);
        texte.addListener((observable, avant, apres) ->
                retour.set(motif(apres).map(RetourOperation::erreur).orElse(RetourOperation.AUCUN)));
    }

    StringProperty texte() {
        return texte;
    }

    ReadOnlyObjectProperty<RetourOperation> retour() {
        return retour.getReadOnlyProperty();
    }

    /// Vide, ou lue et dans les limites du globe.
    BooleanBinding valide() {
        return valide;
    }

    /// La position lue, `{latitude, longitude}`, vide quand le champ est vide ou refusé.
    Optional<double[]> coordonnees() {
        return lue(texte.get()).filter(position -> motif(texte.get()).isEmpty()).map(position ->
                new double[] {position.latitude(), position.longitude()});
    }

    /// Écrit une position dans le champ, sous la forme que la lecture relit : deux décimaux à six
    /// chiffres, point décimal. C'est ce que fait le marqueur qu'on glisse, et l'édition d'un point.
    void placer(double latitude, double longitude) {
        texte.set(enTexte(latitude, longitude));
    }

    static String enTexte(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude);
    }

    /// Ce qui empêche d'enregistrer ce texte, vide s'il n'y a rien.
    private static Optional<String> motif(String texte) {
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        LecturePosition lecture = PositionCollee.lire(texte);
        if (!(lecture instanceof LecturePosition.Lue position)) {
            return Optional.of(lecture.message());
        }
        if (Math.abs(position.latitude()) > LATITUDE_MAX) {
            return Optional.of("La latitude doit être comprise entre -90 et 90 : c'est le premier des deux nombres.");
        }
        if (Math.abs(position.longitude()) > LONGITUDE_MAX) {
            return Optional.of("La longitude doit être comprise entre -180 et 180 : c'est le second des deux nombres.");
        }
        return Optional.empty();
    }

    private static Optional<LecturePosition.Lue> lue(String texte) {
        if (texte == null || texte.isBlank()) {
            return Optional.empty();
        }
        return PositionCollee.lire(texte) instanceof LecturePosition.Lue position
                ? Optional.of(position)
                : Optional.empty();
    }
}

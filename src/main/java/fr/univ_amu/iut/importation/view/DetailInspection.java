package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.IconeSelonEtat;
import fr.univ_amu.iut.commun.view.IconesSeverite;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Label;
import org.kordamp.ikonli.javafx.FontIcon;

/// Une ligne d'inspection : ce que le dossier contient, et si c'est **présent ou absent**.
///
/// Les trois lignes de la section 2 (journal du capteur, relevé climatique, enregistrements WAV)
/// écrivaient leur présence sous forme de glyphe en tête du texte - `« ✓ Journal du capteur : … »`,
/// `« ⚠ Relevé climatique absent »`. Un caractère dans un libellé dépend des polices installées et ne
/// se teinte pas avec le texte ([ADR
/// 0035](../../../../../../../dev-docs/decisions/0035-un-pictogramme-est-une-icone-pas-un-caractere.md)).
///
/// Les classes `.insp-ok` (vert) et `.insp-absent` (ambre) **existaient déjà** dans `importation.css`,
/// sans aucun usage : la couleur avait été prévue pour dire cette présence, et le glyphe l'a dite à sa
/// place. Elles reprennent leur rôle, et l'icône vient de [IconesSeverite] - la même table que le
/// bandeau, le compte rendu et l'encart, pour qu'un même sens ait partout la même forme.
final class DetailInspection {

    private DetailInspection() {}

    /// Lie le libellé à son texte, son icône de présence et sa couleur.
    ///
    /// Le **glyphe** est délégué à [IconeSelonEtat], le composant du socle né de #1933 : il le lie par
    /// `Bindings.when`, donc l'icône suit l'état sans qu'aucun écouteur ait à survivre. La première
    /// version posait l'icône à la main sur un écouteur branché à `propriete.asObject()` - enveloppe que
    /// plus rien ne retenait après l'appel, donc collectée, donc figée : un journal **présent**
    /// s'affichait avec le triangle de l'absence. Une liaison ne peut pas avoir ce défaut.
    ///
    /// Reste ici ce que le socle ne fait pas : le texte, et la **couleur** portée par la classe. Celle-ci
    /// garde un écouteur, mais sur la propriété réelle - jamais sur une enveloppe temporaire.
    static void lier(Label label, FontIcon icone, ObservableBooleanValue present, ObservableStringValue texte) {
        label.textProperty().bind(texte);
        IconeSelonEtat.lier(
                icone, present, IconesSeverite.ikon(Severite.SUCCES), IconesSeverite.ikon(Severite.AVERTISSEMENT));
        colorer(label, present.get());
        present.addListener((observable, avant, apres) -> colorer(label, apres));
    }

    /// Lie un libellé dont l'objet est **toujours** présent : il n'a pas de branche « absent ».
    static void lierPresent(Label label, FontIcon icone, ObservableStringValue texte) {
        label.textProperty().bind(texte);
        icone.setIconCode(IconesSeverite.ikon(Severite.SUCCES));
        colorer(label, true);
    }

    private static void colorer(Label label, boolean present) {
        label.getStyleClass().setAll("insp-detail", present ? "insp-ok" : "insp-absent");
    }
}

package fr.univ_amu.iut.commun.viewmodel;

/// Le réglage du spectrogramme **adapté au daltonisme** (#1006), lu par tout écran qui affiche un son.
///
/// Il vivait dans l'onglet de réglages de la fonctionnalité audio, que seul Sons & validation lisait :
/// la Qualification, qui affiche le même son par le même composant, ne pouvait pas le lire sans
/// dépendre d'une autre fonctionnalité, et l'ignorait (#5603).
public final class ReglageDaltonien {

    /// Clé du réglage persistant.
    public static final String CLE = "audio.daltonien";

    /// Valeur par défaut : palette ordinaire.
    public static final boolean DEFAUT = false;

    private ReglageDaltonien() {}
}

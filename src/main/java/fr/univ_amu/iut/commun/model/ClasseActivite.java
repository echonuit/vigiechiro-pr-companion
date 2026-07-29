package fr.univ_amu.iut.commun.model;

/// Où se situe un nombre de contacts par rapport à ce qu'on observe habituellement (#2351).
///
/// Quatre classes, découpées par les quantiles du référentiel ACTICHIRO :
///
/// ```
/// Faible  <  Q25  ≤  Moyenne  <  Q75  ≤  Forte  <  Q98  ≤  Très forte
/// ```
///
/// **Ce n'est pas un niveau d'enjeu de conservation** (#2353), et les classes **ne se comparent pas
/// d'une espèce à l'autre** : la détectabilité varie trop d'un taxon au suivant pour qu'une « Forte »
/// de Pipistrelle commune et une « Forte » de Barbastelle disent la même chose. L'avertissement
/// accompagne la classe partout où elle se lit, écran comme export.
public enum ClasseActivite {
    FAIBLE("Faible"),
    MOYENNE("Moyenne"),
    FORTE("Forte"),
    TRES_FORTE("Très forte");

    private final String libelle;

    ClasseActivite(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /// La classe d'un nombre de contacts, d'après les trois quantiles.
    ///
    /// Les bornes sont **inclusives vers le haut** : un compte exactement égal à Q25 est « Moyenne »,
    /// pas « Faible ». C'est la convention du référentiel, et elle évite qu'une valeur pile sur le
    /// seuil tombe dans la classe inférieure — ce qui se lirait comme une sous-estimation.
    public static ClasseActivite de(int contacts, SeuilsActivite seuils) {
        if (contacts < seuils.q25()) {
            return FAIBLE;
        }
        if (contacts < seuils.q75()) {
            return MOYENNE;
        }
        return contacts < seuils.q98() ? FORTE : TRES_FORTE;
    }
}

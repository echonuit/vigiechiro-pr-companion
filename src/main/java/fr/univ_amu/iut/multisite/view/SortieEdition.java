package fr.univ_amu.iut.multisite.view;

/// Ce que l'utilisateur décide de ses positions déplacées quand il quitte le mode édition de la carte.
///
/// **Deux** décisions, et non trois. Le dialogue propose bien « Enregistrer / Abandonner / Annuler »,
/// mais « Annuler » n'est **pas une décision sur le travail** : c'est le refus de décider - on reste en
/// édition, rien n'est enregistré, rien n'est perdu. Il se modélise donc comme un
/// [java.util.Optional#empty()], exactement comme un sélecteur de fichier qu'on ferme (#1431).
///
/// C'est ce qui permet de le porter par [fr.univ_amu.iut.commun.view.DemandeurDeChoix] sans inventer un
/// contrat « à trois issues » qui n'aurait décrit qu'un seul écran.
enum SortieEdition {

    /// Persister les déplacements en attente, puis sortir du mode édition.
    ENREGISTRER("Enregistrer"),

    /// Jeter les déplacements en attente, puis sortir du mode édition.
    ABANDONNER("Abandonner");

    private final String libelle;

    SortieEdition(String libelle) {
        this.libelle = libelle;
    }

    /// Le texte du bouton, tel que l'utilisateur le lit.
    String libelle() {
        return libelle;
    }
}

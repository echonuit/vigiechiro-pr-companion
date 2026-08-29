package fr.univ_amu.iut.commun.model;

/// Méthode de constitution d'une sélection d'écoute (C11, R12).
///
/// [#REPARTITION_TEMPORELLE] est la méthode par défaut à l'ouverture de la vue de
/// vérification.
///
/// [#RECUE_D_UN_PAQUET] n'est pas une méthode de tirage : c'est une **provenance**. Elle dit que la
/// sélection est arrivée figée dans un paquet, et c'est ce qui permet de lui refuser la
/// régénération (ADR 4627).
public enum MethodeSelection {
    REPARTITION_TEMPORELLE("RéparTemporel"),
    ALEATOIRE("Aléatoire"),
    MANUEL("Manuel"),
    RECUE_D_UN_PAQUET("Reçue d'un paquet");

    private final String libelle;

    MethodeSelection(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    public static MethodeSelection parLibelle(String libelle) {
        for (MethodeSelection methode : values()) {
            if (methode.libelle.equals(libelle)) {
                return methode;
            }
        }
        throw new IllegalArgumentException("Méthode de sélection inconnue : " + libelle);
    }
}
